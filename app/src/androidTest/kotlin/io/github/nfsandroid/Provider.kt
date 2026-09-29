package io.github.nfsandroid

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nfsandroid.data.Server
import io.github.nfsandroid.data.ServerStore
import io.github.nfsandroid.log.NfsLog

/**
 * The provider as another app sees it, against the CI's nfsd (the emulator reaches the machine it
 * runs on at 10.0.2.2), and results for the CI's annotation (logcat tag "nfs-test").
 */
object Provider {
    val context = InstrumentationRegistry.getInstrumentation().targetContext!!
    val resolver = context.contentResolver!!
    val authority = "${context.packageName}.documents"
    val host = InstrumentationRegistry.getArguments().getString("nfsServer", "10.0.2.2")
    const val ROOT = "ci:"

    fun setUp() = ServerStore.put(Server(id = "ci", name = "CI", host = host, export = "/"))

    /** The export "/sec" (ci/links.sh) as root, as a plain user (uid 1234), and following links up. */
    fun setUpSec() = listOf(
        Server(id = "ci-sec", name = "Sec", host = host, export = "/sec"),
        Server(id = "ci-user", name = "User", host = host, export = "/sec", uid = 1234, gid = 1234),
        Server(id = "ci-follow", name = "Follow", host = host, export = "/sec", followParentLinks = true),
    ).forEach(ServerStore::put)

    /** A directory's entries by name, with their MIME types; null if it cannot be listed. */
    fun listing(id: String): Map<String, String>? =
        resolver.query(DocumentsContract.buildChildDocumentsUri(authority, id), null, null, null, null)?.use { c ->
            generateSequence {
                if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)) to
                    c.getString(c.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)) else null
            }.toMap()
        }

    /** Whether the document opens and reads, or opens to append a byte ("wa": nothing truncated). */
    fun opens(id: String) = runCatching { resolver.openInputStream(uri(id))!!.use { it.read() } }.isSuccess
    fun appends(id: String) = runCatching { resolver.openOutputStream(uri(id), "wa")!!.use { it.write('\n'.code) } }.isSuccess
    fun deletes(id: String) = runCatching { DocumentsContract.deleteDocument(resolver, uri(id)) }.getOrDefault(false)

    fun uri(id: String): Uri = DocumentsContract.buildDocumentUri(authority, id)

    fun report(line: String) = Log.i("nfs-test", "RESULT $line")

    /** A failed query comes back empty (DocumentsProvider logs it): the app's log says why. */
    fun why(what: String): Nothing =
        error("$what failed; nfs-log.txt ends with:\n" + NfsLog.file(context).let { if (it.exists()) it.readText().takeLast(2000) else "(no log)" })

    fun children(id: String): Map<String, String> =
        (resolver.query(DocumentsContract.buildChildDocumentsUri(authority, id), null, null, null, null) ?: why("listing $id")).use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)) to c.getString(0) else null }.toMap()
        }

    /** A document by its path under the export. */
    fun find(path: String): String = path.split('/').fold(ROOT) { id, name -> children(id)[name] ?: why("finding $path") }

    /** A document and, if a directory, all it holds. */
    fun removeTree(id: String) {
        runCatching { children(id) }.getOrDefault(emptyMap()).values.forEach(::removeTree)
        DocumentsContract.deleteDocument(resolver, uri(id))
    }

    /** A new file at the root with [data]; its document id. */
    fun create(name: String, data: ByteArray): Uri {
        children(ROOT)[name]?.let { DocumentsContract.deleteDocument(resolver, uri(it)) }
        val created = DocumentsContract.createDocument(resolver, uri(ROOT), "application/octet-stream", name)!!
        resolver.openOutputStream(created, "w")!!.use { it.write(data) }
        return created
    }
}
