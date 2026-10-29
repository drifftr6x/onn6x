package com.example.onnlauncher

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Movie and TV data from The Movie Database (https://www.themoviedb.org). You need a free API key
 * from your TMDB account (Settings > API). Both the short "API Key" and the long "API Read Access
 * Token" work. Streaming availability in [providers] is JustWatch data supplied by TMDB.
 *
 * Blocking calls: always run them off the main thread.
 * This product uses the TMDB API but is not endorsed or certified by TMDB.
 */
object TmdbClient {

    class KeyRejected : Exception("TMDB rejected the key")

    data class Item(
        val id: Int,
        val type: String,
        val name: String,
        val year: String,
        val overview: String,
        val rating: Double,
        val posterPath: String,
        val backdropPath: String = "",
        val genreIds: List<Int> = emptyList(),
    )

    data class Providers(val streaming: List<String>, val rentOrBuy: List<String>)

    private fun get(path: String, key: String): String {
        val bearer = key.length > 40
        val separator = if ('?' in path) "&" else "?"
        val url = "https://api.themoviedb.org/3$path" +
            if (bearer) "" else "${separator}api_key=" + URLEncoder.encode(key, "UTF-8")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/json")
        if (bearer) connection.setRequestProperty("Authorization", "Bearer $key")
        try {
            if (connection.responseCode == 401 || connection.responseCode == 403) throw KeyRejected()
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseItems(results: org.json.JSONArray?, forcedType: String?): List<Item> {
        results ?: return emptyList()
        val items = ArrayList<Item>()
        for (i in 0 until results.length()) {
            val o = results.getJSONObject(i)
            val type = forcedType ?: o.optString("media_type")
            val poster = o.optString("poster_path")
            if ((type != "movie" && type != "tv") || poster.isBlank() || poster == "null") continue
            val date = o.optString("release_date").ifBlank { o.optString("first_air_date") }
            val genres = o.optJSONArray("genre_ids")
            val backdrop = o.optString("backdrop_path").let { if (it == "null") "" else it }
            items.add(
                Item(
                    id = o.getInt("id"),
                    type = type,
                    name = o.optString("title").ifBlank { o.optString("name") },
                    year = date.take(4),
                    overview = o.optString("overview"),
                    rating = o.optDouble("vote_average", 0.0),
                    posterPath = poster,
                    backdropPath = backdrop,
                    genreIds = if (genres == null) emptyList() else (0 until genres.length()).map { genres.getInt(it) },
                ),
            )
        }
        return items
    }

    /** Movies and shows matching a title, best match first. Used to recognise what you are watching. */
    fun search(key: String, query: String): List<Item> =
        parseItems(
            JSONObject(get("/search/multi?language=en-US&include_adult=false&query=" + URLEncoder.encode(query, "UTF-8"), key))
                .optJSONArray("results"),
            null,
        ).take(5)

    /** This week's trending movies and shows, most popular first. Throws [KeyRejected] for a bad key. */
    fun trending(key: String): List<Item> =
        parseItems(JSONObject(get("/trending/all/week?language=en-US", key)).optJSONArray("results"), null).take(20)

    /**
     * Popular titles streaming (on subscription services) in [region]. Optional genre ids narrow it
     * to what you like; movie and TV genres use different ids, so each type gets its own list.
     */
    fun discover(key: String, region: String, movieGenres: List<Int>, tvGenres: List<Int>): List<Item> {
        fun one(type: String, genres: List<Int>): List<Item> {
            var path = "/discover/$type?language=en-US&sort_by=popularity.desc&watch_region=$region" +
                "&with_watch_monetization_types=flatrate"
            if (genres.isNotEmpty()) path += "&with_genres=" + genres.joinToString("|")
            return try {
                parseItems(JSONObject(get(path, key)).optJSONArray("results"), type)
            } catch (e: KeyRejected) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
        val movies = one("movie", movieGenres)
        val shows = one("tv", tvGenres)
        // Interleave so you get a mix of movies and shows.
        val mixed = ArrayList<Item>()
        for (i in 0 until maxOf(movies.size, shows.size)) {
            movies.getOrNull(i)?.let { mixed.add(it) }
            shows.getOrNull(i)?.let { mixed.add(it) }
        }
        return mixed.take(20)
    }

    /** Path of the official title logo (English or textless) or "" if there isn't one. */
    fun logoPath(type: String, id: Int, key: String): String = try {
        val logos = JSONObject(get("/$type/$id/images?include_image_language=en,null", key)).optJSONArray("logos")
        var best = ""
        if (logos != null) {
            for (i in 0 until logos.length()) {
                val o = logos.getJSONObject(i)
                val path = o.optString("file_path")
                if (path.endsWith(".svg")) continue
                if (o.optString("iso_639_1") == "en") {
                    best = path
                    break
                }
                if (best.isEmpty()) best = path
            }
        }
        best
    } catch (e: Exception) {
        ""
    }

    fun providers(type: String, id: Int, region: String, key: String): Providers = try {
        val region = JSONObject(get("/$type/$id/watch/providers", key))
            .optJSONObject("results")?.optJSONObject(region)
        fun names(field: String): List<String> {
            val array = region?.optJSONArray(field) ?: return emptyList()
            return (0 until array.length()).map { array.getJSONObject(it).optString("provider_name") }
        }
        Providers(
            streaming = (names("flatrate") + names("free") + names("ads")).filter { it.isNotBlank() }.distinct(),
            rentOrBuy = (names("rent") + names("buy")).filter { it.isNotBlank() }.distinct(),
        )
    } catch (e: Exception) {
        if (e is KeyRejected) throw e
        Providers(emptyList(), emptyList())
    }

    /** YouTube id of the best trailer for a title (an official trailer if there is one), or "" if TMDB lists none. */
    fun trailerKey(type: String, id: Int, key: String): String = try {
        val results = JSONObject(get("/$type/$id/videos?language=en-US", key)).optJSONArray("results")
        val videos = (0 until (results?.length() ?: 0)).map { results!!.getJSONObject(it) }
            .filter { it.optString("site") == "YouTube" && it.optString("key").isNotBlank() }
        val trailers = videos.filter { it.optString("type") == "Trailer" }
        (trailers.firstOrNull { it.optBoolean("official") } ?: trailers.firstOrNull() ?: videos.firstOrNull { it.optString("type") == "Teaser" })
            ?.optString("key").orEmpty()
    } catch (e: Exception) {
        if (e is KeyRejected) throw e
        ""
    }

    /**
     * Where downloaded pictures are kept, so rows fill instantly after a restart. Set once at startup;
     * null means no disk cache. TMDB never changes the picture behind a path, so files never go stale.
     */
    @Volatile var imageDir: File? = null
    private const val IMAGE_DIR_MAX_BYTES = 40L * 1024 * 1024
    private val trimLock = Any()

    /** [size] is a TMDB image width such as "w185" or "w342". */
    fun image(path: String, size: String): ImageBitmap? {
        if (path.isBlank()) return null
        val dir = imageDir
        // Only safe characters in the file name; TMDB paths look like "/abc123.jpg".
        val file = dir?.let { File(it, size + "_" + path.filter { c -> c.isLetterOrDigit() || c in "._-" }) }
        if (file != null && file.isFile) {
            val cached = BitmapFactory.decodeFile(file.path)
            if (cached != null) {
                file.setLastModified(System.currentTimeMillis())
                return cached.asImageBitmap()
            }
            file.delete()
        }
        val bytes = download("https://image.tmdb.org/t/p/$size$path") ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (dir != null && file != null) save(dir, file, bytes)
        return bitmap.asImageBitmap()
    }

    private fun download(url: String): ByteArray? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    /** Writes via a temp file so a half-written picture is never read, then trims the oldest files. */
    private fun save(dir: File, file: File, bytes: ByteArray) {
        try {
            dir.mkdirs()
            val temp = File(dir, file.name + ".tmp" + Thread.currentThread().id)
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) temp.delete()
            synchronized(trimLock) {
                val files = dir.listFiles()?.filter { it.isFile } ?: return
                var total = files.sumOf { it.length() }
                if (total <= IMAGE_DIR_MAX_BYTES) return
                for (old in files.sortedBy { it.lastModified() }) {
                    if (total <= IMAGE_DIR_MAX_BYTES * 3 / 4) break
                    total -= old.length()
                    old.delete()
                }
            }
        } catch (e: Exception) {
            // A full or missing cache only means downloading again next time.
        }
    }
}
