# Panduan Mengizinkan "Kontrol" (Accessibility) It's Me HP

Berlaku untuk **semua merek & seri HP, Android 8 ke atas**. "Kontrol" = sentuh/geser/ketik
dari dashboard; ini butuh izin **Accessibility**. "Lihat layar" sudah jalan tanpa ini.

> Kenapa ribet? Mulai **Android 13**, Google memblokir izin Accessibility untuk aplikasi
> yang dipasang **di luar Play Store** (sideload). Ini aturan keamanan OS untuk SEMUA
> aplikasi (TeamViewer, AnyDesk, dll) — bukan bug It's Me HP, dan tidak bisa dilewati
> dari dalam aplikasi. Caranya: kamu buka izinnya **sekali**; setelah itu tetap aktif.

Catatan paket: perintah di bawah memakai `<PAKET>` = **Application ID** aplikasimu
(yang kamu isi di "Buat APK"). Bawaan = `com.itsme.hp`. Kalau kamu ganti (mis. `com.mantap`),
pakai itu. Komponen layanan selalu: `<PAKET>/com.itsme.hp.HpAccessibility`.

---

## Cara A - Android 8, 9, 10, 11, 12 (paling mudah, langsung)
Belum ada blokir "setelan terbatas" di versi ini.

1. **Setelan - Aksesibilitas** (di sebagian HP: **Setelan - Setelan tambahan - Aksesibilitas**).
2. Buka **Layanan terpasang / Aplikasi yang didownload**.
3. Ketuk **"It's Me HP (kontrol)"** - geser **AKTIF** - **Izinkan**.

Jalur per merek:
- **Samsung**: Setelan - Aksesibilitas - Aplikasi terpasang.
- **Xiaomi/Redmi/POCO (MIUI)**: Setelan - Setelan tambahan - Aksesibilitas - Terdownload.
- **Oppo/Realme (ColorOS)**: Setelan - Setelan tambahan - Aksesibilitas.
- **Vivo (Funtouch)**: Setelan - Aksesibilitas.

---

## Cara B - Android 13, 14, 15+ : "Izinkan setelan terbatas"
Kalau saat menyalakan toggle muncul **"Aplikasi tidak diberi akses"**:

1. Tekan **Tutup** pada dialog itu.
2. Buka **info aplikasi**: tekan-tahan ikon aplikasi di layar - **Info aplikasi (i)**.
   (atau Setelan - Aplikasi - [nama aplikasimu]).
3. Tekan **titik tiga (titik tiga di pojok kanan atas)**.
4. Pilih **"Izinkan setelan terbatas"** ("Allow restricted settings").
5. Kembali ke **Aksesibilitas** (Cara A) - nyalakan togglenya. Kini tidak diblokir.

Jalur per merek (lokasi "Izinkan setelan terbatas"):
- **Pixel / Android stock / Motorola / Nothing**: info aplikasi - titik tiga - Allow restricted settings.
- **Samsung (OneUI)**: info aplikasi - titik tiga - Allow restricted settings.
- **Oppo/Realme (ColorOS) & Xiaomi (MIUI/HyperOS)**: SERING menyembunyikan opsi ini.
  Kalau tidak ada di titik tiga - lompat ke **Cara C** (tanpa komputer) atau **Cara D** (dengan komputer).

---

## Cara C - TANPA komputer (Android 11+), kalau Cara B tidak ada
Dipakai saat merek HP menyembunyikan "Izinkan setelan terbatas" (umum di ColorOS/MIUI).
Memakai **LADB** - aplikasi debug nirkabel yang jalan DI HP sendiri. Sah & aman, tanpa PC.

**Tahap 1 - nyalakan Opsi pengembang + Debug nirkabel**
1. **Setelan - Tentang perangkat - Versi** - ketuk **"Nomor bentukan / Build number" 7x cepat**
   sampai muncul "Anda sekarang pengembang".
2. Buka **Opsi pengembang** (biasanya di Setelan - Setelan tambahan / Setelan sistem).
3. Nyalakan **"Debug nirkabel" (Wireless debugging)** - **Izinkan**.

**Tahap 2 - pasang LADB**
4. Buka **Play Store** - cari **"LADB"** (oleh Charles Lombardo) - **Pasang** - buka.

**Tahap 3 - pasangkan (pairing)**
5. Buka DUA layar bersamaan: Debug nirkabel & LADB.
6. Di **Debug nirkabel** ketuk **"Pasangkan perangkat dengan kode pemasangan"**.
   Muncul: **kode 6 angka** + **IP:port** (mis. 192.168.1.5:37xxx).
7. Di **LADB**, saat diminta, masukkan **port + kode** itu (ikuti kolom yang muncul).
   Kalau berhasil, LADB menampilkan prompt siap ketik (`$`).

**Tahap 4 - jalankan 2 perintah**
8. Dulu cari paket aplikasimu (salin-tempel):
   ```
   pm list packages | grep -iE "itsme|mantap|aplikasi"
   ```
   Catat hasilnya, mis. `package:com.itsme.hp` atau `package:com.mantap`. Itu `<PAKET>`-mu.
9. Lalu ketik (ganti `<PAKET>` dengan milikmu):
   ```
   settings put secure enabled_accessibility_services <PAKET>/com.itsme.hp.HpAccessibility
   settings put secure accessibility_enabled 1
   ```
10. Buka aplikasi It's Me HP - **kontrol sekarang aktif**. Selesai, tanpa komputer, selamanya.

---

## Cara D - DENGAN komputer (USB), tercepat, semua versi Android
1. HP: nyalakan **Opsi pengembang** (Cara C tahap 1) lalu **"USB debugging"**.
2. Komputer: pasang **platform-tools (adb)** dari https://developer.android.com/tools/releases/platform-tools
3. Colok HP ke PC pakai USB - di HP tekan **Izinkan** (Allow USB debugging).
4. Di komputer jalankan (ganti `<PAKET>`):
   ```
   adb shell settings put secure enabled_accessibility_services <PAKET>/com.itsme.hp.HpAccessibility
   adb shell settings put secure accessibility_enabled 1
   ```

---

## Setelah kontrol aktif
- Izin ini **sekali saja** dan tetap aktif setelahnya (kecuali kamu copot aplikasinya).
- Untuk "lihat layar": buka aplikasi - **Mulai** - izinkan **"rekam layar"** (yang ini per sesi,
  jadi diulang tiap aplikasi dinyalakan ulang).
- Di dashboard tab **HP**, centang **"Kendalikan (sentuh & ketik)"** lalu klik/geser di layar.
