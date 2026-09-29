package io.github.nfsandroid

import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.data.ServerStore
import io.github.nfsandroid.log.NfsLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * Each server's thumbnail strategy: its sources in its order, those turned off skipped, and its
 * cap on what one may read. The videos here cannot be decoded: a thumbnail comes from where the
 * strategy says. What each search did is in the log (level detail).
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailTest {
    private val dir = "thumbs-${System.nanoTime()}"

    private fun server(id: String, change: (Server) -> Server = { it }) =
        ServerStore.put(change(Server(id = id, name = id, host = Provider.host, export = "/", logLevel = "debug")))

    private fun image(width: Int, noise: Boolean = false): ByteArray = ByteArrayOutputStream().also { out ->
        val pixels = IntArray(width * width) { if (noise) Random.nextInt() or (0xff shl 24) else 0xff3366cc.toInt() }
        Bitmap.createBitmap(pixels, width, width, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 100, out)
    }.toByteArray()

    private fun put(name: String, data: ByteArray) {
        val uri = DocumentsContract.createDocument(Provider.resolver, Provider.uri("ci:$dir"), "application/octet-stream", name)!!
        Provider.resolver.openOutputStream(uri, "w")!!.use { it.write(data) }
    }

    private fun thumbnail(server: String, name: String): Bitmap? = runCatching {
        DocumentsContract.getDocumentThumbnail(Provider.resolver, Provider.uri("$server:$dir/$name"), Point(96, 96), null)
    }.getOrNull()

    /** The search's line in the log, as key=value fields. */
    private fun search(name: String): Map<String, String> =
        NfsLog.file(Provider.context).readLines().last { " thumbnails " in it && "file=/$dir/$name " in it }
            .split(' ').mapNotNull { it.split('=', limit = 2).takeIf { f -> f.size == 2 }?.let { (k, v) -> k to v } }.toMap()

    @Test
    fun theStrategyDecidesWhereThumbnailsComeFrom() {
        Provider.setUp()
        server("ci-thumbs")
        server("ci-thumbs-mkv-first") { it.copy(thumbnailOrder = listOf("attachment", "sidecar", "embedded", "exif", "decode")) }
        server("ci-thumbs-no-sidecar") { it.copy(thumbnailOff = setOf("sidecar", "exif")) }
        server("ci-thumbs-capped") { it.copy(thumbnailMaxMb = 1) }
        DocumentsContract.createDocument(Provider.resolver, Provider.uri("ci:"), DocumentsContract.Document.MIME_TYPE_DIR, dir)
        put("clip.mp4", Random.nextBytes(2 shl 20))
        put("clip-poster.jpg", image(400))
        put("show.mkv", Mkv.withCover(image(64)))
        put("both.mkv", Mkv.withCover(image(64)))
        put("both.jpg", image(400))
        put("noise.mp4", Random.nextBytes(1 shl 20))
        put("noise-poster.jpg", image(1600, noise = true))
        put("noise.jpg", image(1600, noise = true))

        assertNotNull(thumbnail("ci-thumbs", "clip.mp4"))
        assertEquals("sidecar", search("clip.mp4")["source"])
        assertNotNull(thumbnail("ci-thumbs", "show.mkv"))
        assertEquals("attachment", search("show.mkv")["source"])
        // Tried first, the attachment is found without reading the 1 MiB of video before it.
        assertNotNull(thumbnail("ci-thumbs-mkv-first", "show.mkv"))
        val mkv = search("show.mkv")
        assertTrue("the MKV's video was read: $mkv", mkv.getValue("read").removeSuffix("MB").toDouble() < 0.2)
        assertNotNull(thumbnail("ci-thumbs", "both.mkv"))
        assertEquals("sidecar", search("both.mkv")["source"])
        assertNotNull(thumbnail("ci-thumbs-mkv-first", "both.mkv"))
        assertEquals("attachment", search("both.mkv")["source"])
        assertNull(thumbnail("ci-thumbs-no-sidecar", "clip.mp4"))
        assertEquals(null, search("clip.mp4")["source"])
        // The cap holds Android's decoders only: a poster of several MB next to the video still
        // comes, and decoding a large image stops at 1 MB, its partial picture dropped.
        assertNotNull(thumbnail("ci-thumbs-capped", "noise.mp4"))
        assertEquals("sidecar", search("noise.mp4")["source"])
        assertNull(thumbnail("ci-thumbs-capped", "noise.jpg"))
        val capped = search("noise.jpg")
        assertTrue("not capped: $capped", " capped " in NfsLog.file(Provider.context).readLines().last { "file=/$dir/noise.jpg " in it })
        assertEquals("1.0MB", capped["read"])
        Provider.report("Thumbnails: sidecar, MKV attachment read ${mkv["read"]} in ${mkv["reads"]} reads, capped at ${capped["read"]}")
        Provider.removeTree("ci:$dir")
    }
}
