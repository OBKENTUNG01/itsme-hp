package com.itsme.hp

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import org.json.JSONArray
import org.json.JSONObject

/** Kemampuan **Device Owner** — tanpa root, tanpa dialog izin.
 *
 *  Hanya berlaku kalau aplikasi ini sudah jadi Device Owner, sekali saja lewat ADB:
 *  `adb shell dpm set-device-owner com.itsme.hp/.HpAdmin` (HP harus tanpa akun).
 *  Semua fungsi mengembalikan JSONObject `{ok, message, ...}` dan tidak pernah melempar.
 */
object Owner {

    private fun dpm(c: Context) =
        c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(c: Context) = ComponentName(c, HpAdmin::class.java)

    private fun ok(msg: String) = JSONObject().put("ok", true).put("message", msg)

    private fun err(msg: String?) = JSONObject().put("ok", false).put("message", msg ?: "gagal")

    /** null = boleh lanjut; JSONObject = penolakan yang siap dikirim balik. */
    private fun guard(c: Context): JSONObject? =
        if (dpm(c).isDeviceOwnerApp(c.packageName)) null
        else err("Bukan Device Owner. Sekali saja lewat ADB: adb shell dpm set-device-owner ${c.packageName}/.HpAdmin")

    private fun isRuntime(pm: PackageManager, p: String): Boolean = try {
        (pm.getPermissionInfo(p, 0).protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) ==
            PermissionInfo.PROTECTION_DANGEROUS
    } catch (_: Exception) {
        false
    }

    /** Beri semua izin runtime yang diminta manifest tanpa dialog. */
    fun grantOwnPermissions(c: Context): JSONObject = try {
        guard(c) ?: run {
            val pm = c.packageManager
            @Suppress("DEPRECATION")
            val requested = pm.getPackageInfo(c.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions ?: emptyArray()
            val failed = JSONArray()
            var granted = 0
            for (p in requested) {
                if (!isRuntime(pm, p)) continue
                try {
                    val done = dpm(c).setPermissionGrantState(
                        admin(c), c.packageName, p, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    )
                    if (done) granted++ else failed.put(p)
                } catch (e: Exception) {
                    failed.put("$p: ${e.message}")
                }
            }
            JSONObject()
                .put("ok", failed.length() == 0)
                .put("granted", granted)
                .put("failed", failed)
                .put("message", "Izin diberikan otomatis: $granted" +
                    if (failed.length() > 0) ", gagal: ${failed.length()}" else "")
        }
    } catch (e: Exception) {
        err(e.message)
    }

    /** Kiosk: izinkan lock task untuk paket ini + matikan status bar. */
    fun setKiosk(c: Context, enable: Boolean): JSONObject = try {
        guard(c) ?: run {
            val d = dpm(c)
            d.setLockTaskPackages(admin(c), if (enable) arrayOf(c.packageName) else emptyArray<String>())
            d.setStatusBarDisabled(admin(c), enable)
            ok(if (enable) "Kiosk aktif: lock task + status bar dimatikan." else "Kiosk dimatikan.")
        }
    } catch (e: Exception) {
        err(e.message)
    }

    fun status(c: Context): JSONObject = try {
        val d = dpm(c)
        val owner = d.isDeviceOwnerApp(c.packageName)
        JSONObject()
            .put("ok", true)
            .put("isDeviceOwner", owner)
            .put("isAdminActive", d.isAdminActive(admin(c)))
            .put("isLockTaskPermitted", d.isLockTaskPermitted(c.packageName))
            .put("message", if (owner) "Device Owner aktif."
                else "Belum Device Owner. Sekali saja lewat ADB: adb shell dpm set-device-owner ${c.packageName}/.HpAdmin")
    } catch (e: Exception) {
        err(e.message)
    }
}
