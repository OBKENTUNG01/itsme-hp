package com.itsme.hp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val data = res.data
            if (res.resultCode == Activity.RESULT_OK && data != null) {
                launchService(res.resultCode, data)
                status("Aktif. HP ini akan muncul di tab HP dashboard milikmu.")
            } else {
                status("Izin rekam layar dibatalkan, jadi tidak dimulai.")
            }
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("itsme", Context.MODE_PRIVATE)
        findViewById<EditText>(R.id.url).setText(prefs.getString("url", ""))
        findViewById<EditText>(R.id.name).setText(prefs.getString("name", "HP"))
        findViewById<EditText>(R.id.token).setText(prefs.getString("token", ""))
        findViewById<Button>(R.id.start).setOnClickListener { start() }
        findViewById<Button>(R.id.stop).setOnClickListener {
            startService(Intent(this, HpService::class.java).apply { action = "stop" })
            status("Dihentikan.")
        }
        findViewById<Button>(R.id.files).setOnClickListener { requestAllFiles() }
        findViewById<Button>(R.id.control).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            status("Nyalakan \"It's Me HP (kontrol)\". Kalau muncul \"Aplikasi tidak diberi akses\" (setelan terbatas), tekan \"Izinkan setelan terbatas\" di bawah dulu, lalu ulangi.")
        }
        findViewById<Button>(R.id.restricted).setOnClickListener { allowRestricted() }
    }

    // Android 13+ memblokir izin Accessibility untuk aplikasi yang dipasang di luar
    // Play Store ("setelan terbatas" / restricted settings). Ini BUKAN bug aplikasi dan
    // tidak bisa dilewati lewat kode - pemilik HP harus membukanya sendiri dari halaman
    // info aplikasi: titik tiga (pojok kanan atas) -> "Izinkan setelan terbatas".
    private fun allowRestricted() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
        status("Di halaman info aplikasi: tekan titik tiga (⋮) di pojok kanan atas, lalu pilih \"Izinkan setelan terbatas\". Setelah itu baru nyalakan \"Izinkan kontrol (Accessibility)\".")
    }

    private fun save() {
        prefs.edit()
            .putString("url", findViewById<EditText>(R.id.url).text.toString().trim())
            .putString("name", findViewById<EditText>(R.id.name).text.toString().trim().ifEmpty { "HP" })
            .putString("token", findViewById<EditText>(R.id.token).text.toString().trim())
            .apply()
    }

    private fun start() {
        save()
        // Shizuku (kalau terpasang) memberi hak adb shell: input, install app, ubah setelan.
        // Dialog izinnya muncul sendiri; tanpa Shizuku, aplikasi tetap jalan seperti biasa.
        if (ShizukuShell.available() && !ShizukuShell.granted()) ShizukuShell.request()
        if (prefs.getString("url", "").isNullOrBlank() || prefs.getString("token", "").isNullOrBlank()) {
            status("Isi alamat dashboard dan token dulu.")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun launchService(resultCode: Int, data: Intent) {
        val i = Intent(this, HpService::class.java)
        i.putExtra("url", prefs.getString("url", ""))
        i.putExtra("token", prefs.getString("token", ""))
        i.putExtra("name", prefs.getString("name", "HP"))
        i.putExtra("resultCode", resultCode)
        i.putExtra("data", data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
    }

    private fun requestAllFiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                status("Akses semua berkas sudah diberikan.")
            }
        } else {
            status("Di Android ini tidak perlu izin itu.")
        }
    }

    private fun status(s: String) {
        findViewById<TextView>(R.id.status).text = s
    }
}
