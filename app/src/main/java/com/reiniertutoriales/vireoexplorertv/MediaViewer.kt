package com.reiniertutoriales.vireoexplorertv

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
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
internal fun MediaViewer(entry: Entry, onClose: () -> Unit) {
    val context = LocalContext.current
    var player by remember(entry.uri) { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var error by remember { mutableStateOf<String?>(null) }
    var trackType by remember { mutableIntStateOf(C.TRACK_TYPE_UNKNOWN) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(entry.uri, lifecycleOwner) {
        var session: MediaSession? = null
        var active: ExoPlayer? = null
        var position = 0L
        fun stop() {
            active?.let { position = it.currentPosition; session?.release(); session = null; it.release() }
            active = null
            player = null
        }
        fun start() {
            if (active != null) return
            val p = ExoPlayer.Builder(context).build()
            active = p
            p.setAudioAttributes(AudioAttributes.DEFAULT, true)
            p.setHandleAudioBecomingNoisy(true)
            p.addListener(object : Player.Listener {
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
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> start()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); stop() }
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
        error?.let { Text(it) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onClose) { Text("Back") }
            Button(enabled = p != null, onClick = { p?.seekBack() }) { Text("−10s") }
            Button(enabled = p != null, onClick = { p?.let { if (it.isPlaying) it.pause() else it.play() } }) { Text(if (playing) "Pause" else "Play") }
            Button(enabled = p != null, onClick = { p?.seekForward() }) { Text("+10s") }
            Button(enabled = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }, onClick = { trackType = C.TRACK_TYPE_AUDIO }) { Text("Audio") }
            Button(enabled = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }, onClick = { trackType = C.TRACK_TYPE_TEXT }) { Text("Subtitles") }
        }
    }
}
