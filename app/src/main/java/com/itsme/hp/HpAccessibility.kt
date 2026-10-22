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
