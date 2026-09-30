package io.github.nfsandroid.provider

import android.content.Context
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.log.LogCategory
import io.github.nfsandroid.log.LogLevel
import io.github.nfsandroid.log.NfsLog
import io.github.nfsandroid.log.NfsLog.megabytes
import io.github.nfsandroid.log.NfsLog.seconds
import io.github.nfsandroid.service.UploadNotice
import io.github.nfsandroid.ui.common.Format
import java.util.Locale

/**
 * One upload's figures, logged when it ends. Where its time went: "writing", while the app had the
 * file open; "core", handing pieces to the core (which blocks while its queue to the server is full:
 * the network or the server set the pace); "closing", from the app's close until the server has it
 * all. [via] says how: "local" (a local copy), or the proxy and why ("proxy" as set, "proxy-edit" for
 * a file edited in place, "proxy-low-space" when there was no room for a local copy).
 */
class Upload(private val context: Context, private val server: Server, private val name: String, private val via: String) {
    private val start = System.nanoTime()
    private var bytes = 0L
    private var pieces = 0L
    private var core = 0L
    private var closed = 0L

    /** [size] bytes handed to the core in [nanos]; [of], the whole file's size when known (a local copy's). */
    fun piece(size: Int, nanos: Long, of: Long = 0) {
        bytes += size
        pieces++
        core += nanos
        UploadNotice.progress(context, name, bytes, of)
    }

    /** The app closed the file. */
    fun closed() {
        closed = System.nanoTime()
    }

    fun done() {
        if (bytes == 0L) return
        val end = System.nanoTime()
        val closedAt = closed.takeIf { it > 0 } ?: end
        NfsLog.log(
            LogLevel.INFO, LogCategory.UPLOADS, server, "uploaded", "file" to name, "size" to megabytes(bytes),
            "time" to seconds(end - start), "speed" to "%.1fMB/s".format(Locale.ROOT, bytes / 1e6 / ((end - start) / 1e9)), "via" to via,
            "pieces" to pieces, "piece" to "${bytes / pieces / 1024}KiB", "writing" to seconds(closedAt - start),
            "core" to seconds(core), "closing" to seconds(end - closedAt),
        )
        val took = (end - start) / 1e9
        val summary = "${Format.bytes(bytes)} · %.1f s · ${Format.rate(context, (bytes / took).toLong())}".format(took)
        UploadNotice.done(context, name, summary)
    }

    fun failed(e: Throwable) {
        NfsLog.log(
            LogLevel.ERROR, LogCategory.UPLOADS, server, "upload failed", "file" to name, "via" to via,
            "sent" to megabytes(bytes), "error" to NfsLog.reason(e),
        )
        UploadNotice.failed(context, name, NfsLog.reason(e))
    }
}
