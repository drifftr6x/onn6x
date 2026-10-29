package com.example.onnlauncher

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A look for the whole launcher. All built-in themes are original designs made of plain colors,
 * corner shapes and a font choice. Add your own by adding an entry to [Themes.all].
 */
data class LauncherTheme(
    val name: String,
    /** Highlight color: row titles, focus borders. The Accent setting can override it. */
    val accent: Color,
    /** Solid base color behind the wallpaper. */
    val backdrop: Color,
    /** Dark wash over the wallpaper, top and bottom, so text stays readable. */
    val washTop: Color,
    val washBottom: Color,
    /** Behind app icons on a tile. */
    val tile: Color,
    /** Text-only tiles (quick settings, smart home). */
    val shortcut: Color,
    /** Dialog background and the text fields inside dialogs. */
    val panel: Color,
    val field: Color,
    /** Secondary text. */
    val subtle: Color,
    /** Tile corner radius. 0 = square. */
    val corner: Dp,
    val monospace: Boolean = false,
    val upperTitles: Boolean = false,
    /** Main text color. White on dark themes, near-black on light ones. */
    val text: Color = Color.White,
    /** True for light themes: switches the Material color scheme so buttons and cards read correctly. */
    val light: Boolean = false,
    /** Draws the first row inside a frosted rounded bar, like a dock. */
    val dock: Boolean = false,
    val dockColor: Color = Color(0x33FFFFFF),
    /** Optional drawn background: "bricks", "lattice", "meadow" or "swirl". See ThemePattern. */
    val pattern: String = "",
)

