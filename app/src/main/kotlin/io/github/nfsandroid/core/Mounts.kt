package io.github.nfsandroid.core

import android.content.Context
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.data.ServerStore
import io.github.nfsandroid.log.LogCategory
import io.github.nfsandroid.log.LogLevel
import io.github.nfsandroid.log.NfsLog
import io.github.nfsandroid.service.ConnectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uniffi.nfscore.Mount

/**
 * One mount per server, connected on first use and closed after its minutes unused (5 by default,
 * or never: the server's Advanced settings). While any is
 * connected, the foreground service keeps the app's network allowed in the background.
 */
object Mounts {
    /** Nobody there shows in 4 s (the core's reach timeout); a slow link that answers gets this long. */
    private const val CONNECT_MS = 15_000L
    private val lock = Mutex()
    private val mounts = HashMap<String, Pair<Mount, Long>>()
    private val connecting = HashMap<String, Deferred<Mount>>()
    /** How many servers are connected now. */
    val count = kotlinx.coroutines.flow.MutableStateFlow(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
        Live.start(scope) { if (it.servers.isNotEmpty()) ConnectionService.show(this.context, it) }
        scope.launch {
            while (true) {
                delay(60_000)
                closeIdle()
            }
        }
    }

    /**
     * The server's mount, connecting it if needed: one attempt per server at a time (the others
     * wait for it, not for another server), for [CONNECT_MS] at most; after a failure, calls fail
     * at once for a while (see [Unreachable]).
     */
    suspend fun get(server: Server): Mount {
        // Whoever asks (a picker, the folder watcher), a disabled server stays unconnected.
        check(server.enabled) { "${server.title} is disabled" }
        // Its network (that VPN, that Wi-Fi) is not up: nothing to wait for.
        if (!Networks.allows(server)) throw IOException("its network is not up")
        val attempt = lock.withLock {
            mounts[server.id]?.let { (mount, _) ->
                mounts[server.id] = mount to System.currentTimeMillis()
                return mount
            }
            Unreachable.check(server.id)
            connecting.getOrPut(server.id) { scope.async { connect(server) } }
        }
        return attempt.await()
    }

    private suspend fun connect(server: Server): Mount {
        ConnectionService.start(context)
        val result = runCatching {
            withTimeoutOrNull(CONNECT_MS) { Connector.connect(context, server) } ?: throw IOException("no answer in ${CONNECT_MS / 1000} s")
        }
        lock.withLock {
            connecting.remove(server.id)
            result.onSuccess { mounts[server.id] = it to System.currentTimeMillis() }
            count.value = mounts.size
            if (mounts.isEmpty()) ConnectionService.stop(context)
        }
        result.onSuccess {
            Unreachable.reachable(server.id)
            NfsLog.log(LogLevel.INFO, LogCategory.CONNECTION, server, "connected", "transport" to server.transport, "security" to server.security)
            io.github.nfsandroid.provider.Spaces.refresh(context, server)
        }
            .onFailure {
                Unreachable.failed(server.id, it.message.orEmpty())
                NfsLog.log(LogLevel.ERROR, LogCategory.CONNECTION, server, "could not connect", "error" to NfsLog.reason(it))
            }
        return result.getOrThrow()
    }

    /** The device changed networks: every mount reconnects now, and servers that did not answer are tried again. */
    fun networkChanged() {
        Unreachable.clear()
        scope.launch { lock.withLock { mounts.values.map { it.first } }.forEach { it.networkChanged() } }
    }

    /** A network came or went: servers are tried again, and one whose own network went is closed. */
    fun networksChanged() {
        Unreachable.clear()
        scope.launch {
            val gone = lock.withLock { mounts.keys.filter { id -> ServerStore.get(id)?.let { !Networks.allows(it) } == true } }
            gone.forEach { forget(it) }
        }
    }

    /** Each connected server's connections now, and minutes since it was last used. */
    suspend fun live(): List<Live.Server> = lock.withLock {
        val now = System.currentTimeMillis()
        mounts.map { (id, entry) -> Live.Server(id, entry.first.stats(), (now - entry.second) / 60_000) }
    }

    /** The mount of a server already connected, without connecting it. */
    suspend fun connected(id: String): Mount? = lock.withLock { mounts[id]?.first }

    /** A server was edited or removed: its mount goes. */
    suspend fun forget(id: String) = lock.withLock {
        Unreachable.reachable(id)
        mounts.remove(id)?.first?.disconnect()
        after()
    }

    private suspend fun closeIdle() = lock.withLock {
        val now = System.currentTimeMillis()
        mounts.entries.removeAll { (id, entry) ->
            val minutes = ServerStore.get(id)?.disconnectMinutes ?: 5
            (minutes > 0 && now - entry.second > minutes * 60_000L).also { idle -> if (idle) entry.first.disconnect().also { ServerStore.get(id)?.let { idleClosed(it, minutes) } } }
        }
        after()
    }

    private fun idleClosed(server: Server, minutes: Int) =
        NfsLog.log(LogLevel.DEBUG, LogCategory.CONNECTION, server, "closed", "reason" to "unused for $minutes min")

    private fun after() {
        count.value = mounts.size
        if (mounts.isEmpty()) ConnectionService.stop(context)
    }
}
