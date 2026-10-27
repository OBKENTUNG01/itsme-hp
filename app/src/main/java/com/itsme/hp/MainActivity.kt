package com.itsme.hp

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
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
        findViewById<Button>(R.id.control).setOnClickListener { enableControl() }
        findViewById<Button>(R.id.restricted).setOnClickListener { allowRestricted() }
        findViewById<Button>(R.id.owner).setOnClickListener { ownerSetup() }
    }

    // Device Owner = izin otomatis tanpa dialog + kiosk. Dua langkah sekali saja:
    // aktifkan admin di layar sistem, lalu dari komputer:
    // adb shell dpm set-device-owner <paket>/.HpAdmin  (HP harus tanpa akun).
    private fun ownerSetup() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(packageName)) {
            status("Sudah Device Owner. ${Owner.grantOwnPermissions(this).optString("message")}")
            return
        }
        try {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@MainActivity, HpAdmin::class.java))
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Aktifkan admin supaya izin diberikan otomatis dan HP bisa dikunci ke aplikasi ini.")
            })
            status("1) Aktifkan admin di layar berikut. 2) Sekali saja dari komputer: adb shell dpm set-device-owner $packageName/.HpAdmin (HP harus tanpa akun). 3) Tekan tombol ini lagi — izin diberikan otomatis.")
        } catch (e: Exception) {
            status("Layar admin tidak bisa dibuka (${e.message}). Tetap bisa lewat ADB: adb shell dpm set-device-owner $packageName/.HpAdmin")
        }
    }

    /** Nyalakan layanan kontrol: otomatis kalau WRITE_SECURE_SETTINGS sudah diberikan,
     *  kalau belum buka layar setelan seperti biasa. */
    private fun enableControl() {
        if (Auto.enableAccessibility(this)) {
            status("Kontrol layar aktif otomatis (tanpa dialog). Tarik layar HP dari dashboard.")
            return
        }
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        status("Nyalakan \"It's Me HP (kontrol)\".\n" +
            "Kalau muncul \"setelan terbatas\", tekan tombol \"Izinkan setelan terbatas\", atau jalankan sekali dari komputer:\n" +
            Auto.adbLine(this))
    }

    // Android 13+ memblokir izin Accessibility untuk aplikasi yang dipasang di luar
    // Play Store ("setelan terbatas" / restricted settings). Ini BUKAN bug aplikasi dan
    // tidak bisa dilewati lewat kode - pemilik HP harus membukanya sendiri dari halaman
    // info aplikasi: titik tiga (pojok kanan atas) -> "Izinkan setelan terbatas".
    private fun allowRestricted() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
        status("Di halaman info aplikasi: titik tiga (⋮) kanan atas -> \"Izinkan setelan terbatas\".\n" +
            "Cara yang lebih cepat (sekali saja, tanpa aplikasi lain): sambungkan HP ke komputer lalu jalankan\n" +
            Auto.adbLine(this) + "\nSesudah itu tekan \"Kontrol layar\" — menyala sendiri tanpa dialog.")
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
