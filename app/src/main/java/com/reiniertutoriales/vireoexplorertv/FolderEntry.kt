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
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val restingBorder = Border.None
    val selectedRestingBorder = Border(BorderStroke(1.dp, colors.primary), shape = shape)
    val focusBorder = Border(BorderStroke(3.dp, colors.primary), shape = shape)
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
            modifier = modifier.heightIn(min = 112.dp),
            shape = SelectableSurfaceDefaults.shape(shape),
            colors = SelectableSurfaceDefaults.colors(
                containerColor = if (entry.directory) colors.background else colors.surface,
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
            scale = SelectableSurfaceDefaults.scale(focusedScale = 1.05f, focusedSelectedScale = 1.05f)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().height(if (entry.directory) 74.dp else 88.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (entry.directory) {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.symbol_folder), contentDescription = null,
                            modifier = Modifier.size(64.dp), tint = colors.primary)
                    } else {
                        FileThumbnail(entry, revision, Modifier.fillMaxSize())
                    }
                    if (selected) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp)
                                .background(colors.primary, VireoPillShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = colors.onPrimary)
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
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
                    typeLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            },
            leadingContent = {
                Box(
                    Modifier
                        .size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (entry.directory) Icon(androidx.compose.ui.res.painterResource(R.drawable.symbol_folder), null,
                        modifier = Modifier.size(36.dp), tint = colors.primary)
                    else FileThumbnail(entry, revision)
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
            scale = ListItemDefaults.scale(focusedScale = 1.05f, focusedSelectedScale = 1.05f)
        )
    }
}
