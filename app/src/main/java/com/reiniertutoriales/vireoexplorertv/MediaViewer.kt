package com.reiniertutoriales.vireoexplorertv

import android.view.ViewGroup
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MimeTypes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
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
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun MediaViewer(entry: Entry, sourceFactory: androidx.media3.datasource.DataSource.Factory? = null, onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var error by remember(entry.uri) { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("playback_positions", android.content.Context.MODE_PRIVATE) }
    var subtitle by remember(entry.uri) { mutableStateOf<Uri?>(null) }
    var subtitleMime by remember(entry.uri) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                }
                val mime = subtitleMimeForName(name.orEmpty())
                if (mime != null) { subtitleMime = mime; subtitle = uri; error = null }
                else error = "Unsupported subtitle format"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Cannot read subtitle" }
        }
    }
    var player by remember(entry.uri) { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember(entry.uri) { mutableLongStateOf(0L) }
    var durationMs by remember(entry.uri) { mutableLongStateOf(C.TIME_UNSET) }
    var buffering by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        val active = player ?: return@LaunchedEffect
        while (true) {
            positionMs = active.currentPosition.coerceAtLeast(0L)
            durationMs = active.duration
            delay(if (active.isPlaying) 500L else 1000L)
        }
    }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var trackType by remember { mutableIntStateOf(C.TRACK_TYPE_UNKNOWN) }
    LaunchedEffect(subtitle, subtitleMime, player) {
        val p = player
        val uri = subtitle
        val mime = subtitleMime
        if (p != null && uri != null && mime != null) {
            val item = MediaItem.Builder().setUri(entry.uri)
                .setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build())).build()
            if (p.currentMediaItem?.localConfiguration?.subtitleConfigurations != item.localConfiguration?.subtitleConfigurations) {
                val position = p.currentPosition
                p.setMediaItem(item, position)
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT).build()
                p.prepare()
            }
        }
    }
    LifecycleStartEffect(entry.uri, sourceFactory) {
        var session: MediaSession? = null
        var active: ExoPlayer? = null
        var position = prefs.getLong(entry.uri.toString(), 0L).coerceAtLeast(0L)
        fun stop() {
            active?.let { position = if (it.playbackState == Player.STATE_ENDED) 0L else it.currentPosition.coerceAtLeast(0L); prefs.edit().putLong(entry.uri.toString(), position).apply(); session?.release(); session = null; it.release() }
            active = null
            player = null
            playing = false
            buffering = false
            tracks = Tracks.EMPTY
        }
        fun start() {
            if (active != null) return
            val builder = ExoPlayer.Builder(context).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000)
            if (sourceFactory != null) builder.setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context).setDataSourceFactory(sourceFactory))
            val p = builder.build()
            active = p
            p.setAudioAttributes(AudioAttributes.DEFAULT, true)
            p.setHandleAudioBecomingNoisy(true)
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onTracksChanged(value: Tracks) { tracks = value }
                override fun onPlayerError(value: PlaybackException) { error = value.errorCodeName }
            })
            session = MediaSession.Builder(context, p).build()
            p.setMediaItem(MediaItem.fromUri(entry.uri), position)
            p.prepare()
            p.playWhenReady = true
            player = p
        }
        try { start() } catch (e: Exception) {
            stop()
            error = e.localizedMessage ?: "Cannot initialize player"
        }
        onStopOrDispose { stop() }
    }
    BackHandler { if (trackType != C.TRACK_TYPE_UNKNOWN) trackType = C.TRACK_TYPE_UNKNOWN else onClose() }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        Text(entry.name)
        AndroidView(factory = { ctx -> PlayerView(ctx).apply {
            useController = false
            isFocusable = false
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        } }, update = { it.player = player; it.keepScreenOn = playing },
            onRelease = { it.player = null; it.keepScreenOn = false },
            modifier = Modifier.weight(1f).fillMaxWidth())
        if (buffering) Text("Buffering…")
        error?.let { Text(it) }
        if (durationMs > 0) {
            val fraction = (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                drawRect(Color.DarkGray)
                drawRect(Color(0xFF80DCC0), size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height))
            }
            Text(playbackTime(positionMs) + " / " + playbackTime(durationMs))
        }
        val p = player
        if (trackType != C.TRACK_TYPE_UNKNOWN && p != null) {
            LazyColumn(Modifier.heightIn(max = 160.dp)) {
                if (trackType == C.TRACK_TYPE_TEXT) item {
                    Button(onClick = {
                        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                        trackType = C.TRACK_TYPE_UNKNOWN
                    }) { Text("Subtitles off") }
                }
                items(tracks.groups.filter { it.type == trackType }) { group ->
                    for (i in 0 until group.length) {
                        if (group.isTrackSupported(i)) {
                            val format = group.getTrackFormat(i)
                            Button(onClick = {
                                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(group.type, false).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i)).build()
                                trackType = C.TRACK_TYPE_UNKNOWN
                            }) { Text((if (group.isTrackSelected(i)) "✓ " else "") + (format.label ?: format.language ?: "Track ${i + 1}")) }
                        }
                    }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onClose) { Text("Back") }
            Button(enabled = p?.isCurrentMediaItemSeekable == true, onClick = { p?.seekBack() }) { Text("−10s") }
            Button(enabled = p != null, onClick = { p?.let { if (it.isPlaying) it.pause() else it.play() } }) { Text(if (playing) "Pause" else "Play") }
            Button(enabled = p?.isCurrentMediaItemSeekable == true, onClick = { p?.seekForward() }) { Text("+10s") }
            Button(enabled = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }, onClick = { trackType = C.TRACK_TYPE_AUDIO }) { Text("Audio") }
            Button(enabled = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }, onClick = { trackType = C.TRACK_TYPE_TEXT }) { Text("Subtitles") }
            Button(enabled = p != null, onClick = { try { picker.launch(arrayOf("*/*")) } catch (e: Exception) { error = "Subtitle picker unavailable" } }) { Text("External") }
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
