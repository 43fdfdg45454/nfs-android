package io.github.nfsandroid.data

import android.content.Context
import android.provider.DocumentsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The servers, in files/servers.json. The file carries a format number: a later version must
 * keep reading what an earlier one saved (fields added later have defaults).
 */
object ServerStore {
    private const val FORMAT = 1
    private val state = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = state
    private lateinit var file: File
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
        file = File(context.filesDir, "servers.json")
        state.value = runCatching {
            val array = JSONObject(file.readText()).getJSONArray("servers")
            List(array.length()) { Server.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun get(id: String) = state.value.find { it.id == id }

    @Synchronized
    fun put(server: Server) = save(
        // An edited server keeps its place in the list.
        if (get(server.id) == null) state.value + server else state.value.map { if (it.id == server.id) server else it },
    )

    @Synchronized
    fun remove(id: String) = save(state.value.filter { it.id != id })

    private fun save(servers: List<Server>) {
        val json = JSONObject().put("format", FORMAT).put("servers", JSONArray(servers.map { it.toJson() }))
        val temporary = File(file.parentFile, "servers.json.new")
        temporary.writeText(json.toString())
        temporary.renameTo(file)
        state.value = servers
        // The file pickers keep each provider's roots until told they changed.
        context.contentResolver.notifyChange(DocumentsContract.buildRootsUri("${context.packageName}.documents"), null)
    }
}
