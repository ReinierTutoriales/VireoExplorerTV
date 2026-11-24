package com.reiniertutoriales.vireoexplorertv

import android.content.ContentResolver
import android.database.Cursor
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
    private fun requireGrant(uri: Uri, write: Boolean = false) {
        val treeId = D.getTreeDocumentId(uri)
        check(resolver.persistedUriPermissions.any {
            it.uri.authority == uri.authority && D.getTreeDocumentId(it.uri) == treeId &&
                it.isReadPermission && (!write || it.isWritePermission)
        }) { "Storage permission unavailable" }
    }
    fun root(tree: Uri): Uri = D.buildDocumentUriUsingTree(tree, D.getTreeDocumentId(tree))
    suspend fun children(parent: Uri): List<Entry> = withContext(Dispatchers.IO) {
        requireGrant(parent)
        val uri = D.buildChildDocumentsUriUsingTree(parent, D.getDocumentId(parent))
        val columns = arrayOf(D.Document.COLUMN_DOCUMENT_ID, D.Document.COLUMN_DISPLAY_NAME, D.Document.COLUMN_MIME_TYPE, D.Document.COLUMN_SIZE, D.Document.COLUMN_FLAGS)
        val cursor = resolver.query(uri, columns, null, null, null) ?: error("Storage unavailable")
        cursor.use { c ->
            buildList {
                while (c.moveToNext()) { coroutineContext.ensureActive(); add(Entry(D.buildDocumentUriUsingTree(parent, c.getString(0)), c.getString(1) ?: "Unnamed", c.getString(2) ?: "application/octet-stream", if(c.isNull(3)) -1 else c.getLong(3), c.getInt(4))) }
            }.sortedWith(compareByDescending<Entry> { it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }
    private inline fun <T> queryChildNames(parent: Uri, read: (Cursor, Int) -> T): T {
        requireGrant(parent)
        val uri = D.buildChildDocumentsUriUsingTree(parent, D.getDocumentId(parent))
        val projection = arrayOf(D.Document.COLUMN_DISPLAY_NAME)
        val cursor = resolver.query(uri, projection, null, null, null) ?: error("Storage unavailable")
        return cursor.use { read(it, it.getColumnIndexOrThrow(D.Document.COLUMN_DISPLAY_NAME)) }
    }

    internal suspend fun childNames(parent: Uri): List<String> = withContext(Dispatchers.IO) {
        queryChildNames(parent) { rows, displayName ->
            buildList {
                while (rows.moveToNext()) {
                    coroutineContext.ensureActive()
                    add(rows.getString(displayName) ?: "Unnamed")
                }
            }
        }
    }

    private suspend fun hasChildNamed(parent: Uri, name: String): Boolean = withContext(Dispatchers.IO) {
        queryChildNames(parent) { rows, displayName ->
            while (rows.moveToNext()) {
                coroutineContext.ensureActive()
                if ((rows.getString(displayName) ?: "Unnamed").equals(name, ignoreCase = true)) {
                    return@queryChildNames true
                }
            }
            false
        }
    }

    suspend fun createZip(entry: Entry, parent: Uri, progress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        requireGrant(entry.uri)
        requireGrant(parent, true)
        revalidate(entry)
        val name = entry.name + ".zip"
        require(validName(name))
        check(!hasChildNamed(parent, name)) { "Destination exists" }
        val plan = mutableListOf<Pair<Entry, String>>()
        val seen = HashSet<Uri>()
        suspend fun visit(item: Entry, path: String, depth: Int) {
            coroutineContext.ensureActive()
            check(depth <= 32 && plan.size < 10000 && seen.add(item.uri)) { "Archive traversal limit or repeated document" }
            zipPath(path)
            plan.add(item to path)
            if (item.directory) for (child in children(item.uri)) visit(child, path + "/" + child.name, depth + 1)
        }
        visit(entry, entry.name, 1)
        val target = checkNotNull(D.createDocument(resolver, parent, "application/zip", name))
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        try {
            java.util.zip.ZipOutputStream(checkNotNull(resolver.openOutputStream(target, "w")).buffered()).use { zip ->
                for ((item, path) in plan) {
                    coroutineContext.ensureActive()
                    revalidate(item)
                    zip.putNextEntry(java.util.zip.ZipEntry(if (item.directory) path + "/" else path))
                    if (!item.directory) {
                        var bytes = 0L
                        checkNotNull(resolver.openInputStream(item.uri)).use { input ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                bytes += n
                                total += n
                                check(total <= 4L * 1024 * 1024 * 1024) { "Archive input limit exceeded" }
                                zip.write(buffer, 0, n)
                                progress(total)
                            }
                        }
                        check(item.size < 0 || bytes == item.size) { "Source size changed" }
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            withContext(NonCancellable) { runCatching { D.deleteDocument(resolver, target) } }
            throw e
        }
    }
    suspend fun extractZip(entry: Entry, parent: Uri, progress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        requireGrant(entry.uri)
        requireGrant(parent, true)
        revalidate(entry)
        val rawStem = if (entry.name.endsWith(".zip", ignoreCase = true)) entry.name.dropLast(4) else entry.name
        val name = rawStem.ifBlank { "Extracted" }
        require(validName(name))
        check(!hasChildNamed(parent, name)) { "Destination exists" }
        val root = checkNotNull(D.createDocument(resolver, parent, D.Document.MIME_TYPE_DIR, name))
        val folders = mutableMapOf<List<String>, Uri>(emptyList<String>() to root)
        val paths = HashSet<List<String>>()
        var total = 0L
        var count = 0
        val buffer = ByteArray(64 * 1024)
        suspend fun folder(parts: List<String>): Uri {
            var current = root
            for (i in parts.indices) {
                coroutineContext.ensureActive()
                val key = parts.take(i + 1)
                current = folders[key] ?: run {
                    check(folders.size < 10000) { "Too many directories" }
                    check(!hasChildNamed(current, parts[i])) { "Archive name conflict" }
                    checkNotNull(D.createDocument(resolver, current, D.Document.MIME_TYPE_DIR, parts[i])).also { folders[key] = it }
                }
            }
            return current
        }
        try {
            java.util.zip.ZipInputStream(checkNotNull(resolver.openInputStream(entry.uri)).buffered()).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val item = zip.nextEntry ?: break
                    check(++count <= 10000) { "Too many archive entries" }
                    val parts = zipPath(item.name)
                    check(paths.add(parts)) { "Duplicate archive path" }
                    if (item.isDirectory) {
                        folder(parts)
                        check(zip.read() == -1) { "Invalid directory entry" }
                    } else {
                        val target = folder(parts.dropLast(1))
                        check(!hasChildNamed(target, parts.last())) { "Archive name conflict" }
                        val file = checkNotNull(D.createDocument(resolver, target, "application/octet-stream", parts.last()))
                        try {
                            checkNotNull(resolver.openOutputStream(file, "w")).use { output ->
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    check(total <= 4L * 1024 * 1024 * 1024) { "Extraction size limit exceeded" }
                                    output.write(buffer, 0, n)
                                    progress(total)
                                }
                            }
                        } catch (e: Exception) {
                            withContext(NonCancellable) { runCatching { D.deleteDocument(resolver, file) } }
                            throw e
                        }
                    }
                    zip.closeEntry()
                }
                check(count > 0) { "Empty or invalid ZIP archive" }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { throw java.io.IOException("Extraction incomplete; completed files may remain: " + e.localizedMessage, e) }
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
        var sampleBudget = 64L * 1024 * 1024
        var budget = 512L * 1024 * 1024
        for ((size, group) in files.filter { it.size > 0 }.distinctBy { it.uri }.groupBy { it.size }) {
            if (group.size < 2) continue
            // Stage 1: progressive 64 KiB prefix sample hash for files larger than 128 KiB using a dedicated sample budget.
            val candidateGroups = if (size > 128 * 1024L) {
                val sampleGroups = mutableMapOf<String, MutableList<Entry>>()
                val sampleSize = 64 * 1024
                for (entry in group) {
                    coroutineContext.ensureActive()
                    if (sampleSize > sampleBudget) { limited = true; continue }
                    try {
                        val (prefixHash, sampledBytes) = checkNotNull(resolver.openInputStream(entry.uri)).use { input ->
                            sampledPrefixSha256(input, sampleSize) { coroutineContext.ensureActive() }
                        }
                        sampleBudget -= sampledBytes
                        sampleGroups.getOrPut(prefixHash) { mutableListOf() }.add(entry)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { failures++ }
                }
                sampleGroups.values.filter { it.size > 1 }
            } else {
                listOf(group)
            }
            // Stage 2: full SHA-256 verification only for entries whose size and prefix sample matched.
            for (candidates in candidateGroups) {
                val hashes = mutableMapOf<String, MutableList<Entry>>()
                for (entry in candidates) {
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
                        val hash = bytesToHex(md.digest())
                        hashes.getOrPut(hash) { mutableListOf() }.add(entry)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { failures++ }
                }
                duplicates.addAll(hashes.values.filter { it.size > 1 })
            }
        }
        ScanResult(files.size, files.filter { it.size >= 0 }.sumOf { it.size }, files.sortedByDescending { it.size }.take(100), duplicates, limited, failures, (files.filter { cleanupReason(it.name) != null } + emptyFolders).distinctBy { it.uri })
    }
    private fun revalidate(entry: Entry): Entry {
        val columns = arrayOf(D.Document.COLUMN_DISPLAY_NAME, D.Document.COLUMN_SIZE, D.Document.COLUMN_FLAGS, D.Document.COLUMN_MIME_TYPE)
        val current = checkNotNull(resolver.query(entry.uri, columns, null, null, null)).use { c ->
            check(c.moveToFirst()) { "File unavailable" }
            Entry(entry.uri, c.getString(0), c.getString(3), if (c.isNull(1)) -1 else c.getLong(1), c.getInt(2))
        }
        check(current.name == entry.name && current.size == entry.size && current.mime == entry.mime) { "File changed; scan again" }
        return current
    }
    suspend fun removeDuplicate(entry: Entry, keep: Entry) = withContext(Dispatchers.IO) {
        require(entry.uri != keep.uri && entry.uri.authority == keep.uri.authority)
        require(D.getDocumentId(entry.uri) != D.getDocumentId(keep.uri))
        require(!entry.directory && !keep.directory && entry.size > 0 && entry.size == keep.size)
        val target = revalidate(entry)
        revalidate(keep)
        checkNotNull(resolver.openInputStream(target.uri)).buffered().use { first ->
            checkNotNull(resolver.openInputStream(keep.uri)).buffered().use { second ->
                check(equalStreams(first, second, entry.size) { coroutineContext.ensureActive() }) { "Files differ; scan again" }
            }
        }
        revalidate(keep)
        val checked = revalidate(target)
        coroutineContext.ensureActive()
        delete(checked)
    }
    suspend fun cleanCandidate(entry: Entry) = withContext(Dispatchers.IO) {
        val current = revalidate(entry)
        if (current.directory) check(children(current.uri).isEmpty()) { "Folder is no longer empty" }
        else check(cleanupReason(current.name) != null)
        coroutineContext.ensureActive()
        delete(current)
    }
    private suspend fun containsDirectory(root: Uri, target: Uri): Boolean {
        val queue = ArrayDeque<Uri>()
        val seen = HashSet<String>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val folder = queue.removeFirst()
            val id = D.getDocumentId(folder)
            if (id == D.getDocumentId(target)) return true
            if (!seen.add(id)) continue
            check(seen.size <= 10000) { "Cannot safely verify destination ancestry" }
            for (child in children(folder)) {
                if (child.directory) {
                    check(queue.size < 10000) { "Cannot safely verify destination ancestry" }
                    queue.add(child.uri)
                }
            }
        }
        return false
    }
    suspend fun moveDirectory(entry: Entry, sourceParent: Uri, destination: Uri): Uri = withContext(Dispatchers.IO) {
        require(entry.directory)
        requireGrant(entry.uri, true)
        requireGrant(sourceParent, true)
        requireGrant(destination, true)
        check(entry.uri.authority == destination.authority && D.getTreeDocumentId(entry.uri) == D.getTreeDocumentId(destination)) { "Folder move requires the same storage grant" }
        check(D.getDocumentId(sourceParent) != D.getDocumentId(destination)) { "Already in destination" }
        check(D.getDocumentId(entry.uri) != D.getDocumentId(destination)) { "Cannot move into itself" }
        val descendant = if (android.os.Build.VERSION.SDK_INT >= 29) D.isChildDocument(resolver, entry.uri, destination)
            else containsDirectory(entry.uri, destination)
        check(!descendant) { "Cannot move into a descendant" }
        check(revalidate(entry).flags and D.Document.FLAG_SUPPORTS_MOVE != 0) { "Provider does not support folder move" }
        check(children(sourceParent).any { it.uri == entry.uri }) { "Source folder changed" }
        check(!hasChildNamed(destination, entry.name)) { "Destination already exists" }
        coroutineContext.ensureActive()
        checkNotNull(D.moveDocument(resolver, entry.uri, sourceParent, destination)) { "Provider move failed" }
    }
    suspend fun copyDirectory(entry: Entry, parent: Uri, destinationName: String = entry.name, progress: (Long) -> Unit): Uri = withContext(Dispatchers.IO) {
        require(entry.directory && validName(entry.name) && validName(destinationName))
        requireGrant(entry.uri)
        requireGrant(parent, true)
        // Reject same-provider destination aliases and descendants before creating anything.
        val seen = HashSet<String>()
        val queue = ArrayDeque<Pair<Entry, Int>>()
        val plan = LinkedHashMap<Entry, List<Entry>>()
        queue.add(entry to 0)
        var count = 0
        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (folder, depth) = queue.removeFirst()
            check(depth <= 32 && ++count <= 10000) { "Directory copy limit exceeded" }
            check(seen.add(D.getDocumentId(folder.uri))) { "Directory cycle detected" }
            check(folder.uri.authority != parent.authority || D.getDocumentId(folder.uri) != D.getDocumentId(parent)) { "Cannot copy into source directory" }
            val items = children(folder.uri)
            check(items.size <= 10000 - count) { "Directory copy limit exceeded" }
            count += items.count { !it.directory }
            plan[folder] = items
            items.filter { it.directory }.forEach { queue.add(it to depth + 1) }
            check(queue.size <= 10000 - count) { "Directory copy limit exceeded" }
        }
        check(!hasChildNamed(parent, destinationName)) { "Destination already exists" }
        val target = create(parent, destinationName)
        val destinations = HashMap<Uri, Uri>()
        destinations[entry.uri] = target
        var total = 0L
        try {
            for ((folder, items) in plan) {
                coroutineContext.ensureActive()
                val destination = checkNotNull(destinations[folder.uri])
                for (item in items) {
                    coroutineContext.ensureActive()
                    revalidate(item)
                    if (item.directory) {
                        check(!hasChildNamed(destination, item.name))
                        destinations[item.uri] = create(destination, item.name)
                    } else {
                        transfer(item, destination, false) { progress(Math.addExact(total, it)) }
                        if (item.size >= 0) total = Math.addExact(total, item.size)
                    }
                }
            }
            target
        } catch (e: Exception) {
            // Preserve completed files; provider recursive deletion is not guaranteed safe.
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw IllegalStateException("Directory copy incomplete; destination may contain copied files", e)
        }
    }
    suspend fun transfer(entry: Entry, parent: Uri, move: Boolean, destinationName: String = entry.name, progress: (Long) -> Unit): Uri = withContext(Dispatchers.IO) {
        requireGrant(entry.uri, move)
        requireGrant(parent, true)
        require(!entry.directory && validName(entry.name) && validName(destinationName))
        if (move) check(entry.flags and D.Document.FLAG_SUPPORTS_DELETE != 0)
        check(!hasChildNamed(parent, destinationName)) { "Destination already exists" }
        val target = checkNotNull(D.createDocument(resolver, parent, entry.mime, destinationName))
        var verified = false
        try {
            check(target != entry.uri) { "Destination equals source" }
            val expectedSize = entry.size.takeIf { it >= 0 }
            val original = checkNotNull(resolver.openInputStream(entry.uri)).use { input ->
                checkNotNull(resolver.openOutputStream(target, "w")).use { output ->
                    transferDigest(input, output, expectedSize, progress)
                }
            }
            suspend fun verify(uri: Uri): TransferDigest =
                checkNotNull(resolver.openInputStream(uri)).use { transferDigest(it, expected = original.bytes) }
            check(original.matches(verify(target))) { "Destination verification failed" }
            if (move) check(original.matches(verify(entry.uri))) { "Source changed during transfer" }
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
    suspend fun availableBytes(parent: Uri): Long? = withContext(Dispatchers.IO) {
        requireGrant(parent)
        val rootsUri = D.buildRootsUri(checkNotNull(parent.authority))
        // Root IDs are not necessarily document IDs. Match the root document ID only.
        val treeId = D.getTreeDocumentId(parent)
        val columns = arrayOf(D.Root.COLUMN_DOCUMENT_ID, D.Root.COLUMN_AVAILABLE_BYTES)
        try {
            resolver.query(rootsUri, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(0) == treeId && !cursor.isNull(1)) return@withContext cursor.getLong(1).takeIf { it >= 0 }
                }
                null
            }
        } catch (_: SecurityException) { null }
        catch (_: IllegalArgumentException) { null }
        catch (_: UnsupportedOperationException) { null }
    }
    suspend fun create(parent: Uri, name: String) = withContext(Dispatchers.IO) {
        requireGrant(parent, true)
        require(validName(name))
        checkNotNull(D.createDocument(resolver, parent, D.Document.MIME_TYPE_DIR, name))
    }
    suspend fun rename(entry: Entry, name: String) = withContext(Dispatchers.IO) {
        requireGrant(entry.uri, true)
        require(validName(name))
        check(revalidate(entry).flags and D.Document.FLAG_SUPPORTS_RENAME != 0)
        checkNotNull(D.renameDocument(resolver, entry.uri, name))
    }
    suspend fun delete(entry: Entry) = withContext(Dispatchers.IO) {
        requireGrant(entry.uri, true)
        val current = revalidate(entry)
        check(current.flags and D.Document.FLAG_SUPPORTS_DELETE != 0)
        if (current.directory) check(children(current.uri).isEmpty()) { "Only empty folders can be deleted" }
        coroutineContext.ensureActive()
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

private val HEX_CHARS = "0123456789abcdef".toCharArray()

internal fun bytesToHex(bytes: ByteArray): String {
    val out = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
        val v = bytes[i].toInt() and 0xFF
        out[i * 2] = HEX_CHARS[v ushr 4]
        out[i * 2 + 1] = HEX_CHARS[v and 0x0F]
    }
    return String(out)
}

internal inline fun sampledPrefixSha256(input: java.io.InputStream, maxBytes: Int = 64 * 1024, checkCancellation: () -> Unit): Pair<String, Int> {
    require(maxBytes > 0)
    val md = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(minOf(maxBytes, 16 * 1024))
    var remaining = maxBytes
    var totalRead = 0
    while (remaining > 0) {
        checkCancellation()
        val n = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (n <= 0) break
        md.update(buffer, 0, n)
        remaining -= n
        totalRead += n
    }
    return bytesToHex(md.digest()) to totalRead
}

internal inline fun equalStreams(a: java.io.InputStream, b: java.io.InputStream, expected: Long, checkCancellation: () -> Unit): Boolean {
    var remaining = expected
    val left = ByteArray(32768)
    val right = ByteArray(32768)
    while (remaining > 0) {
        checkCancellation()
        val count = minOf(remaining, left.size.toLong()).toInt()
        var offset = 0
        while (offset < count) {
            val n = a.read(left, offset, count - offset)
            if (n <= 0) return false
            offset += n
        }
        offset = 0
        while (offset < count) {
            val n = b.read(right, offset, count - offset)
            if (n <= 0) return false
            offset += n
        }
        for (i in 0 until count) if (left[i] != right[i]) return false
        remaining -= count
    }
    return a.read() == -1 && b.read() == -1
}
