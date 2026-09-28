package io.github.nfsandroid.core

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uniffi.nfscore.MountStats

/** What the connected servers are doing, every 2 s: for the notification, the home and the activity screen. */
object Live {
    data class Server(val id: String, val stats: MountStats, val idleMinutes: Long)

    /** [down] and [up] in bytes per second, for all servers together; [unreachable]: why, by server. */
    data class State(
        val servers: List<Server> = emptyList(), val down: Long = 0, val up: Long = 0, val unreachable: Map<String, String> = emptyMap(),
    ) {
        fun of(id: String) = servers.find { it.id == id }
    }

    private const val PERIOD_MS = 2000L
    private val current = MutableStateFlow(State())
    val state: StateFlow<State> = current

    fun start(scope: CoroutineScope, onEach: (State) -> Unit) = scope.launch {
        // Only while something is connected or a screen shows it: otherwise it waits without
        // waking the phone every 2 s.
        val needed = combine(Mounts.count, current.subscriptionCount) { mounts, watchers -> mounts > 0 || watchers > 0 }
        var last = uniffi.nfscore.traffic() to SystemClock.elapsedRealtime()
        while (true) {
            if (!needed.first()) {
                current.value = State()
                needed.first { it }
                last = uniffi.nfscore.traffic() to SystemClock.elapsedRealtime()
            }
            delay(PERIOD_MS)
            val now = uniffi.nfscore.traffic() to SystemClock.elapsedRealtime()
            val seconds = (now.second - last.second).coerceAtLeast(1) / 1000.0
            fun rate(bytes: ULong) = (bytes.toDouble() / seconds).toLong()
            val state = State(
                Mounts.live(), rate(now.first.received - last.first.received), rate(now.first.sent - last.first.sent), Unreachable.now(),
            )
            last = now
            current.value = state
            onEach(state)
        }
    }
}
