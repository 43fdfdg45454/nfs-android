package io.github.nfsandroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nfsandroid.Player.CHUNK
import io.github.nfsandroid.Provider.report
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.nio.channels.FileChannel
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

/**
 * The engine's scenarios through the provider and the system's file proxy (FUSE), as a player
 * app uses them. Content is checked; times are reported, not enforced: on the emulator its CPU
 * and the proxy set them (nfs-core enforces the limits on the host).
 */
@RunWith(AndroidJUnit4::class)
class ScenariosTest {
    @Before fun setUp() {
        Provider.setUp()
    }

    private fun <T> open(name: String, block: (FileChannel, Long) -> T): T {
        val start = System.nanoTime()
        return Provider.resolver.openFileDescriptor(Provider.uri(Provider.find("fixtures/$name")), "r")!!.use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                report("open $name: ${(System.nanoTime() - start) / 1_000_000} ms")
                block(channel, Player.label(name))
            }
        }
    }

    @Test fun aMovieAndAMarathon() {
        for ((name, seconds) in listOf("movie-a.bin" to 20, "movie-d.bin" to 5, "movie-e.bin" to 5)) {
            val played = open(name) { channel, label -> Player.play(channel, label, 0, seconds) }
            report("play $name: first byte ${played.firstByteMs} ms, ${played.stalls} stalls, ${played.stalledMs} ms stalled")
        }
    }

    @Test fun scenesAndTheSameScenesAgain() {
        open("movie-c.bin") { channel, label ->
            val places = (0 until 8).map { (it * 7919L % 97) * (channel.size() * 6 / 1000) / CHUNK * CHUNK }
            val seeks = places.map { Player.play(channel, label, it, 3).firstByteMs }
            val again = places.map { place -> System.nanoTime().also { Player.read(channel, label, place) }.let { (System.nanoTime() - it) / 1_000_000 } }
            report("seeks: mean ${seeks.average().toInt()} ms, max ${seeks.max()} ms; again: max ${again.max()} ms")
        }
    }

    @Test fun threePlayersAtOnce() {
        val results = arrayOfNulls<Player.Played>(3)
        listOf("movie-a.bin", "movie-b.bin", "movie-c.bin").mapIndexed { i, name ->
            thread { results[i] = open(name) { channel, label -> Player.play(channel, label, channel.size() / 3 / CHUNK * CHUNK, 15) } }
        }.forEach { it.join() }
        report("three players: ${results.sumOf { it!!.stalls }} stalls, ${results.sumOf { it!!.stalledMs }} ms stalled")
    }

    @Test fun openingAndClosingInABurstWhileAnotherPlays() {
        var played: Player.Played? = null
        val player = thread { played = open("movie-e.bin") { channel, label -> Player.play(channel, label, 0, 15) } }
        val opens = (0 until 20).map { i ->
            val start = System.nanoTime()
            open("movie-f.bin") { channel, label -> Player.read(channel, label, i * 3L * CHUNK) }
            (System.nanoTime() - start) / 1_000_000
        }
        player.join()
        report("burst: open, read and close mean ${opens.average().toInt()} ms, max ${opens.max()} ms; the other player ${played!!.stalls} stalls")
    }

    @Test fun readAndUploadRates() {
        io.github.nfsandroid.provider.ProxyStats.Reads.reset()
        val read = open("movie-b.bin") { channel, label ->
            val start = System.nanoTime()
            for (offset in 0 until (64L shl 20) step CHUNK.toLong()) Player.read(channel, label, offset)
            64 * 1.048576 / ((System.nanoTime() - start) / 1e9)
        }
        report("proxy: ${io.github.nfsandroid.provider.ProxyStats.Reads}")
        // The same through the core directly, no file proxy: what the proxy costs.
        val direct = runBlocking {
            val server = io.github.nfsandroid.data.ServerStore.get("ci")!!
            val file = io.github.nfsandroid.core.Mounts.get(server).read("fixtures/movie-e.bin")
            val start = System.nanoTime()
            for (offset in 0 until (32L shl 20) step (1L shl 20)) file.readAt(offset.toULong(), (1u shl 20))
            val async = 32 * 1.048576 / ((System.nanoTime() - start) / 1e9)
            val middle = System.nanoTime()
            for (offset in (32L shl 20) until (64L shl 20) step CHUNK.toLong()) file.readBlocking(offset.toULong(), CHUNK.toUInt())
            file.close()
            async to 32 * 1.048576 / ((System.nanoTime() - middle) / 1e9)
        }
        report("core directly: %.1f MB/s async in 1 MiB calls, %.1f MB/s blocking in 128 KiB calls".format(direct.first, direct.second))
        val data = ByteArray(32 shl 20) { (it % 251).toByte() }
        val start = System.nanoTime()
        val created = Provider.create("upload-rate.bin", data)
        // Closing a descriptor returns before Android releases it, which sends what is left.
        io.github.nfsandroid.provider.Proxies.awaitWriters(android.provider.DocumentsContract.getDocumentId(created))
        val upload = 32 * 1.048576 / ((System.nanoTime() - start) / 1e9)
        android.provider.DocumentsContract.deleteDocument(Provider.resolver, created)
        report("read %.1f MB/s, upload %.1f MB/s".format(read, upload))
    }
}
