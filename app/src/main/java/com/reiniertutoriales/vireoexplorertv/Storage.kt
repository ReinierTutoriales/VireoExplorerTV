package com.reiniertutoriales.vireoexplorertv

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.StatFs
import android.provider.DocumentsContract as D
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.coroutineContext

data class Entry(val uri: Uri, val name: String, val mime: String, val size: Long, val flags: Int) {
    val directory get() = mime == D.Document.MIME_TYPE_DIR
}

class Storage(private val resolver: ContentResolver) {
    internal fun isFileUri(uri: Uri): Boolean = uri.scheme == "file"

    internal fun toFile(uri: Uri): File = File(requireNotNull(uri.path) { "Missing file path" })

    internal fun entryFromFile(file: File): Entry =
        entryFromFile(file, file.parentFile?.canWrite() == true)

    private fun entryFromFile(file: File, parentWritable: Boolean, includeWriteFlags: Boolean = true): Entry {
        val isDirectory = file.isDirectory
        val mime = if (isDirectory) D.Document.MIME_TYPE_DIR else inferFileMime(file.name)
        val size = if (isDirectory) -1L else file.length()
        var flags = if (parentWritable) {
            D.Document.FLAG_SUPPORTS_RENAME or D.Document.FLAG_SUPPORTS_DELETE or D.Document.FLAG_SUPPORTS_MOVE
        } else 0
        if (includeWriteFlags && file.canWrite()) {
            flags = flags or if (isDirectory) D.Document.FLAG_DIR_SUPPORTS_CREATE else D.Document.FLAG_SUPPORTS_WRITE
        }
        if (mime.startsWith("image/") || mime.startsWith("video/")) {
            flags = flags or D.Document.FLAG_SUPPORTS_THUMBNAIL
        }
        return Entry(Uri.fromFile(file), file.name.ifEmpty { file.path }, mime, size, flags)
    }

    private fun requireGrant(uri: Uri, write: Boolean = false) {
        if (isFileUri(uri)) {
            val f = toFile(uri)
            check(f.exists()) { "Storage path unavailable" }
            if (write) check(f.canWrite() || f.parentFile?.canWrite() == true) { "Storage write permission unavailable" }
            else check(f.canRead()) { "Storage read permission unavailable" }
            return
        }
        val treeId = D.getTreeDocumentId(uri)
        check(resolver.persistedUriPermissions.any {
            it.uri.authority == uri.authority && D.getTreeDocumentId(it.uri) == treeId &&
                it.isReadPermission && (!write || it.isWritePermission)
        }) { "Storage permission unavailable" }
    }

