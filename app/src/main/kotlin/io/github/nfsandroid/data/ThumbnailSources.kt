package io.github.nfsandroid.data

/**
 * Where a server's thumbnails are looked for, in the order its strategy lists them: an image next
 * to the file (as media servers keep them), the picture a file carries (a song's or an MP4's
 * cover), a Matroska attachment (cover.jpg), a photo's EXIF thumbnail, or decoding (a video's
 * frame, the whole image).
 */
object ThumbnailSources {
    val ALL = listOf("sidecar", "embedded", "attachment", "exif", "decode")
    val OFF_BY_DEFAULT = setOf("exif")

    /** A stored order with the sources it lacks (added later) at its end, and none it does not know. */
    fun order(stored: List<String>) = stored.filter { it in ALL }.distinct().let { it + ALL.filter { s -> s !in it } }
}
