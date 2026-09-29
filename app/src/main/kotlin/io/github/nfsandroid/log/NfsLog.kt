package io.github.nfsandroid.log

import android.content.Context
import android.util.Log
import io.github.nfsandroid.data.Server
import java.io.File
import java.time.Instant
import java.util.Locale

/**
 * What is worth reading after a problem, in Android/data/<package>/files/nfs-log.txt (the app is
 * used without adb), kept under 1 MiB. One event per line: time, level, category, server ("-" for
 * the app's own: network and service), event, and key=value fields (quoted when they have spaces).
 * A server's lines follow its settings (on or off, and its level); the app's are always kept.
 */
object NfsLog {
    private var file: File? = null

    fun init(context: Context) {
        file = file(context)
    }

    fun file(context: Context) = File(context.getExternalFilesDir(null) ?: context.filesDir, "nfs-log.txt")

    fun log(level: LogLevel, category: LogCategory, server: Server?, event: String, vararg fields: Pair<String, Any?>) {
        if (server != null && (!server.logEnabled || level > LogLevel.of(server.logLevel))) return
        write(level, buildString {
            append(level.name.padEnd(5)).append(' ').append(category.name.lowercase().padEnd(10)).append(' ')
            append(server?.let { quote(it.title) } ?: "-").append(' ').append(event)
            fields.forEach { (key, value) -> if (value != null) append(' ').append(key).append('=').append(quote(value.toString())) }
        })
    }

    /** An error in its own words: its message, else its kind (the release build shortens class names). */
    fun reason(e: Throwable) = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName

    /** Seconds and megabytes as the fields show them. */
    fun seconds(nanos: Long) = "%.1fs".format(Locale.ROOT, nanos / 1e9)
    fun megabytes(bytes: Long) = "%.1fMB".format(Locale.ROOT, bytes / 1e6)

    private fun quote(value: String) =
        if (value.isNotEmpty() && value.none { it == ' ' || it == '"' || it == '=' }) value else "\"${value.replace("\"", "\\\"")}\""

    @Synchronized
    private fun write(level: LogLevel, text: String) {
        val priority = when (level) {
            LogLevel.ERROR -> Log.ERROR
            LogLevel.WARN -> Log.WARN
            LogLevel.INFO -> Log.INFO
            LogLevel.DEBUG -> Log.DEBUG
        }
        Log.println(priority, "nfs", text)
        val file = file ?: return
        runCatching {
            if (file.length() > 1 shl 20) file.writeText(file.readText().takeLast(512 shl 10))
            file.appendText("${Instant.now()} $text\n")
        }
    }
}
