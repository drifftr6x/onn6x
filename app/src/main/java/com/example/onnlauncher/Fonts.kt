package com.example.onnlauncher

import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Typography

/**
 * The fonts you can pick in Settings. The first entries are built into Android; the last four are
 * bundled with the app (res/font) and are free to use under the SIL Open Font License:
 * Poppins, Orbitron, Lora and Press Start 2P.
 */
object AppFonts {
    val names = listOf(
        "Theme default", "System", "Serif", "Monospace", "Condensed", "Casual",
        "Poppins", "Orbitron", "Lora", "Pixel (Press Start 2P)",
    )

    private val poppins by lazy {
        FontFamily(Font(R.font.poppins_regular, FontWeight.Normal), Font(R.font.poppins_bold, FontWeight.Bold))
    }
    private val orbitron by lazy { FontFamily(Font(R.font.orbitron, FontWeight.Normal)) }
    private val lora by lazy { FontFamily(Font(R.font.lora, FontWeight.Normal)) }
    private val pixel by lazy { FontFamily(Font(R.font.press_start_2p, FontWeight.Normal)) }
    private val condensed by lazy { FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL)) }
    private val casual by lazy { FontFamily(Typeface.create("casual", Typeface.NORMAL)) }

    /** Null means "leave the theme's own choice alone" (monospace on the retro themes, the system font elsewhere). */
    fun family(index: Int): FontFamily? = when (index) {
        1 -> FontFamily.SansSerif
        2 -> FontFamily.Serif
        3 -> FontFamily.Monospace
        4 -> condensed
        5 -> casual
        6 -> poppins
        7 -> orbitron
        8 -> lora
        9 -> pixel
        else -> null
    }
}

/** The same text styles with every one switched to [family]. */
fun Typography.withFamily(family: FontFamily?): Typography {
    if (family == null) return this
    return Typography(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
}
