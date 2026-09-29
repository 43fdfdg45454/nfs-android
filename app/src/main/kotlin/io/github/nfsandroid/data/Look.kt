package io.github.nfsandroid.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * How the app looks, for the whole app: light, dark or as the system is; the brand's colours, the
 * wallpaper's (Material You) or another palette; pure black when dark (OLED); the text's size.
 */
data class Look(val mode: String = "system", val palette: String = "brand", val pureBlack: Boolean = false, val textScale: Float = 1f) {
    companion object {
        val MODES = listOf("system", "light", "dark")
        val PALETTES = listOf("brand", "wallpaper", "forest", "sunset", "rose")
        val TEXT_SCALES = listOf(0.9f, 1f, 1.15f)

        private val flow = MutableStateFlow(Look())
        val state: StateFlow<Look> = flow

        private fun preferences(context: Context) = context.getSharedPreferences("look", Context.MODE_PRIVATE)

        fun init(context: Context) {
            val p = preferences(context)
            flow.value = Look(
                p.getString("mode", "system")!!, p.getString("palette", "brand")!!, p.getBoolean("pureBlack", false), p.getFloat("textScale", 1f),
            )
        }

        fun set(context: Context, look: Look) {
            flow.value = look
            preferences(context).edit().putString("mode", look.mode).putString("palette", look.palette)
                .putBoolean("pureBlack", look.pureBlack).putFloat("textScale", look.textScale).apply()
        }
    }
}
