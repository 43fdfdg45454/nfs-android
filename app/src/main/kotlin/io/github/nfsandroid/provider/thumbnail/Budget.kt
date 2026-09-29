package io.github.nfsandroid.provider.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.MediaDataSource
import uniffi.nfscore.ReadFile
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

/** Bytes of a file from an offset: what every source reads through. */
typealias Reader = (Long, Int) -> ByteArray

class Spent : IOException("the thumbnail's byte cap was reached")

/**
 * What looking for one thumbnail reads (for the log), and the cap on what Android's media and image
 * decoders may read for it: they can go through most of a file (a video without an index), where
 * the other sources read only what they need.
 */
class Budget(private val max: Long) {
    val read = AtomicLong()
    val reads = AtomicLong()
    private val decoded = AtomicLong()
    val spent get() = decoded.get() >= max

    /** A reader of [file]; [capped], one that stops at the cap. */
    fun reader(file: ReadFile, capped: Boolean = false): Reader = { at, len ->
        val left = if (capped) max - decoded.get() else Long.MAX_VALUE
        if (left <= 0) throw Spent()
        file.readBlocking(at.toULong(), minOf(len.toLong(), left).toInt().toUInt()).also {
            read.addAndGet(it.size.toLong())
            reads.incrementAndGet()
            if (capped) decoded.addAndGet(it.size.toLong())
        }
    }
}

/** The file as a stream from its start (BitmapFactory, ExifInterface). */
fun Reader.stream(): InputStream = object : InputStream() {
    private var at = 0L
    override fun read() = ByteArray(1).let { if (read(it, 0, 1) < 1) -1 else it[0].toInt() and 0xff }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val bytes = this@stream(at, len)
        if (bytes.isEmpty()) return -1
        bytes.copyInto(b, off)
        at += bytes.size
        return bytes.size
    }
}

/** The file as a media source (MediaMetadataRetriever): a spent budget ends it. */
fun Reader.source(length: Long) = object : MediaDataSource() {
    override fun getSize() = length
    override fun close() {}
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        val bytes = try { this@source(position, size) } catch (e: Spent) { return -1 }
        if (bytes.isEmpty()) return -1
        bytes.copyInto(buffer, offset)
        return bytes.size
    }
}

/** An image scaled down to about [size]: two passes over it, its bounds and then its pixels. */
fun decode(size: Point, open: () -> InputStream): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open().use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= size.x && bounds.outHeight / (sample * 2) >= size.y) sample *= 2
    return open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}

fun decode(size: Point, bytes: ByteArray) = decode(size) { bytes.inputStream() }
