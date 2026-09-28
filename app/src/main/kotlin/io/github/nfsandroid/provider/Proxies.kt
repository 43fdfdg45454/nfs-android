package io.github.nfsandroid.provider

import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
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

    private fun io(what: String, block: () -> Int): Int = try {
        block()
    } catch (e: Exception) {
        NfsLog.line("$what: ${e.message}")
        throw ErrnoException(what, OsConstants.EIO)
    }

    /** Reads served, for diagnosis: calls, bytes, and nanoseconds in the core and in all. */
    object ReadStats {
        val calls = java.util.concurrent.atomic.AtomicLong()
        val bytes = java.util.concurrent.atomic.AtomicLong()
        val coreNanos = java.util.concurrent.atomic.AtomicLong()
        val totalNanos = java.util.concurrent.atomic.AtomicLong()
        fun reset() = listOf(calls, bytes, coreNanos, totalNanos).forEach { it.set(0) }
        override fun toString(): String {
            val n = maxOf(calls.get(), 1)
            return "${calls.get()} reads of ${bytes.get() / n / 1024} KiB, ${coreNanos.get() / n / 1000} µs in the core, " +
                "${totalNanos.get() / n / 1000} µs in all"
        }
    }

    fun read(storage: StorageManager, name: String, file: ReadFile): ParcelFileDescriptor {
        val thread = thread(name)
        val callback = object : ProxyFileDescriptorCallback() {
            override fun onGetSize() = file.size().toLong()

            override fun onRead(offset: Long, size: Int, data: ByteArray) = io("read $name") {
                val start = System.nanoTime()
                val bytes = file.readBlocking(offset.toULong(), size.toUInt())
                val core = System.nanoTime()
                bytes.copyInto(data)
                ReadStats.calls.incrementAndGet()
                ReadStats.bytes.addAndGet(bytes.size.toLong())
                ReadStats.coreNanos.addAndGet(core - start)
                ReadStats.totalNanos.addAndGet(System.nanoTime() - start)
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
    fun write(storage: StorageManager, document: String, name: String, file: WriteFile, size: Long = 0, readable: Boolean = false): ParcelFileDescriptor {
        val (thread, token, done) = Triple(thread(name), Any(), CountDownLatch(1))
        writing[token] = document to done
        val callback = object : ProxyFileDescriptorCallback() {
            private var end = size

            override fun onGetSize() = end

            override fun onRead(offset: Long, count: Int, data: ByteArray) = io("read $name") {
                val bytes = file.readBlocking(offset.toULong(), count.toUInt())
                bytes.copyInto(data)
                bytes.size
            }

            override fun onWrite(offset: Long, count: Int, data: ByteArray) = io("write $name") {
                file.writeBlocking(offset.toULong(), data.copyOf(count))
                end = maxOf(end, offset + count)
                count
            }

            override fun onFsync() {
                io("sync $name") { runBlocking { file.sync() }; 0 }
            }

            override fun onRelease() {
                runCatching { runBlocking { file.finish() } }.onFailure { NfsLog.line("closing $name: ${it.message}") }
                file.close()
                writing.remove(token)
                done.countDown()
                thread.quitSafely()
            }
        }
        val mode = if (readable) ParcelFileDescriptor.MODE_READ_WRITE else ParcelFileDescriptor.MODE_WRITE_ONLY
        return storage.openProxyFileDescriptor(mode, callback, Handler(thread.looper))
    }
}
