package com.itsme.hp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Layanan latar: menahan koneksi ke dashboard (long-poll) dan menjawab perintah
 *  (ambil layar, telusur/baca berkas). Terlihat sebagai notifikasi — tidak tersembunyi. */
class HpService : Service() {
    @Volatile private var running = false
    private var capture: ScreenCapture? = null
    private var base = ""
    private var token = ""
    private var name = "HP"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            stopSelf()
            return START_NOT_STICKY
        }
        val prefs = getSharedPreferences("itsme", Context.MODE_PRIVATE)
        base = intent?.getStringExtra("url") ?: prefs.getString("url", "") ?: ""
        token = intent?.getStringExtra("token") ?: prefs.getString("token", "") ?: ""
        name = (intent?.getStringExtra("name") ?: prefs.getString("name", "HP") ?: "HP").ifBlank { "HP" }

        startInForeground()

        val code = intent?.getIntExtra("resultCode", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (code != 0 && data != null && capture == null) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(code, data)
            proj.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { capture = null }
            }, null)
            capture = ScreenCapture(proj, metrics())
        }

        if (!running && base.isNotBlank() && token.isNotBlank()) {
            running = true
            Thread { loop() }.start()
        }
        return START_STICKY
    }

    private fun startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification())
        }
    }

    private fun metrics(): DisplayMetrics {
        val m = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(m)
        return m
    }

    private fun loop() {
        while (running) {
            val caps = JSONArray().apply {
                put("fs")
                // Layar & input lewat Shizuku = tanpa dialog MediaProjection dan
                // tanpa Accessibility, jadi tidak ada "setelan terbatas".
                if (capture != null || HpAccessibility.instance != null || ShellCapture.ready())
                    put("screen")
                if (HpAccessibility.instance != null) {
                    put("input")
                    put("uitree")
                } else if (ShellCapture.ready()) {
                    put("input")
                }
                if (ShizukuShell.granted()) put("shell")
            }
            val req = JSONObject().apply {
                put("name", name); put("token", token); put("caps", caps)
            }
            val cmd = Net.postJson(base, "/api/hp/poll", req, 35000)
            if (cmd == null) {
                try { Thread.sleep(2000) } catch (_: InterruptedException) {}
                continue
            }
            if (cmd.length() == 0 || !cmd.has("type")) continue
            try { handle(cmd) } catch (_: Exception) {}
        }
    }

    private fun handle(cmd: JSONObject) {
        val id = cmd.optString("id")
        when (cmd.optString("type")) {
            "screen" -> {
                val w = cmd.optString("w", "1080").toIntOrNull() ?: 1080
                // Urutan: MediaProjection (kalau sudah diizinkan) -> Accessibility
                // (tanpa dialog, cukup layanan aktif) -> shell (Shizuku).
                val jpeg = capture?.frame(w)
                    ?: HpAccessibility.instance?.screenshot(w)
                    ?: ShellCapture.frame(w)
                if (jpeg != null) {
                    // Token lewat header, bukan query: URL tercatat di log proxy/server.
                    val q = "id=${enc(id)}&name=${enc(name)}"
                    Net.postBytes(base, "/api/hp/frame?$q", jpeg, "image/jpeg", auth = token)
                } else {
                    result(id, JSONObject().put("error", "layar tidak tersedia"))
                }
            }
            "fs_list" -> result(id, Files.list(cmd.optString("path", "")))
            "fs_read" -> result(id, Files.read(cmd.optString("path", "")))
            "uitree" -> {
                val acc = HpAccessibility.instance
                if (acc == null) result(id, JSONObject().put("error", "Accessibility belum aktif"))
                else result(id, acc.uiTree())
            }
            "global" -> {
                val acc = HpAccessibility.instance
                if (acc == null && ShellCapture.ready()) {
                    val ok = ShellCapture.key(cmd.optString("action"))
                    result(id, JSONObject().put("ok", ok))
                } else if (acc == null) {
                    result(id, JSONObject().put("error", "Accessibility belum aktif"))
                } else {
                    acc.run(JSONArray().put(JSONObject().put("t", "key").put("key", cmd.optString("action"))),
                        metrics().widthPixels, metrics().heightPixels)
                    result(id, JSONObject().put("ok", true))
                }
            }
            "shell" -> result(id, ShizukuShell.exec(cmd.optString("cmd"), cmd.optLong("timeout", 15000)))
            "owner" -> result(
                id,
                when (cmd.optString("action")) {
                    "grant" -> Owner.grantOwnPermissions(this)
                    "kiosk_on" -> Owner.setKiosk(this, true)
                    "kiosk_off" -> Owner.setKiosk(this, false)
                    else -> Owner.status(this)
                }
            )
            "devowner" -> {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val owner = dpm.isDeviceOwnerApp(packageName)
                result(id, JSONObject().apply {
                    put("ok", true)
                    put("owner", owner)
                    put("hint", if (owner) "Aplikasi ini Device Owner." else
                        "Belum Device Owner. Sekali saja lewat ADB: adb shell dpm set-device-owner com.itsme.hp/.HpAdmin (HP harus tanpa akun/baru).")
                })
            }
            "input" -> {
                val acc = HpAccessibility.instance
                if (acc == null && ShellCapture.ready()) {
                    result(id, JSONObject().put("ok", shellInput(cmd.optJSONArray("events") ?: JSONArray())))
                } else if (acc == null) {
                    result(id, JSONObject().put("error", "kontrol belum diizinkan (nyalakan Accessibility)"))
                } else {
                    val m = metrics()
                    acc.run(cmd.optJSONArray("events") ?: JSONArray(), m.widthPixels, m.heightPixels)
                    result(id, JSONObject().put("ok", true))
                }
            }
        }
    }

    /** Terjemahkan event sentuh jadi `input tap/swipe` lewat uid shell. */
    private fun shellInput(events: JSONArray): Boolean {
        val m = metrics()
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            when (e.optString("t")) {
                "tap" -> ShellCapture.tap(
                    (e.optDouble("x", 0.5) * m.widthPixels).toInt(),
                    (e.optDouble("y", 0.5) * m.heightPixels).toInt(),
                )
                "swipe" -> ShellCapture.swipe(
                    (e.optDouble("x1", 0.5) * m.widthPixels).toInt(),
                    (e.optDouble("y1", 0.5) * m.heightPixels).toInt(),
                    (e.optDouble("x2", 0.5) * m.widthPixels).toInt(),
                    (e.optDouble("y2", 0.5) * m.heightPixels).toInt(),
                    e.optInt("ms", 300),
                )
                "key" -> ShellCapture.key(e.optString("key"))
            }
        }
        return true
    }

    private fun result(id: String, value: JSONObject) {
        val body = JSONObject().apply {
            put("id", id); put("name", name); put("token", token); put("value", value)
        }
        Net.postJson(base, "/api/hp/result", body, 20000)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun notification(): Notification {
        val ch = "itsme-hp"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(ch, "It's Me HP", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, ch) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("It's Me HP aktif")
            .setContentText("Tersambung ke dashboard milikmu")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        capture?.release()
        capture = null
        super.onDestroy()
    }
}
