package com.itsme.hp

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import java.io.ByteArrayOutputStream

/** Menangkap satu bingkai layar jadi JPEG lewat MediaProjection + ImageReader. */
class ScreenCapture(
    private val projection: MediaProjection,
    private val metrics: DisplayMetrics
) {
    private var width = 0
    private var height = 0
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private val thread = HandlerThread("itsme-cap").apply { start() }
    private val handler = Handler(thread.looper)

    @Synchronized
    private fun ensure(maxW: Int) {
        val sw = metrics.widthPixels.coerceAtLeast(1)
        val sh = metrics.heightPixels.coerceAtLeast(1)
        val scale = minOf(1.0, maxW.toDouble() / sw)
        val w = (sw * scale).toInt().coerceAtLeast(1)
        val h = (sh * scale).toInt().coerceAtLeast(1)
        if (reader != null && w == width && h == height) return
        display?.release()
        reader?.close()
        width = w
        height = h
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        display = projection.createVirtualDisplay(
            "itsme-hp", w, h, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, handler
        )
    }

    @Synchronized
    fun frame(maxW: Int, quality: Int = 55): ByteArray? {
        ensure(maxW)
        val r = reader ?: return null
        var image = r.acquireLatestImage()
        var tries = 0
        while (image == null && tries < 20) {
            Thread.sleep(50)
            image = r.acquireLatestImage()
            tries++
        }
        val img = image ?: return null
        return try {
            val plane = img.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val bmpW = width + if (pixelStride > 0) rowPadding / pixelStride else 0
            val bmp = Bitmap.createBitmap(bmpW.coerceAtLeast(width), height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buffer)
            val cropped = if (bmp.width == width) bmp else Bitmap.createBitmap(bmp, 0, 0, width, height)
            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            img.close()
        }
    }

    fun release() {
        try { display?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection.stop() } catch (_: Exception) {}
        thread.quitSafely()
    }
}
