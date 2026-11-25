package com.reiniertutoriales.vireoexplorertv

import android.view.ViewGroup
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MimeTypes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.compose.foundation.focusable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.Format
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.MediaMetadata
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun MediaViewer(entry: Entry, sourceFactory: androidx.media3.datasource.DataSource.Factory? = null, sidecars: List<Entry> = emptyList(), onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var error by remember(entry.uri) { mutableStateOf<String?>(null) }
    var subtitleError by remember(entry.uri) { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("playback_positions", android.content.Context.MODE_PRIVATE) }
    val automaticSubtitles = remember(entry.uri, sidecars) {
        sidecars.filter { !it.directory && matchesSubtitle(entry.name, it.name) }.take(16).mapNotNull { candidate ->
            subtitleMimeForName(candidate.name)?.let { mime ->
                MediaItem.SubtitleConfiguration.Builder(candidate.uri).setMimeType(mime).setLabel(candidate.name).build()
            }
        }
    }
    var manualSubtitle by remember(entry.uri) { mutableStateOf<MediaItem.SubtitleConfiguration?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                }
                val mime = subtitleMimeForName(name.orEmpty())
                if (mime != null) {
                    manualSubtitle = MediaItem.SubtitleConfiguration.Builder(uri)
                        .setMimeType(mime).setLabel(name ?: uri.lastPathSegment ?: "External subtitle")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
                    subtitleError = null
                }
                else subtitleError = "Unsupported subtitle format"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { subtitleError = "Cannot read subtitle" }
        }
    }
    var player by remember(entry.uri) { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember(entry.uri) { mutableStateOf(false) }
    var wantsPlayback by remember(entry.uri) { mutableStateOf(true) }
    var ended by remember(entry.uri) { mutableStateOf(false) }
    var savedTrackParameters by remember(entry.uri) { mutableStateOf<androidx.media3.common.TrackSelectionParameters?>(null) }
    var positionMs by remember(entry.uri) { mutableLongStateOf(0L) }
    var durationMs by remember(entry.uri) { mutableLongStateOf(C.TIME_UNSET) }
    var buffering by remember(entry.uri) { mutableStateOf(false) }
    var seekable by remember(entry.uri) { mutableStateOf(false) }
    var firstFrame by remember(entry.uri) { mutableStateOf(false) }
    var captionPresent by remember(entry.uri) { mutableStateOf(false) }
    var metadata by remember(entry.uri) { mutableStateOf(MediaMetadata.EMPTY) }
    var captionSize by remember(entry.uri) { mutableIntStateOf(1) }
    val captionFractions = listOf(0.04f, 0.0533f, 0.07f)
    val cinemaCaptionStyle = remember {
        CaptionStyleCompat(0xFFFFE600.toInt(), android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            android.graphics.Color.BLACK, android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))
    }
    var showInfo by remember(entry.uri) { mutableStateOf(false) }
    var aspect by remember(entry.uri) { mutableIntStateOf(0) }
    var speedIndex by remember(entry.uri) { mutableIntStateOf(1) }
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
    val aspectModes = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)
    val aspectLabels = listOf("Fit", "Zoom", "Stretch")
    LaunchedEffect(player, speedIndex) { player?.setPlaybackSpeed(speeds[speedIndex]) }
    var controlsVisible by remember(entry.uri) { mutableStateOf(true) }
    LaunchedEffect(player, controlsVisible) {
        val active = player ?: return@LaunchedEffect
        if (!controlsVisible) return@LaunchedEffect
        while (true) {
            positionMs = active.currentPosition.coerceAtLeast(0L)
            durationMs = active.duration
            seekable = active.isCurrentMediaItemSeekable
            delay(if (active.isPlaying) 500L else 1000L)
        }
    }
    var tracks by remember(entry.uri) { mutableStateOf(Tracks.EMPTY) }
    var trackType by remember(entry.uri) { mutableIntStateOf(C.TRACK_TYPE_UNKNOWN) }
    fun buildConfiguredMediaItem(): MediaItem {
        val manual = manualSubtitle
        val subs = if (manual != null) automaticSubtitles.filter { it.uri != manual.uri } + manual
            else automaticSubtitles
        return MediaItem.Builder().setUri(entry.uri).setSubtitleConfigurations(subs).build()
    }
    LaunchedEffect(manualSubtitle, automaticSubtitles, player) {
        val p = player
        if (p != null) {
            val item = buildConfiguredMediaItem()
            if (p.currentMediaItem?.localConfiguration?.subtitleConfigurations != item.localConfiguration?.subtitleConfigurations) {
                val position = p.currentPosition
                p.setMediaItem(item, position)
                if (manualSubtitle != null) {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
                }
                p.prepare()
            }
        }
    }
    var retry by remember(entry.uri) { mutableIntStateOf(0) }
    LifecycleStartEffect(entry.uri, sourceFactory, retry) {
        var session: MediaSession? = null
        var active: ExoPlayer? = null
        val position = prefs.getLong(entry.uri.toString(), 0L).coerceAtLeast(0L)
        fun stop() {
            active?.let {
                val resume = if (it.playbackState == Player.STATE_ENDED) 0L else it.currentPosition.coerceAtLeast(0L)
                saveBoundedResumePosition(prefs, entry.uri.toString(), resume)
                wantsPlayback = it.playWhenReady && it.playbackState != Player.STATE_ENDED
                savedTrackParameters = it.trackSelectionParameters
                session?.release()
                session = null
                it.release()
            }
            active = null
            player = null
            playing = false
            buffering = false
            seekable = false
            firstFrame = false
            captionPresent = false
            metadata = MediaMetadata.EMPTY
            tracks = Tracks.EMPTY
        }
        fun start() {
            if (active != null) return
            val builder = ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
            if (sourceFactory != null) builder.setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context).setDataSourceFactory(sourceFactory))
            val p = builder.build()
            active = p
            p.setAudioAttributes(AudioAttributes.DEFAULT, true)
            p.setHandleAudioBecomingNoisy(true)
            savedTrackParameters?.let { p.trackSelectionParameters = it }
            p.addListener(object : Player.Listener {
                override fun onMediaMetadataChanged(value: MediaMetadata) { metadata = value }
                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                    ended = state == Player.STATE_ENDED
                    if (ended) controlsVisible = true
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { wantsPlayback = playWhenReady }
                override fun onEvents(player: Player, events: Player.Events) {
                    seekable = player.isCurrentMediaItemSeekable
                    durationMs = player.duration
                    positionMs = player.currentPosition.coerceAtLeast(0L)
                }
                override fun onTracksChanged(value: Tracks) { tracks = value }
                override fun onRenderedFirstFrame() { firstFrame = true }
                override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                    captionPresent = cueGroup.cues.isNotEmpty()
                }
                override fun onPlayerError(value: PlaybackException) { error = value.errorCodeName }
            })
            session = MediaSession.Builder(context, p).build()
            val resumePlayback = wantsPlayback
            p.setMediaItem(buildConfiguredMediaItem(), position)
            p.playWhenReady = resumePlayback
            p.prepare()
            player = p
        }
        error = null
        try { start() } catch (e: Exception) {
            stop()
            error = e.localizedMessage ?: "Cannot initialize player"
        }
        onStopOrDispose { stop() }
    }
    var interaction by remember(entry.uri) { mutableIntStateOf(0) }
    fun togglePlayback(p: Player) {
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekToDefaultPosition()
            p.play()
        } else if (p.playWhenReady) p.pause() else p.play()
    }
    val controlsFocus = remember { FocusRequester() }
    val tracksFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val surfaceFocus = remember { FocusRequester() }
    val video = entry.mime.startsWith("video/") || tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }
    LaunchedEffect(playing, controlsVisible, interaction, trackType, video, error, subtitleError, buffering, showInfo) {
        if (video && playing && controlsVisible && trackType == C.TRACK_TYPE_UNKNOWN && error == null && subtitleError == null && !buffering && !showInfo) {
            delay(4000)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible, player, error) {
        when {
            !controlsVisible -> surfaceFocus.requestFocus()
            player != null && error == null -> controlsFocus.requestFocus()
            else -> closeFocus.requestFocus()
        }
    }
    LaunchedEffect(trackType) {
        if (trackType == C.TRACK_TYPE_UNKNOWN) return@LaunchedEffect
        tracksFocus.requestFocus()
    }
    LaunchedEffect(error, subtitleError, buffering) {
        if (error != null || subtitleError != null || buffering) controlsVisible = true
    }
    BackHandler {
        if (subtitleError != null) subtitleError = null
        else if (showInfo) showInfo = false
        else if (trackType != C.TRACK_TYPE_UNKNOWN) { trackType = C.TRACK_TYPE_UNKNOWN; controlsFocus.requestFocus() }
        else if (video && controlsVisible && error == null && playing) controlsVisible = false
        else onClose()
    }
    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
        val p = player
        val isFirstDown = event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0
        when (event.key) {
            Key.MediaPlayPause -> {
                if (isFirstDown) {
                    p?.let(::togglePlayback)
                    controlsVisible = true
                    interaction++
                }
                true
            }
            Key.MediaPlay -> {
                if (isFirstDown) { p?.play(); controlsVisible = true; interaction++ }
                true
            }
            Key.MediaPause -> {
                if (isFirstDown) { p?.pause(); controlsVisible = true; interaction++ }
                true
            }
            Key.MediaFastForward -> {
                if (event.type == KeyEventType.KeyDown) {
                    if (p?.isCurrentMediaItemSeekable == true) p.seekForward()
                    controlsVisible = true
                    interaction++
                }
                true
            }
            Key.MediaRewind -> {
                if (event.type == KeyEventType.KeyDown) {
                    if (p?.isCurrentMediaItemSeekable == true) p.seekBack()
                    controlsVisible = true
                    interaction++
                }
                true
            }
            else -> if (!controlsVisible && event.key in listOf(Key.DirectionCenter, Key.Enter, Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) {
                if (event.type == KeyEventType.KeyUp) {
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter) p?.let(::togglePlayback)
                    else if (event.key == Key.DirectionLeft && p?.isCurrentMediaItemSeekable == true) p.seekBack()
                    else if (event.key == Key.DirectionRight && p?.isCurrentMediaItemSeekable == true) p.seekForward()
                    controlsVisible = true
                    interaction++
                }
                true
            } else {
                if (event.type == KeyEventType.KeyDown) interaction++
                false
            }
        }
    }) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply {
            useController = false
            isFocusable = false
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        } }, update = { it.player = player; it.keepScreenOn = playing; it.resizeMode = aspectModes[aspect]; it.subtitleView?.apply {
                setStyle(cinemaCaptionStyle)
                setApplyEmbeddedStyles(false)
                setApplyEmbeddedFontSizes(false)
                setFractionalTextSize(captionFractions[captionSize])
                setBottomPaddingFraction(0.08f)
            } },
            onRelease = { it.player = null; it.keepScreenOn = false },
            modifier = Modifier.fillMaxSize())
        if (!video) {
            val artwork = metadata.artworkData
            val cover by produceState<android.graphics.Bitmap?>(null, artwork) {
                value = null
                if (artwork != null && artwork.size <= 8 * 1024 * 1024) {
                    value = withContext(Dispatchers.IO) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(artwork, 0, artwork.size, options)
                        if (options.outWidth <= 0 || options.outHeight <= 0) null
                        else {
                            var sample = 1
                            while (maxOf(options.outWidth, options.outHeight) / sample > 1024) sample *= 2
                            BitmapFactory.decodeByteArray(artwork, 0, artwork.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.Center).fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 140.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Box(Modifier.weight(1f).aspectRatio(1f).background(Color(0xFF121212)), contentAlignment = Alignment.Center) {
                    val bitmap = cover
                    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Album artwork", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Text("♪", fontSize = 64.sp)
                }
                Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: entry.name, fontSize = 32.sp, maxLines = 2)
                    metadata.artist?.toString()?.let { Text(it, fontSize = 24.sp, maxLines = 2) }
                    metadata.albumTitle?.toString()?.let { Text(it, fontSize = 18.sp, maxLines = 2) }
                }
            }
        }
        if (!controlsVisible) Box(Modifier.fillMaxSize().focusRequester(surfaceFocus).focusable())
        if (controlsVisible || buffering || error != null) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.88f)).padding(16.dp)
        ) {
        if (video) Text(metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: entry.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (buffering) Text("Buffering…")
        if (video && !firstFrame && error == null && !buffering && !ended) Text("Waiting for video…")
        (error ?: subtitleError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (durationMs > 0) {
            val fraction = (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
            VireoProgressBar(progress = fraction, modifier = Modifier.padding(vertical = 4.dp))
            Text(playbackTime(positionMs) + " / " + playbackTime(durationMs))
        }
        val p = player
        if (showInfo) {
            if (video) Text(if (firstFrame) "Video rendered" else "Video not rendered")
            if (tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }) Text(if (captionPresent) "Subtitle cue active" else "No active subtitle cue")
            LazyColumn(Modifier.heightIn(max = 180.dp)) {
                items(tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO || it.type == C.TRACK_TYPE_AUDIO || it.type == C.TRACK_TYPE_TEXT }) { group ->
                    for (i in 0 until group.length) {
                        Text((if (group.isTrackSelected(i)) "✓ " else "") + describeTrack(group.getTrackFormat(i)) + if (group.isTrackSupported(i)) "" else " (unsupported)")
                    }
                }
            }
        }
        if (trackType != C.TRACK_TYPE_UNKNOWN && p != null) {
            LazyColumn(Modifier.heightIn(max = 200.dp)) {
                item { OutlinedButton(modifier = Modifier.focusRequester(tracksFocus), onClick = { trackType = C.TRACK_TYPE_UNKNOWN; controlsFocus.requestFocus() }) { Text("Close tracks") } }
                if (trackType == C.TRACK_TYPE_TEXT) item {
                    Button(onClick = {
                        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                        trackType = C.TRACK_TYPE_UNKNOWN
                        controlsFocus.requestFocus()
                    }) { Text("Subtitles off") }
                }
                items(tracks.groups.filter { it.type == trackType }) { group ->
                    for (i in 0 until group.length) {
                        if (group.isTrackSupported(i)) {
                            val format = group.getTrackFormat(i)
                            Button(onClick = {
                                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(group.type, false).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i)).build()
                                trackType = C.TRACK_TYPE_UNKNOWN
                        controlsFocus.requestFocus()
                            }) { Text((if (group.isTrackSelected(i)) "✓ " else "") + (format.label ?: format.language ?: "Track ${i + 1}")) }
                        }
                    }
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(modifier = Modifier.focusRequester(closeFocus), onClick = onClose) { Text("Back") }
            if (error != null) Button(onClick = { retry++ }) { Text("Retry") }
            OutlinedButton(enabled = seekable, onClick = { p?.seekBack() }) { Text("−10s") }
            Button(modifier = Modifier.focusRequester(controlsFocus), enabled = p != null, onClick = {
                p?.let(::togglePlayback)
            }) { Text(if (wantsPlayback && !ended) "Pause" else "Play") }
            OutlinedButton(enabled = seekable, onClick = { p?.seekForward() }) { Text("+10s") }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (video) OutlinedButton(onClick = { aspect = (aspect + 1) % aspectModes.size }) { Text(aspectLabels[aspect]) }
            OutlinedButton(enabled = p != null, onClick = { speedIndex = (speedIndex + 1) % speeds.size }) {
                Text(speeds[speedIndex].toString() + "×")
            }
            OutlinedButton(enabled = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO },
                onClick = { trackType = C.TRACK_TYPE_AUDIO }) { Text("Audio") }
            OutlinedButton(enabled = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT },
                onClick = { trackType = C.TRACK_TYPE_TEXT }) { Text("Subtitles") }
            OutlinedButton(enabled = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT },
                onClick = { captionSize = (captionSize + 1) % captionFractions.size }) {
                Text("CC " + listOf("S", "M", "L")[captionSize])
            }
            OutlinedButton(onClick = { showInfo = !showInfo }) { Text("Info") }
            OutlinedButton(enabled = p != null, onClick = {
                try { picker.launch(arrayOf("*/*")) }
                catch (_: Exception) { subtitleError = "Subtitle picker unavailable" }
            }) { Text("External subtitle") }
        }
        }
    }
}

