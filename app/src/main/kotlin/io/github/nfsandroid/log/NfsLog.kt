package io.github.nfsandroid.log

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant

/**
 * What is worth reading after a problem (connections, network changes, failures), in
 * Android/data/<package>/files/nfs-log.txt: the app is used without adb. Kept under 1 MiB.
 */
object NfsLog {
    private var file: File? = null

    fun init(context: Context) {
        file = File(context.getExternalFilesDir(null) ?: context.filesDir, "nfs-log.txt")
    }

    @Synchronized
    fun line(text: String) {
        Log.i("nfs", text)
        val file = file ?: return
        runCatching {
            if (file.length() > 1 shl 20) file.writeText(file.readText().takeLast(512 shl 10))
            file.appendText("${Instant.now()} $text\n")
        }
    }

    fun file(context: Context) = File(context.getExternalFilesDir(null) ?: context.filesDir, "nfs-log.txt")
}
