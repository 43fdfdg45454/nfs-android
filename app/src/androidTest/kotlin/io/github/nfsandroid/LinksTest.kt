package io.github.nfsandroid

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/** Symbolic links (made by the CI in "links"), and a document's path for the system's picker. */
@RunWith(AndroidJUnit4::class)
class LinksTest {
    @Test
    fun followsLinksInsideTheExport() {
        Provider.setUp()
        val links = Provider.children(Provider.find("links"))
        // A link to a directory is one: it lists what the directory holds.
        assertTrue("movie-a.bin" in Provider.children(links.getValue("fixtures")))
        // A link to a file opens that file.
        Provider.resolver.openFileDescriptor(Provider.uri(links.getValue("movie")), "r")!!.use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { Player.read(it, 'a'.code.toLong(), 5L shl 20) }
        }
        // Out of the export, or a loop: still listed, as the links they are.
        assertTrue(links.keys.containsAll(listOf("outside", "loop")))
    }

    @Test
    fun findsThePathOfADocument() {
        Provider.setUp()
        val path = DocumentsContract.findDocumentPath(Provider.resolver, Provider.uri(Provider.find("fixtures/movie-a.bin")))!!
        assertEquals("ci", path.rootId)
        assertEquals(listOf("ci:", "ci:fixtures", "ci:fixtures/movie-a.bin"), path.path)
    }
}
