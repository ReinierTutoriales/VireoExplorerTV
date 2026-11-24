package com.reiniertutoriales.vireoexplorertv

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*

@Composable
internal fun FolderEntry(
    entry: Entry,
    revision: Int,
    grid: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = VireoCardShape
    val restingBorder = Border(BorderStroke(1.dp, colors.borderVariant), shape = shape)
    val selectedRestingBorder = Border(BorderStroke(1.dp, colors.primary), shape = shape)
    val focusBorder = Border(BorderStroke(2.dp, colors.primary), shape = shape)
    val size = if (!entry.directory && entry.size >= 0) {
        Formatter.formatFileSize(LocalContext.current, entry.size)
    } else null

    if (grid) {
        Surface(
            selected = selected,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.heightIn(min = 116.dp),
            shape = SelectableSurfaceDefaults.shape(shape),
            colors = SelectableSurfaceDefaults.colors(
                containerColor = colors.surface,
                contentColor = colors.onSurface,
                selectedContainerColor = colors.primaryContainer,
                selectedContentColor = colors.onPrimaryContainer,
                focusedContainerColor = colors.surfaceVariant,
                focusedContentColor = colors.onSurface,
                focusedSelectedContainerColor = colors.primaryContainer,
                focusedSelectedContentColor = colors.onPrimaryContainer
            ),
            border = SelectableSurfaceDefaults.border(
                border = restingBorder,
                selectedBorder = selectedRestingBorder,
                focusedBorder = focusBorder,
                focusedSelectedBorder = focusBorder
            ),
            scale = SelectableSurfaceDefaults.scale(focusedScale = 1.02f, focusedSelectedScale = 1.02f)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) { FileThumbnail(entry, revision) }
                Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                size?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    } else {
        DenseListItem(
            selected = selected,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
            headlineContent = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            leadingContent = { FileThumbnail(entry, revision) },
            shape = ListItemDefaults.shape(VireoCardShape),
            trailingContent = size?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
            colors = ListItemDefaults.colors(
                containerColor = colors.surface,
                contentColor = colors.onSurface,
                selectedContainerColor = colors.primaryContainer,
                selectedContentColor = colors.onPrimaryContainer,
                focusedContainerColor = colors.surfaceVariant,
                focusedContentColor = colors.onSurface,
                focusedSelectedContainerColor = colors.primaryContainer,
                focusedSelectedContentColor = colors.onPrimaryContainer
            ),
            border = ListItemDefaults.border(
                focusedBorder = focusBorder, focusedSelectedBorder = focusBorder
            ),
            scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f)
        )
    }
}
