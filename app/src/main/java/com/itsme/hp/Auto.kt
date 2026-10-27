package com.itsme.hp

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/** Izin yang biasanya butuh dialog, dikerjakan lewat hak "adb shell".
 *
 *  Sekali dari komputer (tanpa root, tanpa Shizuku, tanpa aplikasi lain):
 *      adb shell pm grant com.itsme.hp android.permission.WRITE_SECURE_SETTINGS
 *
 *  Sesudah itu layanan kontrol (Accessibility) bisa dinyalakan aplikasi sendiri,
 *  jadi tidak ada lagi layar "setelan terbatas" / "aplikasi ini berisiko".
 */
object Auto {
    private fun svc(c: Context) = "${c.packageName}/${HpAccessibility::class.java.name}"

    fun canWriteSecure(c: Context): Boolean =
        c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** True kalau layanan kontrol berhasil dinyalakan tanpa dialog. */
    fun enableAccessibility(c: Context): Boolean {
        if (!canWriteSecure(c)) return false
        return try {
            val mine = svc(c)
            val now = Settings.Secure.getString(
                c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val daftar = now.split(':').map { it.trim() }.filter { it.isNotEmpty() }
            if (mine !in daftar) {
                Settings.Secure.putString(
                    c.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    (daftar + mine).joinToString(":"),
                )
            }
            Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun adbLine(c: Context) = "adb shell pm grant ${c.packageName} android.permission.WRITE_SECURE_SETTINGS"
}