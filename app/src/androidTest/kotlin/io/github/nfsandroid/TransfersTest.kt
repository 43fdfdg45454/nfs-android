package io.github.nfsandroid

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/** Copies and moves within one server, as a file manager asks for them: the server does them. */
@RunWith(AndroidJUnit4::class)
class TransfersTest {
    private fun received() = uniffi.nfscore.traffic().received.toLong()

    private fun check(uri: Uri) = Provider.resolver.openFileDescriptor(uri, "r")!!.use { fd ->
        FileInputStream(fd.fileDescriptor).channel.use { c -> listOf(0L, 100L shl 20, 255L shl 20).forEach { Player.read(c, 'a'.code.toLong(), it) } }
    }

    @Test
    fun copiesAndMovesOnTheServer() {
        Provider.setUp()
        Provider.children(Provider.ROOT)["transfers"]?.let(Provider::removeTree)
        val fixtures = Provider.find("fixtures")
        val dir = DocumentsContract.createDocument(Provider.resolver, Provider.uri(Provider.ROOT), Document.MIME_TYPE_DIR, "transfers")!!
        val before = received()
        // Into the same directory: the name is taken, so the copy gets another one.
        val copy = DocumentsContract.copyDocument(Provider.resolver, Provider.uri(Provider.find("fixtures/movie-a.bin")), Provider.uri(fixtures))!!
        assertEquals("fixtures/movie-a (1).bin", DocumentsContract.getDocumentId(copy).substringAfter(':'))
        val moved = DocumentsContract.moveDocument(Provider.resolver, copy, Provider.uri(fixtures), dir)!!
        assertTrue("movie-a (1).bin" in Provider.children(DocumentsContract.getDocumentId(dir)))
        assertTrue("movie-a (1).bin" !in Provider.children(fixtures))
        // A whole directory, next to itself.
        val tree = DocumentsContract.copyDocument(Provider.resolver, dir, Provider.uri(Provider.ROOT))!!
        val inTree = Provider.children(DocumentsContract.getDocumentId(tree)).getValue("movie-a (1).bin")
        val received = received() - before
        Provider.report("Server copies: 2 × 256 MiB copied, 1 moved; ${received shr 10} KiB received")
        assertTrue("the copies came through the device: $received bytes", received < 1 shl 20)
        check(moved)
        check(Provider.uri(inTree))
        listOf(dir, tree).forEach { Provider.removeTree(DocumentsContract.getDocumentId(it)) }
    }
}
