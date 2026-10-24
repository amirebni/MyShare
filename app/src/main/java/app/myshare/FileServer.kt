package app.myshare

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLConnection
import java.net.URLEncoder

class FileServer(private val root: File, private val token: String, port: Int) : NanoHTTPD(port) {

    private fun txt(st: Status, s: String): Response =
        NanoHTTPD.newFixedLengthResponse(st, "text/plain; charset=utf-8", s)

    private fun IHTTPSession.q(k: String): String? = parameters[k]?.firstOrNull()

    private fun resolve(rel: String?): File? {
        val f = File(root, rel ?: "").canonicalFile
        val r = root.canonicalPath
        return if (f.path == r || f.path.startsWith(r + File.separator)) f else null
    }

    override fun serve(s: IHTTPSession): Response =
        try { handle(s) } catch (e: Exception) { txt(Status.INTERNAL_ERROR, "error: " + e.message) }

    private fun handle(s: IHTTPSession): Response {
        if (token.isEmpty() || s.q("t") != token) return txt(Status.FORBIDDEN, "forbidden")
        val post = s.method == Method.POST
        return when (s.uri) {
            "/" -> NanoHTTPD.newFixedLengthResponse(Status.OK, "text/html; charset=utf-8", PAGE)
            "/api/list" -> list(s)
            "/api/get" -> get(s)
            "/api/upload" -> if (post) upload(s) else txt(Status.METHOD_NOT_ALLOWED, "post only")
            "/api/mkdir" -> if (post) mkdir(s) else txt(Status.METHOD_NOT_ALLOWED, "post only")
            "/api/delete" -> if (post) delete(s) else txt(Status.METHOD_NOT_ALLOWED, "post only")
            else -> txt(Status.NOT_FOUND, "404")
        }
    }

    private fun list(s: IHTTPSession): Response {
        val d = resolve(s.q("path")) ?: return txt(Status.FORBIDDEN, "bad path")
        val arr = JSONArray()
        (d.listFiles() ?: emptyArray())
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .forEach {
                arr.put(
                    JSONObject().put("name", it.name).put("dir", it.isDirectory).put("size", it.length())
                )
            }
        val out = JSONObject().put("items", arr).put("free", root.freeSpace)
        return NanoHTTPD.newFixedLengthResponse(
            Status.OK, "application/json; charset=utf-8", out.toString()
        )
    }

    private fun get(s: IHTTPSession): Response {
        val f = resolve(s.q("path")) ?: return txt(Status.FORBIDDEN, "bad path")
        if (!f.isFile) return txt(Status.NOT_FOUND, "not a file")
        val mime = URLConnection.guessContentTypeFromName(f.name) ?: "application/octet-stream"
        val r = NanoHTTPD.newFixedLengthResponse(Status.OK, mime, FileInputStream(f), f.length())
        r.addHeader(
            "Content-Disposition",
            "attachment; filename*=UTF-8''" + URLEncoder.encode(f.name, "UTF-8").replace("+", "%20")
        )
        return r
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (f.exists()) {
            f = File(dir, base + " (" + i + ")" + ext)
            i++
        }
        return f
    }

    private fun upload(s: IHTTPSession): Response {
        val dir = resolve(s.q("path")) ?: return txt(Status.FORBIDDEN, "bad path")
        val name = File(s.q("name") ?: "").name
        if (name.isEmpty()) return txt(Status.BAD_REQUEST, "name")
        var left = s.headers["content-length"]?.toLongOrNull() ?: return txt(Status.BAD_REQUEST, "length")
        dir.mkdirs()
        val target = uniqueFile(dir, name)
        val part = File(dir, target.name + ".part")
        var ok = false
        try {
            FileOutputStream(part).use { o ->
                val buf = ByteArray(64 * 1024)
                val i = s.inputStream
                while (left > 0) {
                    val n = i.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) break
                    o.write(buf, 0, n)
                    left -= n
                }
            }
            ok = left == 0L
        } finally {
            if (!ok) part.delete()
        }
        if (!ok) return txt(Status.BAD_REQUEST, "incomplete")
        part.renameTo(target)
        return txt(Status.OK, target.name)
    }

    private fun mkdir(s: IHTTPSession): Response {
        val d = resolve(s.q("path")) ?: return txt(Status.FORBIDDEN, "bad path")
        val name = File(s.q("name") ?: "").name
        if (name.isEmpty()) return txt(Status.BAD_REQUEST, "name")
        File(d, name).mkdirs()
        return txt(Status.OK, "ok")
    }

    private fun delete(s: IHTTPSession): Response {
        val f = resolve(s.q("path")) ?: return txt(Status.FORBIDDEN, "bad path")
        if (f.path == root.canonicalPath) return txt(Status.FORBIDDEN, "root")
        f.deleteRecursively()
        return txt(Status.OK, "ok")
    }
}