internal fun subtitleMimeForName(name: String): String? = when (name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)) {
    "srt" -> MimeTypes.APPLICATION_SUBRIP
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    "vtt" -> MimeTypes.TEXT_VTT
    "ttml", "dfxp" -> MimeTypes.APPLICATION_TTML
    else -> null
}

private fun playbackTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

internal fun describeTrack(format: Format): String = buildList {
    add(format.sampleMimeType ?: "Unknown format")
    format.codecs?.let { add(it) }
    if (format.width > 0 && format.height > 0) add(format.width.toString() + "×" + format.height)
    if (format.frameRate > 0) add(format.frameRate.toString() + " fps")
    if (format.channelCount > 0) add(format.channelCount.toString() + " ch")
    if (format.sampleRate > 0) add(format.sampleRate.toString() + " Hz")
    format.language?.let { add(it) }
}.joinToString(" · ")

internal fun matchesSubtitle(mediaName: String, subtitleName: String): Boolean {
    if (subtitleMimeForName(subtitleName) == null) return false
    val media = mediaName.substringBeforeLast('.')
    val caption = subtitleName.substringBeforeLast('.')
    return media.isNotBlank() && (caption.equals(media, ignoreCase = true) ||
        caption.startsWith(media + ".", ignoreCase = true))
}

