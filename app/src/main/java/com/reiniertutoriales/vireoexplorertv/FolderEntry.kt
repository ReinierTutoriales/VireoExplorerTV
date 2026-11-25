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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import java.util.Locale

internal fun entryTypeSubtitle(entry: Entry, folderLabel: String): String {
    if (entry.directory) return folderLabel
    val ext = entry.name.substringAfterLast('.', "").uppercase(Locale.ROOT)
    return if (ext.isNotEmpty() && ext.length <= 8) ext else ""
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
    val type = entryTypeSubtitle(entry, stringResource(R.string.explorer_type_folder))
    ExplorerFileItem(
        name = entry.name, directory = entry.directory, bytes = entry.size,
        typeLabel = type, grid = grid, selected = selected, enabled = enabled,
        onClick = onClick, onLongClick = onLongClick, modifier = modifier
    ) { artworkModifier -> FileThumbnail(entry, revision, artworkModifier) }
}

internal val ExplorerGridWidth = 156.dp

@Composable
internal fun ExplorerFileItem(
    name: String,
    directory: Boolean,
    bytes: Long,
    typeLabel: String,
    grid: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    artwork: @Composable (Modifier) -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val restingBorder = Border.None
    val selectedRestingBorder = Border(BorderStroke(1.dp, colors.primary), shape = shape)
    val focusBorder = Border(BorderStroke(3.dp, colors.primary), shape = shape)
    val size = if (!directory && bytes >= 0) Formatter.formatFileSize(context, bytes) else null
    val subtitle = listOfNotNull(typeLabel.takeIf { it.isNotBlank() && !directory }, size).joinToString(" · ")

    if (grid) {
        Surface(
            selected = selected,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
            shape = SelectableSurfaceDefaults.shape(shape),
            colors = SelectableSurfaceDefaults.colors(
                containerColor = if (directory) colors.background else colors.surface,
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
                    Modifier.fillMaxWidth().height(92.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (directory) {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.symbol_folder), contentDescription = null,
                            modifier = Modifier.size(80.dp), tint = colors.primary)
                    } else {
                        artwork(Modifier.fillMaxSize())
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
                    Text(name, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 2,
                        textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall)
                    if (subtitle.isNotEmpty()) Text(subtitle, modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    else Spacer(Modifier.height(16.dp))
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
                    name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = if (!directory && typeLabel.isNotBlank()) {{
                Text(
                    typeLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }} else null,
            leadingContent = {
                Box(
                    Modifier
                        .size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (directory) Icon(androidx.compose.ui.res.painterResource(R.drawable.symbol_folder), null,
                        modifier = Modifier.size(36.dp), tint = colors.primary)
                    else artwork(Modifier.size(40.dp))
                }
            },
            shape = ListItemDefaults.shape(shape),
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
                containerColor = colors.background,
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
