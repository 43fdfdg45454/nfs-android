package io.github.nfsandroid

import java.nio.ByteBuffer

/**
 * A small Matroska file for the thumbnail tests: its seek head points past 1 MiB of "video" (a
 * cluster) to the attachments, a font and then the cover.
 */
object Mkv {
    private fun id(id: Long) = ByteBuffer.allocate(8).putLong(id).array().dropWhile { it == 0.toByte() }.toByteArray()

    /** An element with an 8-byte size (0x01 and seven bytes). */
    private fun element(id: Long, data: ByteArray) = id(id) + byteArrayOf(1) + ByteBuffer.allocate(8).putLong(data.size.toLong()).array().copyOfRange(1, 8) + data

    fun withCover(cover: ByteArray): ByteArray {
        fun file(name: String, mime: String, data: ByteArray) =
            element(0x61A7, element(0x466E, name.toByteArray()) + element(0x4660, mime.toByteArray()) + element(0x465C, data))
        val attachments = element(0x1941A469, file("font.ttf", "application/x-truetype-font", ByteArray(10_000)) + file("cover.png", "image/png", cover))
        val cluster = element(0x1F43B675, ByteArray(1 shl 20))
        fun head(position: Long) = element(
            0x114D9B74,
            element(0x4DBB, element(0x53AB, id(0x1941A469)) + element(0x53AC, ByteBuffer.allocate(8).putLong(position).array())),
        )
        val position = head(0).size.toLong() + cluster.size
        return element(0x1A45DFA3, element(0x4282, "matroska".toByteArray())) + element(0x18538067, head(position) + cluster + attachments)
    }
}
