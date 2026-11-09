# It's Me HP (Android)

Aplikasi HP **milikmu sendiri** untuk mengakses HP dari dashboard It's Me — melihat
**layar** dan menelusuri **berkas** — tanpa aplikasi/pihak ketiga dan tanpa server
relay. HP menyambung keluar ke **dashboard It's Me-mu sendiri** (lewat domain/tunnel
sendiri atau WiFi yang sama), lalu muncul di tab **📱 HP**.

Aplikasi ini **terlihat** dan izinnya kamu berikan sendiri (bukan tersembunyi):
rekam layar lewat izin Android (ada ikon "merekam"), dan akses berkas lewat izin
"Akses semua berkas". Akses hilang kalau aplikasi di-uninstall atau HP di-reset.

## Ambil APK (tanpa toko aplikasi)

1. Buka tab **Releases** di repo ini → unduh **itsme-hp.apk** (dibangun otomatis
   oleh GitHub Actions dari kode di repo ini — jadi 100% milikmu).
2. Di HP, buka berkas APK-nya, izinkan **"Pasang aplikasi tidak dikenal"** sekali,
   lalu pasang.

## Pakai

### Cara tercepat: `siapkan-hp.cmd` (satu klik, tanpa dialog)
Kalau punya kabel USB sebentar saja (hanya untuk setup, HP tidak perlu tetap
tercolok sesudahnya):
1. Nyalakan **"USB debugging"** di HP (Setelan → Opsi pengembang) dan colok ke PC.
2. Taruh **itsme-hp-release.apk** (dari tab **Releases**) satu folder dengan
   `siapkan-hp.cmd`, lalu jalankan `siapkan-hp.cmd`.
3. Skrip ini **sekali jalan** memasang APK + memberi semua izin (termasuk
   **WRITE_SECURE_SETTINGS** dan membuka kunci "setelan terbatas") lewat adb —
   **tanpa dialog Accessibility, tanpa "izinkan setelan terbatas" manual**.
4. Buka aplikasi di HP → isi alamat dashboard + token → tekan **Kontrol layar**
   (nyala sendiri, tanpa dialog) → tekan **Mulai**.
5. (Opsional, HP tanpa akun Google) tekan **"Jadikan Device Owner"** di aplikasi
   untuk kiosk + izin runtime otomatis selamanya, tanpa menyambung USB lagi.

Kabel USB **hanya dipakai sekali saat setup** — sesudahnya HP lepas kabel dan
menyambung ke dashboard lewat WiFi/internet seperti biasa.

### Cara manual (tanpa komputer/USB)
1. Di komputer, buka dashboard It's Me → tab **📱 HP** → salin **token HP**.
2. Buka aplikasi **It's Me HP** di HP, isi:
   - **Alamat dashboard**: `https://domainku.com` (atau `http://100.x.y.z:8750` di WiFi sama).
   - **Nama HP**: bebas (mis. "HP Achmad").
   - **Token HP**: dari langkah 1.
3. Tekan **Mulai** → beri izin **rekam layar** (kalau Shizuku belum aktif — lihat
   di bawah). Untuk berkas, tekan **Izinkan akses semua berkas**.
