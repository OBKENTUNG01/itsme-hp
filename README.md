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

1. Di komputer, buka dashboard It's Me → tab **📱 HP** → salin **token HP**.
2. Buka aplikasi **It's Me HP** di HP, isi:
   - **Alamat dashboard**: `https://domainku.com` (atau `http://100.x.y.z:8750` di WiFi sama).
   - **Nama HP**: bebas (mis. "HP Achmad").
   - **Token HP**: dari langkah 1.
3. Tekan **Mulai** → beri izin **rekam layar**. (Untuk berkas, tekan **Izinkan akses
   semua berkas**.)
4. Di dashboard, tab **HP** → HP-mu muncul 🟢. Buka **Layar** atau **Berkas**.

## Tahap

- **Fase 1 (sekarang):** sambung ke dashboard, **lihat layar**, **telusur & baca berkas**.
- **Fase 2 (berikutnya):** **kontrol penuh** (sentuh & ketik) lewat layanan Accessibility.

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
  1. Buka tab **Actions** repo ini → **make-keystore** → **Run workflow** (sekali saja).
  2. Unduh artifact **itsme-keystore**, buka **SECRETS.txt**, lalu tambahkan **4 secret**
     yang tertera di **Settings → Secrets and variables → Actions**. Simpan berkas
     `itsme.keystore` sebagai cadangan (jangan commit).
  3. Build berikutnya (push, atau **Buat APK** di dashboard) otomatis memakai kunci itu.

  Tanpa keystore, build tetap jalan tetapi APK ditandatangani kunci **debug** (kurang
  tepercaya). Catatan jujur: karena dipasang di luar Play Store dan meminta izin layar +
  Accessibility + semua berkas, Android **tetap** menampilkan peringatan "sumber tidak
  dikenal"/Play Protect sekali saat memasang — itu bawaan sideload, bukan karena APK-nya
  berbahaya. Tanda tangan rilis yang tetap mengurangi gesekan dan membuat update mulus.

## Bangun sendiri (opsional)

Butuh Android SDK + JDK 17. Dari folder ini: `gradle assembleRelease`
(APK di `app/build/outputs/apk/release/`). Untuk APK bertanda tangan rilis, set env
`ITSME_KEYSTORE` (path keystore), `ITSME_KEYSTORE_PASSWORD`, `ITSME_KEY_ALIAS`,
`ITSME_KEY_PASSWORD` sebelum build; tanpa itu dipakai kunci debug.
