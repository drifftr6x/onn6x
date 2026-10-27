package com.example.onnlauncher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Weather from Open-Meteo (https://open-meteo.com), which needs no account or API key.
 * Blocking calls: always run them off the main thread.
 */
object WeatherClient {

    data class Place(val name: String, val lat: Double, val lon: Double)

    /** City name -> coordinates, or null if nothing matched or there is no network. */
    fun geocode(query: String): Place? = try {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(query, "UTF-8") + "&count=1&language=en&format=json"
        val first = JSONObject(get(url)).optJSONArray("results")?.optJSONObject(0)
        if (first == null) {
            null
        } else {
            Place(
                name = listOf(first.optString("name"), first.optString("admin1"))
                    .filter { it.isNotBlank() }
                    .joinToString(", "),
                lat = first.getDouble("latitude"),
                lon = first.getDouble("longitude"),
            )
        }
    } catch (e: Exception) {
        null
    }

    /** Text like "72°F · Partly cloudy", or null on any failure. */
    fun current(lat: Double, lon: Double, fahrenheit: Boolean): String? = try {
        val unit = if (fahrenheit) "fahrenheit" else "celsius"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code&temperature_unit=$unit"
        val current = JSONObject(get(url)).getJSONObject("current")
        val temp = Math.round(current.getDouble("temperature_2m")).toInt()
        "$temp°${if (fahrenheit) "F" else "C"} · ${describe(current.getInt("weather_code"))}"
    } catch (e: Exception) {
        null
    }

    /** Current conditions plus hourly, daily and sun times in one request. Null on any failure. */
    fun load(lat: Double, lon: Double, fahrenheit: Boolean): Snapshot? = try {
        val unit = if (fahrenheit) "fahrenheit" else "celsius"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code" +
            "&hourly=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset" +
            "&timezone=auto&timeformat=unixtime&forecast_days=6&temperature_unit=$unit"
        val root = JSONObject(get(url))
        val current = root.getJSONObject("current")
        val nowSec = current.optLong("time", System.currentTimeMillis() / 1000)
        val temp = Math.round(current.getDouble("temperature_2m")).toInt()
        val text = "$temp°${if (fahrenheit) "F" else "C"} · ${describe(current.getInt("weather_code"))}"

        val h = root.getJSONObject("hourly")
        val ht = h.getJSONArray("time")
        val htemp = h.getJSONArray("temperature_2m")
        val hcode = h.getJSONArray("weather_code")
        val hours = ArrayList<HourPoint>()
        for (i in 0 until ht.length()) {
            val t = ht.getLong(i)
            if (t >= nowSec - 1800 && hours.size < 8 && !htemp.isNull(i)) {
                hours.add(HourPoint(t, Math.round(htemp.getDouble(i)).toInt(), hcode.optInt(i)))
            }
        }

        val d = root.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val days = ArrayList<DayPoint>()
        for (i in 0 until dt.length()) {
            days.add(
                DayPoint(
                    time = dt.getLong(i),
                    hi = Math.round(d.getJSONArray("temperature_2m_max").getDouble(i)).toInt(),
                    lo = Math.round(d.getJSONArray("temperature_2m_min").getDouble(i)).toInt(),
                    code = d.getJSONArray("weather_code").optInt(i),
                    sunrise = d.getJSONArray("sunrise").optLong(i, 0),
                    sunset = d.getJSONArray("sunset").optLong(i, 0),
                ),
            )
        }
        Snapshot(
            text,
            Forecast(root.optInt("utc_offset_seconds", 0), hours, days, temp, current.getInt("weather_code"), describe(current.getInt("weather_code"))),
        )
    } catch (e: Exception) {
        null
    }

    data class HourPoint(val time: Long, val temp: Int, val code: Int)

    data class DayPoint(val time: Long, val hi: Int, val lo: Int, val code: Int, val sunrise: Long, val sunset: Long)

    data class Forecast(
        val utcOffset: Int,
        val hours: List<HourPoint>,
        val days: List<DayPoint>,
        val nowTemp: Int = 0,
        val nowCode: Int = 0,
        val nowText: String = "",
    ) {
        /** True/false if the sun times say so, null when there is no usable sun data for [nowSec]. */
        fun isDaylight(nowSec: Long): Boolean? {
            val usable = days.filter { it.sunrise > 0 && it.sunset > 0 }
            if (usable.isEmpty()) return null
            if (usable.any { nowSec in it.sunrise..it.sunset }) return true
            val first = usable.first().sunrise - 86_400
            val last = usable.last().sunset
            return if (nowSec in first..last) false else null
        }

        /** Today's sunrise and sunset for the card. */
        fun today(nowSec: Long): DayPoint? =
            days.firstOrNull { nowSec < it.sunset || it.sunset == 0L } ?: days.lastOrNull()
    }

    data class Snapshot(val text: String, val forecast: Forecast)

    fun icon(code: Int): String = when (code) {
        0, 1 -> "☀️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        try {
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** WMO weather interpretation codes, as Open-Meteo reports them. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "Weather"
    }
}
