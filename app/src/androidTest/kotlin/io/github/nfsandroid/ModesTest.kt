package io.github.nfsandroid

import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nfsandroid.data.Settings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** What an app can do with a file besides reading it: the open modes, thumbnails, the cache. */
@RunWith(AndroidJUnit4::class)
class ModesTest {
    @Before fun setUp() {
        Provider.setUp()
    }

    private fun content(uri: Uri) = Provider.resolver.openInputStream(uri)!!.use { it.readBytes() }

    @Test fun writingInPlaceAppendingAndTruncating() {
        val file = Provider.create("modes.txt", "0123456789".toByteArray())
        Provider.resolver.openFileDescriptor(file, "rw")!!.use { fd ->
            Os.pwrite(fd.fileDescriptor, "ab".toByteArray(), 0, 2, 4)
            val back = ByteArray(10).also { Os.pread(fd.fileDescriptor, it, 0, 10, 0) }
            assertEquals("reads through the same descriptor see the write", "0123ab6789", String(back))
        }
        assertEquals("0123ab6789", String(content(file)))
        Provider.resolver.openOutputStream(file, "wa")!!.use { it.write("XY".toByteArray()) }
        assertEquals("0123ab6789XY", String(content(file)))
        Provider.resolver.openOutputStream(file, "wt")!!.use { it.write("new".toByteArray()) }
        assertEquals("new", String(content(file)))
        DocumentsContract.deleteDocument(Provider.resolver, file)
    }

    @Test fun anImageHasAThumbnail() {
        val png = ByteArrayOutputStream().also { out ->
            Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff3366cc.toInt()) }
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }.toByteArray()
        val image = Provider.create("thumbnail.png", png)
        val thumbnail = DocumentsContract.getDocumentThumbnail(Provider.resolver, image, Point(96, 96), null)
        assertNotNull("no thumbnail", thumbnail)
        assertTrue("thumbnail ${thumbnail!!.width}x${thumbnail.height}", thumbnail.width in 96..640 && thumbnail.width < 640)
        DocumentsContract.deleteDocument(Provider.resolver, image)
    }

    @Test fun theCacheFillsAndEmpties() {
        val dir = Settings.cacheDir(Provider.context)
        uniffi.nfscore.cacheClear(dir)
        val data = ByteArray(4 shl 20) { (it % 13).toByte() }
        val file = Provider.create("cached.bin", data)
        assertArrayEquals(data, content(file))
        Thread.sleep(1000)
        val used = uniffi.nfscore.cacheUsed(dir).toLong()
        Provider.report("cache after reading 4 MiB: $used bytes")
        assertTrue("nothing cached", used > 0)
        uniffi.nfscore.cacheClear(dir)
        assertEquals(0L, uniffi.nfscore.cacheUsed(dir).toLong())
        DocumentsContract.deleteDocument(Provider.resolver, file)
    }
}
