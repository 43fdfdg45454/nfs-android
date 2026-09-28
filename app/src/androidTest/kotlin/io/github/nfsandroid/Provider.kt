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
    private val authority = "${context.packageName}.documents"
    val host = InstrumentationRegistry.getArguments().getString("nfsServer", "10.0.2.2")
    const val ROOT = "ci:"

    fun setUp() = ServerStore.put(Server(id = "ci", name = "CI", host = host, export = "/"))

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
