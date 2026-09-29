package io.github.nfsandroid.provider.thumbnail

/**
 * The cover a Matroska file carries as an attachment (cover.jpg, cover_land.png, as its
 * specification names them), found without reading its video: the segment's first elements, the
 * seek head's pointer to the attachments, and each attachment's name and type before its data.
 */
object Matroska {
    private const val EBML = 0x1A45DFA3L
    private const val SEGMENT = 0x18538067L
    private const val SEEK_HEAD = 0x114D9B74L
    private const val SEEK_ID = 0x53ABL
    private const val SEEK_POSITION = 0x53ACL
    private const val ATTACHMENTS = 0x1941A469L
    private const val ATTACHED_FILE = 0x61A7L
    private const val FILE_NAME = 0x466EL
    private const val FILE_MIME = 0x4660L
    private const val FILE_DATA = 0x465CL
    private const val CLUSTER = 0x1F43B675L

    fun named(path: String) = path.substringAfterLast('.').lowercase() in setOf("mkv", "mka", "mks", "mk3d")

    /** An element: its id, where its data starts, and its size (up to [end] when unknown). */
    private class Element(val id: Long, val data: Long, val size: Long) { val end get() = data + size }

    private fun element(read: Reader, at: Long, end: Long): Element? {
        val b = read(at, 12).takeIf { it.size >= 2 } ?: return null
        val idLength = Integer.numberOfLeadingZeros(b[0].toInt() and 0xff) - 23
        if (idLength !in 1..4) return null
        val id = (0 until idLength).fold(0L) { v, i -> v shl 8 or (b[i].toLong() and 0xff) }
        val first = b[idLength].toInt() and 0xff
        val sizeLength = Integer.numberOfLeadingZeros(first) - 23
        if (sizeLength !in 1..8 || idLength + sizeLength > b.size) return null
        var size = (first and (0xff shr sizeLength)).toLong()
        for (i in 1 until sizeLength) size = size shl 8 or (b[idLength + i].toLong() and 0xff)
        val data = at + idLength + sizeLength
        val unknown = size == (1L shl (7 * sizeLength)) - 1
        return Element(id, data, if (unknown) end - data else size)
    }

    private fun children(read: Reader, parent: Element, limit: Int = 256) = sequence {
        var at = parent.data
        repeat(limit) {
            if (at >= parent.end) return@sequence
            val child = element(read, at, parent.end) ?: return@sequence
            yield(child)
            at = child.end
        }
    }

    private fun number(read: Reader, e: Element) = read(e.data, e.size.toInt()).fold(0L) { v, b -> v shl 8 or (b.toLong() and 0xff) }

    private fun text(read: Reader, e: Element) = String(read(e.data, e.size.coerceAtMost(256).toInt())).trimEnd('\u0000')

    fun cover(read: Reader, length: Long): ByteArray? {
        val header = element(read, 0, length)?.takeIf { it.id == EBML } ?: return null
        val segment = element(read, header.end, length)?.takeIf { it.id == SEGMENT } ?: return null
        for (child in children(read, segment, limit = 32)) {
            when (child.id) {
                ATTACHMENTS -> return attachment(read, child)
                SEEK_HEAD -> seek(read, child, segment)?.let { return attachment(read, it) }
                CLUSTER -> return null
            }
        }
        return null
    }

    /** The attachments, where the seek head says they are. */
    private fun seek(read: Reader, head: Element, segment: Element): Element? = children(read, head).firstNotNullOfOrNull { seek ->
        val parts = children(read, seek).associateBy { it.id }
        val id = parts[SEEK_ID]?.let { number(read, it) }
        val position = parts[SEEK_POSITION]?.let { number(read, it) }
        if (id == ATTACHMENTS && position != null) element(read, segment.data + position, segment.end) else null
    }?.takeIf { it.id == ATTACHMENTS }

    /** The image attachment named cover (any other image if none is), as bytes. */
    private fun attachment(read: Reader, attachments: Element): ByteArray? {
        val images = children(read, attachments).filter { it.id == ATTACHED_FILE }.mapNotNull { file ->
            val parts = children(read, file).associateBy { it.id }
            val mime = parts[FILE_MIME]?.let { text(read, it) }.orEmpty()
            val name = parts[FILE_NAME]?.let { text(read, it) }.orEmpty().lowercase()
            parts[FILE_DATA]?.takeIf { mime.startsWith("image/") }?.let { name to it }
        }.toList()
        val data = (images.firstOrNull { it.first.startsWith("cover") } ?: images.firstOrNull())?.second ?: return null
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() < data.size) {
            val part = read(data.data + bytes.size(), (data.size - bytes.size()).toInt())
            if (part.isEmpty()) return null
            bytes.write(part)
        }
        return bytes.toByteArray()
    }
}
