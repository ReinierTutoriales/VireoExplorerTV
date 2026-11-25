package com.reiniertutoriales.vireoexplorertv

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import java.util.Locale

internal fun entryTypeSubtitle(entry: Entry, folderLabel: String): String {
    if (entry.directory) return folderLabel
    val ext = entry.name.substringAfterLast('.', "").uppercase(Locale.ROOT)
    return if (ext.isNotEmpty() && ext.length <= 8) ext else entry.mime
}

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
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val shape = VireoCardShape
    val restingBorder = Border(BorderStroke(1.dp, colors.borderVariant), shape = shape)
    val selectedRestingBorder = Border(BorderStroke(1.dp, colors.primary), shape = shape)
    val focusBorder = Border(BorderStroke(2.dp, colors.primary), shape = shape)
    val size = if (!entry.directory && entry.size >= 0) {
        Formatter.formatFileSize(context, entry.size)
    } else null
    val typeLabel = entryTypeSubtitle(entry, stringResource(R.string.explorer_type_folder))
    val subtitle = if (size != null) "$typeLabel · $size" else typeLabel

    if (grid) {
        Surface(
            selected = selected,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.heightIn(min = 124.dp),
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
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(colors.surfaceVariant, VireoPillShape),
                        contentAlignment = Alignment.Center
                    ) {
                        FileThumbnail(entry, revision)
                    }
                    if (selected) {
                        Box(
                            Modifier.size(26.dp).background(colors.primary, VireoPillShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = colors.onPrimary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Text(
                    entry.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    } else {
        ListItem(
            selected = selected,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
            headlineContent = {
                Text(
                    entry.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            },
            leadingContent = {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(colors.surfaceVariant, VireoPillShape),
                    contentAlignment = Alignment.Center
                ) {
                    FileThumbnail(entry, revision)
                }
            },
            shape = ListItemDefaults.shape(VireoCardShape),
            trailingContent = if (selected) {
                {
                    Box(
                        Modifier.size(26.dp).background(colors.primary, VireoPillShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", color = colors.onPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else size?.let {
                { Text(it, style = MaterialTheme.typography.labelLarge, color = colors.primary) }
            },
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
                border = restingBorder,
                selectedBorder = selectedRestingBorder,
                focusedBorder = focusBorder,
                focusedSelectedBorder = focusBorder
            ),
            scale = ListItemDefaults.scale(focusedScale = 1.01f, focusedSelectedScale = 1.01f)
        )
    }
}