object Themes {
    val all: List<LauncherTheme> = listOf(
        LauncherTheme(
            name = "Classic",
            accent = Color(0xFF4FC3F7),
            backdrop = Color(0xFF05070C),
            washTop = Color(0xCC111723), washBottom = Color(0xEE05070C),
            tile = Color(0xFF222B3A), shortcut = Color(0xFF2A3548),
            panel = Color(0xFF1B2230), field = Color(0xFF0E131C),
            subtle = Color(0xFFB8C4D9), corner = 12.dp,
        ),
        LauncherTheme(
            name = "Midnight",
            accent = Color(0xFF8AB4FF),
            backdrop = Color(0xFF03030A),
            washTop = Color(0xCC0B1026), washBottom = Color(0xEE03030A),
            tile = Color(0xFF182040), shortcut = Color(0xFF1F2A55),
            panel = Color(0xFF121936), field = Color(0xFF0A0F24),
            subtle = Color(0xFFA9B6E0), corner = 16.dp,
        ),
        LauncherTheme(
            name = "Sunset",
            accent = Color(0xFFFF8A65),
            backdrop = Color(0xFF140807),
            washTop = Color(0xCC3B1420), washBottom = Color(0xEE140807),
            tile = Color(0xFF4A2230), shortcut = Color(0xFF5C2B38),
            panel = Color(0xFF2E1520), field = Color(0xFF1E0D14),
            subtle = Color(0xFFF2C4B8), corner = 20.dp,
        ),
        LauncherTheme(
            name = "Forest",
            accent = Color(0xFF69F0AE),
            backdrop = Color(0xFF040A06),
            washTop = Color(0xCC0F2418), washBottom = Color(0xEE040A06),
            tile = Color(0xFF1C3626), shortcut = Color(0xFF244630),
            panel = Color(0xFF122A1C), field = Color(0xFF0A1A11),
            subtle = Color(0xFFB5D8C2), corner = 14.dp,
        ),
        LauncherTheme(
            name = "Ocean",
            accent = Color(0xFF4DD0E1),
            backdrop = Color(0xFF03090D),
            washTop = Color(0xCC0A2A3A), washBottom = Color(0xEE03090D),
            tile = Color(0xFF14384A), shortcut = Color(0xFF1A4A5E),
            panel = Color(0xFF0E2B3A), field = Color(0xFF08181F),
            subtle = Color(0xFFADD8E6), corner = 18.dp,
        ),
        LauncherTheme(
            name = "Neon City",
            accent = Color(0xFFFF4DDB),
            backdrop = Color(0xFF07030F),
            washTop = Color(0xCC1E0A3A), washBottom = Color(0xEE07030F),
            tile = Color(0xFF2A1250), shortcut = Color(0xFF3A1766),
            panel = Color(0xFF1A0B33), field = Color(0xFF0F0620),
            subtle = Color(0xFFD9B8FF), corner = 8.dp,
        ),
        LauncherTheme(
            name = "Retro Arcade",
            accent = Color(0xFFFFEB3B),
            backdrop = Color(0xFF000000),
            washTop = Color(0xCC0D0D0D), washBottom = Color(0xEE000000),
            tile = Color(0xFF1A1A2E), shortcut = Color(0xFF16213E),
            panel = Color(0xFF0F0F1A), field = Color(0xFF000000),
            subtle = Color(0xFF9FE870), corner = 0.dp,
            monospace = true, upperTitles = true,
        ),
        LauncherTheme(
            name = "Glass",
            accent = Color(0xFF2F6BFF),
            backdrop = Color(0xFFE6E9EF),
            washTop = Color(0xDDF4F6FA), washBottom = Color(0xEED9DFE8),
            tile = Color(0xFFFFFFFF), shortcut = Color(0xFFD8DFEA),
            panel = Color(0xFFF3F5F9), field = Color(0xFFFFFFFF),
            subtle = Color(0xFF566074), corner = 20.dp,
            text = Color(0xFF12161D), light = true,
            dock = true, dockColor = Color(0x99FFFFFF),
        ),
        LauncherTheme(
            name = "OLED Black",
            accent = Color(0xFFFFFFFF),
            backdrop = Color(0xFF000000),
            washTop = Color(0x99000000), washBottom = Color(0xFF000000),
            tile = Color(0xFF111111), shortcut = Color(0xFF1A1A1A),
            panel = Color(0xFF0D0D0D), field = Color(0xFF000000),
            subtle = Color(0xFF9E9E9E), corner = 10.dp,
        ),
        LauncherTheme(
            name = "Brick & Sky",
            accent = Color(0xFFFFD84D),
            backdrop = Color(0xFF3A6FE0),
            washTop = Color(0x00FFFFFF), washBottom = Color(0x223A6FE0),
            tile = Color(0xFFFFFFFF), shortcut = Color(0xFFE52521),
            panel = Color(0xFFFFF4D6), field = Color(0xFFFFFFFF),
            subtle = Color(0xFF10204A), corner = 6.dp,
            text = Color(0xFF10204A), light = true,
            pattern = "bricks",
        ),
        LauncherTheme(
            name = "Ancient Ruins",
            accent = Color(0xFF1F7A5A),
            backdrop = Color(0xFFBFE3F0),
            washTop = Color(0x00FFFFFF), washBottom = Color(0x22FFFFFF),
            tile = Color(0xFFF6EBD0), shortcut = Color(0xFFE3CF98),
            panel = Color(0xFFF6EBD0), field = Color(0xFFFFFAEB),
            subtle = Color(0xFF5A4B2C), corner = 14.dp,
            text = Color(0xFF2B2314), light = true,
            pattern = "meadow",
        ),
        LauncherTheme(
            name = "Portal Lab",
            accent = Color(0xFF7CFF4F),
            backdrop = Color(0xFF04100C),
            washTop = Color(0x660A2A22), washBottom = Color(0xEE050D0A),
            tile = Color(0xFF123A3A), shortcut = Color(0xFF1A4B52),
            panel = Color(0xFF0C2A2A), field = Color(0xFF06161A),
            subtle = Color(0xFF9FE8D8), corner = 24.dp,
            pattern = "swirl",
        ),
    )

    fun get(index: Int): LauncherTheme = all[index.coerceIn(0, all.lastIndex)]
}

val LocalLauncherTheme = staticCompositionLocalOf { Themes.all.first() }
