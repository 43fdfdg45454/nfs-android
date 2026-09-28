package io.github.nfsandroid.provider

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import uniffi.nfscore.ReadFile
import java.io.File
import java.io.InputStream
import java.util.concurrent.Semaphore

/**
 * Thumbnails of images, videos (a frame) and audio (its embedded picture), two at a time so that
 * playback does not wait behind them, kept on disk by document and version.
 */
object Thumbnails {
    private val slots = Semaphore(2)

    fun supported(mime: String) = mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")

    fun get(context: Context, key: String, mime: String, size: Point, open: () -> ReadFile): AssetFileDescriptor? {
        val file = File(File(context.cacheDir, "thumbnails").apply { mkdirs() }, "${key.hashCode().toUInt()}-${size.x}.jpg")
        if (!file.exists()) {
            slots.acquire()
            try {
                val bitmap = open().use { make(it, mime, size) } ?: return null
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            } finally {
                slots.release()
            }
        }
        return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length())
    }

    private fun make(file: ReadFile, mime: String, size: Point): Bitmap? = when {
        mime.startsWith("image/") -> {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream(file), null, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= size.x && bounds.outHeight / (sample * 2) >= size.y) sample *= 2
            BitmapFactory.decodeStream(stream(file), null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        else -> MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(source(file))
            retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?: retriever.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size.x, size.y)
        }
    }

    private fun stream(file: ReadFile) = object : InputStream() {
        private var at = 0L
        override fun read() = ByteArray(1).let { if (read(it, 0, 1) < 1) -1 else it[0].toInt() and 0xff }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytes = file.readBlocking(at.toULong(), len.toUInt())
            if (bytes.isEmpty()) return -1
            bytes.copyInto(b, off)
            at += bytes.size
            return bytes.size
        }
    }

    private fun source(file: ReadFile) = object : MediaDataSource() {
        override fun getSize() = file.size().toLong()
        override fun close() {}
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            val bytes = file.readBlocking(position.toULong(), size.toUInt())
            if (bytes.isEmpty()) return -1
            bytes.copyInto(buffer, offset)
            return bytes.size
        }
    }
}
