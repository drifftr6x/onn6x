package com.example.onnlauncher

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Smart-home buttons. Two kinds:
 *  - "ha": a Home Assistant service call, e.g. light.toggle on light.living_room.
 *    POSTs to {base}/api/services/{domain}/{service} with a Bearer token.
 *  - "url": any web request, e.g. a webhook.
 * Blocking calls: run them off the main thread.
 */
object SmartHome {

    private val METHODS = setOf("GET", "POST", "PUT")

    fun parse(json: String): List<SmartTile> = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val label = obj.optString("label").trim()
            if (label.isEmpty()) return@mapNotNull null
            SmartTile(
                id = obj.optString("id").ifBlank { "t$index" },
                label = label.take(40),
                kind = if (obj.optString("kind") == "url") "url" else "ha",
                domain = obj.optString("domain").trim(),
                service = obj.optString("service").trim(),
                entity = obj.optString("entity").trim(),
                data = obj.optString("data").trim(),
                url = obj.optString("url").trim(),
                method = obj.optString("method").uppercase().let { if (it in METHODS) it else "POST" },
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun toJson(tile: SmartTile): JSONObject = JSONObject()
        .put("id", tile.id)
        .put("label", tile.label)
        .put("kind", tile.kind)
        .put("domain", tile.domain)
        .put("service", tile.service)
        .put("entity", tile.entity)
        .put("data", tile.data)
        .put("url", tile.url)
        .put("method", tile.method)

    fun encode(tiles: List<SmartTile>): String {
        val array = JSONArray()
        tiles.forEach { array.put(toJson(it)) }
        return array.toString()
    }

    /** Runs the tile and returns a short message for a toast. */
    fun run(baseUrl: String, token: String, tile: SmartTile): String {
        return try {
            val isHa = tile.kind == "ha"
            if (isHa) {
                if (!baseUrl.startsWith("http")) {
                    return "Set the Home Assistant address first (phone editor, Automation tab)."
                }
                if (tile.domain.isBlank() || tile.service.isBlank()) return "${tile.label}: missing domain or service."
            } else if (!tile.url.startsWith("http")) {
                return "${tile.label}: the address must start with http:// or https://"
            }
            val target = if (isHa) {
                baseUrl.trimEnd('/') + "/api/services/" + tile.domain + "/" + tile.service
            } else {
                tile.url
            }
            val method = if (isHa) "POST" else tile.method
            val body: String? = if (isHa) haBody(tile) else tile.data.ifBlank { null }

            val connection = URL(target).openConnection() as HttpURLConnection
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.requestMethod = method
            if (tile.kind == "ha" && token.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null && method != "GET") {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            connection.disconnect()
            when {
                code in 200..299 -> "Done: ${tile.label}"
                code == 401 -> "${tile.label}: not allowed (HTTP 401). Check the token."
                else -> "${tile.label}: HTTP $code"
            }
        } catch (e: Exception) {
            "${tile.label}: ${e.message ?: "failed"}"
        }
    }

    private fun haBody(tile: SmartTile): String {
        val obj = try {
            if (tile.data.isBlank()) JSONObject() else JSONObject(tile.data)
        } catch (e: Exception) {
            JSONObject()
        }
        if (tile.entity.isNotBlank()) obj.put("entity_id", tile.entity)
        return obj.toString()
    }
}
