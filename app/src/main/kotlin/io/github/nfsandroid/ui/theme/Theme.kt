package io.github.nfsandroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.nfsandroid.data.Look
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The brand: indigo to cyan, as in the icon. */
object Brand {
    val indigo = Color(0xFF4F46E5)
    val cyan = Color(0xFF06B6D4)
    val gradient = Brush.linearGradient(listOf(indigo, cyan))
    val success = Color(0xFF16A34A)
}

private val Light = lightColorScheme(
    primary = Brand.indigo, onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF), onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0891B2), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE), onSecondaryContainer = Color(0xFF083344),
    tertiary = Color(0xFF7C3AED), tertiaryContainer = Color(0xFFEDE9FE), onTertiaryContainer = Color(0xFF2E1065),
    background = Color(0xFFF6F7FB), onBackground = Color(0xFF111827),
    surface = Color(0xFFF6F7FB), onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE6E8F0), onSurfaceVariant = Color(0xFF4B5563),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color(0xFFF0F2F8),
    surfaceContainerHigh = Color(0xFFE9ECF4), surfaceContainerHighest = Color(0xFFE2E6F0),
    outline = Color(0xFFB6BCCB), outlineVariant = Color(0xFFDDE1EA),
    error = Color(0xFFDC2626), errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF7F1D1D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA5B4FC), onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3), onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF67E8F9), onSecondary = Color(0xFF083344),
    secondaryContainer = Color(0xFF155E75), onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFC4B5FD), tertiaryContainer = Color(0xFF4C1D95), onTertiaryContainer = Color(0xFFEDE9FE),
    background = Color(0xFF0B1020), onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF0B1020), onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1E2640), onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainerLowest = Color(0xFF070B17), surfaceContainerLow = Color(0xFF121A2E), surfaceContainer = Color(0xFF161F36),
    surfaceContainerHigh = Color(0xFF1C2640), surfaceContainerHighest = Color(0xFF232E4A),
    outline = Color(0xFF475069), outlineVariant = Color(0xFF2A3350),
    error = Color(0xFFF87171), errorContainer = Color(0xFF7F1D1D), onErrorContainer = Color(0xFFFEE2E2),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
)

private val typography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Dark as the look says: always, never, or as the system is. */
@Composable
fun Look.dark() = when (mode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

/** The app's look (Settings › Appearance): its colours, dark or not, and the text's size. */
@Composable
fun NfsTheme(content: @Composable () -> Unit) {
    val look by Look.state.collectAsState()
    val dark = look.dark()
    val context = LocalContext.current
    val scheme = when (look.palette) {
        "wallpaper" -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> Palettes.scheme(look.palette, if (dark) Dark else Light, dark)
    }.let { if (dark && look.pureBlack) Palettes.black(it) else it }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * look.textScale)) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography, content = content)
    }
}
