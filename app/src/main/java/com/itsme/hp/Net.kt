package com.itsme.hp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** HTTP sederhana ke dashboard It's Me (pakai HttpURLConnection, tanpa pustaka luar). */
object Net {
    private fun open(base: String, path: String): HttpURLConnection {
        val c = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.setRequestProperty("X-Itsme-Dashboard", "1")
        return c
    }

    /** POST JSON; kembalikan JSON balasan, atau null kalau gagal. */
    fun postJson(base: String, path: String, body: JSONObject, readTimeout: Int = 30000): JSONObject? {
        var c: HttpURLConnection? = null
        return try {
            c = open(base, path)
            c.requestMethod = "POST"
            c.readTimeout = readTimeout
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    /** POST bytes mentah (mis. bingkai JPEG). Query sudah di dalam path. */
    fun postBytes(base: String, path: String, bytes: ByteArray, contentType: String): Boolean {
        var c: HttpURLConnection? = null
        return try {
            c = open(base, path)
            c.requestMethod = "POST"
            c.readTimeout = 20000
            c.doOutput = true
            c.setRequestProperty("Content-Type", contentType)
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            c.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            c?.disconnect()
        }
    }
}
