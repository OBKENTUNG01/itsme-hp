package com.itsme.hp

import android.content.pm.PackageManager
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

/** Perintah shell lewat Shizuku: hak `adb shell` tanpa root.
 *
 *  Shizuku harus dipasang dan diaktifkan SEKALI oleh pemilik lewat Wireless
 *  debugging (Android 11+, tanpa PC). Sesudah aktif, aplikasi ini bisa menjalankan
 *  `input`, `screencap`, `pm`, `settings`, `am`, `dumpsys` seperti adb — tanpa root,
 *  tanpa Play Store, tanpa Google Console.
 */
object ShizukuShell {
    const val REQ = 4210
    private const val MAX_OUT = 40000

    fun available(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun granted(): Boolean = try {
        available() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** Minta izin; dialog Shizuku muncul sendiri. Hasil dicek lagi di panggilan berikutnya. */
    fun request(): Boolean = try {
        if (!available()) false
        else if (granted()) true
        else { Shizuku.requestPermission(REQ); granted() }
    } catch (_: Throwable) {
        false
    }

    /** Jalankan satu perintah di shell (uid adb). */
    fun exec(cmd: String, timeoutMs: Long = 15000): JSONObject {
        if (!granted()) return error("Shizuku belum aktif atau belum diizinkan")
        val p = try {
            remoteProcess(arrayOf("sh", "-c", cmd))
        } catch (e: Throwable) {
            return error(e.message ?: e.javaClass.simpleName)
        }
        val out = StringBuilder()
        val reader = Thread {
            try {
                BufferedReader(InputStreamReader(p.inputStream)).use { r ->
                    r.forEachLine { if (out.length < MAX_OUT) out.append(it).append('\n') }
                }
            } catch (_: Throwable) {
            }
        }
        reader.start()
        val finished = waitFor(p, timeoutMs)
        if (!finished) p.destroy()
        reader.join(500)
        val ok = try { finished && p.exitValue() == 0 } catch (_: Throwable) { false }
        return JSONObject().put("ok", ok).put("out", out.toString())
    }

    private fun error(msg: String) = JSONObject().put("ok", false).put("error", msg)

    // ponytail: Shizuku 13.1.5 menandai newProcess() privat, padahal ShizukuRemoteProcess
    // (extends java.lang.Process) publik. Panggil lewat reflection; kalau Shizuku mengganti
    // nama/ signature, ganti ke UserService (bindUserService) yang resmi.
    private val newProcessMethod: Method? by lazy {
        try {
            Shizuku::class.java
                .getDeclaredMethod("newProcess", Array<String>::class.java,
                    Array<String>::class.java, String::class.java)
                .apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }

    private fun remoteProcess(cmd: Array<String>): Process {
        val m = newProcessMethod ?: throw IllegalStateException("Shizuku tanpa newProcess")
        return m.invoke(null, cmd, null, null) as Process
    }

    private fun waitFor(p: Process, ms: Long): Boolean {
        val deadline = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < deadline) {
            try {
                p.exitValue(); return true
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(50)
            }
        }
        return false
    }
}