    internal suspend fun canCreateChildren(parent: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            requireGrant(parent, write = true)
            if (isFileUri(parent)) {
                val folder = toFile(parent)
                return@withContext folder.isDirectory && folder.canWrite()
            }
            val cursor = resolver.query(parent, arrayOf(D.Document.COLUMN_FLAGS), null, null, null)
            cursor?.use { it.moveToFirst() && (it.getInt(0) and D.Document.FLAG_DIR_SUPPORTS_CREATE != 0) } == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    fun root(tree: Uri): Uri = if (isFileUri(tree)) tree else D.buildDocumentUriUsingTree(tree, D.getTreeDocumentId(tree))

    internal fun openInput(uri: Uri): InputStream =
        if (isFileUri(uri)) FileInputStream(toFile(uri))
        else checkNotNull(resolver.openInputStream(uri)) { "Cannot open input stream" }

    private fun openOutput(uri: Uri): OutputStream =
        if (isFileUri(uri)) FileOutputStream(toFile(uri), false)
        else checkNotNull(resolver.openOutputStream(uri, "w")) { "Cannot open output stream" }

    private fun createChildDocument(parent: Uri, mime: String, name: String): Uri {
        require(validName(name))
        if (isFileUri(parent)) {
            val dir = toFile(parent)
            check(dir.isDirectory && dir.canWrite()) { "Parent directory is not writable" }
            val target = File(dir, name)
            check(!target.exists()) { "Destination already exists" }
            if (mime == D.Document.MIME_TYPE_DIR) {
                check(target.mkdir()) { "Could not create folder" }
            } else {
                check(target.createNewFile()) { "Could not create file" }
            }
            return Uri.fromFile(target)
        }
        return checkNotNull(D.createDocument(resolver, parent, mime, name))
    }

    private fun deleteDocumentRaw(uri: Uri): Boolean =
        if (isFileUri(uri)) toFile(uri).delete()
        else D.deleteDocument(resolver, uri)

    suspend fun children(parent: Uri): List<Entry> = listChildren(parent, sorted = true)

    private suspend fun listChildren(
        parent: Uri,
        sorted: Boolean,
        includeWriteFlags: Boolean = true
    ): List<Entry> = withContext(Dispatchers.IO) {
        requireGrant(parent)
        if (isFileUri(parent)) {
            val dir = toFile(parent)
            val list = dir.listFiles() ?: error("Cannot read folder")
            val parentWritable = includeWriteFlags && dir.canWrite()
            return@withContext buildList(list.size) {
                for (f in list) {
                    coroutineContext.ensureActive()
                    add(entryFromFile(f, parentWritable, includeWriteFlags))
                }
            }.let { if (sorted) it.sortedWith(defaultEntryComparator) else it }
        }
        val uri = D.buildChildDocumentsUriUsingTree(parent, D.getDocumentId(parent))
        val columns = arrayOf(
            D.Document.COLUMN_DOCUMENT_ID,
            D.Document.COLUMN_DISPLAY_NAME,
            D.Document.COLUMN_MIME_TYPE,
            D.Document.COLUMN_SIZE,
            D.Document.COLUMN_FLAGS
        )
        val cursor = resolver.query(uri, columns, null, null, null) ?: error("Storage unavailable")
        cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    coroutineContext.ensureActive()
                    add(
                        Entry(
                            D.buildDocumentUriUsingTree(parent, c.getString(0)),
                            c.getString(1) ?: "Unnamed",
                            c.getString(2) ?: "application/octet-stream",
                            if (c.isNull(3)) -1 else c.getLong(3),
                            c.getInt(4)
                        )
                    )
                }
            }.let { if (sorted) it.sortedWith(defaultEntryComparator) else it }
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
        if (isFileUri(parent)) {
            requireGrant(parent)
            return@withContext toFile(parent).list()?.toList().orEmpty()
        }
        queryChildNames(parent) { rows, displayName ->
            buildList {
                while (rows.moveToNext()) {
                    coroutineContext.ensureActive()
                    rows.getString(displayName)?.let { add(it) }
                }
            }
        }
    }

    private suspend fun hasChildNamed(parent: Uri, name: String): Boolean = withContext(Dispatchers.IO) {
        if (isFileUri(parent)) {
            requireGrant(parent)
            val dir = toFile(parent)
            if (File(dir, name).exists()) return@withContext true
            val names = dir.list() ?: return@withContext false
            for (existing in names) {
                coroutineContext.ensureActive()
                if (existing.equals(name, ignoreCase = true)) return@withContext true
            }
            return@withContext false
        }
        queryChildNames(parent) { rows, displayName ->
            while (rows.moveToNext()) {
                coroutineContext.ensureActive()
                if (rows.getString(displayName)?.equals(name, ignoreCase = true) == true) {
                    return@queryChildNames true
                }
            }
            false
        }
    }

    internal fun categorySearch(roots: List<Entry>, category: FileCategory?, query: String): SearchTraversal<Entry> =
        SearchTraversal(
            roots = roots,
            identity = { item ->
                if (isFileUri(item.uri)) {
                    runCatching { toFile(item.uri).canonicalPath }.getOrElse { item.uri.toString() }
                } else {
                    "${item.uri.authority}:${D.getDocumentId(item.uri)}"
                }
            },
            children = { listChildren(it.uri, sorted = false, includeWriteFlags = false) },
            matches = { item ->
                val queryMatch = query.isEmpty() || item.name.contains(query, ignoreCase = true)
                queryMatch && if (item.directory) category == null && query.isNotEmpty()
                else category == null || category.matches(item)
            },
            descend = { it.directory && !it.name.startsWith('.') && !it.name.equals("Android", ignoreCase = true) }
        )

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
        val target = createChildDocument(parent, "application/zip", name)
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        try {
            java.util.zip.ZipOutputStream(openOutput(target).buffered()).use { zip ->
                for ((item, path) in plan) {
                    coroutineContext.ensureActive()
                    revalidate(item)
                    zip.putNextEntry(java.util.zip.ZipEntry(if (item.directory) path + "/" else path))
                    if (!item.directory) {
                        var bytes = 0L
                        openInput(item.uri).use { input ->
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
            withContext(NonCancellable) { runCatching { deleteDocumentRaw(target) } }
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
        val root = createChildDocument(parent, D.Document.MIME_TYPE_DIR, name)
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
                    createChildDocument(current, D.Document.MIME_TYPE_DIR, parts[i]).also { folders[key] = it }
                }
            }
            return current
        }
        try {
            java.util.zip.ZipInputStream(openInput(entry.uri).buffered()).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val item = zip.nextEntry ?: break
                    check(++count <= 10000) { "Too many archive entries" }
                    val parts = zipPath(item.name)
                    check(paths.add(parts.map { it.lowercase(Locale.ROOT) })) { "Duplicate archive path" }
                    if (item.isDirectory) {
                        folder(parts)
                        check(zip.read() == -1) { "Invalid directory entry" }
                    } else {
                        val target = folder(parts.dropLast(1))
                        check(!hasChildNamed(target, parts.last())) { "Archive name conflict" }
                        val file = createChildDocument(target, "application/octet-stream", parts.last())
                        try {
                            openOutput(file).use { output ->
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
                            withContext(NonCancellable) { runCatching { deleteDocumentRaw(file) } }
                            throw e
                        }
                    }
                    zip.closeEntry()
                }
                check(count > 0) { "Empty or invalid ZIP archive" }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw java.io.IOException("Extraction incomplete; completed files may remain: " + e.localizedMessage, e) }
    }

    suspend fun scan(root: Uri): ScanResult = withContext(Dispatchers.IO) {
        val queue = ArrayDeque<Pair<Uri, Int>>()
        val seen = HashSet<String>()
        val files = ArrayList<Entry>()
        val emptyFolders = ArrayList<Entry>()
        val folders = HashMap<Uri, Entry>()
        queue.add(root to 0)
        var limited = false
        var failures = 0
        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (folder, depth) = queue.removeFirst()
            val seenKey = if (isFileUri(folder)) {
                runCatching { toFile(folder).canonicalPath }.getOrElse { folder.toString() }
            } else folder.toString()
            if (!seen.add(seenKey)) continue
            if (seen.size > 2000 || files.size >= 20000) { limited = true; break }
            val entries = try { listChildren(folder, sorted = false) } catch (e: CancellationException) { throw e } catch (_: Exception) { failures++; continue }
            if (entries.isEmpty()) folders[folder]?.let { emptyFolders.add(it) }
            for (entry in entries) {
                coroutineContext.ensureActive()
                if (entry.directory) {
                    if (entry.name.equals("Android", ignoreCase = true) && depth == 0) continue
                    if (depth < 32 && queue.size < 2000) { folders[entry.uri] = entry; queue.add(entry.uri to depth + 1) } else limited = true
                } else if (files.size < 20000) files.add(entry) else limited = true
            }
        }
        val duplicates = ArrayList<List<Entry>>()
        val buffer = ByteArray(128 * 1024)
        var sampleBudget = 64L * 1024 * 1024
        var budget = 512L * 1024 * 1024
        for ((size, group) in files.filter { it.size > 0 }.distinctBy { it.uri }.groupBy { it.size }.toSortedMap()) {
            if (group.size < 2) continue
            val candidateGroups = if (size > 128 * 1024L) {
                val sampleGroups = mutableMapOf<String, MutableList<Entry>>()
                val sampleSize = 64 * 1024
                for (entry in group) {
                    coroutineContext.ensureActive()
                    if (sampleSize > sampleBudget) { limited = true; continue }
                    sampleBudget -= sampleSize
                    try {
                        val (prefixHash, sampledBytes) = openInput(entry.uri).use { input ->
                            sampledPrefixSha256(input, sampleSize, buffer) { coroutineContext.ensureActive() }
                        }
                        check(sampledBytes == sampleSize) { "File changed during scan" }
                        sampleGroups.getOrPut(prefixHash) { mutableListOf() }.add(entry)
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { failures++ }
                }
                sampleGroups.values.filter { it.size > 1 }
            } else {
                listOf(group)
            }
            for (candidates in candidateGroups) {
                val hashes = mutableMapOf<String, MutableList<Entry>>()
                for (entry in candidates) {
                    coroutineContext.ensureActive()
                    if (size > budget) { limited = true; continue }
                    try {
                        val md = MessageDigest.getInstance("SHA-256")
                        var read = 0L
                        openInput(entry.uri).use { input ->
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
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { failures++ }
                }
                duplicates.addAll(hashes.values.filter { it.size > 1 })
            }
        }
        ScanResult(
            files.size,
            files.filter { it.size >= 0 }.sumOf { it.size },
            files.sortedByDescending { it.size }.take(100),
            duplicates,
            limited,
            failures,
            (files.filter { cleanupReason(it.name) != null } + emptyFolders).distinctBy { it.uri }
        )
    }

    private fun revalidate(entry: Entry): Entry {
        if (isFileUri(entry.uri)) {
            val file = toFile(entry.uri)
            check(file.exists()) { "File unavailable" }
            val current = entryFromFile(file)
            check(current.name == entry.name && current.size == entry.size && current.directory == entry.directory) {
                "File changed; scan again"
            }
            return current
        }
        val columns = arrayOf(D.Document.COLUMN_DISPLAY_NAME, D.Document.COLUMN_SIZE, D.Document.COLUMN_FLAGS, D.Document.COLUMN_MIME_TYPE)
        val current = checkNotNull(resolver.query(entry.uri, columns, null, null, null)).use { c ->
            check(c.moveToFirst()) { "File unavailable" }
            Entry(entry.uri, c.getString(0), c.getString(3), if (c.isNull(1)) -1 else c.getLong(1), c.getInt(2))
        }
        check(current.name == entry.name && current.size == entry.size && current.mime == entry.mime) { "File changed; scan again" }
        return current
    }

    private fun documentIdentity(uri: Uri): String =
        if (isFileUri(uri)) runCatching { toFile(uri).canonicalPath }.getOrElse { toFile(uri).absolutePath }
        else D.getDocumentId(uri)

    suspend fun removeDuplicate(entry: Entry, keep: Entry) = withContext(Dispatchers.IO) {
        require(entry.uri != keep.uri && entry.uri.scheme == keep.uri.scheme)
        require(documentIdentity(entry.uri) != documentIdentity(keep.uri))
        require(!entry.directory && !keep.directory && entry.size > 0 && entry.size == keep.size)
        val target = revalidate(entry)
        revalidate(keep)
        openInput(target.uri).buffered().use { first ->
            openInput(keep.uri).buffered().use { second ->
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

    private fun isSameOrDescendantFile(ancestor: File, candidate: File): Boolean {
        val ancestorPath = ancestor.canonicalPath
        val candidatePath = candidate.canonicalPath
        if (candidatePath == ancestorPath) return true
        val prefix = if (ancestorPath.endsWith(File.separatorChar)) ancestorPath else ancestorPath + File.separatorChar
        return candidatePath.startsWith(prefix)
    }

    private suspend fun containsDirectory(root: Uri, target: Uri): Boolean {
        if (isFileUri(root) && isFileUri(target)) {
            return isSameOrDescendantFile(toFile(root), toFile(target))
        }
        val queue = ArrayDeque<Uri>()
        val seen = HashSet<String>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val folder = queue.removeFirst()
            val id = documentIdentity(folder)
            if (id == documentIdentity(target)) return true
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
        if (isFileUri(entry.uri) && isFileUri(destination)) {
            val current = revalidate(entry)
            check(current.flags and D.Document.FLAG_SUPPORTS_MOVE != 0) { "Folder move permission unavailable" }
            val srcFile = toFile(entry.uri)
            check(srcFile.parentFile?.canonicalFile == toFile(sourceParent).canonicalFile) { "Source folder changed" }
            val dstDir = toFile(destination)
            check(srcFile.parentFile?.canonicalFile != dstDir.canonicalFile) { "Already in destination" }
            check(!isSameOrDescendantFile(srcFile, dstDir)) { "Cannot move into itself or descendant" }
            val target = File(dstDir, entry.name)
            check(!target.exists()) { "Destination already exists" }
            coroutineContext.ensureActive()
            check(srcFile.renameTo(target)) {
                "This folder cannot be moved directly. Copy it to the destination and review the copy before removing the original."
            }
            return@withContext Uri.fromFile(target)
        }
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
        val seen = HashSet<String>()
        val queue = ArrayDeque<Pair<Entry, Int>>()
        val plan = LinkedHashMap<Entry, List<Entry>>()
        queue.add(entry to 0)
        var count = 0
        val parentId = documentIdentity(parent)
        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (folder, depth) = queue.removeFirst()
            check(depth <= 32 && ++count <= 10000) { "Directory copy limit exceeded" }
            val folderId = documentIdentity(folder.uri)
            check(seen.add(folderId)) { "Directory cycle detected" }
            check(folder.uri.scheme != parent.scheme || folder.uri.authority != parent.authority || folderId != parentId) { "Cannot copy into source directory" }
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
            if (e is CancellationException) throw e
            throw IllegalStateException("Directory copy incomplete; destination may contain copied files", e)
        }
    }

    suspend fun transfer(entry: Entry, parent: Uri, move: Boolean, destinationName: String = entry.name, progress: (Long) -> Unit): Uri = withContext(Dispatchers.IO) {
        requireGrant(entry.uri, move)
        requireGrant(parent, true)
        require(!entry.directory && validName(entry.name) && validName(destinationName))
        val current = revalidate(entry)
        if (move) check(current.flags and D.Document.FLAG_SUPPORTS_DELETE != 0)
        check(!hasChildNamed(parent, destinationName)) { "Destination already exists" }
        coroutineContext.ensureActive()
        if (move && isFileUri(entry.uri) && isFileUri(parent)) {
            val src = toFile(entry.uri)
            val dst = File(toFile(parent), destinationName)
            check(!dst.exists()) { "Destination already exists" }
            if (src.renameTo(dst)) {
                progress(dst.length())
                return@withContext Uri.fromFile(dst)
            }
        }
        val target = createChildDocument(parent, entry.mime, destinationName)
        var verified = false
        try {
            check(target != entry.uri) { "Destination equals source" }
            val expectedSize = entry.size.takeIf { it >= 0 }
            val original = openInput(entry.uri).use { input ->
                openOutput(target).use { output ->
                    transferDigest(input, output, expectedSize, progress)
                }
            }
            suspend fun verify(uri: Uri): TransferDigest =
                openInput(uri).use { transferDigest(it, expected = original.bytes) }
            check(original.matches(verify(target))) { "Destination verification failed" }
            if (move) check(original.matches(verify(entry.uri))) { "Source changed during transfer" }
            coroutineContext.ensureActive()
            verified = true
            if (move) delete(entry)
            target
        } finally {
            if (!verified && target != entry.uri) withContext(NonCancellable) {
                check(deleteDocumentRaw(target)) { "Incomplete destination could not be removed" }
            }
        }
    }

    suspend fun availableBytes(parent: Uri): Long? = withContext(Dispatchers.IO) {
        requireGrant(parent)
        if (isFileUri(parent)) {
            return@withContext try {
                StatFs(toFile(parent).absolutePath).availableBytes.takeIf { it >= 0 }
            } catch (_: Exception) {
                null
            }
        }
        val rootsUri = D.buildRootsUri(checkNotNull(parent.authority))
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

    suspend fun create(parent: Uri, name: String): Uri = withContext(Dispatchers.IO) {
        requireGrant(parent, true)
        require(validName(name))
        createChildDocument(parent, D.Document.MIME_TYPE_DIR, name)
    }

    suspend fun rename(entry: Entry, name: String): Uri = withContext(Dispatchers.IO) {
        requireGrant(entry.uri, true)
        require(validName(name))
        check(revalidate(entry).flags and D.Document.FLAG_SUPPORTS_RENAME != 0)
        if (isFileUri(entry.uri)) {
            val src = toFile(entry.uri)
            val parent = checkNotNull(src.parentFile) { "Cannot rename root directory" }
            val dst = File(parent, name)
            check(!dst.exists()) { "Destination already exists" }
            check(src.renameTo(dst)) { "Rename failed" }
            return@withContext Uri.fromFile(dst)
        }
        checkNotNull(D.renameDocument(resolver, entry.uri, name))
    }

    suspend fun delete(entry: Entry) = withContext(Dispatchers.IO) {
        requireGrant(entry.uri, true)
        val current = revalidate(entry)
        check(current.flags and D.Document.FLAG_SUPPORTS_DELETE != 0)
        if (current.directory) check(children(current.uri).isEmpty()) { "Only empty folders can be deleted" }
        coroutineContext.ensureActive()
        check(deleteDocumentRaw(entry.uri))
    }
}

fun validName(name: String) = name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }

data class ScanResult(val count: Int, val bytes: Long, val largest: List<Entry>, val possibleDuplicates: List<List<Entry>>, val limited: Boolean, val failures: Int, val candidates: List<Entry>)

internal fun cleanupReason(name: String): String? {
    val lower = name.lowercase(Locale.ROOT)
    if (lower.startsWith(".trashed-") || lower.startsWith("~$") || lower == "thumbs.db" || lower == ".ds_store") {
        return "Temporary or trash file"
    }
    return when (lower.substringAfterLast('.', "")) {
        "tmp", "temp", "cache", "bak", "old", "orig" -> "Possible temporary file"
        "log", "dmp", "crash" -> "Diagnostic or log file"
        "part", "partial", "crdownload", "download" -> "Possible incomplete download"
        "apk", "apks", "xapk" -> "APK installer"
        else -> null
    }
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

internal inline fun sampledPrefixSha256(
    input: InputStream,
    maxBytes: Int = 64 * 1024,
    buffer: ByteArray = ByteArray(16 * 1024),
    checkCancellation: () -> Unit
): Pair<String, Int> {
    require(maxBytes > 0 && buffer.isNotEmpty())
    val md = MessageDigest.getInstance("SHA-256")
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

internal inline fun equalStreams(a: InputStream, b: InputStream, expected: Long, checkCancellation: () -> Unit): Boolean {
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
