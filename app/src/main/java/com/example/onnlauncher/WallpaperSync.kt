package com.example.onnlauncher

import android.content.Context
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Copies pictures from a web folder (a NAS, a PC, a Nextcloud share...) into the wallpaper folder.
 *
 * The address can be:
 *  - a WebDAV folder (Synology, QNAP, Nextcloud, TrueNAS, Apache and nginx with DAV...). Found with PROPFIND.
 *  - a plain web folder whose index page links to pictures (Apache/nginx autoindex, python -m http.server).
 *  - one direct picture address.
 * Only the pictures directly inside the folder are used (no subfolders). They are stored as net_<name>, so
 * pictures you pushed by hand are never touched, and ones that vanished from the source are removed.
 * An smb:// share is not supported: expose the folder over WebDAV/HTTP on the NAS instead.
 *
 * Blocking: run it on a background thread.
 */
object WallpaperSync {

    data class Result(val ok: Boolean, val message: String)

    private class Remote(val url: HttpUrl, val name: String, val size: Long)

    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    private const val MAX_FILES = 60
    private const val MAX_BYTES = 20L * 1024 * 1024
    private const val PREFIX = "net_"

    private fun client(insecure: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
        if (insecure) {
            // Only for a NAS with a self-signed certificate, and only if you switch this on.
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf(trustAll), SecureRandom())
            builder.sslSocketFactory(context.socketFactory, trustAll).hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    fun sync(context: Context, rawUrl: String, user: String, pass: String, insecure: Boolean): Result {
        val dir = context.getExternalFilesDir("wallpapers") ?: return Result(false, "No storage available.")
        dir.mkdirs()

        var text = rawUrl.trim()
        if (text.isEmpty()) return Result(false, "No wallpaper source is set.")
        if (text.startsWith("smb://", ignoreCase = true)) {
            return Result(false, "smb:// isn't supported. Share the folder over WebDAV or HTTP on the NAS and use that address.")
        }
        if (!text.contains("://")) text = "http://$text"
        val parsed = text.toHttpUrlOrNull() ?: return Result(false, "That doesn't look like a web address.")
        // Login can be typed into the address too: http://name:password@nas/photos/
        val login = user.ifBlank { parsed.username }
        val secret = if (user.isBlank()) parsed.password else pass
        var base = parsed.newBuilder().username("").password("").build()
        val isFile = base.pathSegments.lastOrNull().orEmpty().substringAfterLast('.', "").lowercase() in EXTENSIONS
        if (!isFile && !base.encodedPath.endsWith("/")) base = base.newBuilder().addPathSegment("").build()

        val http = client(insecure)
        fun request(): Request.Builder {
            val b = Request.Builder().url(base).header("User-Agent", "onn6x-launcher")
            if (login.isNotBlank()) b.header("Authorization", Credentials.basic(login, secret))
            return b
        }

        val remote: List<Remote> = try {
            if (isFile) {
                listOf(Remote(base, base.pathSegments.last(), -1))
            } else {
                listFolder(http, base, login, secret) ?: return Result(false, "The server asked for a login. Check the username and password.")
            }
        } catch (e: IOException) {
            return Result(false, "Couldn't reach ${base.host}: ${e.message ?: "network error"}")
        } catch (e: Exception) {
            return Result(false, "Couldn't read the folder: ${e.message ?: "unknown error"}")
        }
        if (remote.isEmpty()) return Result(false, "No pictures (jpg, png or webp) found at that address.")

        val chosen = remote.sortedBy { it.name.lowercase() }.take(MAX_FILES)
        val keep = HashSet<String>()
        var fresh = 0
        var failed = 0
        var dupes = 0
        for (item in chosen) {
            val local = File(dir, PREFIX + safeName(item.name))
            keep.add(local.name)
            if (local.exists() && item.size > 0 && local.length() == item.size) continue
            if (item.size > MAX_BYTES) { failed++; continue }
            try {
                val builder = Request.Builder().url(item.url).header("User-Agent", "onn6x-launcher")
                if (login.isNotBlank()) builder.header("Authorization", Credentials.basic(login, secret))
                http.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) { failed++; return@use }
                    val temp = File(dir, local.name + ".part")
                    var total = 0L
                    response.body!!.byteStream().use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                total += n
                                if (total > MAX_BYTES) throw IOException("too big")
                                output.write(buffer, 0, n)
                            }
                        }
                    }
                    if (Dedupe.findDuplicate(dir, temp, ignoreName = local.name) != null) {
                        temp.delete()
                        dupes++
                        return@use
                    }
                    if (local.exists()) local.delete()
                    if (temp.renameTo(local)) fresh++ else failed++
                }
            } catch (e: Exception) {
                File(dir, local.name + ".part").delete()
                failed++
            }
        }

        // Drop pictures that were synced before but are gone from the source. Never touches hand-added ones.
        dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name !in keep }?.forEach { it.delete() }
        dir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }

        val total = keep.count { File(dir, it).exists() }
        val note = (if (dupes > 0) " ($dupes skipped, already have them)" else "") +
            (if (failed > 0) " ($failed couldn't be copied)" else "")
        return Result(total > 0, "Synced $total pictures from ${base.host}, $fresh new$note.")
    }

    /** WebDAV first, then a plain index page. Returns null when the server wants a login we don't have. */
    private fun listFolder(http: OkHttpClient, base: HttpUrl, login: String, secret: String): List<Remote>? {
        fun builder(): Request.Builder {
            val b = Request.Builder().url(base).header("User-Agent", "onn6x-launcher")
            if (login.isNotBlank()) b.header("Authorization", Credentials.basic(login, secret))
            return b
        }
        val body = """<?xml version="1.0"?><propfind xmlns="DAV:"><prop><getcontentlength/><resourcetype/></prop></propfind>"""
        http.newCall(
            builder().method("PROPFIND", body.toRequestBody("application/xml".toMediaType())).header("Depth", "1").build(),
        ).execute().use { response ->
            if (response.code == 401 || response.code == 403) return null
            if (response.code == 207) return parseWebDav(base, response.body!!.string())
        }
        http.newCall(builder().get().build()).execute().use { response ->
            if (response.code == 401 || response.code == 403) return null
            if (!response.isSuccessful) throw IOException("the server answered ${response.code}")
            return parseIndexPage(base, response.body!!.string())
        }
    }

    private fun parseWebDav(base: HttpUrl, xml: String): List<Remote> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val responses = doc.getElementsByTagNameNS("*", "response")
        val out = ArrayList<Remote>()
        for (i in 0 until responses.length) {
            val node = responses.item(i) as Element
            val href = node.getElementsByTagNameNS("*", "href").item(0)?.textContent?.trim().orEmpty()
            if (href.isEmpty() || node.getElementsByTagNameNS("*", "collection").length > 0) continue
            val url = base.resolve(href) ?: continue
            val name = url.pathSegments.lastOrNull().orEmpty()
            if (name.substringAfterLast('.', "").lowercase() !in EXTENSIONS) continue
            val size = node.getElementsByTagNameNS("*", "getcontentlength").item(0)?.textContent?.trim()?.toLongOrNull() ?: -1L
            out.add(Remote(url, name, size))
        }
        return out
    }

    private fun parseIndexPage(base: HttpUrl, html: String): List<Remote> {
        val out = ArrayList<Remote>()
        val seen = HashSet<String>()
        Regex("""href\s*=\s*["']([^"'#?]+)["']""", RegexOption.IGNORE_CASE).findAll(html).forEach { match ->
            val url = base.resolve(match.groupValues[1].replace("&amp;", "&")) ?: return@forEach
            val name = url.pathSegments.lastOrNull().orEmpty()
            if (name.substringAfterLast('.', "").lowercase() in EXTENSIONS && seen.add(url.toString())) {
                out.add(Remote(url, name, -1))
            }
        }
        return out
    }

    private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
}
