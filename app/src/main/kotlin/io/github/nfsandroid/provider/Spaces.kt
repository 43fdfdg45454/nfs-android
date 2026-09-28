package io.github.nfsandroid.provider

import android.content.Context
import android.os.SystemClock
import android.provider.DocumentsContract
import io.github.nfsandroid.core.Mounts
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.ui.common.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Each server's space (available to this client, and total) for the file pickers' roots: the last
 * known at once, kept across restarts, and asked again in the background when a root is shown and
 * its server is connected; never connecting just for it. A new answer tells the pickers.
 */
object Spaces {
    private const val AGAIN_MS = 10_000L
    private val known = ConcurrentHashMap<String, Pair<Long, Long>>()
    private val asked = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Every server's space known, for the servers' cards. */
    val state = kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, Pair<Long, Long>>())

    private fun preferences(context: Context) = context.getSharedPreferences("spaces", Context.MODE_PRIVATE)

    fun get(context: Context, id: String): Pair<Long, Long>? = known[id] ?: preferences(context).getString(id, null)
        ?.split(',')?.let { (available, total) -> (available.toLong() to total.toLong()).also { known[id] = it; state.value = HashMap(known) } }

    /** "1.2 TB free of 3.6 TB", as the pickers and the servers' cards show it. */
    fun describe(context: Context, available: Long, total: Long) =
        context.getString(io.github.nfsandroid.R.string.space_free, Format.bytes(available), Format.bytes(total))

    fun refresh(context: Context, server: Server) {
        val now = SystemClock.elapsedRealtime()
        if (now - (asked[server.id] ?: -AGAIN_MS) < AGAIN_MS) return
        asked[server.id] = now
        scope.launch {
            val mount = Mounts.connected(server.id) ?: return@launch
            val space = runCatching { mount.space() }.getOrNull() ?: return@launch
            val value = space.available.toLong() to space.total.toLong()
            if (known.put(server.id, value) == value) return@launch
            state.value = HashMap(known)
            preferences(context).edit().putString(server.id, "${value.first},${value.second}").apply()
            context.contentResolver.notifyChange(DocumentsContract.buildRootsUri("${context.packageName}.documents"), null)
        }
    }
}
