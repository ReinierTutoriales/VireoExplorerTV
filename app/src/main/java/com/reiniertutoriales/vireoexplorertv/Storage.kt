package com.reiniertutoriales.vireoexplorertv

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract as D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

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
    suspend fun scan(root: Uri): ScanResult = withContext(Dispatchers.IO) {
        val queue = ArrayDeque<Pair<Uri, Int>>()
        val seen = HashSet<Uri>()
        val files = ArrayList<Entry>()
        val emptyFolders = ArrayList<Entry>()
        val folders = HashMap<Uri, Entry>()
        queue.add(root to 0)
        var limited = false
        var failures = 0
        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (folder, depth) = queue.removeFirst()
            if (!seen.add(folder)) continue
            if (seen.size > 2000 || files.size >= 20000) { limited = true; break }
            val entries = try { children(folder) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { failures++; continue }
            if (entries.isEmpty()) folders[folder]?.let { emptyFolders.add(it) }
            for (entry in entries) {
                coroutineContext.ensureActive()
                if (entry.directory) {
                    if (depth < 32 && queue.size < 2000) { folders[entry.uri] = entry; queue.add(entry.uri to depth + 1) } else limited = true
                } else if (files.size < 20000) files.add(entry) else limited = true
            }
        }
        val duplicates = ArrayList<List<Entry>>()
        val buffer = ByteArray(128 * 1024)
        var budget = 512L * 1024 * 1024
        for ((size, group) in files.filter { it.size > 0 }.distinctBy { it.uri }.groupBy { it.size }) {
            if (group.size < 2) continue
            val hashes = mutableMapOf<String, MutableList<Entry>>()
            for (entry in group) {
                coroutineContext.ensureActive()
                if (size > budget) { limited = true; continue }
                try {
                    val md = MessageDigest.getInstance("SHA-256")
                    var read = 0L
                    checkNotNull(resolver.openInputStream(entry.uri)).use { input ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            read += n
                            budget -= n
                            check(budget >= 0 && read <= size)
                            md.update(buffer, 0, n)
                        }
                    }
                    check(read == size)
                    val hash = android.util.Base64.encodeToString(md.digest(), android.util.Base64.NO_WRAP)
                    hashes.getOrPut(hash) { mutableListOf() }.add(entry)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { failures++ }
            }
            duplicates.addAll(hashes.values.filter { it.size > 1 })
        }
        ScanResult(files.size, files.filter { it.size >= 0 }.sumOf { it.size }, files.sortedByDescending { it.size }.take(100), duplicates, limited, failures, (files.filter { cleanupReason(it.name) != null } + emptyFolders).distinctBy { it.uri })
    }
    suspend fun cleanCandidate(entry: Entry) = withContext(Dispatchers.IO) {
        val columns = arrayOf(D.Document.COLUMN_DISPLAY_NAME, D.Document.COLUMN_SIZE, D.Document.COLUMN_FLAGS, D.Document.COLUMN_MIME_TYPE)
        val current = checkNotNull(resolver.query(entry.uri, columns, null, null, null)).use { c ->
            check(c.moveToFirst()) { "File unavailable" }
            Entry(entry.uri, c.getString(0), c.getString(3), if (c.isNull(1)) -1 else c.getLong(1), c.getInt(2))
        }
        check(current.name == entry.name && current.size == entry.size && current.mime == entry.mime) { "File changed; scan again" }
        if (current.directory) check(children(current.uri).isEmpty()) { "Folder is no longer empty" }
        else check(cleanupReason(current.name) != null)
        coroutineContext.ensureActive()
        delete(current)
    }
    suspend fun transfer(entry: Entry, parent: Uri, move: Boolean, progress: (Long) -> Unit): Uri = withContext(Dispatchers.IO) {
        require(!entry.directory && validName(entry.name))
        if (move) check(entry.flags and D.Document.FLAG_SUPPORTS_DELETE != 0)
        check(children(parent).none { it.name.equals(entry.name, ignoreCase = true) }) { "Destination already exists" }
        val target = checkNotNull(D.createDocument(resolver, parent, entry.mime, entry.name))
        var verified = false
        try {
            check(target != entry.uri) { "Destination equals source" }
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(128 * 1024)
            var total = 0L
            checkNotNull(resolver.openInputStream(entry.uri)).use { input ->
                checkNotNull(resolver.openOutputStream(target, "w")).use { output ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        total += count
                        progress(total)
                    }
                }
            }
            check(entry.size < 0 || total == entry.size) { "Source size changed" }
            val expected = digest.digest()
            suspend fun hash(uri: Uri): ByteArray {
                val md = MessageDigest.getInstance("SHA-256")
                checkNotNull(resolver.openInputStream(uri)).use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        md.update(buffer, 0, count)
                    }
                }
                return md.digest()
            }
            check(expected.contentEquals(hash(target))) { "Destination verification failed" }
            if (move) check(expected.contentEquals(hash(entry.uri))) { "Source changed during transfer" }
            coroutineContext.ensureActive()
            verified = true
            if (move) delete(entry)
            target
        } finally {
            if (!verified && target != entry.uri) withContext(NonCancellable) {
                check(D.deleteDocument(resolver, target)) { "Incomplete destination could not be removed" }
            }
        }
    }
    suspend fun create(parent: Uri, name: String) = withContext(Dispatchers.IO) {
        require(validName(name))
        checkNotNull(D.createDocument(resolver, parent, D.Document.MIME_TYPE_DIR, name))
    }
    suspend fun rename(entry: Entry, name: String) = withContext(Dispatchers.IO) {
        require(validName(name))
        check(entry.flags and D.Document.FLAG_SUPPORTS_RENAME != 0)
        checkNotNull(D.renameDocument(resolver, entry.uri, name))
    }
    suspend fun delete(entry: Entry) = withContext(Dispatchers.IO) {
        check(entry.flags and D.Document.FLAG_SUPPORTS_DELETE != 0)
        check(D.deleteDocument(resolver, entry.uri))
    }
}
fun validName(name: String) = name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }

data class ScanResult(val count: Int, val bytes: Long, val largest: List<Entry>, val possibleDuplicates: List<List<Entry>>, val limited: Boolean, val failures: Int, val candidates: List<Entry>)

internal fun cleanupReason(name: String): String? = when (name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)) {
    "tmp", "temp" -> "Possible temporary file"
    "part", "partial", "crdownload" -> "Possible incomplete download"
    "apk" -> "APK installer"
    else -> null
}
