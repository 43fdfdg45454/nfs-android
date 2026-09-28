package io.github.nfsandroid.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.storage.StorageManager
import android.system.Os
import android.system.OsConstants
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import io.github.nfsandroid.core.Mounts
import io.github.nfsandroid.data.ServerStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.FileNotFoundException

/** Every server as a root of the system's file picker, for any app to open files from. */
class NfsProvider : DocumentsProvider() {
    private val authority get() = "${context!!.packageName}.documents"

    override fun onCreate() = true

    private fun <T> nfs(id: String, block: suspend (uniffi.nfscore.Mount, String, io.github.nfsandroid.data.Server) -> T): T {
        val (serverId, path) = Documents.parse(id)
        val server = ServerStore.get(serverId) ?: throw FileNotFoundException("no server for $id")
        return try {
            // A server that stopped answering must not hang the app asking (a picker, a file manager).
            runBlocking { withTimeout(CALL_MS) { block(Mounts.get(server), path, server) } }
        } catch (e: Exception) {
            // DocumentsProvider turns this into an empty answer: the log is where it shows.
            io.github.nfsandroid.log.NfsLog.line("${server.title}/$path: $e")
            throw FileNotFoundException("${server.title}/$path: ${e.message}")
        }
    }

    override fun queryRoots(projection: Array<String>?): Cursor = MatrixCursor(projection ?: ROOT_COLUMNS).apply {
        ServerStore.servers.value.filter { it.enabled }.forEach { server ->
            val flags = if (server.readOnly) Root.FLAG_SUPPORTS_IS_CHILD else Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD
            val space = Spaces.get(context!!, server.id)
            // The system's Files app shows the summary, and the free space only without one: it goes in it.
            val summary = space?.let { (available, total) -> Spaces.describe(context!!, available, total) } ?: server.host
            val row = newRow().add(Root.COLUMN_ROOT_ID, server.id).add(Root.COLUMN_DOCUMENT_ID, Documents.id(server.id, ""))
                .add(Root.COLUMN_TITLE, server.title).add(Root.COLUMN_SUMMARY, summary).add(Root.COLUMN_FLAGS, flags)
                .add(Root.COLUMN_ICON, io.github.nfsandroid.R.drawable.ic_stat)
            space?.let { (available, total) -> row.add(Root.COLUMN_AVAILABLE_BYTES, available).add(Root.COLUMN_CAPACITY_BYTES, total) }
            Spaces.refresh(context!!, server)
        }
    }

    override fun queryDocument(id: String, projection: Array<String>?): Cursor = MatrixCursor(projection ?: Documents.COLUMNS).apply {
        nfs(id) { mount, path, server -> Documents.row(this, id, mount.stat(path), server, path.substringAfterLast('/').ifEmpty { server.title }) }
    }

    override fun queryChildDocuments(parent: String, projection: Array<String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: Documents.COLUMNS).apply {
            nfs(parent) { mount, path, server -> mount.list(path).forEach { Documents.row(this, Documents.child(parent, it.name), it, server) } }
            Watcher.listed(context!!, parent)
            setNotificationUri(context!!.contentResolver, DocumentsContract.buildChildDocumentsUri(authority, parent))
        }

    override fun openDocument(id: String, mode: String, signal: CancellationSignal?) = nfs(id) { mount, path, _ ->
        val storage = context!!.getSystemService(StorageManager::class.java)
        val truncate = 't' in mode
        // Whatever the mode, a writer this document still has finishes first (close-to-open).
        Proxies.awaitWriters(id)
        when (mode) {
            "r" -> Proxies.read(storage, path, mount.read(path))
            "w", "wt" -> Proxies.write(storage, id, path, mount.create(path, exclusive = false))
            // In place: "rw" reads and writes, "rwt" empties it first, "wa" appends (the
            // descriptor starts at the end: the proxy does not know O_APPEND).
            else -> {
                val size = if (truncate) 0L else mount.stat(path).size.toLong()
                Proxies.write(storage, id, path, mount.edit(path, truncate), size, readable = 'r' in mode)
                    .also { if (mode == "wa") Os.lseek(it.fileDescriptor, size, OsConstants.SEEK_SET) }
            }
        }
    }

    override fun openDocumentThumbnail(id: String, size: android.graphics.Point, signal: CancellationSignal?) = nfs(id) { mount, path, _ ->
        val stat = mount.stat(path)
        val key = "$id:${stat.modified}:${stat.size}"
        Thumbnails.get(context!!, key, Documents.mime(stat), size) { runBlocking { mount.read(path) } }
            ?: throw FileNotFoundException("no thumbnail for $path")
    }

    override fun createDocument(parent: String, mimeType: String, name: String): String = nfs(parent) { mount, path, _ ->
        val child = if (path.isEmpty()) name else "$path/$name"
        if (mimeType == Document.MIME_TYPE_DIR) mount.mkdir(child) else mount.create(child, exclusive = true).run { finish(); close() }
        changed(parent)
        Documents.child(parent, name)
    }

    override fun deleteDocument(id: String) = nfs(id) { mount, path, _ -> mount.remove(path); changed(parent(id)) }

    override fun renameDocument(id: String, name: String): String = nfs(id) { mount, path, _ ->
        val to = path.substringBeforeLast('/', "").let { if (it.isEmpty()) name else "$it/$name" }
        mount.rename(path, to)
        changed(parent(id))
        Documents.id(Documents.parse(id).first, to)
    }

    override fun isChildDocument(parent: String, id: String) = parent.substringAfter(':').let { p ->
        Documents.parse(id).let { (server, path) -> server == Documents.parse(parent).first && (p.isEmpty() || path.startsWith("$p/")) }
    }

    private fun parent(id: String) = Documents.parse(id).let { (server, path) -> Documents.id(server, path.substringBeforeLast('/', "")) }

    private fun changed(parent: String) = context!!.contentResolver.notifyChange(DocumentsContract.buildChildDocumentsUri(authority, parent), null)

    companion object {
        private const val CALL_MS = 20_000L
        private val ROOT_COLUMNS = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY, Root.COLUMN_FLAGS, Root.COLUMN_ICON,
            Root.COLUMN_AVAILABLE_BYTES, Root.COLUMN_CAPACITY_BYTES)
    }
}
