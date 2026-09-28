package io.github.nfsandroid

import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nfsandroid.core.Connector
import io.github.nfsandroid.data.Server
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A folder an app shows hears of what another client changes in it. */
@RunWith(AndroidJUnit4::class)
class WatchTest {
    @Before fun setUp() {
        Provider.setUp()
    }

    @Test fun aChangeByAnotherClientIsAnnounced() {
        val children = DocumentsContract.buildChildDocumentsUri(Provider.uri(Provider.ROOT).authority!!, Provider.ROOT)
        val changed = CountDownLatch(1)
        val thread = HandlerThread("observer").apply { start() }
        val observer = object : ContentObserver(Handler(thread.looper)) {
            override fun onChange(selfChange: Boolean) = changed.countDown()
        }
        Provider.resolver.registerContentObserver(children, false, observer)
        Provider.children(Provider.ROOT)
        val name = "watched-${System.nanoTime()}.txt"
        // Another client: the same server under another id, so another client owner.
        val other = runBlocking { Connector.connect(Provider.context, Server(id = "ci-other", host = Provider.host, export = "/")) }
        runBlocking { other.create(name, true).run { finish(); close() } }
        val start = System.nanoTime()
        val heard = changed.await(40, TimeUnit.SECONDS)
        Provider.report("change by another client announced after ${(System.nanoTime() - start) / 1_000_000} ms")
        Provider.resolver.unregisterContentObserver(observer)
        runBlocking { other.remove(name); other.disconnect() }
        thread.quitSafely()
        assertTrue("the change was not announced", heard)
    }
}
