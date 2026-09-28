package io.github.nfsandroid.provider

import android.database.MatrixCursor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import java.io.FileNotFoundException
import io.github.nfsandroid.data.Server
import uniffi.nfscore.Kind
import uniffi.nfscore.Stat

/** Document ids ("server id:path under the export") and the rows describing documents. */
object Documents {
    /** What a writable server's files and directories allow besides their own flags. */
    private const val EDIT = Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or
        Document.FLAG_SUPPORTS_COPY or Document.FLAG_SUPPORTS_MOVE

    val COLUMNS = arrayOf(
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS,
    )

    fun id(server: String, path: String) = "$server:${path.trim('/')}"

    /** The server and the path of a document id. */
    fun parse(id: String): Pair<String, String> = id.substringBefore(':') to id.substringAfter(':', "")

    fun child(parent: String, name: String) = parse(parent).let { (server, path) -> id(server, if (path.isEmpty()) name else "$path/$name") }

    /** From the root (or [parent]) down to [child]: the path is in the id, no call needed. */
    fun path(parent: String?, child: String): DocumentsContract.Path {
        val (server, path) = parse(child)
        val below = path.split('/').filter { it.isNotEmpty() }.runningReduce { dir, name -> "$dir/$name" }
        val ids = (listOf("") + below).map { id(server, it) }
        val from = if (parent == null) 0 else ids.indexOf(parent).takeIf { it >= 0 } ?: throw FileNotFoundException("$child is not in $parent")
        return DocumentsContract.Path(if (parent == null) server else null, ids.drop(from))
    }

    fun mime(stat: Stat): String = when (stat.kind) {
        Kind.DIRECTORY -> Document.MIME_TYPE_DIR
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(stat.name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
    }

    fun row(cursor: MatrixCursor, id: String, stat: Stat, server: Server, name: String = stat.name) {
        val directory = stat.kind == Kind.DIRECTORY
        val flags = when {
            server.readOnly -> 0
            directory -> Document.FLAG_DIR_SUPPORTS_CREATE or EDIT
            else -> Document.FLAG_SUPPORTS_WRITE or EDIT
        }
        val mime = mime(stat)
        val thumbnail = if (Thumbnails.supported(mime)) Document.FLAG_SUPPORTS_THUMBNAIL else 0
        cursor.newRow().add(Document.COLUMN_DOCUMENT_ID, id).add(Document.COLUMN_DISPLAY_NAME, name)
            .add(Document.COLUMN_MIME_TYPE, mime).add(Document.COLUMN_SIZE, stat.size.toLong())
            .add(Document.COLUMN_LAST_MODIFIED, stat.modified).add(Document.COLUMN_FLAGS, flags or thumbnail)
    }
}
