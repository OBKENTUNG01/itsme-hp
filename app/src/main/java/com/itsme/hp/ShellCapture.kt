package com.itsme.hp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/** Tangkap layar lewat hak "adb shell" (`screencap -p`), TANPA MediaProjection.
 *
 *  MediaProjection selalu memunculkan dialog "Mulai merekam layar?" dan itu tidak
 *  bisa dilewati tanpa root/aplikasi sistem. Perintah `screencap` milik uid shell
 *  tidak butuh dialog sama sekali — sama seperti yang dipakai scrcpy.
 *
 *  Syarat: Shizuku aktif dan diizinkan (sekali pasang, tanpa PC).
 */
object ShellCapture {
    fun ready(): Boolean = ShizukuShell.granted()

    /** JPEG siap kirim, atau null kalau Shizuku belum siap. */
    fun frame(maxW: Int, quality: Int = 55): ByteArray? {
        val png = ShizukuShell.execBytes("screencap -p", 12000) ?: return null
        if (png.size < 8) return null
        val bmp = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        val skala = if (bmp.width > maxW) maxW.toFloat() / bmp.width else 1f
        val out = if (skala < 1f)
            Bitmap.createScaledBitmap(bmp, maxW, (bmp.height * skala).toInt(), true)
        else bmp
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        if (out !== bmp) out.recycle()
        bmp.recycle()
        return bos.toByteArray()
    }

    /** Ketuk/geser lewat `input` (uid shell) — tidak butuh Accessibility. */
    fun tap(x: Int, y: Int): Boolean =
        ShizukuShell.exec("input tap $x $y", 8000).optBoolean("ok")

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Int): Boolean =
        ShizukuShell.exec("input swipe $x1 $y1 $x2 $y2 $ms", 12000).optBoolean("ok")

    fun key(nama: String): Boolean =
        ShizukuShell.exec("input keyevent $nama", 8000).optBoolean("ok")
}
