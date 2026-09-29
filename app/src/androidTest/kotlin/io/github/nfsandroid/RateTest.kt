package io.github.nfsandroid

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.data.ServerStore
import io.github.nfsandroid.provider.Proxies
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A server's caps on its transfers (2 MB/s each way here): 8 MiB read or written through the
 * provider take at least what the cap allows, where the emulator alone goes several times faster.
 */
@RunWith(AndroidJUnit4::class)
class RateTest {
    private val size = 8 shl 20
    private val least = size / 2e6 * 0.85

    private fun timed(block: () -> Unit): Double = System.nanoTime().let { start -> block(); (System.nanoTime() - start) / 1e9 }

    private fun capped(what: String, seconds: Double) {
        Provider.report("Capped at 2 MB/s, $what 8 MiB in %.1f s (%.1f MB/s)".format(seconds, size / 1e6 / seconds))
        assertTrue("$what took $seconds s: under the cap's $least s", seconds >= least)
    }

    @Test
    fun readsAndWritesKeepToTheCap() {
        Provider.setUp()
        ServerStore.put(
            Server(
                id = "ci-rate", name = "Rate", host = Provider.host, export = "/", useCache = false, readAheadMb = 16,
                downLimit = 2, downUnit = "MBps", upLimit = 2, upUnit = "MBps",
            ),
        )
        val read = timed {
            Provider.resolver.openInputStream(Provider.uri("ci-rate:fixtures/movie-a.bin"))!!.use { input ->
                val buffer = ByteArray(128 shl 10)
                var left = size
                while (left > 0) left -= input.read(buffer, 0, minOf(buffer.size, left)).also { check(it > 0) }
            }
        }
        capped("read", read)
        val uri = DocumentsContract.createDocument(Provider.resolver, Provider.uri("ci-rate:"), "application/octet-stream", "rate.bin")!!
        val written = timed {
            Provider.resolver.openOutputStream(uri, "w")!!.use { it.write(ByteArray(size)) }
            // Until the proxy's writer ends (each wait is at most 3 s; once it ended, none).
            repeat(20) { Proxies.awaitWriters(DocumentsContract.getDocumentId(uri)) }
        }
        DocumentsContract.deleteDocument(Provider.resolver, uri)
        capped("written", written)
    }
}
