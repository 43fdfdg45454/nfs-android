package io.github.nfsandroid

import android.provider.DocumentsContract.Document
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nfsandroid.Provider.listing
import io.github.nfsandroid.Provider.opens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No symbolic link leads out of the export or into a loop (the trees of ci/links.sh). */
@RunWith(AndroidJUnit4::class)
class LinkEscapesTest {
    /** Shown as the link it is: not a folder, nothing listed or read through it, soon. */
    private fun unfollowed(id: String, mime: String?) {
        val start = System.nanoTime()
        assertNotEquals("$id shows as a folder", Document.MIME_TYPE_DIR, mime)
        assertNull("$id lists", listing(id))
        assertFalse("$id opens", opens(id))
        assertTrue("$id took long", System.nanoTime() - start < 5_000_000_000L)
    }

    @Test
    fun noLinkLeadsOutOfTheExport() {
        Provider.setUpSec()
        val links = listing("ci-sec:links")!!
        for (name in listOf("root", "etc", "passwd", "escape", "mixed", "dotted", "chain1", "prefix", "other-export")) {
            unfollowed("ci-sec:links/$name", links[name])
        }
        // Nothing past an escaping link either.
        assertNull(listing("ci-sec:links/root/etc"))
        assertFalse(opens("ci-sec:links/etc/passwd"))
        assertFalse(opens("ci-sec:links/escape/etc/passwd"))
        // Inside the export, absolute or relative: followed.
        assertEquals(Document.MIME_TYPE_DIR, links["abs-inside"])
        assertTrue("file.txt" in listing("ci-sec:links/abs-inside")!!)
        assertTrue(opens("ci-sec:links/data/file.txt"))
    }

    @Test
    fun loopsAndLinksAboveStayLinks() {
        Provider.setUpSec()
        val links = listing("ci-sec:links")!!
        for (name in listOf("self", "up", "loop", "pa", "pb")) unfollowed("ci-sec:links/$name", links[name])
        assertFalse(opens("ci-sec:links/up/data/file.txt"))
        // Allowed in Advanced: the folders they point to, and still no loop without end.
        val followed = listing("ci-follow:links")!!
        assertEquals(Document.MIME_TYPE_DIR, followed["self"])
        assertTrue("links" in listing("ci-follow:links/up")!!)
        assertTrue(opens("ci-follow:links/up/links/up/data/file.txt"))
        for (name in listOf("loop", "pa")) unfollowed("ci-follow:links/$name", followed[name])
        // A chain of 40 links is followed, one of 41 is not.
        assertTrue(opens("ci-sec:chain/c2"))
        assertFalse(opens("ci-sec:chain/c1"))
    }
}
