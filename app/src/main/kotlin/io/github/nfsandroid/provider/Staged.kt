package io.github.nfsandroid.provider

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import io.github.nfsandroid.data.Server
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import uniffi.nfscore.WriteFile

/**
 * Files written whole through a local copy, as Android means providers of remote files to: the
 * app gets a real file (disk speed, seeks, fsync; the file proxy costs about 1 ms per write), and
 * it is uploaded while it grows and finished once the app closes it ([Uploader]). The copy is a
 * cache file only this app sees, gone when the upload ends; the document is not opened again (nor
 * its thumbnail made) until then.
 */
object Staged {
    private val pending = ConcurrentHashMap<String, CountDownLatch>()

    /** Whether [document] is being uploaded. */
    fun uploading(document: String) = document in pending

    /** Waits up to [ms] for [document]'s upload to end; whether it did. */
    fun await(document: String, ms: Long) = pending[document]?.await(ms, TimeUnit.MILLISECONDS) ?: true

    /** Room for a local copy: never under 1 GB free. */
    fun room(context: Context) = dir(context).usableSpace > 1L shl 30

    /** Copies left by a process that ended mid-upload: their uploads cannot resume. */
    fun clean(context: Context) = dir(context).listFiles()?.forEach { it.delete() }

    private fun dir(context: Context) = File(context.cacheDir, "uploads").apply { mkdirs() }

    fun write(context: Context, server: Server, document: String, path: String, file: WriteFile): ParcelFileDescriptor {
        val local = File.createTempFile("upload", null, dir(context))
        val (closed, done) = CountDownLatch(1) to CountDownLatch(1)
        pending[document] = done
        val mode = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE
        val fd = ParcelFileDescriptor.open(local, mode, Handler(Looper.getMainLooper())) { closed.countDown() }
        thread(name = "nfs-upload") {
            Uploader(context, server, path, local, file).run(closed)
            pending.remove(document)
            local.delete()
            done.countDown()
        }
        return fd
    }
}
