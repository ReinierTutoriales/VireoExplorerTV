package com.reiniertutoriales.vireoexplorertv

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val MAX_IMAGE_STREAM_BYTES = 32 * 1024 * 1024

internal sealed interface DecodedVisual {
    data class Static(val bitmap: Bitmap) : DecodedVisual
    data class Animated(val drawable: Drawable) : DecodedVisual
}

internal fun sampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
    require(width > 0 && height > 0 && maxWidth > 0 && maxHeight > 0)
    var sample = 1
    while (width / sample > maxWidth || height / sample > maxHeight) sample *= 2
    return sample
}

internal fun exifTransformDegreesAndFlip(orientation: Int): Pair<Float, Boolean> = when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90f to false
    ExifInterface.ORIENTATION_ROTATE_180 -> 180f to false
    ExifInterface.ORIENTATION_ROTATE_270 -> 270f to false
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0f to true
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180f to true
    ExifInterface.ORIENTATION_TRANSPOSE -> 90f to true
    ExifInterface.ORIENTATION_TRANSVERSE -> 270f to true
    else -> 0f to false
}

internal fun readBoundedImageBytes(input: InputStream, maxBytes: Int = MAX_IMAGE_STREAM_BYTES): ByteArray {
    require(maxBytes > 0)
    val buffer = ByteArray(64 * 1024)
    val output = ByteArrayOutputStream()
    var total = 0
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        check(total <= maxBytes) { "Image exceeds ${maxBytes / (1024 * 1024)} MiB memory limit" }
        output.write(buffer, 0, n)
    }
    check(total > 0) { "Empty image stream" }
    return output.toByteArray()
}

internal fun clampPanOffset(offset: Float, delta: Float, viewportPx: Int, zoom: Float): Float {
    if (zoom <= 1f || viewportPx <= 0) return 0f
    val maxOffset = (viewportPx * (zoom - 1f) / 2f).coerceAtLeast(0f)
    return (offset + delta).coerceIn(-maxOffset, maxOffset)
}

private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val (degrees, flipHorizontal) = exifTransformDegreesAndFlip(orientation)
    if (degrees == 0f && !flipHorizontal) return bitmap
    val matrix = Matrix().apply {
        if (flipHorizontal) postScale(-1f, 1f)
        if (degrees != 0f) postRotate(degrees)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun decodeSampledBytes(bytes: ByteArray, width: Int, height: Int): Bitmap {
    val orientation = try {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, width, height)
    }
    val decoded = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) {
        "Cannot decode image"
    }
    return applyExifOrientation(decoded, orientation)
}

@androidx.annotation.RequiresApi(28)
private fun decodeSourceVisual(source: ImageDecoder.Source, width: Int, height: Int): DecodedVisual {
    val listener = ImageDecoder.OnHeaderDecodedListener { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        require(w > 0 && h > 0)
        val scale = minOf(1.0, width.toDouble() / w, height.toDouble() / h)
        decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
    }
    return when (val drawable = ImageDecoder.decodeDrawable(source, listener)) {
        is AnimatedImageDrawable -> DecodedVisual.Animated(drawable)
        is BitmapDrawable -> DecodedVisual.Static(drawable.bitmap)
        else -> DecodedVisual.Static(ImageDecoder.decodeBitmap(source, listener))
    }
}

private suspend fun decode(
    resolver: ContentResolver?,
    entry: Entry,
    width: Int,
    height: Int,
    openStream: (suspend (Entry) -> InputStream)? = null
): DecodedVisual = withContext(Dispatchers.IO) {
    if (openStream != null) {
        val bytes = openStream(entry).use { readBoundedImageBytes(it) }
        if (Build.VERSION.SDK_INT >= 28) {
            decodeSourceVisual(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)), width, height)
        } else {
            DecodedVisual.Static(decodeSampledBytes(bytes, width, height))
        }
    } else if (entry.uri.scheme == "file") {
        val file = java.io.File(requireNotNull(entry.uri.path))
        if (Build.VERSION.SDK_INT >= 28) {
            decodeSourceVisual(ImageDecoder.createSource(file), width, height)
        } else {
            val bytes = java.io.FileInputStream(file).use { readBoundedImageBytes(it) }
            DecodedVisual.Static(decodeSampledBytes(bytes, width, height))
        }
    } else {
        val contentResolver = checkNotNull(resolver)
        if (Build.VERSION.SDK_INT >= 28) {
            decodeSourceVisual(ImageDecoder.createSource(contentResolver, entry.uri), width, height)
        } else {
            val bytes = checkNotNull(contentResolver.openInputStream(entry.uri)).use { readBoundedImageBytes(it) }
            DecodedVisual.Static(decodeSampledBytes(bytes, width, height))
        }
    }
}

