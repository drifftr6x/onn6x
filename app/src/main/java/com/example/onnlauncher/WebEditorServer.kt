package com.example.onnlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asAndroidBitmap
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * A tiny web server so the launcher can be edited from a phone or laptop on the same Wi-Fi.
 * It serves assets/editor.html and a small JSON API. Every /api/ call needs the PIN shown on the TV,
 * sent in an X-Pin header. Too many wrong PINs and the editor locks until it is restarted.
 * Plain HTTP on the home network only. Nothing here is meant to be exposed to the internet.
 */
class WebEditorServer(
    private val context: Context,
    private val vm: LauncherViewModel,
    private val pin: String,
) {

    private class HttpError(val status: Int, message: String) : Exception(message)

    private class Req(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val input: InputStream,
        val length: Long,
    ) {
        var consumed = false
    }

    private val prefs = LauncherPrefs(context)
    private val pool = Executors.newCachedThreadPool()
    private val failures = AtomicInteger(0)
    private val iconCache = ConcurrentHashMap<String, ByteArray>()

    @Volatile
    private var socket: ServerSocket? = null

    @Volatile
    private var stopped = false

    var port: Int = 0
        private set

    /** Returns false if none of the ports 8080-8090 is free. */
    fun start(): Boolean {
        for (candidate in PORTS) {
            try {
                socket = ServerSocket(candidate, 20)
                port = candidate
                break
            } catch (e: IOException) {
                socket = null
            }
        }
        val server = socket ?: return false
        thread(isDaemon = true, name = "editor-accept") {
            while (!stopped) {
                try {
                    val client = server.accept()
                    pool.execute { serve(client) }
                } catch (e: IOException) {
                    break
                }
            }
        }
        return true
    }

    fun stop() {
        stopped = true
        try {
            socket?.close()
        } catch (e: IOException) {
            // already closed
        }
        pool.shutdownNow()
    }

    // ---- connection handling ----

    private fun serve(client: Socket) {
        try {
            client.soTimeout = 60_000
            val input = BufferedInputStream(client.getInputStream())
            val out = client.getOutputStream()
            var current: Req? = null
            try {
                val req = readRequest(input)
                current = req
                route(req, out)
            } catch (e: HttpError) {
                // Read what the browser is still sending, or it sees a dropped connection instead of our message.
                current?.let { drain(it) }
                sendJson(out, e.status, JSONObject().put("error", e.message ?: "Error"))
            } catch (e: Exception) {
                sendJson(out, 500, JSONObject().put("error", e.message ?: "Server error"))
            }
        } catch (e: Exception) {
            // client went away
        } finally {
            try {
                client.close()
            } catch (e: IOException) {
                // ignore
            }
        }
    }

    private fun readRequest(input: InputStream): Req {
        val first = readLine(input) ?: throw HttpError(400, "Empty request")
        val parts = first.split(" ")
        if (parts.size < 2) throw HttpError(400, "Bad request")
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = HashMap<String, String>()
        if (target.contains('?')) {
            for (pair in target.substringAfter('?').split("&")) {
                if (pair.isEmpty()) continue
                val key = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
                val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
                query[key] = value
            }
        }
        val headers = HashMap<String, String>()
        var count = 0
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            count++
            if (count > 60) throw HttpError(400, "Too many headers")
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toLongOrNull() ?: 0L
        return Req(parts[0].uppercase(), path, query, headers, input, length)
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buffer.size() == 0) null else buffer.toString("ISO-8859-1")
            if (b == '\n'.code) break
            if (b != '\r'.code) buffer.write(b)
            if (buffer.size() > 8192) throw IOException("Line too long")
        }
        return buffer.toString("ISO-8859-1")
    }

    private fun sendBytes(out: OutputStream, status: Int, type: String, body: ByteArray, cache: String = "no-store") {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            409 -> "Conflict"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            else -> "Error"
        }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: $cache\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    private fun sendJson(out: OutputStream, status: Int, obj: JSONObject) {
        sendBytes(out, status, "application/json; charset=utf-8", obj.toString().toByteArray(Charsets.UTF_8))
    }

    private fun ok(out: OutputStream, extra: JSONObject = JSONObject()) {
        sendJson(out, 200, extra.put("ok", true))
    }

    // ---- request bodies ----

    private fun readJson(req: Req): JSONObject {
        val bytes = readBody(req, MAX_JSON)
        if (bytes.isEmpty()) return JSONObject()
        return try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw HttpError(400, "That wasn't valid JSON")
        }
    }

    private fun readBody(req: Req, max: Long): ByteArray {
        if (req.length > max) throw HttpError(413, "Too large")
        req.consumed = true
        val data = ByteArray(req.length.toInt())
        var read = 0
        while (read < data.size) {
            val n = req.input.read(data, read, data.size - read)
            if (n < 0) throw IOException("Upload cut off")
            read += n
        }
        return data
    }

    /** Reads and discards whatever is left of a request body (bounded), so an error reply can get through. */
    private fun drain(req: Req) {
        if (req.consumed) return
        try {
            var left = minOf(req.length, 40L * 1024 * 1024)
            val buffer = ByteArray(32 * 1024)
            while (left > 0) {
                val n = req.input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) break
                left -= n
            }
        } catch (e: Exception) {
            // the browser gave up; nothing to send back to
        }
    }

    /**
     * Resumable upload. The browser sends a file in small pieces (offset = where this piece starts, total = file
     * size). Pieces are appended to name.part; if the connection drops, the browser asks where it got to
     * (probe=1) or gets a 409 with "have", and carries on from there instead of starting over.
     */
    private fun receiveChunk(req: Req, out: OutputStream) {
        val kind = req.query["kind"].orEmpty()
        val (folder, extensions, max) = when (kind) {
            "wallpaper" -> Triple("wallpapers", setOf("jpg", "jpeg", "png", "webp"), MAX_WALLPAPER)
            "apk" -> Triple("apks", setOf("apk"), MAX_APK)
            else -> throw HttpError(400, "Unknown upload type")
        }
        val dir = context.getExternalFilesDir(folder) ?: throw HttpError(500, "No storage available")
        dir.mkdirs()
        val name = safeName(req.query["name"].orEmpty(), extensions)
        val total = req.query["total"]?.toLongOrNull() ?: throw HttpError(400, "Missing file size")
        if (total <= 0) throw HttpError(400, "That file is empty")
        if (total > max) throw HttpError(413, "That file is too large (limit ${max / 1_048_576} MB)")

        val temp = File(dir, "$name.part")
        val have = if (temp.exists()) temp.length() else 0L
        if (req.query["probe"] == "1") {
            sendJson(out, 200, JSONObject().put("ok", true).put("have", have))
            return
        }
        val offset = req.query["offset"]?.toLongOrNull() ?: 0L
        if (offset == 0L) {
            temp.delete()
        } else if (offset != have) {
            drain(req)
            sendJson(out, 409, JSONObject().put("error", "Out of step").put("have", have))
            return
        }
        if (req.length <= 0 || req.length > MAX_CHUNK) throw HttpError(400, "Bad piece size")
        if (offset + req.length > total) throw HttpError(400, "That piece goes past the end of the file")

        req.consumed = true
        FileOutputStream(temp, true).use { stream ->
            val buffer = ByteArray(64 * 1024)
            var left = req.length
            while (left > 0) {
                val n = req.input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) throw IOException("Upload cut off")
                stream.write(buffer, 0, n)
                left -= n
            }
        }
        val done = offset + req.length == total
        if (done) {
            if (temp.length() != total) {
                temp.delete()
                throw HttpError(500, "The file came through incomplete. Try again.")
            }
            val target = File(dir, name)
            // Same picture already here under another name? Keep the existing one.
            val twin = Dedupe.findDuplicate(dir, temp, ignoreName = name)
            if (twin != null) {
                temp.delete()
                ok(out, JSONObject().put("have", total).put("done", true).put("duplicateOf", twin))
                return
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw HttpError(500, "Couldn't save the file")
        }
        ok(out, JSONObject().put("have", offset + req.length).put("done", done))
    }

    /** Streams the request body to [target] without holding it in memory. */
    private fun saveBody(req: Req, target: File, max: Long) {
        if (req.length <= 0) throw HttpError(400, "Empty upload")
        if (req.length > max) throw HttpError(413, "That file is too large (limit ${max / 1_048_576} MB)")
        val temp = File(target.parentFile, target.name + ".part")
        try {
            FileOutputStream(temp).use { stream ->
                val buffer = ByteArray(64 * 1024)
                var left = req.length
                while (left > 0) {
                    val n = req.input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) throw IOException("Upload cut off")
                    stream.write(buffer, 0, n)
                    left -= n
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw IOException("Couldn't save the file")
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    // ---- routing ----

    private fun route(req: Req, out: OutputStream) {
        if (req.method == "GET" && (req.path == "/" || req.path == "/index.html")) {
            val html = context.assets.open("editor.html").use { it.readBytes() }
            sendBytes(out, 200, "text/html; charset=utf-8", html)
            return
        }
        if (!req.path.startsWith("/api/")) throw HttpError(404, "Not found")

        if (failures.get() >= MAX_FAILURES) throw HttpError(429, "Locked after too many wrong PINs. Restart the editor on the TV.")
        // <img> tags can't send headers, so pictures (and only pictures) may carry the PIN in the address.
        val pinInAddress = req.method == "GET" && (req.path == "/api/icon" || req.path == "/api/wallpaper/thumb")
        val given = req.headers["x-pin"] ?: if (pinInAddress) req.query["pin"].orEmpty() else ""
        if (!MessageDigest.isEqual(given.toByteArray(), pin.toByteArray())) {
            failures.incrementAndGet()
            throw HttpError(401, "Wrong PIN")
        }
        failures.set(0)

        when (req.path) {
            "/api/state" -> sendJson(out, 200, stateJson())
            "/api/ping" -> ok(out, JSONObject().put("left", vm.editorSecondsLeft()))
            "/api/extend" -> ok(out, JSONObject().put("left", vm.extendEditor()))

            "/api/icon" -> {
                val pkg = requirePackage(req.query["pkg"].orEmpty())
                val bytes = iconCache[pkg] ?: run {
                    val app = vm.snapshotApps().firstOrNull { it.packageName == pkg } ?: throw HttpError(404, "No such app")
                    val png = ByteArrayOutputStream()
                    app.icon.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, png)
                    png.toByteArray().also { iconCache[pkg] = it }
                }
                sendBytes(out, 200, "image/png", bytes, "private, max-age=3600")
            }
            "/api/wallpaper/thumb" -> {
                val dir = context.getExternalFilesDir("wallpapers") ?: throw HttpError(404, "Not found")
                val file = File(dir, req.query["name"].orEmpty())
                if (file.parentFile?.canonicalPath != dir.canonicalPath || !file.isFile) throw HttpError(404, "Not found")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 480) sample *= 2
                val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: throw HttpError(404, "Not a picture")
                val jpeg = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 72, jpeg)
                bitmap.recycle()
                sendBytes(out, 200, "image/jpeg", jpeg.toByteArray(), "private, max-age=600")
            }

            "/api/upload" -> receiveChunk(req, out)
            "/api/backup" -> sendBytes(out, 200, "application/json; charset=utf-8", prefs.exportJson().toByteArray())

            "/api/restore" -> {
                val text = String(readBody(req, MAX_JSON), Charsets.UTF_8)
                if (!vm.restoreFromJson(text)) throw HttpError(400, "That isn't a valid layout backup. Nothing was changed.")
                ok(out)
            }

            "/api/rows/add" -> {
                if (!vm.addRow(readJson(req).optString("name"))) throw HttpError(400, "That row name is empty, reserved or already used")
                ok(out)
            }
            "/api/rows/rename" -> {
                val body = readJson(req)
                if (!vm.renameRow(body.optString("name"), body.optString("newName"))) {
                    throw HttpError(400, "That row name is empty, reserved or already used")
                }
                ok(out)
            }
            "/api/rows/delete" -> {
                vm.deleteRow(readJson(req).optString("name"))
                ok(out)
            }
            "/api/rows/move" -> {
                val body = readJson(req)
                vm.moveRow(body.optString("name"), body.optInt("delta").coerceIn(-1, 1))
                ok(out)
            }
            "/api/rows/addapp" -> {
                val body = readJson(req)
                vm.addToRow(requirePackage(body.optString("pkg")), body.optString("row"))
                ok(out)
            }
            "/api/rows/removeapp" -> {
                val body = readJson(req)
                vm.removeFromRow(requirePackage(body.optString("pkg")), body.optString("row"))
                ok(out)
            }
            "/api/rows/moveapp" -> {
                val body = readJson(req)
                vm.move(requirePackage(body.optString("pkg")), body.optString("row"), body.optInt("delta").coerceIn(-1, 1))
                ok(out)
            }

            "/api/app/hide" -> {
                val body = readJson(req)
                val pkg = requirePackage(body.optString("pkg"))
                if (body.optBoolean("hidden", true)) vm.hide(pkg) else vm.unhide(pkg)
                ok(out)
            }
            "/api/app/rename" -> {
                val body = readJson(req)
                vm.setAppLabel(requirePackage(body.optString("pkg")), body.optString("name"))
                ok(out)
            }
            "/api/app/image" -> {
                val pkg = requirePackage(req.query["pkg"].orEmpty())
                val ext = req.query["ext"].orEmpty().lowercase().let { if (it == "jpeg") "jpg" else it }
                if (ext !in TileImages.EXTENSIONS) throw HttpError(400, "Use a png, jpg or webp picture")
                val dir = TileImages.dir(context) ?: throw HttpError(500, "No storage available")
                dir.mkdirs()
                TileImages.delete(context, pkg)
                saveBody(req, File(dir, "$pkg.$ext"), MAX_IMAGE)
                vm.reloadTileImages()
                ok(out)
            }
            "/api/app/image/delete" -> {
                vm.removeTileImage(requirePackage(readJson(req).optString("pkg")))
                ok(out)
            }

            "/api/settings" -> {
                applySettings(readJson(req))
                ok(out)
            }
            "/api/autostart" -> {
                val body = readJson(req)
                val pkg = body.optString("pkg")
                val delay = body.optInt("delay", prefs.autoStartDelay())
                vm.setAutoStart(if (pkg.isBlank()) "" else requirePackage(pkg), delay)
                ok(out)
            }

            "/api/wallpapers/source" -> {
                val body = readJson(req)
                val pass = if (body.has("pass") && !body.isNull("pass") && body.optString("pass").isNotEmpty()) body.optString("pass") else null
                vm.saveWallpaperSource(body.optString("url"), body.optString("user"), pass, body.optBoolean("insecure"))
                ok(out)
            }
            "/api/wallpapers/sync" -> {
                val latch = java.util.concurrent.CountDownLatch(1)
                var message = ""
                vm.syncWallpapers { message = it; latch.countDown() }
                latch.await(120, java.util.concurrent.TimeUnit.SECONDS)
                if (message.isBlank()) throw HttpError(504, "Still syncing. Check back in a minute.")
                ok(out, JSONObject().put("message", message))
            }
            "/api/tmdb" -> {
                val message = vm.saveTmdbKey(readJson(req).optString("key"))
                if (!message.startsWith("TMDB connected")) throw HttpError(400, message)
                ok(out)
            }
            "/api/ha/config" -> {
                val body = readJson(req)
                val token = if (body.has("token") && !body.isNull("token")) body.optString("token") else null
                prefs.setHaConfig(body.optString("url"), token)
                vm.reload()
                ok(out)
            }
            "/api/ha/tiles" -> {
                val array = readJson(req).optJSONArray("tiles") ?: JSONArray()
                val tiles = SmartHome.parse(array.toString())
                prefs.setSmartTilesJson(SmartHome.encode(tiles))
                vm.reload()
                ok(out)
            }
            "/api/ha/test" -> {
                val tileJson = readJson(req).optJSONObject("tile") ?: throw HttpError(400, "No tile given")
                val tile = SmartHome.parse(JSONArray().put(tileJson).toString()).firstOrNull()
                    ?: throw HttpError(400, "That tile needs a name")
                val message = SmartHome.run(prefs.haUrl(), prefs.haToken(), tile)
                ok(out, JSONObject().put("message", message))
            }

            "/api/wallpaper" -> {
                val dir = context.getExternalFilesDir("wallpapers") ?: throw HttpError(500, "No storage available")
                dir.mkdirs()
                val name = safeName(req.query["name"].orEmpty(), setOf("jpg", "jpeg", "png", "webp"))
                saveBody(req, File(dir, name), MAX_WALLPAPER)
                ok(out)
            }
            "/api/wallpaper/delete" -> {
                deleteIn("wallpapers", readJson(req).optString("name"))
                ok(out)
            }
            "/api/apk" -> {
                val dir = context.getExternalFilesDir("apks") ?: throw HttpError(500, "No storage available")
                dir.mkdirs()
                val name = safeName(req.query["name"].orEmpty(), setOf("apk"))
                saveBody(req, File(dir, name), MAX_APK)
                ok(out)
            }
            "/api/apk/delete" -> {
                deleteIn("apks", readJson(req).optString("name"))
                ok(out)
            }

            else -> throw HttpError(404, "Not found")
        }
    }

    // ---- helpers ----

    private fun requirePackage(raw: String): String {
        if (!PACKAGE_PATTERN.matches(raw)) throw HttpError(400, "Bad package name")
        return raw
    }

    /** Keeps only safe characters and an allowed extension. Throws if nothing usable is left. */
    private fun safeName(raw: String, extensions: Set<String>): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().take(80)
        if (cleaned.isEmpty() || cleaned.contains("..") || cleaned.startsWith(".")) throw HttpError(400, "Bad file name")
        if (cleaned.substringAfterLast('.', "").lowercase() !in extensions) {
            throw HttpError(400, "Allowed file types: ${extensions.joinToString(", ")}")
        }
        return cleaned
    }

    private fun deleteIn(folder: String, rawName: String) {
        val dir = context.getExternalFilesDir(folder) ?: return
        val file = File(dir, rawName)
        if (file.parentFile?.canonicalPath != dir.canonicalPath) throw HttpError(400, "Bad file name")
        if (file.isFile) file.delete()
    }

    private fun applySettings(body: JSONObject) {
        val city = body.optString("weatherCity").trim()
        val place = if (city.isEmpty()) null else WeatherClient.geocode(city)
        if (city.isNotEmpty() && place == null) throw HttpError(400, "Couldn't find \"$city\". Check the spelling.")

        vm.updateSettings { s ->
            var n = s
            val tile = body.optInt("tileSize", -1)
            if (tile in Options.tileWidths.indices) n = n.copy(tileSize = tile)
            val accent = body.optInt("accentIndex", -1)
            if (accent in Options.accents.indices) n = n.copy(accentIndex = accent)
            val wall = body.optInt("wallpaperMinutes", -1)
            if (wall in Options.wallpaperMinutes) n = n.copy(wallpaperMinutes = wall)
            val idle = body.optInt("idleMinutes", -1)
            if (idle in Options.idleMinutes) n = n.copy(idleMinutes = idle)
            if (body.has("showRecents")) n = n.copy(showRecents = body.optBoolean("showRecents"))
            if (body.has("showWeather")) n = n.copy(showWeather = body.optBoolean("showWeather"))
            if (body.has("fahrenheit")) n = n.copy(fahrenheit = body.optBoolean("fahrenheit"))
            if (body.has("showForecast")) n = n.copy(showForecast = body.optBoolean("showForecast"))
            if (body.has("showCrypto")) n = n.copy(showCrypto = body.optBoolean("showCrypto"))
            val round = body.optInt("roundness", -1)
            if (round in Options.roundnessDp.indices) n = n.copy(roundness = round)
            if (body.has("showHero")) n = n.copy(showHero = body.optBoolean("showHero"))
            if (body.has("heroFull")) n = n.copy(heroFull = body.optBoolean("heroFull"))
            val font = body.optInt("fontIndex", -1)
            if (font in AppFonts.names.indices) n = n.copy(fontIndex = font)
            if (body.has("idleBackdrops")) n = n.copy(idleBackdrops = body.optBoolean("idleBackdrops"))
            val mode = body.optInt("titleMode", -1)
            if (mode in Options.titleModes.indices) n = n.copy(titleMode = mode)
            if (body.has("showTrending")) n = n.copy(showTrending = body.optBoolean("showTrending"))
            val region = body.optInt("tmdbRegion", -1)
            if (region in Options.regions.indices) n = n.copy(tmdbRegion = region)
            if (body.has("dashboard")) n = n.copy(dashboard = body.optBoolean("dashboard"))
            if (body.has("autoTheme")) n = n.copy(autoTheme = body.optBoolean("autoTheme"))
            val themeIdx = body.optInt("themeIndex", -1)
            if (themeIdx in Themes.all.indices) n = n.copy(themeIndex = themeIdx)
            val dayIdx = body.optInt("dayTheme", -1)
            if (dayIdx in Themes.all.indices) n = n.copy(dayTheme = dayIdx)
            val nightIdx = body.optInt("nightTheme", -1)
            if (nightIdx in Themes.all.indices) n = n.copy(nightTheme = nightIdx)
            if (body.has("cryptoCoins")) {
                val coins = CryptoClient.normalize(body.optString("cryptoCoins"))
                if (coins.isNotEmpty()) n = n.copy(cryptoCoins = coins, showCrypto = true)
            }
            if (place != null) {
                n = n.copy(weatherName = place.name, weatherLat = place.lat, weatherLon = place.lon, showWeather = true)
            }
            n
        }
    }

    private fun stateJson(): JSONObject {
        val labels = prefs.appLabels()
        val images = TileImages.packagesWithImages(context)
        val hidden = prefs.hidden()
        val userRows = prefs.userRows()

        val apps = JSONArray()
        for (app in vm.snapshotApps().sortedBy { it.label.lowercase() }) {
            apps.put(
                JSONObject()
                    .put("pkg", app.packageName)
                    .put("label", app.label)
                    .put("custom", labels[app.packageName].orEmpty())
                    .put("hidden", app.packageName in hidden)
                    .put("image", app.packageName in images),
            )
        }

        val rows = JSONArray()
        for (name in prefs.rowNames()) {
            rows.put(
                JSONObject()
                    .put("name", name)
                    .put("apps", JSONArray(prefs.rowApps(name)))
                    .put("user", name in userRows),
            )
        }

        val s = prefs.settings()
        val settings = JSONObject()
            .put("tileSize", s.tileSize)
            .put("accentIndex", s.accentIndex)
            .put("wallpaperMinutes", s.wallpaperMinutes)
            .put("idleMinutes", s.idleMinutes)
            .put("showRecents", s.showRecents)
            .put("showWeather", s.showWeather)
            .put("fahrenheit", s.fahrenheit)
            .put("weatherName", s.weatherName)
            .put("themeIndex", s.themeIndex)
            .put("showForecast", s.showForecast)
            .put("showCrypto", s.showCrypto)
            .put("cryptoCoins", s.cryptoCoins)
            .put("autoTheme", s.autoTheme)
            .put("dashboard", s.dashboard)
            .put("showTrending", s.showTrending)
            .put("showHero", s.showHero)
            .put("heroFull", s.heroFull)
            .put("idleBackdrops", s.idleBackdrops)
            .put("fontIndex", s.fontIndex)
            .put("titleMode", s.titleMode)
            .put("tmdbRegion", s.tmdbRegion)
            .put("roundness", s.roundness)
            .put("dayTheme", s.dayTheme)
            .put("nightTheme", s.nightTheme)

        val options = JSONObject()
            .put("tileSizes", JSONArray(Options.tileSizeNames))
            .put("accents", JSONArray(Options.accents.map { it.first }))
            .put("themes", JSONArray(Themes.all.map { it.name }))
            .put("roundness", JSONArray(Options.roundnessNames))
            .put("fonts", JSONArray(AppFonts.names))
            .put("regions", JSONArray(Options.regions))
            .put("titleModes", JSONArray(Options.titleModes))
            .put("wallpaperMinutes", JSONArray(Options.wallpaperMinutes))
            .put("idleMinutes", JSONArray(Options.idleMinutes))
            .put("autoStartDelays", JSONArray(Options.autoStartDelays))

        val tiles = JSONArray()
        SmartHome.parse(prefs.smartTilesJson()).forEach { tiles.put(SmartHome.toJson(it)) }

        return JSONObject()
            .put("apps", apps)
            .put("rows", rows)
            .put("settings", settings)
            .put("options", options)
            .put("autostart", JSONObject().put("pkg", prefs.autoStartPackage()).put("delay", prefs.autoStartDelay()))
            .put("tmdb", JSONObject().put("hasKey", prefs.tmdbKey().isNotBlank()))
            .put(
                "wallpaperSource",
                JSONObject().put("url", prefs.wpUrl()).put("user", prefs.wpUser())
                    .put("hasPass", prefs.wpPass().isNotBlank()).put("insecure", prefs.wpInsecure())
                    .put("status", prefs.wpStatus()),
            )
            .put("ha", JSONObject().put("url", prefs.haUrl()).put("hasToken", prefs.haToken().isNotBlank()))
            .put("tiles", tiles)
            .put("wallpapers", listFiles("wallpapers", setOf("jpg", "jpeg", "png", "webp")))
            .put("apks", listFiles("apks", setOf("apk")))
    }

    private fun listFiles(folder: String, extensions: Set<String>): JSONArray {
        val array = JSONArray()
        val files = context.getExternalFilesDir(folder)?.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in extensions }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        for (file in files) array.put(JSONObject().put("name", file.name).put("size", file.length()))
        return array
    }

    private companion object {
        val PORTS = 8080..8090
        const val MAX_FAILURES = 8
        const val MAX_JSON = 2L * 1024 * 1024
        const val MAX_IMAGE = 15L * 1024 * 1024
        const val MAX_WALLPAPER = 30L * 1024 * 1024
        const val MAX_APK = 600L * 1024 * 1024
        const val MAX_CHUNK = 8L * 1024 * 1024
        val PACKAGE_PATTERN = Regex("[A-Za-z0-9_.]{1,200}")
    }
}
