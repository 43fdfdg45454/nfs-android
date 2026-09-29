package io.github.nfsandroid

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nfsandroid.Provider.appends
import io.github.nfsandroid.Provider.deletes
import io.github.nfsandroid.Provider.listing
import io.github.nfsandroid.Provider.opens
import io.github.nfsandroid.Provider.uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Symbolic links grant nothing the server does not, and deleting or copying one keeps its target. */
@RunWith(AndroidJUnit4::class)
class LinkRightsTest {
    @Test
    fun linksGrantNothingTheServerDoesNot() {
        Provider.setUpSec()
        // Allowed to the plain user: through the link as by the path.
        assertTrue(opens("ci-user:links/data/file.txt"))
        val made = DocumentsContract.createDocument(Provider.resolver, uri("ci-user:links/data"), "text/plain", "by-user.txt")!!
        Provider.resolver.openOutputStream(made, "w")!!.use { it.write(1) }
        assertTrue("by-user.txt" in listing("ci-user:data")!!)
        // Root's 0700 folder and 0600 file (nfsd may read them, the user may not): refused both ways.
        assertNull(listing("ci-user:private"))
        assertNull(listing("ci-user:links/private"))
        assertFalse(opens("ci-user:private/secret.txt"))
        assertFalse(opens("ci-user:links/secret"))
        // A read-only file: not written through its link either.
        assertFalse(appends("ci-user:readonly.txt"))
        assertFalse(appends("ci-user:links/readonly"))
        // A link in a folder the user cannot write stays.
        assertFalse(deletes("ci-user:locked/l"))
        assertTrue("l" in listing("ci-sec:locked")!!)
        // Through a folder the user may only cross (0711): as by its path.
        val crossed = listing("ci-user:noread/ok")
        assertNotNull(crossed)
        assertEquals(crossed, listing("ci-user:links/through-noread"))
    }

    @Test
    fun deletingALinkKeepsWhatItPointsTo() {
        Provider.setUpSec()
        assertTrue(deletes("ci-sec:links/victim"))
        assertFalse("victim" in listing("ci-sec:links")!!)
        assertEquals(setOf("a", "b"), listing("ci-sec:victim")!!.keys)
        // Links to a folder, out of the export, in a loop and to a file, deleted one by one.
        listing("ci-sec:bag")!!.keys.forEach { assertTrue(it, deletes("ci-sec:bag/$it")) }
        assertEquals(emptyMap<String, String>(), listing("ci-sec:bag"))
        assertTrue(opens("ci-sec:data/file.txt"))
        assertTrue("sub" in listing("ci-sec:data")!!)
    }

    @Test
    fun copiesKeepLinksAndNeverCopyIntoThemselves() {
        Provider.setUpSec()
        // A folder's links are copied as links: the copy of "in" shows what is new in its target.
        val copy = DocumentsContract.getDocumentId(DocumentsContract.copyDocument(Provider.resolver, uri("ci-sec:tocopy"), uri("ci-sec:"))!!)
        assertNull(listing("$copy/out"))
        DocumentsContract.createDocument(Provider.resolver, uri("ci-sec:data"), "text/plain", "later.txt")
        assertTrue("later.txt" in listing("$copy/in")!!)
        // A link to a folder copies the folder: what is new in it later is not in the copy.
        val deep = DocumentsContract.getDocumentId(DocumentsContract.copyDocument(Provider.resolver, uri("ci-sec:links/data"), uri("ci-sec:"))!!)
        assertTrue("file.txt" in listing(deep)!! && "inner.txt" in listing("$deep/sub")!!)
        DocumentsContract.createDocument(Provider.resolver, uri("ci-sec:data"), "text/plain", "later-2.txt")
        assertFalse("later-2.txt" in listing(deep)!!)
        // Into itself, by its name or through a link: refused, nothing made.
        for (target in listOf("ci-sec:data/sub", "ci-sec:links/data")) {
            assertNull(runCatching { DocumentsContract.copyDocument(Provider.resolver, uri("ci-sec:data"), uri(target)) }.getOrNull())
        }
        assertFalse("data" in listing("ci-sec:data")!! || "data" in listing("ci-sec:data/sub")!!)
        listOf("$copy/in", "$copy/out", copy).forEach(::deletes)
        Provider.removeTree(deep)
    }
}
