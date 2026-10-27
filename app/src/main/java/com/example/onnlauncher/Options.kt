package com.example.onnlauncher

/** User-editable settings, stored in [LauncherPrefs] and changed from the in-app Settings dialog or the phone editor. */
data class AppSettings(
    val tileSize: Int = 1,
    val accentIndex: Int = 0,
    val themeIndex: Int = 0,
    val wallpaperMinutes: Int = 5,
    /** Minutes of no input before the screensaver starts. 0 = off. */
    val idleMinutes: Int = 5,
    val showRecents: Boolean = true,
    val showWeather: Boolean = true,
    val fahrenheit: Boolean = true,
    val weatherName: String = "",
    val weatherLat: Double = Double.NaN,
    val weatherLon: Double = Double.NaN,
    val showForecast: Boolean = true,
    val showCrypto: Boolean = false,
    /** CoinGecko ids, comma separated. */
    val cryptoCoins: String = "bitcoin,ethereum",
    /** Switch between [dayTheme] and [nightTheme] at sunrise and sunset. */
    val autoTheme: Boolean = false,
    val dayTheme: Int = 7,
    val nightTheme: Int = 1,
    /** Weather and markets as two side-by-side cards under the first row. */
    val dashboard: Boolean = false,
    /** Index into [Options.roundness]; 0 keeps each theme's own corner radius. */
    val roundness: Int = 0,
    /** Trending row from TMDB. Needs an API key to show anything. */
    val showTrending: Boolean = true,
    /** Index into [Options.regions]: where "streaming on" is looked up. */
    val tmdbRegion: Int = 0,
    /** 0 = trending this week, 1 = popular streaming in your region, 2 = tailored by the taste test. */
    val titleMode: Int = 0,
    val showHero: Boolean = true,
) {
    val hasLocation: Boolean get() = !weatherLat.isNaN() && !weatherLon.isNaN()
}

/** The values each setting cycles through. */
object Options {
    val tileWidths = listOf(144, 176, 224)
    val tileSizeNames = listOf("Small", "Medium", "Large")

    val accents: List<Pair<String, Long>> = listOf(
        "Theme default" to 0L,
        "Sky" to 0xFF4FC3F7L,
        "Mint" to 0xFF69F0AEL,
        "Amber" to 0xFFFFCA28L,
        "Coral" to 0xFFFF8A65L,
        "Violet" to 0xFFB388FFL,
        "White" to 0xFFFFFFFFL,
    )

    val roundnessNames = listOf("Theme default", "Soft", "Round", "Very round")
    /** Tile corner radius in dp for each roundness step; 0 = use the theme. */
    val roundnessDp = listOf(0, 14, 26, 40)

    val titleModes = listOf("Trending this week", "Popular in your region", "My tastes")

    val regions = listOf("US", "CA", "GB", "AU", "IE", "DE", "FR", "ES", "IT", "NL", "SE", "MX", "BR", "JP", "IN")

    val wallpaperMinutes = listOf(1, 5, 15, 30)
    val idleMinutes = listOf(0, 2, 5, 10, 30)
    val autoStartDelays = listOf(0, 5, 8, 15, 30)

    /** Next value in [options] after [current], wrapping around. */
    fun next(options: List<Int>, current: Int): Int {
        val i = options.indexOf(current)
        return options[(i + 1) % options.size]
    }
}

/** Shortcut tiles shown in the "Quick settings" row and the quick menu. */
enum class QuickAction(val label: String) {
    ASSISTANT("Gemini / Voice"),
    WIFI("Wi-Fi"),
    BLUETOOTH("Bluetooth"),
    DISPLAY("Display"),
    SOUND("Sound"),
    DEVELOPER("Developer options"),
    SLEEP("Sleep timer"),
    DEVICE_INFO("Device info"),
    INSTALL_APK("Install APKs"),
    PHONE_EDITOR("Phone editor"),
    FREE_MEMORY("Free up memory"),
}

data class WeatherState(
    val text: String,
    val place: String,
    val forecast: WeatherClient.Forecast? = null,
)

/**
 * A button that calls out to a smart-home service when pressed.
 * kind "ha": Home Assistant service call (domain, service, entity, optional JSON data).
 * kind "url": plain web request to [url] with [method] and optional JSON body in [data].
 */
data class SmartTile(
    val id: String,
    val label: String,
    val kind: String = "ha",
    val domain: String = "",
    val service: String = "",
    val entity: String = "",
    val data: String = "",
    val url: String = "",
    val method: String = "POST",
)

/** Shown as a countdown banner while an app is about to open by itself after boot. */
data class AutoStartState(val label: String, val secondsLeft: Int)

/** Address and PIN for the phone editor while it is running. */
data class EditorInfo(val url: String, val pin: String)

/** A trending movie or show with its small poster. */
data class TrendingTitle(
    val id: Int,
    val type: String,
    val name: String,
    val year: String,
    val overview: String,
    val rating: Double,
    val posterPath: String,
    val poster: androidx.compose.ui.graphics.ImageBitmap?,
    val genreIds: List<Int> = emptyList(),
    /** Wide picture and logo, loaded only for the few titles the hero banner rotates through. */
    val backdrop: androidx.compose.ui.graphics.ImageBitmap? = null,
    val logo: androidx.compose.ui.graphics.ImageBitmap? = null,
)

/** The "where can I watch this" dialog for one title. */
data class TitleDetail(
    val title: TrendingTitle,
    val loading: Boolean = true,
    val streaming: List<String> = emptyList(),
    val rentOrBuy: List<String> = emptyList(),
    /** Installed apps that match a streaming service for this title. */
    val apps: List<AppEntry> = emptyList(),
    val bigPoster: androidx.compose.ui.graphics.ImageBitmap? = null,
)
