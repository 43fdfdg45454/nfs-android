package io.github.nfsandroid.provider

import io.github.nfsandroid.log.NfsLog

/** One upload's figures, logged when it ends: how fast, in what pieces, how long inside the app. */
class Upload(private val name: String, private val pieces: String) {
    private val start = System.nanoTime()
    private var bytes = 0L
    private var count = 0L
    private var inside = 0L

    fun piece(size: Int, nanos: Long) {
        bytes += size
        count++
        inside += nanos
    }

    fun done() {
        if (bytes == 0L) return
        val seconds = (System.nanoTime() - start) / 1e9
        NfsLog.line(
            "upload %s: %.1f MB in %.1f s (%.1f MB/s); %d %s of %d KiB; %.1f s inside the app".format(
                name, bytes / 1e6, seconds, bytes / 1e6 / seconds, count, pieces, bytes / count / 1024, inside / 1e9,
            ),
        )
    }
}
