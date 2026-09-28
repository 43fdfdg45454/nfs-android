package io.github.nfsandroid

import org.junit.Assert.assertEquals
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * A video player as strict as nfs-core's: 128 KiB reads, playback at 1 MB/s once 2 s are
 * buffered, never more than that read ahead; a later chunk is a stall. Every byte is checked:
 * each 8-byte word holds its offset divided by 8, plus the file's label shifted by 48 bits.
 */
object Player {
    const val CHUNK = 128 shl 10
    private const val RATE = 1_000_000.0
    private const val BUFFER_MS = 2000L

    class Played(val firstByteMs: Long, val stalls: Int, val stalledMs: Long)

    /** The fixture's label: the letter before ".bin". */
    fun label(name: String) = name[name.length - 5].code.toLong()

    fun read(channel: FileChannel, label: Long, offset: Long): Int {
        val buffer = ByteBuffer.allocate(CHUNK).order(ByteOrder.LITTLE_ENDIAN)
        channel.position(offset)
        while (buffer.hasRemaining() && channel.read(buffer) > 0) Unit
        buffer.flip()
        for (i in 0 until buffer.remaining() / 8) {
            assertEquals("at ${offset + 8 * i}", (offset / 8 + i) or (label shl 48), buffer.long)
        }
        return buffer.limit()
    }

    fun play(channel: FileChannel, label: Long, from: Long, seconds: Int): Played {
        val chunks = (seconds * RATE / CHUNK).toInt()
        val buffered = (BUFFER_MS / 1000.0 * RATE / CHUNK).toInt()
        val begin = System.nanoTime()
        val arrivals = ArrayList<Long>()
        var playing = 0L
        for (k in 0 until chunks) {
            val offset = from + k.toLong() * CHUNK
            if (offset >= channel.size()) break
            if (playing != 0L) {
                val dueMs = playing / 1_000_000 + (k * CHUNK / RATE * 1000).toLong() - BUFFER_MS
                Thread.sleep(maxOf(0, dueMs - System.nanoTime() / 1_000_000))
            }
            read(channel, label, offset)
            arrivals += (System.nanoTime() - begin) / 1_000_000
            if (k + 1 == buffered) playing = System.nanoTime()
        }
        return stalls(arrivals, buffered)
    }

    private fun stalls(arrivals: List<Long>, buffered: Int): Played {
        val first = arrivals.firstOrNull() ?: 0
        val start = arrivals.getOrNull(buffered - 1) ?: return Played(first, 0, 0)
        var (stalls, stalled) = 0 to 0L
        for (k in buffered until arrivals.size) {
            val due = start + stalled + (k * CHUNK / RATE * 1000).toLong()
            if (arrivals[k] > due) { stalls++; stalled += arrivals[k] - due }
        }
        return Played(first, stalls, stalled)
    }
}
