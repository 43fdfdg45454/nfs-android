package io.github.nfsandroid

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nfsandroid.provider.Proxies
import io.github.nfsandroid.provider.ProxyStats
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uploads through the provider as apps write: 8 KiB at a time (Java's streams, what file managers
 * use) and 1 MiB at a time. Speeds are reported (the emulator's CPU sets them); content is checked.
 */
@RunWith(AndroidJUnit4::class)
class UploadTest {
    private val size = 64 shl 20

    private fun upload(block: Int): String {
        val data = ByteArray(block) { (it % 251).toByte() }
        val uri = Provider.create("upload-$block.bin", ByteArray(0))
        ProxyStats.Writes.reset()
        val start = System.nanoTime()
        Provider.resolver.openOutputStream(uri, "w")!!.use { out -> repeat(size / block) { out.write(data) } }
        Proxies.awaitWriters(DocumentsContract.getDocumentId(uri))
        val mbs = size / 1e6 / ((System.nanoTime() - start) / 1e9)
        Provider.resolver.openInputStream(uri)!!.use { input ->
            val back = input.readBytes()
            assertEquals(size, back.size)
            for (at in listOf(0, block - 1, size / 2 + 7, size - 1)) assertEquals("at $at", ((at % block) % 251).toByte(), back[at])
        }
        DocumentsContract.deleteDocument(Provider.resolver, uri)
        return "%.1f MB/s (%s)".format(mbs, ProxyStats.Writes)
    }

    @Test
    fun uploadsInSmallAndLargeWrites() {
        Provider.setUp()
        Provider.report("Upload 64 MiB by 8 KiB writes: ${upload(8 shl 10)}")
        Provider.report("Upload 64 MiB by 1 MiB writes: ${upload(1 shl 20)}")
    }
}
