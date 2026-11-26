package com.reiniertutoriales.vireoexplorertv

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*

@Composable
internal fun FolderWorkspace(
    enabled: Boolean,
    onNavigate: (ExplorerPage) -> Unit,
    details: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var showDetails by remember { mutableStateOf(false) }
    val toolsFocus = remember { FocusRequester() }
    val dismissFocus = remember { FocusRequester() }
    var restoreToolsFocus by remember { mutableStateOf(false) }
    fun closeDetails() {
        restoreToolsFocus = true
        showDetails = false
    }
    BackHandler(enabled = showDetails && enabled) { closeDetails() }
    ExplorerNavigation(page = ExplorerPage.FILES, enabled = enabled, onNavigate = onNavigate) {
        Row(
            Modifier.fillMaxSize().padding(start = 24.dp, end = 40.dp, top = 24.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            if (showDetails) {
                Surface(
                    modifier = Modifier.width(224.dp).fillMaxHeight(),
                    shape = VireoCardShape,
                    colors = SurfaceDefaults.colors(containerColor = colors.surface, contentColor = colors.onSurface)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        LaunchedEffect(Unit) { dismissFocus.requestFocus() }
                        OutlinedButton(
                            onClick = ::closeDetails,
                            modifier = Modifier.fillMaxWidth().focusRequester(dismissFocus)
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        Box(Modifier.weight(1f)) { details() }
                    }
                }
            } else {
                LaunchedEffect(restoreToolsFocus) {
                    if (restoreToolsFocus) {
                        toolsFocus.requestFocus()
                        restoreToolsFocus = false
                    }
                }
                IconButton(
                    enabled = enabled,
                    onClick = { showDetails = true },
                    modifier = Modifier.focusRequester(toolsFocus)
                ) {
                    ExplorerIcon(R.drawable.symbol_description, description = stringResource(R.string.explorer_folder_tools))
                }
            }
        }
    }
}

@Composable
internal fun FolderDetails(
    entry: Entry?,
    revision: Int,
    enabled: Boolean,
    canMove: Boolean,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onMore: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (entry == null) {
            ExplorerIcon(R.drawable.symbol_folder, Modifier.size(64.dp))
            Text(stringResource(R.string.explorer_preview_empty),
                style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f), contentAlignment = Alignment.Center) {
                if (entry.directory) ExplorerIcon(R.drawable.symbol_folder, Modifier.size(80.dp))
                else FileThumbnail(entry, revision, Modifier.fillMaxSize(), preview = true)
            }
            Text(entry.name, style = MaterialTheme.typography.titleLarge,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(entryTypeSubtitle(entry, stringResource(R.string.explorer_type_folder)),
                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (!entry.directory && entry.size >= 0) {
                Text(Formatter.formatFileSize(context, entry.size), color = colors.primary)
            }
            Button(enabled = enabled, onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (!entry.directory &&
                    mediaMime(entry.name, entry.mime).let { it.startsWith("video/") || it.startsWith("audio/") })
                    R.string.explorer_preview_play else R.string.explorer_preview_open))
            }
            OutlinedButton(enabled = enabled, onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.explorer_preview_copy))
            }
            OutlinedButton(enabled = enabled && canMove, onClick = onMove, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.explorer_preview_move))
            }
            OutlinedButton(enabled = enabled, onClick = onMore, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.explorer_preview_more))
            }
        }
    }
}