@Composable
internal fun ImageViewer(
    entry: Entry,
    resolver: ContentResolver? = null,
    siblings: List<Entry> = emptyList(),
    openStream: (suspend (Entry) -> InputStream)? = null,
    onClose: () -> Unit
) {
    val images = remember(entry.uri, siblings) {
        val filtered = siblings.filter { !it.directory && mediaMime(it.name, it.mime).startsWith("image/") }
        if (filtered.any { it.uri == entry.uri }) filtered else listOf(entry)
    }
    var currentIndex by remember(entry.uri, images) {
        mutableIntStateOf(images.indexOfFirst { it.uri == entry.uri }.coerceAtLeast(0))
    }
    val current = images.getOrElse(currentIndex) { entry }
    val backFocus = remember { FocusRequester() }
    val viewportFocus = remember { FocusRequester() }
    var viewportFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { backFocus.requestFocus() }
    var zoom by remember(current.uri) { mutableFloatStateOf(1f) }
    var rotation by remember(current.uri) { mutableFloatStateOf(0f) }
    var panX by remember(current.uri) { mutableFloatStateOf(0f) }
    var panY by remember(current.uri) { mutableFloatStateOf(0f) }
    var slideshow by remember { mutableStateOf(false) }

    LaunchedEffect(zoom) {
        if (zoom <= 1f && viewportFocused) backFocus.requestFocus()
    }

    LaunchedEffect(slideshow, currentIndex, images.size) {
        if (slideshow && images.size > 1) {
            delay(5000L)
            currentIndex = (currentIndex + 1) % images.size
        }
    }

    BackHandler {
        when {
            viewportFocused -> backFocus.requestFocus()
            zoom > 1f || panX != 0f || panY != 0f -> {
                zoom = 1f
                panX = 0f
                panY = 0f
            }
            else -> onClose()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.roundToPx() }.coerceIn(1, 3840)
        val height = with(density) { maxHeight.roundToPx() }.coerceIn(1, 2160)
        var visual by remember(current.uri) { mutableStateOf<DecodedVisual?>(null) }
        var error by remember(current.uri) { mutableStateOf<String?>(null) }
        LaunchedEffect(current.uri, width, height) {
            visual = null
            error = null
            try { visual = decode(resolver, current, width, height, openStream) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.localizedMessage ?: "Cannot decode image" }
        }
        LifecycleStartEffect(visual) {
            val anim = (visual as? DecodedVisual.Animated)?.drawable
            if (Build.VERSION.SDK_INT >= 28 && anim is AnimatedImageDrawable) anim.start()
            onStopOrDispose {
                if (Build.VERSION.SDK_INT >= 28 && anim is AnimatedImageDrawable) anim.stop()
            }
        }
        val colors = MaterialTheme.colorScheme
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (images.size > 1) "${current.name} (${currentIndex + 1}/${images.size})" else current.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (zoom > 1f) {
                            Modifier
                                .focusRequester(viewportFocus)
                                .onFocusChanged { viewportFocused = it.isFocused }
                                .border(
                                    width = if (viewportFocused) 2.dp else 0.dp,
                                    color = if (viewportFocused) colors.primary else Color.Transparent
                                )
                                .onPreviewKeyEvent { event ->
                                    if (!viewportFocused) return@onPreviewKeyEvent false
                                    val step = 120f
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            if (event.type == KeyEventType.KeyDown) panX = clampPanOffset(panX, step, width, zoom)
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            if (event.type == KeyEventType.KeyDown) panX = clampPanOffset(panX, -step, width, zoom)
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (event.type == KeyEventType.KeyDown) panY = clampPanOffset(panY, step, height, zoom)
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (event.type == KeyEventType.KeyDown) panY = clampPanOffset(panY, -step, height, zoom)
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter -> {
                                            if (event.type == KeyEventType.KeyUp) backFocus.requestFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .focusable()
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                val decoded = visual
                val transformModifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    rotationZ = rotation
                    translationX = if (zoom > 1f) panX else 0f
                    translationY = if (zoom > 1f) panY else 0f
                }
                when (decoded) {
                    is DecodedVisual.Static -> Image(
                        remember(decoded.bitmap) { decoded.bitmap.asImageBitmap() },
                        current.name,
                        transformModifier,
                        contentScale = ContentScale.Fit
                    )
                    is DecodedVisual.Animated -> AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                isFocusable = false
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        update = { view ->
                            if (view.drawable !== decoded.drawable) view.setImageDrawable(decoded.drawable)
                        },
                        onRelease = { view -> view.setImageDrawable(null) },
                        modifier = transformModifier
                    )
                    null -> Text(error ?: "Loading image…")
                }
            }
            if (viewportFocused) {
                Text("Panning active: use D-pad arrows to move, OK or Back for controls.", color = colors.primary)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(modifier = Modifier.focusRequester(backFocus), onClick = onClose) { Text("Back") }
                if (images.size > 1) {
                    OutlinedButton(
                        enabled = currentIndex > 0,
                        onClick = { slideshow = false; currentIndex = (currentIndex - 1).coerceAtLeast(0) }
                    ) { Text("Previous") }
                    OutlinedButton(
                        enabled = currentIndex < images.lastIndex,
                        onClick = { slideshow = false; currentIndex = (currentIndex + 1).coerceAtMost(images.lastIndex) }
                    ) { Text("Next") }
                    OutlinedButton(onClick = { slideshow = !slideshow }) {
                        Text(if (slideshow) "Stop slideshow" else "Slideshow")
                    }
                }
                OutlinedButton(
                    enabled = visual != null && zoom > 1f,
                    onClick = {
                        zoom = (zoom - .5f).coerceAtLeast(1f)
                        panX = clampPanOffset(panX, 0f, width, zoom)
                        panY = clampPanOffset(panY, 0f, height, zoom)
                    }
                ) { Text("Zoom −") }
                OutlinedButton(
                    enabled = visual != null && zoom < 3f,
                    onClick = { zoom = (zoom + .5f).coerceAtMost(3f) }
                ) { Text("Zoom +") }
                if (zoom > 1f) {
                    OutlinedButton(onClick = { viewportFocus.requestFocus() }) { Text("Pan") }
                }
                OutlinedButton(enabled = visual != null, onClick = { rotation = (rotation + 90f) % 360f }) { Text("Rotate") }
                OutlinedButton(
                    enabled = visual != null,
                    onClick = { zoom = 1f; rotation = 0f; panX = 0f; panY = 0f }
                ) { Text("Reset") }
            }
        }
    }
}
