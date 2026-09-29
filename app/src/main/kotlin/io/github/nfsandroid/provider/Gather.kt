package io.github.nfsandroid.provider

import uniffi.nfscore.WriteFile

/**
 * Consecutive writes gathered into 1 MiB before they reach the core: apps write 8 KiB at a time
 * (Java's streams), and each call into the core costs more than 8 KiB take to send. What is
 * gathered goes out before a read, an fsync, a write elsewhere and at the end; a failure shows on
 * that call, as a kernel's page cache would show it.
 */
class Gather(private val file: WriteFile) {
    private val buffer = ByteArray(SIZE)
    private var start = 0L
    private var length = 0

    fun write(offset: Long, count: Int, data: ByteArray) {
        if (length > 0 && (offset != start + length || length + count > SIZE)) flush()
        if (count >= SIZE) return core(offset, data.copyOf(count))
        if (length == 0) start = offset
        data.copyInto(buffer, length, 0, count)
        length += count
        if (length == SIZE) flush()
    }

    fun flush() {
        if (length == 0) return
        val out = buffer.copyOf(length)
        length = 0
        core(start, out)
    }

    private fun core(offset: Long, data: ByteArray) {
        val begin = System.nanoTime()
        file.writeBlocking(offset.toULong(), data)
        ProxyStats.Writes.core.incrementAndGet()
        ProxyStats.Writes.coreNanos.addAndGet(System.nanoTime() - begin)
    }

    companion object {
        const val SIZE = 1 shl 20
    }
}
