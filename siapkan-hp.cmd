@echo off
setlocal EnableDelayedExpansion
title Siapkan HP - It's Me (sekali saja)
cd /d "%~dp0"

REM ===========================================================================
REM  Satu klik: pasang APK + beri SEMUA izin lewat adb.
REM  Tanpa dialog, tanpa "setelan terbatas", tanpa memasang aplikasi lain.
REM  Syarat: kabel USB + "USB debugging" di HP + adb ada di PATH.
REM ===========================================================================

set PAKET=com.itsme.hp
set APK=itsme-hp-release.apk

where adb >nul 2>&1
if errorlevel 1 (
  echo [X] adb tidak ditemukan di PATH.
  echo     Unduh platform-tools: https://developer.android.com/tools/releases/platform-tools
  echo     Lalu tambahkan foldernya ke PATH, buka ulang jendela ini.
  pause & exit /b 1
)

echo [1/7] Menunggu HP terhubung...
adb wait-for-device
adb devices

if not exist "%APK%" (
  echo [X] %APK% tidak ada di folder ini.
  pause & exit /b 1
)

echo [2/7] Memasang APK (adb install melewati dialog Play Protect)...
adb install -r "%APK%" || goto :gagal

echo [3/7] Izin tulis setelan (membuat Accessibility bisa dinyalakan aplikasi sendiri)...
adb shell pm grant %PAKET% android.permission.WRITE_SECURE_SETTINGS

echo [4/7] Akses semua berkas...
adb shell appops set %PAKET% MANAGE_EXTERNAL_STORAGE allow

echo [5/7] Membuka kunci "setelan terbatas" (Android 13+)...
adb shell appops set %PAKET% ACCESS_RESTRICTED_SETTINGS allow

echo [6/7] Izin notifikasi + layanan latar...
adb shell pm grant %PAKET% android.permission.POST_NOTIFICATIONS 2>nul

echo [7/7] Memulai aplikasi...
adb shell monkey -p %PAKET% -c android.intent.category.LAUNCHER 1 >nul 2>&1

echo.
echo ============================================================
echo  SELESAI. Buka aplikasi di HP, lalu:
echo    1) Isi alamat dashboard + token
echo    2) Tekan "Kontrol layar"  -^> nyala sendiri, tanpa dialog
echo    3) Tekan "Mulai"          -^> HP muncul di dashboard
echo.
echo  Layar ^& sentuh akan jalan lewat Shizuku (tanpa dialog rekam
echo  layar) setelah Shizuku dipasang SEKALI dan diizinkan.
echo ============================================================
pause
exit /b 0

:gagal
echo.
echo [X] Pemasangan gagal. Kemungkinan: APK di HP ini ditandatangani
echo     kunci lain. Cabut dulu aplikasi lama:
echo        adb uninstall %PAKET%
echo     lalu jalankan skrip ini lagi.
pause
exit /b 1
