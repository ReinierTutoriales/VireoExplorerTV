package com.reiniertutoriales.vireoexplorertv

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Row(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(Modifier.width(152.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("VireoExplorerTV", color = colors.primary, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 20.dp))
            ExplorerPage.entries.forEach { page ->
                DenseListItem(
                    selected = page == ExplorerPage.FILES,
                    enabled = enabled,
                    onClick = { onNavigate(page) },
                    leadingContent = { ExplorerIcon(page.icon) },
                    headlineContent = {
                        Text(stringResource(page.label), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    },
                    colors = ListItemDefaults.colors(
                        selectedContainerColor = colors.primaryContainer,
                        selectedContentColor = colors.onPrimaryContainer
                    ),
                    shape = ListItemDefaults.shape(VireoPillShape),
                    scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f)
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        Surface(
            modifier = Modifier.width(240.dp).fillMaxHeight(),
            shape = VireoCardShape,
            colors = SurfaceDefaults.colors(containerColor = colors.surface, contentColor = colors.onSurface)
        ) { details() }
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
