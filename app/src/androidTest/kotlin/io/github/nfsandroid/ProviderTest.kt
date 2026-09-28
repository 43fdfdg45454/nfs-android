package io.github.nfsandroid

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/** The provider's basics, as another app uses them through the ContentResolver. */
@RunWith(AndroidJUnit4::class)
class ProviderTest {
    @Test
    fun readsWithSeeksWritesRenamesAndRemoves() {
        Provider.setUp()
        val roots = Provider.resolver.query(DocumentsContract.buildRootsUri(Provider.uri(Provider.ROOT).authority!!), null, null, null, null)!!.use { it.count }
        assertTrue("no roots", roots > 0)
        val movie = Provider.find("fixtures/movie-a.bin")
        Provider.resolver.openFileDescriptor(Provider.uri(movie), "r")!!.use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                for (place in listOf(0L, 100L shl 20, 3L shl 20, 200L shl 20, 7L shl 20)) Player.read(channel, 'a'.code.toLong(), place)
            }
        }
        val created = Provider.create("provider-test.txt", ByteArray(3 shl 20) { (it % 251).toByte() })
        Provider.resolver.openInputStream(created)!!.use { input ->
            val back = input.readBytes()
            assertEquals(3 shl 20, back.size)
            assertTrue(back.withIndex().all { (i, b) -> b == (i % 251).toByte() })
        }
        val renamed = DocumentsContract.renameDocument(Provider.resolver, created, "renamed.txt")!!
        assertTrue("renamed.txt" in Provider.children(Provider.ROOT))
        DocumentsContract.deleteDocument(Provider.resolver, renamed)
        assertTrue("renamed.txt" !in Provider.children(Provider.ROOT))
    }
}
