package io.github.nfsandroid.log

import android.content.Context
import java.io.File
import java.time.Instant

/** An unexpected crash leaves its trace in Android/data/<package>/files/crash-log.txt. */
object CrashLog {
    fun install(context: Context) {
        val file = File(context.getExternalFilesDir(null) ?: context.filesDir, "crash-log.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.appendText("${Instant.now()} in ${thread.name}:\n${error.stackTraceToString()}\n") }
            previous?.uncaughtException(thread, error)
        }
    }
}
