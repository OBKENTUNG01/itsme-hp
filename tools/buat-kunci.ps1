<#
.SYNOPSIS
  Membuat kunci tanda tangan RILIS It's Me HP di komputer INI, lalu memasangnya sebagai secret repo GitHub.

.DESCRIPTION
  Android hanya mau MEMPERBARUI aplikasi bila APK baru ditandatangani kunci yang sama dengan yang terpasang.
  Siapa pun yang memegang kunci ini bisa membuat APK yang diterima HP-mu sebagai "pembaruan" It's Me HP,
  jadi kunci ini RAHASIA:
    - dibuat di komputermu, BUKAN lewat GitHub Actions (artefak di repo publik bisa diunduh siapa pun
      yang punya akun GitHub);
    - sandinya tidak pernah tampil di layar, tidak masuk baris perintah, dan tidak masuk log;
    - cadangannya disimpan di folder pribadimu (bawaan: Documents\ItsMe-HP-kunci).
  Mengganti kunci berarti APK baru TIDAK bisa memperbarui aplikasi yang sudah terpasang. Di HP biasa:
  nonaktifkan dulu "admin perangkat" It's Me HP, copot aplikasinya, lalu pasang APK baru. Di HP yang
  dijadikan Device Owner aplikasinya tidak bisa dicopot tanpa reset pabrik: lihat README sebelum mengganti.

  Butuh: keytool (ikut Android Studio atau JDK 17+) dan, untuk memasang secret, GitHub CLI (gh) yang sudah login.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\buat-kunci.ps1
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\buat-kunci.ps1 -TanpaSecret
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\buat-kunci.ps1 -Timpa
  (mengganti kunci yang sudah ada; kunci lama disimpan sebagai cadangan *.lama-<waktu>)
#>
[CmdletBinding()]
param(
    [string]$Repo = "OBKENTUNG01/itsme-hp",
    [string]$Folder = (Join-Path ([Environment]::GetFolderPath("MyDocuments")) "ItsMe-HP-kunci"),
    [switch]$TanpaSecret,
    [switch]$Timpa
)

$ErrorActionPreference = "Stop"
# Path relatif dihitung dari lokasi PowerShell, sama untuk cmdlet DAN [IO.File] (yang memakai direktori proses).
$Folder = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Folder)
$Alias = "itsme"
$SandiEnv = "ITSME_KS_SANDI_SEMENTARA"

function Find-Keytool {
    $calon = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { $calon.Add((Join-Path $env:JAVA_HOME "bin\keytool.exe")) }
    $cmd = Get-Command keytool.exe -ErrorAction SilentlyContinue
    if ($cmd) { $calon.Add($cmd.Source) }
    $calon.Add((Join-Path $env:ProgramFiles "Android\Android Studio\jbr\bin\keytool.exe"))
    if ($env:LOCALAPPDATA) { $calon.Add((Join-Path $env:LOCALAPPDATA "Programs\Android Studio\jbr\bin\keytool.exe")) }
    foreach ($akar in @("Java", "Eclipse Adoptium", "Microsoft", "Zulu", "BellSoft")) {
        $dir = Join-Path $env:ProgramFiles $akar
        if (Test-Path $dir) {
            Get-ChildItem -Path $dir -Filter keytool.exe -Recurse -Depth 3 -ErrorAction SilentlyContinue |
                ForEach-Object { $calon.Add($_.FullName) }
        }
    }
    foreach ($p in $calon) { if ($p -and (Test-Path $p)) { return $p } }
    return $null
}

function Invoke-Program {
    param([string]$Exe, [string[]]$Arguments)
    # Di Windows PowerShell 5.1, teks stderr program asli dianggap galat kalau ErrorActionPreference=Stop.
    $lama = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = & $Exe @Arguments 2>&1 | ForEach-Object { "$_" }
        return @{ Code = $LASTEXITCODE; Out = ($out -join "`n") }
    } finally {
        $ErrorActionPreference = $lama
    }
}

function New-Sandi {
    param([int]$Panjang = 32)
    $huruf = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".ToCharArray()
    $acak = New-Object byte[] ($Panjang * 4)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($acak) } finally { $rng.Dispose() }
    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $Panjang; $i++) {
        [void]$sb.Append($huruf[[BitConverter]::ToUInt32($acak, $i * 4) % [uint32]$huruf.Length])
    }
    return $sb.ToString()
}

# --- periksa alat dulu, SEBELUM membuat apa pun -------------------------------------------------
$keytool = Find-Keytool
if (-not $keytool) {
    throw "keytool tidak ditemukan. Pasang Android Studio atau JDK 17+, lalu jalankan lagi (atau set JAVA_HOME)."
}
Write-Host "keytool : $keytool"
$gh = $null
if (-not $TanpaSecret) {
    $gh = Get-Command gh.exe -ErrorAction SilentlyContinue
    if (-not $gh) {
        throw "GitHub CLI (gh) tidak ditemukan. Pasang gh lalu 'gh auth login', atau jalankan dengan -TanpaSecret dan pasang secret manual (lihat README)."
    }
    $r = Invoke-Program $gh.Source @("auth", "status")
    if ($r.Code -ne 0) { throw "gh belum login. Jalankan 'gh auth login' dulu." }
}

