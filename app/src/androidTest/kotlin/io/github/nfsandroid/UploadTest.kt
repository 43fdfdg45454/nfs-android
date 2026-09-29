package io.github.nfsandroid

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.data.ServerStore
import io.github.nfsandroid.provider.Proxies
import io.github.nfsandroid.provider.ProxyStats
import io.github.nfsandroid.provider.Staged
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Uploads through the provider as apps write, 8 KiB at a time (Java's streams, what file managers
 * use): through a local copy and through the file proxy. Speeds are reported (the emulator's CPU
 * and network set them); what reaches the server is checked.
 */
@RunWith(AndroidJUnit4::class)
class UploadTest {
    private val size = 64 shl 20

    private fun create(server: String, name: String): Uri {
        Provider.listing("$server:")?.let { if (name in it) Provider.deletes("$server:$name") }
        return DocumentsContract.createDocument(Provider.resolver, Provider.uri("$server:"), "application/octet-stream", name)!!
    }

    private fun upload(server: String, block: Int): String {
        val data = ByteArray(block) { (it % 251).toByte() }
        val uri = create(server, "upload-$server.bin")
        val id = DocumentsContract.getDocumentId(uri)
        ProxyStats.Writes.reset()
        val start = System.nanoTime()
        Provider.resolver.openOutputStream(uri, "w")!!.use { out -> repeat(size / block) { out.write(data) } }
        val written = (System.nanoTime() - start) / 1e9
        Staged.await(id, 120_000)
        Proxies.awaitWriters(id)
        val uploaded = (System.nanoTime() - start) / 1e9
        Provider.resolver.openInputStream(uri)!!.use { input ->
            val back = input.readBytes()
            assertEquals(size, back.size)
            for (at in listOf(0, block - 1, size / 2 + 7, size - 1)) assertEquals("at $at", ((at % block) % 251).toByte(), back[at])
        }
        DocumentsContract.deleteDocument(Provider.resolver, uri)
        return "the app wrote in %.1f s; on the server after %.1f s (%.1f MB/s)".format(written, uploaded, size / 1e6 / uploaded)
    }

    @Test
    fun uploadsThroughALocalCopyAndThroughTheProxy() {
        Provider.setUp()
        ServerStore.put(Server(id = "ci-proxy", name = "Proxy", host = Provider.host, export = "/", writeMode = "proxy"))
        Provider.report("Upload 64 MiB by 8 KiB writes, local copy: ${upload("ci", 8 shl 10)}")
        Provider.report("Upload 64 MiB by 8 KiB writes, file proxy: ${upload("ci-proxy", 8 shl 10)} (${ProxyStats.Writes})")
    }

    @Test
    fun whatTheAppRewritesOrCutsReachesTheServer() {
        Provider.setUp()
        val uri = create("ci", "rewritten.bin")
        val data = ByteArray(3 shl 20) { (it % 251).toByte() }
        val cut = (2 shl 20) + 5
        Provider.resolver.openFileDescriptor(uri, "w")!!.use { fd ->
            FileOutputStream(fd.fileDescriptor).channel.use { c ->
                c.write(ByteBuffer.wrap(data))
                Thread.sleep(1000) // its three blocks are uploaded meanwhile
                c.position(0)
                c.write(ByteBuffer.wrap("HEADER".toByteArray()))
                c.truncate(cut.toLong())
            }
        }
        // Opened again, it waits for the upload: what it reads is the server's.
        val back = Provider.resolver.openInputStream(uri)!!.use { it.readBytes() }
        assertEquals(cut, back.size)
        assertEquals("HEADER", String(back, 0, 6))
        assertEquals(data[6], back[6])
        assertEquals(data[cut - 1], back[cut - 1])
        DocumentsContract.deleteDocument(Provider.resolver, uri)
    }
}
