package com.reiniertutoriales.vireoexplorertv

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun sampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
    require(width > 0 && height > 0 && maxWidth > 0 && maxHeight > 0)
    var sample = 1
    while (width / sample > maxWidth || height / sample > maxHeight) sample *= 2
    return sample
}

private suspend fun decode(resolver: ContentResolver, entry: Entry, width: Int, height: Int): Bitmap =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, entry.uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                require(w > 0 && h > 0)
                val scale = minOf(1.0, width.toDouble() / w, height.toDouble() / h)
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(entry.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, width, height)
            }
            checkNotNull(resolver.openInputStream(entry.uri)?.use { BitmapFactory.decodeStream(it, null, options) })
        }
    }

@Composable
internal fun ImageViewer(entry: Entry, resolver: ContentResolver, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.roundToPx() }.coerceIn(1, 3840)
        val height = with(density) { maxHeight.roundToPx() }.coerceIn(1, 2160)
        var bitmap by remember(entry.uri) { mutableStateOf<Bitmap?>(null) }
        var error by remember(entry.uri) { mutableStateOf<String?>(null) }
        var zoom by remember(entry.uri) { mutableFloatStateOf(1f) }
        var rotation by remember(entry.uri) { mutableFloatStateOf(0f) }
        LaunchedEffect(entry.uri, width, height) {
            bitmap = null
            error = null
            try { bitmap = decode(resolver, entry, width, height) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.localizedMessage ?: "Cannot decode image" }
        }
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(entry.name)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = bitmap
                if (image != null) Image(image.asImageBitmap(), entry.name,
                    Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; rotationZ = rotation },
                    contentScale = ContentScale.Fit)
                else Text(error ?: "Loading image…")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onClose) { Text("Back") }
                Button(enabled = bitmap != null && zoom > 1f, onClick = { zoom = (zoom - .5f).coerceAtLeast(1f) }) { Text("Zoom −") }
                Button(enabled = bitmap != null && zoom < 3f, onClick = { zoom = (zoom + .5f).coerceAtMost(3f) }) { Text("Zoom +") }
                Button(enabled = bitmap != null, onClick = { rotation = (rotation + 90f) % 360f }) { Text("Rotate") }
                Button(enabled = bitmap != null, onClick = { zoom = 1f; rotation = 0f }) { Text("Reset") }
            }
        }
    }
}
