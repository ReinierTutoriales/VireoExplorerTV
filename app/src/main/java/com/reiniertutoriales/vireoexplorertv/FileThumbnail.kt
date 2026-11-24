package com.reiniertutoriales.vireoexplorertv

import android.graphics.Bitmap
import android.os.Build
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
internal fun FileThumbnail(entry: Entry, revision: Int) {
    if (Build.VERSION.SDK_INT < 29 || entry.directory ||
        entry.flags and DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL == 0) return
    val resolver = LocalContext.current.contentResolver
    var bitmap by remember(entry.uri, entry.size, revision) { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(resolver, entry.uri, entry.size, revision) {
        val signal = CancellationSignal()
        val job = scope.launch {
            try {
                val image = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    resolver.loadThumbnail(entry.uri, Size(160, 96), signal)
                }
                bitmap = image
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { bitmap = null }
        }
        onDispose { signal.cancel(); job.cancel() }
    }
    bitmap?.let {
        Image(bitmap = it.asImageBitmap(), contentDescription = null,
            modifier = Modifier.size(80.dp, 48.dp), contentScale = ContentScale.Fit)
    }
}
