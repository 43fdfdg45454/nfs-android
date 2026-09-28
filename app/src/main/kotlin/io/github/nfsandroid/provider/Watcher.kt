package io.github.nfsandroid.provider

import android.content.Context
import android.provider.DocumentsContract
import io.github.nfsandroid.core.Mounts
import io.github.nfsandroid.data.ServerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Folders an app shows: those listed in the last 2 minutes are checked every 15 s (one GETATTR
 * each), and a change, another client's too, is announced to whoever shows them. Nothing is
 * checked once no folder was listed for 2 minutes.
 */
object Watcher {
    private const val WATCH_MS = 120_000L
    private const val EVERY_MS = 15_000L
    /** Folder id: when it was last listed, and its modification time then (null: not known yet). */
    private val listed = ConcurrentHashMap<String, Pair<Long, Long?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    fun listed(context: Context, id: String) {
        listed[id] = System.currentTimeMillis() to listed[id]?.second
        synchronized(this) {
            if (running?.isActive != true) running = scope.launch { watch(context.applicationContext) }
        }
    }

    private suspend fun watch(context: Context) {
        while (true) {
            listed.entries.removeIf { System.currentTimeMillis() - it.value.first > WATCH_MS }
            if (listed.isEmpty()) return
            for ((id, entry) in listed) {
                val modified = modified(id) ?: continue
                listed[id] = entry.first to modified
                if (entry.second != null && entry.second != modified) {
                    val uri = DocumentsContract.buildChildDocumentsUri("${context.packageName}.documents", id)
                    context.contentResolver.notifyChange(uri, null)
                }
            }
            delay(EVERY_MS)
        }
    }

    private suspend fun modified(id: String): Long? {
        val (serverId, path) = Documents.parse(id)
        val server = ServerStore.get(serverId) ?: return null
        return runCatching { Mounts.get(server).stat(path).modified }.getOrNull()
    }
}
