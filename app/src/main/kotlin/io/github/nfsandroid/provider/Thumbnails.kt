package io.github.nfsandroid.provider

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.log.LogCategory
import io.github.nfsandroid.log.LogLevel
import io.github.nfsandroid.log.NfsLog
import uniffi.nfscore.ReadFile
import java.io.File
import java.io.InputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong

/**
 * Thumbnails of images, videos (a frame) and audio (its embedded picture), two at a time so that
 * playback does not wait behind them, kept on disk by document and version. Each one made is
 * logged with what it read: a warning when it took over 2 s or could not be made.
 */
object Thumbnails {
    private val slots = Semaphore(2)
    private const val SLOW_NANOS = 2_000_000_000L

    fun supported(mime: String) = mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")

    fun get(context: Context, server: Server, name: String, key: String, mime: String, size: Point, open: () -> ReadFile): AssetFileDescriptor? {
        val file = File(File(context.cacheDir, "thumbnails").apply { mkdirs() }, "${key.hashCode().toUInt()}-${size.x}.jpg")
        if (!file.exists()) {
            slots.acquire()
            try {
                val (start, bytes, reads) = Triple(System.nanoTime(), AtomicLong(), AtomicLong())
                val made = runCatching {
                    open().use { f ->
                        val fetch = { at: Long, n: Int ->
                            f.readBlocking(at.toULong(), n.toUInt()).also { bytes.addAndGet(it.size.toLong()); reads.incrementAndGet() }
                        }
                        make(fetch, f.size().toLong(), mime, size)
                    } ?: error("nothing to show")
                }
                val took = System.nanoTime() - start
                val (level, event) = when {
                    made.isFailure -> LogLevel.WARN to "not made"
                    took > SLOW_NANOS -> LogLevel.WARN to "slow"
                    else -> LogLevel.DEBUG to "made"
                }
                NfsLog.log(level, LogCategory.THUMBNAILS, server, event, "file" to name, "time" to NfsLog.seconds(took),
                    "read" to NfsLog.megabytes(bytes.get()), "reads" to reads.get(), "error" to made.exceptionOrNull()?.let(NfsLog::reason))
                val bitmap = made.getOrNull() ?: return null
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            } finally {
                slots.release()
            }
        }
        return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length())
    }

    private fun make(fetch: (Long, Int) -> ByteArray, length: Long, mime: String, size: Point): Bitmap? = when {
        mime.startsWith("image/") -> {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream(fetch), null, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= size.x && bounds.outHeight / (sample * 2) >= size.y) sample *= 2
            BitmapFactory.decodeStream(stream(fetch), null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        else -> MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(source(fetch, length))
            retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?: retriever.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size.x, size.y)
        }
    }

    private fun stream(fetch: (Long, Int) -> ByteArray) = object : InputStream() {
        private var at = 0L
        override fun read() = ByteArray(1).let { if (read(it, 0, 1) < 1) -1 else it[0].toInt() and 0xff }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytes = fetch(at, len)
            if (bytes.isEmpty()) return -1
            bytes.copyInto(b, off)
            at += bytes.size
            return bytes.size
        }
    }

    private fun source(fetch: (Long, Int) -> ByteArray, length: Long) = object : MediaDataSource() {
        override fun getSize() = length
        override fun close() {}
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            val bytes = fetch(position, size)
            if (bytes.isEmpty()) return -1
            bytes.copyInto(buffer, offset)
            return bytes.size
        }
    }
}
