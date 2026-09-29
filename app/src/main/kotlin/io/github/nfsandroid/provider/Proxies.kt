package io.github.nfsandroid.provider

import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.log.LogCategory
import io.github.nfsandroid.log.LogLevel
import io.github.nfsandroid.log.NfsLog
import kotlinx.coroutines.runBlocking
import uniffi.nfscore.ReadFile
import uniffi.nfscore.WriteFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * File descriptors other apps read and write through (the system's file proxy calls back here).
 * Each descriptor has a thread of its own: two players never wait on each other's reads, and
 * reads and writes block that thread in one native call each (an async call costs 15-20 ms).
 */
object Proxies {
    private fun thread(name: String) = HandlerThread("nfs-$name").apply { start() }

    /** Writers still open, by document: Android releases a descriptor after the app closes it. */
    private val writing = ConcurrentHashMap<Any, Pair<String, CountDownLatch>>()

    /**
     * Waits, up to 3 s, for the open writers of [document] to finish: a file written, closed and
     * opened again right away is read whole (close-to-open, as other NFS clients do).
     */
    fun awaitWriters(document: String) {
        writing.values.filter { it.first == document }.forEach { it.second.await(3, TimeUnit.SECONDS) }
    }

    /** Marks [document] as being written, until the function returned is called. */
    fun writer(document: String): () -> Unit {
        val (token, done) = Any() to CountDownLatch(1)
        writing[token] = document to done
        return {
            writing.remove(token)
            done.countDown()
        }
    }

    private fun io(server: Server, op: String, name: String, block: () -> Int): Int = try {
        block()
    } catch (e: Exception) {
        NfsLog.log(LogLevel.ERROR, LogCategory.FILES, server, "$op failed", "file" to name, "error" to NfsLog.reason(e))
        throw ErrnoException(op, OsConstants.EIO)
    }

    fun read(storage: StorageManager, server: Server, name: String, file: ReadFile): ParcelFileDescriptor {
        val thread = thread(name)
        val callback = object : ProxyFileDescriptorCallback() {
            override fun onGetSize() = file.size().toLong()

            override fun onRead(offset: Long, size: Int, data: ByteArray) = io(server, "read", name) {
                val start = System.nanoTime()
                val bytes = file.readBlocking(offset.toULong(), size.toUInt())
                val core = System.nanoTime()
                bytes.copyInto(data)
                ProxyStats.Reads.calls.incrementAndGet()
                ProxyStats.Reads.bytes.addAndGet(bytes.size.toLong())
                ProxyStats.Reads.coreNanos.addAndGet(core - start)
                ProxyStats.Reads.totalNanos.addAndGet(System.nanoTime() - start)
                bytes.size
            }

            override fun onRelease() {
                file.close()
                thread.quitSafely()
            }
        }
        return storage.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, callback, Handler(thread.looper))
    }

    /**
     * A file to write, starting from [size] bytes; with [readable], to read too (what was written
     * included). fsync puts what was written on the server's stable storage.
     */
    fun write(
        storage: StorageManager, server: Server, document: String, name: String, file: WriteFile, via: String,
        size: Long = 0, readable: Boolean = false,
    ): ParcelFileDescriptor {
        val (thread, done) = thread(name) to writer(document)
        val callback = object : ProxyFileDescriptorCallback() {
            private var end = size
            private val gather = Gather(file)
            private val upload = Upload(server, name, via)

            override fun onGetSize() = end

            override fun onRead(offset: Long, count: Int, data: ByteArray) = io(server, "read", name) {
                gather.flush()
                val bytes = file.readBlocking(offset.toULong(), count.toUInt())
                bytes.copyInto(data)
                bytes.size
            }

            override fun onWrite(offset: Long, count: Int, data: ByteArray) = io(server, "write", name) {
                val begin = System.nanoTime()
                gather.write(offset, count, data)
                end = maxOf(end, offset + count)
                ProxyStats.Writes.calls.incrementAndGet()
                ProxyStats.Writes.nanos.addAndGet(System.nanoTime() - begin)
                upload.piece(count, System.nanoTime() - begin)
                count
            }

            override fun onFsync() {
                io(server, "sync", name) { gather.flush(); runBlocking { file.sync() }; 0 }
            }

            override fun onRelease() {
                upload.closed()
                runCatching { gather.flush(); runBlocking { file.finish() } }.onSuccess { upload.done() }.onFailure { upload.failed(it) }
                file.close()
                done()
                thread.quitSafely()
            }
        }
        val mode = if (readable) ParcelFileDescriptor.MODE_READ_WRITE else ParcelFileDescriptor.MODE_WRITE_ONLY
        return storage.openProxyFileDescriptor(mode, callback, Handler(thread.looper))
    }
}
