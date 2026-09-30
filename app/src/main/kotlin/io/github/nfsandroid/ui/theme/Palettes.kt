package io.github.nfsandroid.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.nfsandroid.data.Look

/**
 * The palettes besides the brand's and the wallpaper's: the brand's scheme with other accents (its
 * primary and secondary colours and their containers), light and dark.
 */
object Palettes {
    private class Accent(
        val primary: Color, val onPrimary: Color, val container: Color, val onContainer: Color,
        val secondary: Color, val secondaryContainer: Color, val onSecondaryContainer: Color,
    )

    private val LIGHT = mapOf(
        "forest" to Accent(Color(0xFF15803D), Color.White, Color(0xFFDCFCE7), Color(0xFF052E16), Color(0xFF0F766E), Color(0xFFCCFBF1), Color(0xFF042F2E)),
        "sunset" to Accent(Color(0xFFC2410C), Color.White, Color(0xFFFFEDD5), Color(0xFF431407), Color(0xFFB45309), Color(0xFFFEF3C7), Color(0xFF451A03)),
        "rose" to Accent(Color(0xFFBE185D), Color.White, Color(0xFFFCE7F3), Color(0xFF500724), Color(0xFF7C3AED), Color(0xFFEDE9FE), Color(0xFF2E1065)),
    )
    private val DARK = mapOf(
        "forest" to Accent(Color(0xFF86EFAC), Color(0xFF052E16), Color(0xFF166534), Color(0xFFDCFCE7), Color(0xFF5EEAD4), Color(0xFF115E59), Color(0xFFCCFBF1)),
        "sunset" to Accent(Color(0xFFFDBA74), Color(0xFF431407), Color(0xFF9A3412), Color(0xFFFFEDD5), Color(0xFFFCD34D), Color(0xFF92400E), Color(0xFFFEF3C7)),
        "rose" to Accent(Color(0xFFF9A8D4), Color(0xFF500724), Color(0xFF9D174D), Color(0xFFFCE7F3), Color(0xFFC4B5FD), Color(0xFF5B21B6), Color(0xFFEDE9FE)),
    )

    fun scheme(palette: String, base: ColorScheme, dark: Boolean): ColorScheme {
        val a = (if (dark) DARK else LIGHT)[palette] ?: return base
        return base.copy(
            primary = a.primary, onPrimary = a.onPrimary, primaryContainer = a.container, onPrimaryContainer = a.onContainer,
            secondary = a.secondary, secondaryContainer = a.secondaryContainer, onSecondaryContainer = a.onSecondaryContainer,
        )
    }

    /** Its two main colours, for the picker's swatch (the brand's if unknown). */
    fun swatch(palette: String): List<Color> = LIGHT[palette]?.let { listOf(it.primary, it.secondary) } ?: listOf(Brand.indigo, Brand.cyan)

    /** Dark on pure black, for OLED screens: the surfaces black and the containers just above it. */
    fun black(scheme: ColorScheme) = scheme.copy(
        background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0B0B0F), surfaceContainer = Color(0xFF121218),
        surfaceContainerHigh = Color(0xFF1A1A22), surfaceContainerHighest = Color(0xFF22222C),
    )

    /** The look's main colour, for what is drawn outside the app (the notifications' accent). */
    fun accent(context: Context): Int = when (val palette = Look.state.value.palette) {
        "wallpaper" -> context.getColor(android.R.color.system_accent1_600)
        else -> swatch(palette).first().toArgb()
    }
}
