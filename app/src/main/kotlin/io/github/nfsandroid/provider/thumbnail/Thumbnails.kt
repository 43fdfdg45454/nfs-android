package io.github.nfsandroid.provider.thumbnail

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Point
import android.os.ParcelFileDescriptor
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.data.ThumbnailSources
import io.github.nfsandroid.log.LogCategory
import io.github.nfsandroid.log.LogLevel
import io.github.nfsandroid.log.NfsLog
import uniffi.nfscore.Mount
import java.io.File
import java.util.concurrent.Semaphore

/**
 * Thumbnails of images, videos and audio, two at a time so that playback does not wait behind
 * them, kept on disk by document, version and strategy. The server's sources are tried in its
 * order; those that go through Android's decoders are held to its cap on what they may read. Each
 * search is logged with the source that gave the picture and what it read.
 */
object Thumbnails {
    private val slots = Semaphore(2)
    private const val SLOW_NANOS = 2_000_000_000L

    fun supported(mime: String) = mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")

    /** The sources a server tries, in order. */
    fun strategy(server: Server) = ThumbnailSources.order(server.thumbnailOrder).filter { it !in server.thumbnailOff }

    suspend fun get(context: Context, server: Server, mount: Mount, path: String, key: String, mime: String, size: Point): AssetFileDescriptor? {
        val name = "${(key + strategy(server)).hashCode().toUInt()}-${size.x}.jpg"
        val file = File(File(context.cacheDir, "thumbnails").apply { mkdirs() }, name)
        if (!file.exists()) {
            slots.acquire()
            try {
                val bitmap = search(server, mount, path, mime, size) ?: return null
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            } finally {
                slots.release()
            }
        }
        return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length())
    }

    private suspend fun search(server: Server, mount: Mount, path: String, mime: String, size: Point): Bitmap? {
        val start = System.nanoTime()
        val budget = Budget(server.thumbnailMaxMb.coerceAtLeast(1).toLong() shl 20)
        var failed: Throwable? = null
        var source: String? = null
        val bitmap = mount.read(path).use { file ->
            val target = Target(server, mount, path, mime, size, budget, file)
            strategy(server).firstNotNullOfOrNull { key ->
                runCatching { Sources.find(key, target) }.onFailure { failed = it }.getOrNull()
                    ?.takeUnless { key in Sources.CAPPED && budget.spent }?.also { source = key }
            }
        }
        val took = System.nanoTime() - start
        val (level, event) = when {
            bitmap == null && budget.spent -> LogLevel.WARN to "capped"
            bitmap == null && failed != null -> LogLevel.WARN to "not made"
            bitmap == null -> LogLevel.DEBUG to "none"
            took > SLOW_NANOS -> LogLevel.WARN to "slow"
            else -> LogLevel.DEBUG to "made"
        }
        NfsLog.log(
            level, LogCategory.THUMBNAILS, server, event, "file" to "/$path", "source" to source, "time" to NfsLog.seconds(took),
            "read" to NfsLog.megabytes(budget.read.get()), "reads" to budget.reads.get(), "error" to failed?.let(NfsLog::reason),
        )
        return bitmap
    }
}
