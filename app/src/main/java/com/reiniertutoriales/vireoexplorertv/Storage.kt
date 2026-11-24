package com.reiniertutoriales.vireoexplorertv

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract as D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Entry(val uri: Uri, val name: String, val mime: String, val size: Long, val flags: Int) {
    val directory get() = mime == D.Document.MIME_TYPE_DIR
}
class Storage(private val resolver: ContentResolver) {
    fun root(tree: Uri): Uri = D.buildDocumentUriUsingTree(tree, D.getTreeDocumentId(tree))
    suspend fun children(parent: Uri): List<Entry> = withContext(Dispatchers.IO) {
        val uri = D.buildChildDocumentsUriUsingTree(parent, D.getDocumentId(parent))
        val columns = arrayOf(D.Document.COLUMN_DOCUMENT_ID, D.Document.COLUMN_DISPLAY_NAME, D.Document.COLUMN_MIME_TYPE, D.Document.COLUMN_SIZE, D.Document.COLUMN_FLAGS)
        val cursor = resolver.query(uri, columns, null, null, null) ?: error("Storage unavailable")
        cursor.use { c ->
            buildList {
                while (c.moveToNext()) add(Entry(D.buildDocumentUriUsingTree(parent, c.getString(0)), c.getString(1) ?: "Unnamed", c.getString(2) ?: "application/octet-stream", if(c.isNull(3)) -1 else c.getLong(3), c.getInt(4)))
            }.sortedWith(compareByDescending<Entry> { it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }
    suspend fun create(parent: Uri, name: String) = withContext(Dispatchers.IO) {
        require(validName(name))
        checkNotNull(D.createDocument(resolver, parent, D.Document.MIME_TYPE_DIR, name))
    }
    suspend fun delete(entry: Entry) = withContext(Dispatchers.IO) {
        check(entry.flags and D.Document.FLAG_SUPPORTS_DELETE != 0)
        check(D.deleteDocument(resolver, entry.uri))
    }
}
fun validName(name: String) = name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }
