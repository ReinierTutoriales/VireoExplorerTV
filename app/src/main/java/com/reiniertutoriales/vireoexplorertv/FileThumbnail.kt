package com.reiniertutoriales.vireoexplorertv

import android.graphics.Bitmap
import android.os.Build
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
private val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(4)

private val thumbnailCache: LruCache<String, Bitmap> by lazy {
    val maxBytes = (Runtime.getRuntime().maxMemory() / 16L).coerceIn(4L * 1024 * 1024, 32L * 1024 * 1024).toInt()
    object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }
}

internal fun thumbnailCacheKey(entry: Entry, revision: Int): String = "${entry.uri}:${entry.size}:$revision"

internal fun trimThumbnailCache(level: Int) {
    if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
        thumbnailCache.evictAll()
    } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
        thumbnailCache.trimToSize(thumbnailCache.maxSize() / 2)
    }
}

@Composable
internal fun FileThumbnail(entry: Entry, revision: Int) {
    if (Build.VERSION.SDK_INT < 29 || entry.directory ||
        entry.flags and DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL == 0) {
        FileTypeIcon(entry)
        return
    }
    val resolver = LocalContext.current.contentResolver
    val key = remember(entry.uri, entry.size, revision) { thumbnailCacheKey(entry, revision) }
    var bitmap by remember(key) { mutableStateOf(thumbnailCache.get(key)) }
    val scope = rememberCoroutineScope()
    DisposableEffect(resolver, key) {
        val signal = CancellationSignal()
        val job = if (bitmap == null) {
            scope.launch {
                try {
                    val image = withContext(thumbnailDispatcher) {
                        thumbnailCache.get(key) ?: resolver.loadThumbnail(entry.uri, Size(160, 96), signal).also {
                            thumbnailCache.put(key, it)
                        }
                    }
                    bitmap = image
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { bitmap = null }
            }
        } else null
        onDispose {
            signal.cancel()
            job?.cancel()
        }
    }
    val image = bitmap
    if (image == null) FileTypeIcon(entry)
    else Image(
        bitmap = image.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.size(80.dp, 48.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun FileTypeIcon(entry: Entry) {
    val icon = if (entry.directory) R.drawable.symbol_folder
    else FileCategory.entries.firstOrNull { it.matches(entry) }?.icon ?: R.drawable.symbol_description
    ExplorerIcon(icon)
}
