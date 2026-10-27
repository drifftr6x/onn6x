package com.example.onnlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** SharedPreferences wrapper: rows, hidden apps, recents, names, remote map, settings, automation, and backup. */
class LauncherPrefs(context: Context) {

    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    // ---- rows ----

    fun hasRows(): Boolean = sp.contains(KEY_ROWS)

    fun rowNames(): List<String> =
        sp.getString(KEY_ROWS, "").orEmpty().split("\n").filter { it.isNotBlank() }

    fun setRowNames(names: List<String>) {
        sp.edit().putString(KEY_ROWS, names.joinToString("\n")).apply()
    }

    fun rowApps(row: String): List<String> =
        sp.getString(KEY_ROW_PREFIX + row, "").orEmpty().split(",").filter { it.isNotBlank() }

    fun setRowApps(row: String, packages: List<String>) {
        sp.edit().putString(KEY_ROW_PREFIX + row, packages.joinToString(",")).apply()
    }

    fun removeRow(row: String) {
        sp.edit().remove(KEY_ROW_PREFIX + row).apply()
    }

    /** Rows the user created. Unlike seeded rows, these stay visible even while empty. */
    fun userRows(): Set<String> = sp.getStringSet(KEY_USER_ROWS, emptySet()).orEmpty().toSet()

    fun setUserRows(rows: Set<String>) {
        sp.edit().putStringSet(KEY_USER_ROWS, rows).apply()
    }

    fun hidden(): Set<String> = sp.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun setHidden(packages: Set<String>) {
        sp.edit().putStringSet(KEY_HIDDEN, packages).apply()
    }

    // ---- custom app names ----

    fun appLabels(): Map<String, String> = try {
        val obj = JSONObject(sp.getString(KEY_LABELS, "{}") ?: "{}")
        obj.keys().asSequence().associateWith { obj.getString(it) }
    } catch (e: Exception) {
        emptyMap()
    }

    /** A null or blank [name] removes the custom name. */
    fun setAppLabel(packageName: String, name: String?) {
        val labels = appLabels().toMutableMap()
        if (name.isNullOrBlank()) labels.remove(packageName) else labels[packageName] = name.trim().take(40)
        sp.edit().putString(KEY_LABELS, JSONObject(labels as Map<*, *>).toString()).apply()
    }

    // ---- recents ----

    /** Most recently used first. */
    fun recents(): List<String> =
        sp.getString(KEY_RECENTS, "").orEmpty().split(",").filter { it.isNotBlank() }

    fun pushRecent(packageName: String) {
        val updated = (listOf(packageName) + recents().filter { it != packageName }).take(MAX_RECENTS)
        sp.edit().putString(KEY_RECENTS, updated.joinToString(",")).apply()
    }

    // ---- remote button remapping: Android key code -> package name ----

    fun keymap(): Map<Int, String> =
        sp.getString(KEY_KEYMAP, "").orEmpty().split(";").mapNotNull { entry ->
            val parts = entry.split("=")
            val code = parts.getOrNull(0)?.toIntOrNull()
            val pkg = parts.getOrNull(1)
            if (parts.size == 2 && code != null && !pkg.isNullOrBlank()) code to pkg else null
        }.toMap()

