package io.github.nfsandroid.data

import android.content.Context
import java.io.File

/** Settings for the whole app, not per server. */
object Settings {
    private fun preferences(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The disk cache's size in GiB; 0 turns it off. It never leaves less than 1 GiB free. */
    fun cacheGb(context: Context) = preferences(context).getInt("cacheGb", 4)

    fun setCacheGb(context: Context, gb: Int) = preferences(context).edit().putInt("cacheGb", gb.coerceIn(0, 1024)).apply()

    fun cacheDir(context: Context): String = File(context.cacheDir, "nfs").path
}
