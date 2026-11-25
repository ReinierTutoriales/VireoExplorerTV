package com.reiniertutoriales.vireoexplorertv

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_EXPORT_BYTES = 1024L * 1024 * 1024
private const val EXPORT_RETENTION_MS = 60L * 60 * 1000
private val stagingLock = Mutex()

class ExternalFileProvider : FileProvider()

internal data class ExternalFile(val uri: Uri, val stagingFolder: File? = null) {
    suspend fun discard() = withContext(Dispatchers.IO) {
        stagingFolder?.deleteRecursively()
    }
}

internal suspend fun prepareExternalFile(
    context: Context, entry: Entry, directRoots: List<File>
): ExternalFile {
    if (entry.uri.scheme == "content") return ExternalFile(entry.uri)
    check(entry.uri.scheme == "file" && !entry.directory && validName(entry.name))
    var prepared: ExternalFile? = null
    try {
        return withContext(Dispatchers.IO) {
            stagingLock.withLock {
                val source = File(requireNotNull(entry.uri.path)).canonicalFile
                val roots = directRoots + listOfNotNull(primaryDirectStorageDir())
                check(
                    hasDirectStorageAccess(context) && source.isFile && source.canRead() &&
                        roots.any { source.path.startsWith(it.canonicalPath.trimEnd(File.separatorChar) + File.separator) }
                ) { context.getString(R.string.explorer_export_failed) }
                stageExternalFile(context, source, entry.name).also { prepared = it }
            }
        }
    } catch (e: CancellationException) {
        withContext(NonCancellable) { prepared?.discard() }
        throw e
    }
}

private fun CoroutineScope.stageExternalFile(context: Context, source: File, displayName: String): ExternalFile {
    val staging = File(context.cacheDir, "external-files")
    check(staging.isDirectory || staging.mkdirs()) { context.getString(R.string.explorer_export_failed) }
    val deadline = System.currentTimeMillis() - EXPORT_RETENTION_MS
    val previous = staging.listFiles().orEmpty()
    previous.filter { it.lastModified() < deadline }.forEach { it.deleteRecursively() }
    val inUse = staging.listFiles().orEmpty().sumOf { folder ->
        folder.listFiles().orEmpty().sumOf { it.length() }
    }
    val available = minOf(MAX_EXPORT_BYTES - inUse, staging.usableSpace)
    check(available > 0 && source.length() <= available) { context.getString(R.string.explorer_export_space) }
    val folder = File.createTempFile("share-", null, staging)
    try {
        check(folder.delete() && folder.mkdir()) { context.getString(R.string.explorer_export_failed) }
        val target = File(folder, displayName)
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(128 * 1024)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    check(total <= available) { context.getString(R.string.explorer_export_space) }
                    output.write(buffer, 0, count)
                }
            }
        }
        folder.setLastModified(System.currentTimeMillis())
        return ExternalFile(FileProvider.getUriForFile(context, "${context.packageName}.files", target), folder)
    } catch (e: Exception) {
        folder.deleteRecursively()
        throw e
    }
}