    fun setKeymap(map: Map<Int, String>) {
        sp.edit().putString(KEY_KEYMAP, map.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    // ---- auto-start after boot ----

    /** Package to open by itself after the box boots, or empty for none. */
    fun autoStartPackage(): String = sp.getString(KEY_AUTOSTART_PKG, "").orEmpty()

    fun autoStartDelay(): Int = sp.getInt(KEY_AUTOSTART_DELAY, 8)

    fun setAutoStart(packageName: String, delaySeconds: Int) {
        sp.edit()
            .putString(KEY_AUTOSTART_PKG, packageName)
            .putInt(KEY_AUTOSTART_DELAY, delaySeconds)
            .apply()
    }

    /** Android's boot counter at the last launch, used to notice "first launch since a reboot". */
    fun lastBootCount(): Int = sp.getInt(KEY_LAST_BOOT, -1)

    fun setLastBootCount(count: Int) {
        sp.edit().putInt(KEY_LAST_BOOT, count).apply()
    }

    // ---- smart home ----

    fun haUrl(): String = sp.getString(KEY_HA_URL, "").orEmpty()

    fun haToken(): String = sp.getString(KEY_HA_TOKEN, "").orEmpty()

    /** A null [token] keeps the saved one. */
    fun setHaConfig(url: String, token: String?) {
        val edit = sp.edit().putString(KEY_HA_URL, url.trim())
        if (token != null) edit.putString(KEY_HA_TOKEN, token.trim())
        edit.apply()
    }

    fun tmdbKey(): String = sp.getString(KEY_TMDB, "").orEmpty()

    fun setTmdbKey(key: String) {
        sp.edit().putString(KEY_TMDB, key.trim()).apply()
    }

    /** Genre ids picked by the taste test, as "28,12,35". */
    fun tasteGenres(type: String): List<Int> =
        sp.getString("taste_$type", "").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }

    fun setTasteGenres(type: String, ids: List<Int>) {
        sp.edit().putString("taste_$type", ids.joinToString(",")).apply()
    }

    /** The last few titles opened from the details dialog, newest first, as a JSON array. */
    fun watchedJson(): String = sp.getString("watched", "[]") ?: "[]"
    fun watchlistJson(): String = sp.getString("watchlist", "[]") ?: "[]"
    fun setWatchlistJson(json: String) { sp.edit().putString("watchlist", json).apply() }

    fun setWatchedJson(json: String) {
        sp.edit().putString("watched", json).apply()
    }

    fun smartTilesJson(): String = sp.getString(KEY_SMART_TILES, "[]") ?: "[]"

    fun setSmartTilesJson(json: String) {
        sp.edit().putString(KEY_SMART_TILES, json).apply()
    }

    // ---- Home override and crash detection ----

    /** Whether the accessibility service should bounce the stock launcher back to us. */
    fun overrideEnabled(): Boolean = sp.getBoolean(KEY_OVERRIDE, true)

    fun setOverrideEnabled(enabled: Boolean) {
        sp.edit().putBoolean(KEY_OVERRIDE, enabled).apply()
    }

    /** Set at launch and cleared a few seconds later. Still set on the next launch means we crashed. */
    fun startupPending(): Boolean = sp.getBoolean(KEY_STARTUP_PENDING, false)

    fun setStartupPending(pending: Boolean) {
        sp.edit().putBoolean(KEY_STARTUP_PENDING, pending).apply()
    }

    // ---- settings ----

    fun settings(): AppSettings = AppSettings(
        tileSize = sp.getInt("s_tile", 1).coerceIn(0, Options.tileWidths.lastIndex),
        accentIndex = sp.getInt("s_accent", 0).coerceIn(0, Options.accents.lastIndex),
        themeIndex = sp.getInt("s_theme", 0).coerceIn(0, Themes.all.lastIndex),
        wallpaperMinutes = sp.getInt("s_wall", 5),
        idleMinutes = sp.getInt("s_idle", 5),
        showRecents = sp.getBoolean("s_recents", true),
        showWeather = sp.getBoolean("s_weather", true),
        fahrenheit = sp.getBoolean("s_f", true),
        weatherName = sp.getString("s_wname", "").orEmpty(),
        weatherLat = sp.getString("s_wlat", null)?.toDoubleOrNull() ?: Double.NaN,
        weatherLon = sp.getString("s_wlon", null)?.toDoubleOrNull() ?: Double.NaN,
        showForecast = sp.getBoolean("s_forecast", true),
        showCrypto = sp.getBoolean("s_crypto", false),
        cryptoCoins = sp.getString("s_coins", "bitcoin,ethereum").orEmpty(),
        autoTheme = sp.getBoolean("s_autotheme", false),
        dayTheme = sp.getInt("s_daytheme", 7).coerceIn(0, Themes.all.lastIndex),
        nightTheme = sp.getInt("s_nighttheme", 1).coerceIn(0, Themes.all.lastIndex),
        dashboard = sp.getBoolean("s_dash", false),
        roundness = sp.getInt("s_round", 0).coerceIn(0, Options.roundnessDp.lastIndex),
        showTrending = sp.getBoolean("s_trending", true),
        tmdbRegion = sp.getInt("s_region", 0).coerceIn(0, Options.regions.lastIndex),
        titleMode = sp.getInt("s_tmode", 0).coerceIn(0, Options.titleModes.lastIndex),
        showHero = sp.getBoolean("s_hero", true),
        heroFull = sp.getBoolean("s_herofull", false),
        idleBackdrops = sp.getBoolean("s_idlebd", true),
    )

    fun setSettings(s: AppSettings) {
        sp.edit()
            .putInt("s_tile", s.tileSize)
            .putInt("s_accent", s.accentIndex)
            .putInt("s_theme", s.themeIndex)
            .putInt("s_wall", s.wallpaperMinutes)
            .putInt("s_idle", s.idleMinutes)
            .putBoolean("s_recents", s.showRecents)
            .putBoolean("s_weather", s.showWeather)
            .putBoolean("s_f", s.fahrenheit)
            .putString("s_wname", s.weatherName)
            .putString("s_wlat", if (s.weatherLat.isNaN()) null else s.weatherLat.toString())
            .putString("s_wlon", if (s.weatherLon.isNaN()) null else s.weatherLon.toString())
            .putBoolean("s_forecast", s.showForecast)
            .putBoolean("s_crypto", s.showCrypto)
            .putString("s_coins", s.cryptoCoins)
            .putBoolean("s_autotheme", s.autoTheme)
            .putInt("s_daytheme", s.dayTheme)
            .putInt("s_nighttheme", s.nightTheme)
            .putBoolean("s_dash", s.dashboard)
            .putInt("s_round", s.roundness)
            .putBoolean("s_trending", s.showTrending)
            .putInt("s_region", s.tmdbRegion)
            .putInt("s_tmode", s.titleMode)
            .putBoolean("s_hero", s.showHero)
            .putBoolean("s_herofull", s.heroFull)
            .putBoolean("s_idlebd", s.idleBackdrops)
            .apply()
    }

    // ---- backup and restore of everything above ----

    /** Everything except secrets (the Home Assistant token) and one-off flags. */
    fun exportJson(): String {
        val out = JSONObject()
        for ((key, value) in sp.all) {
            if (key in NOT_EXPORTED || value == null) continue
            val entry = JSONObject()
            when (value) {
                is String -> entry.put("t", "s").put("v", value)
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
                else -> continue
            }
            out.put(key, entry)
        }
        return JSONObject().put("version", 1).put("prefs", out).toString(2)
    }

    /**
     * Replaces all saved layout and settings. Secrets already on this box are kept, since backups don't
     * contain them. Nothing is changed if the text isn't a valid backup.
     */
    fun importJson(text: String): Boolean = try {
        val prefs = JSONObject(text).getJSONObject("prefs")
        val keepSecrets = SECRET_KEYS.associateWith { sp.getString(it, null) }
        val keepBoot = sp.getInt(KEY_LAST_BOOT, -1)
        val edit = sp.edit().clear()
        for (key in prefs.keys()) {
            if (key in NOT_EXPORTED) continue
            val entry = prefs.getJSONObject(key)
            when (entry.getString("t")) {
                "s" -> edit.putString(key, entry.getString("v"))
                "b" -> edit.putBoolean(key, entry.getBoolean("v"))
                "i" -> edit.putInt(key, entry.getInt("v"))
                "l" -> edit.putLong(key, entry.getLong("v"))
                "f" -> edit.putFloat(key, entry.getDouble("v").toFloat())
                "ss" -> {
                    val array = entry.getJSONArray("v")
                    edit.putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
                }
            }
        }
        for ((key, value) in keepSecrets) {
            if (value != null) edit.putString(key, value)
        }
        edit.putInt(KEY_LAST_BOOT, keepBoot)
        edit.commit()
    } catch (e: Exception) {
        false
    }

    private companion object {
        const val KEY_ROWS = "rows"
        const val KEY_ROW_PREFIX = "row_"
        const val KEY_USER_ROWS = "user_rows"
        const val KEY_HIDDEN = "hidden"
        const val KEY_LABELS = "app_labels"
        const val KEY_RECENTS = "recents"
        const val KEY_KEYMAP = "keymap"
        const val KEY_AUTOSTART_PKG = "autostart_pkg"
        const val KEY_AUTOSTART_DELAY = "autostart_delay"
        const val KEY_LAST_BOOT = "last_boot_count"
        const val KEY_HA_URL = "ha_url"
        const val KEY_HA_TOKEN = "ha_token"
        const val KEY_TMDB = "tmdb_key"
        const val KEY_SMART_TILES = "smart_tiles"
        const val KEY_OVERRIDE = "home_override"
        const val KEY_STARTUP_PENDING = "startup_pending"
        const val MAX_RECENTS = 12

        val SECRET_KEYS = setOf(KEY_HA_TOKEN, KEY_TMDB)
        val NOT_EXPORTED = setOf(KEY_HA_TOKEN, KEY_TMDB, KEY_STARTUP_PENDING, KEY_LAST_BOOT)
    }
}
