package io.github.nfsandroid.provider

import android.os.ParcelFileDescriptor
import android.system.Os
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.log.NfsLog
import kotlinx.coroutines.runBlocking
import uniffi.nfscore.WriteFile
import kotlin.concurrent.thread

/**
 * A file written from its start through a pipe instead of the file proxy: the kernel gathers the
 * app's writes (8 KiB each from Java's streams) and they are read here 1 MiB at a time, with no
 * round trip through the proxy for each one (about 1.2 ms on a phone: 6-7 MB/s at most). A pipe
 * has no seek and no fsync: only for apps known to write files whole, unless the server says so.
 */
object Pipes {
    private const val F_SETPIPE_SZ = 1031
    private const val CHUNK = 1 shl 20
    private val MANAGERS = setOf(
        "com.google.android.documentsui", "com.android.documentsui", "com.google.android.apps.nbu.files",
        "me.zhanghai.android.files", "com.amaze.filemanager", "com.mixplorer", "com.mixplorer.silver",
        "pl.solidexplorer2", "com.ghisler.android.TotalCommander", "com.lonelycatgames.Xplore",
        "com.alphainventor.filemanager", "com.cxinventor.file.explorer",
    )

    /** Whether [caller] writes through a pipe on [server] ("managers": file managers only). */
    fun suit(server: Server, caller: String?) = when (server.pipeWrites) {
        "always" -> true
        "never" -> false
        else -> caller in MANAGERS
    }

    fun write(document: String, name: String, file: WriteFile): ParcelFileDescriptor {
        val (read, write) = ParcelFileDescriptor.createReliablePipe()
        runCatching { Os.fcntlInt(read.fileDescriptor, F_SETPIPE_SZ, CHUNK) }
        val done = Proxies.writer(document)
        thread(name = "nfs-pipe") {
            val upload = Upload(name, "reads from a pipe")
            runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(read).use { input ->
                    val buffer = ByteArray(CHUNK)
                    var offset = 0L
                    do {
                        var n = 0
                        while (n < CHUNK) n += input.read(buffer, n, CHUNK - n).takeIf { it >= 0 } ?: break
                        val begin = System.nanoTime()
                        if (n > 0) file.writeBlocking(offset.toULong(), buffer.copyOf(n))
                        upload.piece(n, System.nanoTime() - begin)
                        offset += n
                    } while (n == CHUNK)
                }
                runBlocking { file.finish() }
            }.onFailure { NfsLog.line("writing $name: ${it.message}") }
            upload.done()
            file.close()
            done()
        }
        return write
    }
}
