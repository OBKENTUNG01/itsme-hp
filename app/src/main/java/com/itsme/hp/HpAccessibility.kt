package com.itsme.hp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Layanan Accessibility: menjalankan sentuh/geser/ketik yang dikirim dari dashboard.
 *  Terlihat & harus diaktifkan pemilik di Setelan > Aksesibilitas. */
class HpAccessibility : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    /** Tangkap layar lewat API Accessibility (Android 11+): TANPA dialog MediaProjection.
     *
     *  Cukup layanan ini aktif dan config memuat android:canTakeScreenshot="true".
     *  Hasilnya HardwareBuffer -> Bitmap -> JPEG. null kalau tidak didukung/gagal.
     */
    fun screenshot(maxW: Int, quality: Int = 55, tungguMs: Long = 4000): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val latch = CountDownLatch(1)
        var hasil: ByteArray? = null
        val exec = java.util.concurrent.Executor { r -> main.post(r) }
        try {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY, exec,
                object : TakeScreenshotCallback {
                    override fun onSuccess(r: ScreenshotResult) {
                        try {
                            val bmp = android.graphics.Bitmap.wrapHardwareBuffer(
                                r.hardwareBuffer, r.colorSpace
                            )
                            if (bmp != null) {
                                val skala = if (bmp.width > maxW) maxW.toFloat() / bmp.width else 1f
                                val out = if (skala < 1f)
                                    android.graphics.Bitmap.createScaledBitmap(
                                        bmp, maxW, (bmp.height * skala).toInt(), true
                                    ) else bmp
                                val bos = java.io.ByteArrayOutputStream()
                                out.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, bos)
                                if (out !== bmp) out.recycle()
                                hasil = bos.toByteArray()
                            }
                            bmp?.recycle()
                        } catch (_: Throwable) {
                        } finally {
                            r.hardwareBuffer.close()
                            latch.countDown()
                        }
                    }

                    override fun onFailure(kode: Int) {
                        latch.countDown()
                    }
                }
            )
        } catch (_: Throwable) {
            return null
        }
        latch.await(tungguMs, TimeUnit.MILLISECONDS)
        return hasil
    }

    /** Jalankan daftar aksi. sw/sh = ukuran layar nyata (piksel). */
    fun run(events: JSONArray, sw: Int, sh: Int) {
        main.post {
            for (i in 0 until events.length()) {
                val e = events.optJSONObject(i) ?: continue
                try {
                    one(e, sw.coerceAtLeast(1), sh.coerceAtLeast(1))
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun px(v: Double, max: Int) = (v * max).toInt().coerceIn(0, max - 1)

    private fun one(e: JSONObject, sw: Int, sh: Int) {
        when (e.optString("t")) {
            "tap" -> gesture(px(e.optDouble("x"), sw), px(e.optDouble("y"), sh), null, null, 50)
            "long" -> gesture(px(e.optDouble("x"), sw), px(e.optDouble("y"), sh), null, null, 600)
            "double" -> {
                gesture(px(e.optDouble("x"), sw), px(e.optDouble("y"), sh), null, null, 40)
                main.postDelayed({
                    gesture(px(e.optDouble("x"), sw), px(e.optDouble("y"), sh), null, null, 40)
                }, 90)
            }
            "swipe" -> gesture(
                px(e.optDouble("x"), sw), px(e.optDouble("y"), sh),
                px(e.optDouble("x2"), sw), px(e.optDouble("y2"), sh),
                e.optInt("ms", 250).toLong()
            )
            "key" -> key(e.optString("key"))
            "text" -> typeText(e.optString("text"))
        }
    }

    private fun gesture(x1: Int, y1: Int, x2: Int?, y2: Int?, ms: Long) {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            if (x2 != null && y2 != null) lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, ms.coerceAtLeast(1))
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun key(k: String) {
        when (k) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "enter" -> enter()
            "backspace" -> backspace()
        }
    }

    /** Isi layar sekarang: teks/tombol yang terlihat beserta kotaknya. Ini yang membuat
     *  dashboard bisa "melihat" aplikasi apa yang terbuka, bukan hanya menekan titik. */
    fun uiTree(max: Int = 250): JSONObject {
        val out = JSONObject()
        val done = CountDownLatch(1)
        val job = Runnable {
            val arr = JSONArray()
            fun walk(n: AccessibilityNodeInfo?, depth: Int) {
                if (n == null || arr.length() >= max || depth > 30) return
                val text = n.text?.toString()?.trim().orEmpty()
                val desc = n.contentDescription?.toString()?.trim().orEmpty()
                if (text.isNotEmpty() || desc.isNotEmpty() || n.isClickable || n.isEditable) {
                    val r = android.graphics.Rect()
                    n.getBoundsInScreen(r)
                    arr.put(JSONObject().apply {
                        put("cls", n.className?.toString()?.substringAfterLast('.') ?: "")
                        if (text.isNotEmpty()) put("text", text.take(200))
                        if (desc.isNotEmpty()) put("desc", desc.take(200))
                        put("tap", n.isClickable)
                        put("edit", n.isEditable)
                        put("x", r.left); put("y", r.top)
                        put("w", r.width()); put("h", r.height())
                    })
                }
                for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
            }
            try {
                walk(rootInActiveWindow, 0)
                out.put("nodes", arr)
            } catch (e: Exception) {
                out.put("error", e.message ?: "gagal membaca layar")
            }
            done.countDown()
        }
        main.post(job)
        try { done.await(3, TimeUnit.SECONDS) } catch (_: InterruptedException) {}
        return out
    }

    private fun focused(): AccessibilityNodeInfo? = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    private fun setText(node: AccessibilityNodeInfo, value: String) {
        val b = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun typeText(text: String) {
        if (text.isEmpty()) return
        val node = focused() ?: return
        val cur = node.text?.toString() ?: ""
        setText(node, cur + text)
    }

    private fun backspace() {
        val node = focused() ?: return
        val cur = node.text?.toString() ?: ""
        if (cur.isNotEmpty()) setText(node, cur.dropLast(1))
    }

    private fun enter() {
        val node = focused()
        if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        } else if (node != null) {
            typeText("\n")
        }
    }

    companion object {
        @Volatile
        var instance: HpAccessibility? = null
    }
}
