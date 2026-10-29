package com.example.onnlauncher

import java.util.Locale
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Coin prices from CoinGecko's free public API (no account or key). It is rate limited, so the
 * launcher asks at most every few minutes. Blocking: run off the main thread.
 */
object CryptoClient {

    data class Coin(val id: String, val symbol: String, val price: Double, val change24h: Double) {
        val priceText: String
            get() = when {
                price >= 1000 -> String.format(Locale.US, "$%,.0f", price)
                price >= 1 -> String.format(Locale.US, "$%,.2f", price)
                else -> String.format(Locale.US, "$%.4f", price)
            }
        val changeText: String get() = String.format(Locale.US, "%+.1f%%", change24h)
    }

    private val shortcuts = mapOf(
        "btc" to "bitcoin", "eth" to "ethereum", "sol" to "solana", "doge" to "dogecoin",
        "xrp" to "ripple", "ada" to "cardano", "ltc" to "litecoin", "bnb" to "binancecoin",
        "dot" to "polkadot", "avax" to "avalanche-2", "link" to "chainlink", "xlm" to "stellar",
    )

    /** "BTC, eth, solana" -> "bitcoin,ethereum,solana". At most 6 coins. */
    fun normalize(input: String): String = input.split(',', ' ', ';')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '-' } }
        .map { shortcuts[it] ?: it }
        .distinct()
        .take(6)
        .joinToString(",")

    fun prices(ids: String): List<Coin>? = try {
        if (ids.isBlank()) {
            emptyList()
        } else {
            val url = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&ids=" +
                URLEncoder.encode(ids, "UTF-8") + "&price_change_percentage=24h"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "OnnLauncher/1.0")
            val body = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val array = JSONArray(body)
            val found = (0 until array.length()).map { array.getJSONObject(it) }.associateBy { it.optString("id") }
            ids.split(',').mapNotNull { id ->
                found[id]?.let {
                    Coin(
                        id = id,
                        symbol = it.optString("symbol").uppercase(),
                        price = it.optDouble("current_price", 0.0),
                        change24h = it.optDouble("price_change_percentage_24h", 0.0),
                    )
                }
            }
        }
    } catch (e: Exception) {
        null
    }
}
