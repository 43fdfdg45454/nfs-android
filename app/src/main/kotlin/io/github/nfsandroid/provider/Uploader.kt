package io.github.nfsandroid.provider

import android.content.Context
import io.github.nfsandroid.core.Mounts
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.service.UploadNotice
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import uniffi.nfscore.WriteFile

/**
 * Uploads a local copy while the app writes it: whole MiB blocks as they appear, the rest once it
 * is closed. A block sent while the app could still change it keeps its digest; at the end, those
 * that differ (a header written last, over what was sent first) are sent again.
 */
class Uploader(
    private val context: Context,
    private val server: Server,
    private val path: String,
    private val local: File,
    private val file: WriteFile,
) {
    private val digests = HashMap<Long, ByteArray>()
    private val upload = Upload(server, path, "local")
    private var sent = 0L
    private var end = 0L

    fun run(closed: CountDownLatch) {
        runCatching {
            RandomAccessFile(local, "r").use { copy ->
                while (!closed.await(100, TimeUnit.MILLISECONDS)) {
                    while (copy.length() - sent >= BLOCK) send(copy, sent, BLOCK, keep = true).also { sent += BLOCK }
                    UploadNotice.progress(context, path, sent, copy.length())
                }
                upload.closed()
                val size = copy.length()
                for ((block, sum) in digests.toList()) {
                    val at = block * BLOCK
                    if (at < size && !digest(read(copy, at, minOf(BLOCK.toLong(), size - at).toInt())).contentEquals(sum)) {
                        send(copy, at, minOf(BLOCK.toLong(), size - at).toInt(), keep = false)
                    }
                }
                while (sent < size) {
                    val n = minOf(BLOCK.toLong(), size - sent).toInt()
                    send(copy, sent, n, keep = false)
                    sent += n
                    UploadNotice.progress(context, path, sent, size)
                }
                runBlocking { file.finish() }
                if (end > size) runBlocking { Mounts.get(server).resize(path, size.toULong()) }
            }
            upload.done()
            UploadNotice.done(context, path)
        }.onFailure {
            upload.failed(it)
            UploadNotice.failed(context, path, it.message.orEmpty())
        }
        file.close()
    }

    private fun send(copy: RandomAccessFile, at: Long, size: Int, keep: Boolean) {
        val bytes = read(copy, at, size)
        if (keep) digests[at / BLOCK] = digest(bytes)
        val begin = System.nanoTime()
        runBlocking { Mounts.get(server) } // in use: the mount is not closed as idle meanwhile
        file.writeBlocking(at.toULong(), bytes)
        upload.piece(size, System.nanoTime() - begin)
        end = maxOf(end, at + size)
    }

    private fun read(copy: RandomAccessFile, at: Long, size: Int) = ByteArray(size).also { copy.seek(at); copy.readFully(it) }

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        private const val BLOCK = 1 shl 20
    }
}
