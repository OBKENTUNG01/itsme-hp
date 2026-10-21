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
                if (capture != null) put("screen")
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
                val jpeg = capture?.frame(w)
                if (jpeg != null) {
                    val q = "id=${enc(id)}&name=${enc(name)}&token=${enc(token)}"
                    Net.postBytes(base, "/api/hp/frame?$q", jpeg, "image/jpeg")
                } else {
                    result(id, JSONObject().put("error", "layar tidak tersedia"))
                }
            }
            "fs_list" -> result(id, Files.list(cmd.optString("path", "")))
            "fs_read" -> result(id, Files.read(cmd.optString("path", "")))
        }
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
