package com.reiniertutoriales.vireoexplorertv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*

internal enum class ExplorerPage(val label: Int, val icon: Int) {
    HOME(R.string.explorer_home, R.drawable.symbol_home),
    FILES(R.string.explorer_files, R.drawable.symbol_folder),
    NETWORK(R.string.explorer_network, R.drawable.symbol_lan),
    CLEANER(R.string.explorer_cleaner, R.drawable.symbol_cleaning_services),
    TRANSFERS(R.string.explorer_transfers, R.drawable.symbol_swap_horiz),
    FAVORITES(R.string.explorer_favorites, R.drawable.symbol_star),
    SETTINGS(R.string.explorer_settings, R.drawable.symbol_settings),
}

internal enum class FileCategory(val label: Int, val icon: Int) {
    VIDEO(R.string.explorer_videos, R.drawable.symbol_movie),
    AUDIO(R.string.explorer_audio, R.drawable.symbol_music_note),
    IMAGE(R.string.explorer_photos, R.drawable.symbol_image),
    DOCUMENT(R.string.explorer_documents, R.drawable.symbol_description),
    ZIP(R.string.explorer_archives, R.drawable.symbol_folder_zip),
    APK(R.string.explorer_apk, R.drawable.symbol_android);

    fun matches(entry: Entry): Boolean {
        if (entry.directory) return true
        val mime = mediaMime(entry.name, entry.mime)
        return when (this) {
            VIDEO -> mime.startsWith("video/")
            AUDIO -> mime.startsWith("audio/")
            IMAGE -> mime.startsWith("image/")
            ZIP -> entry.name.endsWith(".zip", ignoreCase = true) || mime == "application/zip"
            APK -> entry.name.endsWith(".apk", ignoreCase = true) || mime == "application/vnd.android.package-archive"
            DOCUMENT -> mime.startsWith("text/") || mime == "application/pdf" ||
                entry.name.substringAfterLast('.').lowercase(java.util.Locale.ROOT) in documentExtensions
        }
    }
}

private val documentExtensions = setOf("doc", "docx", "odt", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "rtf", "txt", "md", "csv", "pdf")

@Composable
internal fun ExplorerHome(
    page: ExplorerPage,
    notice: String?,
    onPage: (ExplorerPage) -> Unit,
    onSearch: () -> Unit,
    onInternal: () -> Unit,
    onUsb: () -> Unit,
    onFolders: () -> Unit,
    onCategory: (FileCategory) -> Unit,
    content: LazyListScope.() -> Unit
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(page) { initialFocus.requestFocus() }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("VireoExplorerTV", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onSearch) {
                ExplorerIcon(R.drawable.symbol_search, description = stringResource(R.string.explorer_search))
            }
            IconButton(
                onClick = { onPage(ExplorerPage.SETTINGS) },
                modifier = if (page == ExplorerPage.SETTINGS) Modifier.focusRequester(initialFocus) else Modifier
            ) {
                ExplorerIcon(R.drawable.symbol_settings, description = stringResource(R.string.explorer_settings))
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LazyColumn(Modifier.width(174.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)) {
                items(ExplorerPage.entries.size - 1) { index ->
                    val destination = ExplorerPage.entries[index]
                    DenseListItem(
                        selected = destination == page,
                        onClick = { onPage(destination) },
                        modifier = Modifier.fillMaxWidth()
                            .then(if (destination == page && page != ExplorerPage.HOME) Modifier.focusRequester(initialFocus) else Modifier),
                        leadingContent = { ExplorerIcon(destination.icon) },
                        shape = ListItemDefaults.shape(VireoPillShape),
                        scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
                        colors = ListItemDefaults.colors(
                            selectedContainerColor = colors.primaryContainer,
                            selectedContentColor = colors.onPrimaryContainer,
                            focusedContainerColor = colors.inverseSurface,
                            focusedContentColor = colors.inverseOnSurface,
                            focusedSelectedContainerColor = colors.inverseSurface,
                            focusedSelectedContentColor = colors.inverseOnSurface
                        ),
                        headlineContent = {
                            Text(stringResource(destination.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    )
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 4.dp, top = 4.dp, end = 6.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (page == ExplorerPage.HOME) {
                    item { Text(stringResource(R.string.explorer_storage), style = MaterialTheme.typography.titleLarge) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExplorerTile(R.string.explorer_internal, R.drawable.symbol_hard_drive,
                                Modifier.weight(1f).focusRequester(initialFocus), onClick = onInternal)
                            ExplorerTile(R.string.explorer_usb, R.drawable.symbol_usb, Modifier.weight(1f), onClick = onUsb)
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExplorerTile(R.string.explorer_local_network, R.drawable.symbol_lan, Modifier.weight(1f),
                                onClick = { onPage(ExplorerPage.NETWORK) })
                            ExplorerTile(R.string.explorer_folders, R.drawable.symbol_folder, Modifier.weight(1f), onClick = onFolders)
                        }
                    }
                    if (notice != null) item { Text(notice, color = MaterialTheme.colorScheme.primary) }
                    item { Text(stringResource(R.string.explorer_categories), style = MaterialTheme.typography.titleLarge) }
                    FileCategory.entries.chunked(3).forEach { row ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { category ->
                                    ExplorerTile(category.label, category.icon, Modifier.weight(1f), compact = true) {
                                        onCategory(category)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item { Text(stringResource(page.label), style = MaterialTheme.typography.titleLarge) }
                    if (notice != null) item { Text(notice, color = MaterialTheme.colorScheme.primary) }
                    content()
                }
            }
        }
    }
}

@Composable
internal fun ExplorerIcon(icon: Int, modifier: Modifier = Modifier, description: String? = null) {
    Icon(painterResource(icon), contentDescription = description, modifier = modifier.size(24.dp))
}

@Composable
private fun ExplorerTile(label: Int, icon: Int, modifier: Modifier, compact: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = VireoCardShape
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = colors.surface,
            contentColor = colors.onSurface,
            focusedContainerColor = colors.primaryContainer,
            focusedContentColor = colors.onPrimaryContainer,
            pressedContainerColor = colors.primary,
            pressedContentColor = colors.onPrimary
        ),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, colors.primary), shape = shape)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (compact) 72.dp else 92.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(if (compact) 40.dp else 48.dp).background(colors.surfaceVariant, VireoPillShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.primary)
            }
            Text(
                stringResource(label),
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ExplorerOption(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val focus = Border(BorderStroke(2.dp, colors.primary), shape = VireoPillShape)
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = SelectableSurfaceDefaults.shape(VireoPillShape),
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
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        border = SelectableSurfaceDefaults.border(focusedBorder = focus, focusedSelectedBorder = focus)
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon?.invoke()
            content()
        }
    }
}