$ks = Join-Path $Folder "itsme.keystore"
$sandiFile = Join-Path $Folder "SANDI.txt"
$sidikFile = Join-Path $Folder "SIDIK-JARI.txt"
if ((Test-Path $ks) -and -not $Timpa) {
    throw "Sudah ada kunci di $ks dan TIDAK ditimpa. Kalau memang mau mengganti kunci, jalankan lagi dengan -Timpa."
}
New-Item -ItemType Directory -Force -Path $Folder | Out-Null
if (Test-Path $ks) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    foreach ($f in @($ks, $sandiFile, $sidikFile)) {
        if (Test-Path $f) { Move-Item -Path $f -Destination "$f.lama-$stamp" }
    }
    Write-Host "Kunci lama disimpan sebagai cadangan (*.lama-$stamp)."
}

# --- buat kunci: sandi lewat variabel lingkungan sementara, tidak pernah di baris perintah --------
$sandi = New-Sandi 32
Set-Item -Path "Env:$SandiEnv" -Value $sandi
try {
    $r = Invoke-Program $keytool @("-genkeypair", "-keystore", $ks, "-storetype", "PKCS12", "-alias", $Alias,
        "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000",
        "-storepass:env", $SandiEnv, "-keypass:env", $SandiEnv,
        "-dname", "CN=ItsMe HP, OU=ItsMe, O=ItsMe, C=ID")
    if ($r.Code -ne 0 -or -not (Test-Path $ks)) { throw "keytool gagal membuat kunci (kode $($r.Code)): $($r.Out)" }

    $r = Invoke-Program $keytool @("-list", "-v", "-keystore", $ks, "-storetype", "PKCS12", "-alias", $Alias,
        "-storepass:env", $SandiEnv)
    $m = [regex]::Match($r.Out, "SHA256:\s*([0-9A-Fa-f:]{95})")
    if ($r.Code -ne 0 -or -not $m.Success) { throw "Kunci dibuat tetapi tidak bisa dibaca ulang (kode $($r.Code))." }
    $sidik = $m.Groups[1].Value.ToUpper()
    $sidikHex = $sidik.Replace(":", "").ToLower()

    [IO.File]::WriteAllText($sandiFile, "Sandi keystore DAN key (alias '$Alias') untuk itsme.keystore:`r`n$sandi`r`n`r`n" +
        "JANGAN dibagikan, JANGAN di-commit. Simpan salinan folder ini di tempat aman (mis. flashdisk atau pengelola sandi).`r`n")
    [IO.File]::WriteAllText($sidikFile, "Sidik jari SHA-256 sertifikat penanda tangan It's Me HP (boleh dibagikan, bukan rahasia):`r`n" +
        "$sidik`r`n(format apksigner: $sidikHex)`r`nDibuat: $(Get-Date -Format s)`r`n")
    Write-Host "Kunci   : $ks"
    Write-Host "Sidik   : $sidik"

    # --- pasang secret repo: lewat berkas sementara (gh secret set -f), lalu berkas itu dihapus -----
    if ($TanpaSecret) {
        Write-Host "Secret GitHub TIDAK diubah (-TanpaSecret)."
    } else {
        $tmp = Join-Path ([IO.Path]::GetTempPath()) ("itsme-secret-" + [guid]::NewGuid().ToString("N") + ".env")
        try {
            $b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($ks))
            [IO.File]::WriteAllText($tmp, "ITSME_KEYSTORE_B64=$b64`nITSME_KEYSTORE_PASSWORD=$sandi`n" +
                "ITSME_KEY_ALIAS=$Alias`nITSME_KEY_PASSWORD=$sandi`n")
            $r = Invoke-Program $gh.Source @("secret", "set", "-f", $tmp, "-R", $Repo)
            if ($r.Code -ne 0) { throw "gh gagal memasang secret (kode $($r.Code)): $($r.Out)" }
        } finally {
            if (Test-Path $tmp) { Remove-Item -Force $tmp }
            $b64 = $null
        }
        $r = Invoke-Program $gh.Source @("secret", "list", "-R", $Repo)
        Write-Host "Secret di $Repo (nama dan waktu diperbarui):"
        $r.Out -split "`n" | Where-Object { $_ -match "ITSME_" } | ForEach-Object { Write-Host "  $_" }
    }
} finally {
    Remove-Item -Path "Env:$SandiEnv" -ErrorAction SilentlyContinue
    $sandi = $null
}

Write-Host ""
Write-Host "Selesai. Build APK berikutnya (push ke main, atau Buat APK di dashboard) memakai kunci ini."
Write-Host "Cocokkan sidik jari di log build (langkah 'Sidik jari tanda tangan APK') dengan $sidikFile."
Write-Host "PENTING: simpan salinan folder $Folder di tempat aman. Kalau kunci hilang, aplikasi di HP harus dipasang ulang"
Write-Host "(HP Device Owner: butuh reset pabrik)."