private const val RESUME_LRU_ORDER_KEY = "_lru_order"

internal fun reconstructOrderedResumeMap(rawPositions: Map<String, Long>, encodedOrder: String?): LinkedHashMap<String, Long> {
    val ordered = LinkedHashMap<String, Long>()
    if (!encodedOrder.isNullOrBlank()) {
        for (line in encodedOrder.lineSequence()) {
            val key = line.trim()
            val pos = rawPositions[key]
            if (key.isNotEmpty() && key != RESUME_LRU_ORDER_KEY && pos != null && pos > 0L) {
                ordered[key] = pos
            }
        }
    }
    for ((k, v) in rawPositions) {
        if (k != RESUME_LRU_ORDER_KEY && k !in ordered && v > 0L) ordered[k] = v
    }
    return ordered
}

internal fun trimResumePositions(entries: Map<String, Long>, updatedKey: String, updatedPosition: Long, maxEntries: Int = 200): Map<String, Long> {
    val result = LinkedHashMap<String, Long>()
    for ((k, v) in entries) {
        if (k != updatedKey && k != RESUME_LRU_ORDER_KEY && v > 0L) result[k] = v
    }
    if (updatedPosition > 0L) result[updatedKey] = updatedPosition
    while (result.size > maxEntries) {
        val oldest = result.keys.firstOrNull() ?: break
        result.remove(oldest)
    }
    return result
}

private fun saveBoundedResumePosition(prefs: android.content.SharedPreferences, uriKey: String, position: Long) {
    val raw = prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
    val ordered = reconstructOrderedResumeMap(raw, prefs.getString(RESUME_LRU_ORDER_KEY, null))
    val trimmed = trimResumePositions(ordered, uriKey, position, 200)
    val editor = prefs.edit().clear()
    for ((k, v) in trimmed) editor.putLong(k, v)
    editor.putString(RESUME_LRU_ORDER_KEY, trimmed.keys.joinToString("\n"))
    editor.apply()
}
