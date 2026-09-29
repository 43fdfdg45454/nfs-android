package io.github.nfsandroid.provider.thumbnail

import android.graphics.Bitmap
import android.graphics.Point
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import io.github.nfsandroid.data.Server
import uniffi.nfscore.Mount
import uniffi.nfscore.ReadFile

/** One thumbnail being looked for: the file, the size asked, and what may still be read. */
class Target(val server: Server, val mount: Mount, val path: String, val mime: String, val size: Point, val budget: Budget, val file: ReadFile) {
    val read: Reader = budget.reader(file)
    val length get() = file.size().toLong()
    val media get() = mime.startsWith("video/") || mime.startsWith("audio/")
}

/** Each source (ThumbnailSources) by its key: a picture, or null when it has none for this file. */
object Sources {
    suspend fun find(key: String, t: Target): Bitmap? = when (key) {
        "sidecar" -> if (t.media) Sidecars.find(t) else null
        "embedded" -> if (t.media) retrieve(t) { it.embeddedPicture }?.let { decode(t.size, it) } else null
        "attachment" -> if (Matroska.named(t.path)) Matroska.cover(t.read, t.length)?.let { decode(t.size, it) } else null
        "exif" -> if (t.mime.startsWith("image/")) t.read.stream().use { ExifInterface(it).thumbnailBitmap } else null
        "decode" -> when {
            t.mime.startsWith("image/") -> decode(t.size) { t.read.stream() }
            t.mime.startsWith("video/") -> retrieve(t) {
                it.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, t.size.x, t.size.y)
            }
            else -> null
        }
        else -> null
    }

    private fun <T> retrieve(t: Target, block: (MediaMetadataRetriever) -> T): T =
        MediaMetadataRetriever().use { it.setDataSource(t.read.source(t.length)); block(it) }
}