4. Tekan **"Izinkan kontrol (Accessibility)"**. Kalau muncul **"Aplikasi tidak
   diberi akses"** (Android 13+), tekan dulu **"Izinkan setelan terbatas"** di
   aplikasi, baru ulangi — lihat panduan lengkap semua merek HP di
   [website/PANDUAN_IZIN_KONTROL.md](https://github.com/OBKENTUNG01/website/blob/main/jarvis/PANDUAN_IZIN_KONTROL.md).
5. Di dashboard, tab **HP** → HP-mu muncul 🟢. Buka **Layar** atau **Berkas**.

### Shizuku (opsional, tanpa root) — layar & sentuh tanpa dialog rekam layar
Kalau **Shizuku** terpasang & diaktifkan (sekali, lewat *Wireless debugging* di
HP, tanpa PC — lihat [shizuku.rikka.app](https://shizuku.rikka.app/)), aplikasi
memakainya untuk `screencap`/`input` setingkat adb: **tanpa dialog "mulai
merekam", tanpa MediaProjection**. Tanpa Shizuku, aplikasi tetap jalan seperti
biasa (Accessibility untuk sentuh, dialog rekam layar untuk lihat layar).

## Tahap

- **Fase 1:** sambung ke dashboard, **lihat layar**, **telusur & baca berkas**.
- **Fase 2:** **kontrol penuh** (sentuh & ketik) lewat layanan Accessibility.
- **Fase 3 (sekarang):** **tanpa dialog** — `siapkan-hp.cmd`/Device Owner memberi
  izin otomatis lewat adb (WRITE_SECURE_SETTINGS membuka Accessibility tanpa
  "setelan terbatas"); **Shizuku** (opsional) mengganti rekam-layar bergaya
  MediaProjection dengan `screencap`/`input` setingkat adb, tanpa dialog sama sekali.

## Catatan

- Hanya **HP milikmu sendiri**, dengan izin yang kamu berikan sendiri.
- Kontrol tanpa root memakai Accessibility (Fase 2); data privat aplikasi lain tetap
  butuh root (dibatasi Android).
- iPhone tidak didukung (dibatasi Apple).

## Android yang didukung & tanda tangan rilis

- **Berjalan di Android 8.0 sampai yang terbaru** (`minSdk 26`, `targetSdk 34`;
  `targetSdk` lama tetap jalan di Android 15/16+). Layanan latar sudah memakai tipe
  `mediaProjection` + izin `FOREGROUND_SERVICE_MEDIA_PROJECTION` (wajib sejak Android 14).
- **Tanda tangan rilis (supaya lebih tepercaya & update bisa menimpa versi lama):**
  1. Di komputermu (butuh Android Studio atau JDK 17+, dan [GitHub CLI](https://cli.github.com/)
     yang sudah `gh auth login`), dari folder repo ini jalankan:
     `powershell -ExecutionPolicy Bypass -File tools\buat-kunci.ps1`
     Skrip membuat kunci di `Documents\ItsMe-HP-kunci` (`itsme.keystore`, `SANDI.txt`,
     `SIDIK-JARI.txt`) dan langsung memasang 4 secret repo **tanpa menampilkan sandinya**.
     Tanpa GitHub CLI: jalankan dengan `-TanpaSecret`, lalu buat 4 secret di **Settings → Secrets
     and variables → Actions**: `ITSME_KEYSTORE_B64` (hasil
     `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\Documents\ItsMe-HP-kunci\itsme.keystore")) | Set-Clipboard`,
     lalu tempel), `ITSME_KEYSTORE_PASSWORD` dan `ITSME_KEY_PASSWORD` (isi baris kedua `SANDI.txt`),
     serta `ITSME_KEY_ALIAS` = `itsme`.
  2. Simpan salinan folder itu di tempat aman (flashdisk / pengelola sandi). Jangan commit,
     jangan unggah ke mana pun. Kalau kunci hilang, aplikasi di HP harus dipasang ulang
     (HP Device Owner: butuh reset pabrik).
  3. Build berikutnya (push, atau **Buat APK** di dashboard) otomatis memakai kunci itu. Di log
     build, langkah **Sidik jari tanda tangan APK** harus sama dengan isi `SIDIK-JARI.txt`.

  **Jangan** membuat kunci lewat GitHub Actions: artefak di repo publik bisa diunduh siapa pun
  yang punya akun GitHub (dulu ada workflow `make-keystore` yang melakukan itu; workflow itu
  sudah dihapus dan kuncinya diganti). **Mengganti kunci** (`tools\buat-kunci.ps1 -Timpa`)
  membuat APK baru **tidak bisa memperbarui** aplikasi yang sudah terpasang:
  - HP biasa: nonaktifkan dulu **admin perangkat** It's Me HP (Setelan → Keamanan → Aplikasi admin
    perangkat), copot aplikasinya, lalu pasang APK baru dan beri izinnya lagi.
  - HP yang dijadikan **Device Owner**: aplikasinya **tidak bisa dicopot** tanpa reset pabrik. Jangan
    ganti kunci sebelum ada rencana transisi, yaitu satu APK yang ditandatangani kunci lama + baru
    (rotasi kunci APK Signature Scheme v3, Android 9+) supaya HP menerima kunci baru sebagai pembaruan.

  Tanpa keystore, build tetap jalan tetapi APK ditandatangani kunci **debug** (kurang
  tepercaya). Catatan jujur: karena dipasang di luar Play Store, Android **tetap**
  menampilkan peringatan "sumber tidak dikenal"/Play Protect **sekali saat memasang
  APK-nya** — itu bawaan sideload (sama untuk TeamViewer/AnyDesk dkk di luar Play
  Store), bukan karena APK-nya berbahaya, dan tidak bisa dihindari oleh tanda tangan
  apa pun. Yang **BISA** dihindari (lewat `siapkan-hp.cmd`/Device Owner di atas):
  dialog izin Accessibility & "setelan terbatas" SESUDAH APK terpasang — itu dua hal
  berbeda. Tanda tangan rilis tetap berguna: update berikutnya mulus (menimpa versi
  lama) tanpa pasang ulang.

## Bangun sendiri (opsional)

Butuh Android SDK + JDK 17. Dari folder ini: `gradle assembleRelease`
(APK di `app/build/outputs/apk/release/`). Untuk APK bertanda tangan rilis, set env
`ITSME_KEYSTORE` (path keystore), `ITSME_KEYSTORE_PASSWORD`, `ITSME_KEY_ALIAS`,
`ITSME_KEY_PASSWORD` sebelum build; tanpa itu dipakai kunci debug.
