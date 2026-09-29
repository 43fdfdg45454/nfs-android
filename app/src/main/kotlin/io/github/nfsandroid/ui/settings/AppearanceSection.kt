package io.github.nfsandroid.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nfsandroid.R
import io.github.nfsandroid.data.Look
import io.github.nfsandroid.ui.common.Help
import io.github.nfsandroid.ui.common.Section
import io.github.nfsandroid.ui.common.Segmented
import io.github.nfsandroid.ui.common.SwitchRow
import io.github.nfsandroid.ui.theme.Palettes

private val MODES = mapOf("system" to R.string.look_system, "light" to R.string.look_light, "dark" to R.string.look_dark)
private val PALETTES = mapOf(
    "brand" to R.string.palette_brand, "wallpaper" to R.string.palette_wallpaper, "forest" to R.string.palette_forest,
    "sunset" to R.string.palette_sunset, "rose" to R.string.palette_rose,
)
private val TEXT = mapOf(0.9f to R.string.text_small, 1f to R.string.text_normal, 1.15f to R.string.text_large)

/** How the app looks: light or dark, its colours, pure black, the text's size. Applied at once. */
@Composable
fun AppearanceSection() {
    val context = LocalContext.current
    val look by Look.state.collectAsState()
    fun set(changed: Look) = Look.set(context, changed)
    Section(stringResource(R.string.section_look), painterResource(R.drawable.ic_palette), stringResource(R.string.section_look_help)) {
        Segmented(Look.MODES.map { it to stringResource(MODES.getValue(it)) }, look.mode) { set(look.copy(mode = it)) }
        Text(stringResource(R.string.look_palette), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Look.PALETTES.forEach { palette ->
                val colors = if (palette == "wallpaper") dynamicLightColorScheme(context).let { listOf(it.primary, it.tertiary) } else Palettes.swatch(palette)
                Swatch(stringResource(PALETTES.getValue(palette)), colors, look.palette == palette) { set(look.copy(palette = palette)) }
            }
        }
        SwitchRow(stringResource(R.string.look_black), stringResource(R.string.look_black_help), look.pureBlack) { set(look.copy(pureBlack = it)) }
        Text(stringResource(R.string.look_text), style = MaterialTheme.typography.bodyLarge)
        Segmented(Look.TEXT_SCALES.map { it to stringResource(TEXT.getValue(it)) }, look.textScale) { set(look.copy(textScale = it)) }
        Help(stringResource(R.string.look_text_help))
    }
}

@Composable
private fun Swatch(label: String, colors: List<Color>, selected: Boolean, onClick: () -> Unit) =
    Column(Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(colors))
                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Filled.Check, null, tint = Color.White) }
        Text(label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
