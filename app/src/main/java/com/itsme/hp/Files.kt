package com.itsme.hp

import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Telusur & baca berkas HP (dalam penyimpanan yang diizinkan pemilik). */
object Files {
    private fun root() = Environment.getExternalStorageDirectory()

    fun list(path: String): JSONObject {
        val res = JSONObject()
        val dir = if (path.isBlank()) root() else File(path)
        if (!dir.isDirectory) {
            res.put("error", "bukan folder (atau izin berkas belum diberikan)")
            return res
        }
        res.put("path", dir.absolutePath)
        res.put("parent", dir.parentFile?.absolutePath ?: "")
        val arr = JSONArray()
        val items = (dir.listFiles() ?: emptyArray())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        for (f in items) {
            val o = JSONObject()
            o.put("name", f.name)
            o.put("dir", f.isDirectory)
            o.put("size", if (f.isDirectory) 0L else f.length())
            arr.put(o)
        }
        res.put("entries", arr)
        return res
    }

    fun read(path: String, maxBytes: Int = 1_000_000): JSONObject {
        val res = JSONObject()
        val f = if (path.isBlank()) null else File(path)
        if (f == null || !f.isFile) {
            res.put("error", "berkas tidak ada")
            return res
        }
        return try {
            val size = f.length()
            val out = ByteArrayOutputStream()
            f.inputStream().use { ins ->
                val buf = ByteArray(8192)
                var total = 0
                while (total < maxBytes) {
                    val r = ins.read(buf, 0, minOf(buf.size, maxBytes - total))
                    if (r <= 0) break
                    out.write(buf, 0, r)
                    total += r
                }
            }
            val bytes = out.toByteArray()
            res.put("size", size)
            res.put("truncated", size > bytes.size)
            res.put("text", String(bytes, Charsets.UTF_8))
            res
        } catch (e: Exception) {
            res.put("error", e.message ?: "gagal membaca")
            res
        }
    }
}
