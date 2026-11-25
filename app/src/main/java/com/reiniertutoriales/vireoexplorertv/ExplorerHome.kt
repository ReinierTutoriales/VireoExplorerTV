package com.reiniertutoriales.vireoexplorertv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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

    fun matches(entry: Entry): Boolean = matches(entry.directory, entry.name, entry.mime)

    fun matches(directory: Boolean, name: String, reportedMime: String): Boolean {
        if (directory) return true
        val mime = mediaMime(name, reportedMime)
        return when (this) {
            VIDEO -> mime.startsWith("video/")
            AUDIO -> mime.startsWith("audio/")
            IMAGE -> mime.startsWith("image/")
            ZIP -> name.endsWith(".zip", ignoreCase = true) || mime == "application/zip"
            APK -> name.endsWith(".apk", ignoreCase = true) || mime == "application/vnd.android.package-archive"
            DOCUMENT -> mime.startsWith("text/") || mime == "application/pdf" ||
                name.substringAfterLast('.').lowercase(java.util.Locale.ROOT) in documentExtensions
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
    val drawerHomeFocus = remember { FocusRequester() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    LaunchedEffect(page) { initialFocus.requestFocus() }
    val colors = MaterialTheme.colorScheme
    val drawerItemColors = NavigationDrawerItemDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = colors.onSurfaceVariant,
        selectedContainerColor = colors.primaryContainer,
        selectedContentColor = colors.onPrimaryContainer,
        focusedContainerColor = colors.inverseSurface,
        focusedContentColor = colors.inverseOnSurface,
        focusedSelectedContainerColor = colors.inverseSurface,
        focusedSelectedContentColor = colors.inverseOnSurface
    )

    NavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(Color.Black)
                    .padding(start = 16.dp, top = 24.dp, bottom = 24.dp, end = 8.dp)
                    .focusProperties {
                        onEnter = {
                            if (page == ExplorerPage.HOME) {
                                drawerHomeFocus.requestFocus()
                            } else {
                                initialFocus.requestFocus()
                            }
                        }
                    }
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val mainDestinations = ExplorerPage.entries.dropLast(1)
                mainDestinations.forEach { destination ->
                    NavigationDrawerItem(
                        selected = destination == page,
                        onClick = { onPage(destination) },
                        leadingContent = { ExplorerIcon(destination.icon) },
                        modifier = when {
                            destination == ExplorerPage.HOME -> Modifier.focusRequester(drawerHomeFocus)
                            destination == page -> Modifier.focusRequester(initialFocus)
                            else -> Modifier
                        },
                        shape = NavigationDrawerItemDefaults.shape(shape = VireoPillShape),
                        colors = drawerItemColors,
                        scale = NavigationDrawerItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f)
                    ) {
                        Text(stringResource(destination.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.weight(1f))
                val settingsDest = ExplorerPage.SETTINGS
                NavigationDrawerItem(
                    selected = page == settingsDest,
                    onClick = { onPage(settingsDest) },
                    leadingContent = { ExplorerIcon(settingsDest.icon) },
                    modifier = if (page == settingsDest) Modifier.focusRequester(initialFocus) else Modifier,
                    shape = NavigationDrawerItemDefaults.shape(shape = VireoPillShape),
                    colors = drawerItemColors,
                    scale = NavigationDrawerItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f)
                ) {
                    Text(stringResource(settingsDest.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 40.dp, top = 24.dp, bottom = 24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(page.label),
                    Modifier.weight(1f),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.headlineMedium
                )
                IconButton(onClick = onSearch) {
                    ExplorerIcon(R.drawable.symbol_search, description = stringResource(R.string.explorer_search))
                }
                IconButton(onClick = { onPage(ExplorerPage.SETTINGS) }) {
                    ExplorerIcon(R.drawable.symbol_settings, description = stringResource(R.string.explorer_settings))
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 6.dp, top = 6.dp, end = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (page == ExplorerPage.HOME) {
                    item { Text(stringResource(R.string.explorer_storage), style = MaterialTheme.typography.titleLarge) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ExplorerTile(
                                label = R.string.explorer_internal,
                                subtitle = R.string.explorer_internal_subtitle,
                                icon = R.drawable.symbol_hard_drive,
                                modifier = Modifier.weight(1f).focusRequester(initialFocus),
                                onClick = onInternal
                            )
                            ExplorerTile(
                                label = R.string.explorer_usb,
                                subtitle = R.string.explorer_usb_subtitle,
                                icon = R.drawable.symbol_usb,
                                modifier = Modifier.weight(1f),
                                onClick = onUsb
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ExplorerTile(
                                label = R.string.explorer_local_network,
                                subtitle = R.string.explorer_local_network_subtitle,
                                icon = R.drawable.symbol_lan,
                                modifier = Modifier.weight(1f),
                                onClick = { onPage(ExplorerPage.NETWORK) }
                            )
                            ExplorerTile(
                                label = R.string.explorer_folders,
                                subtitle = R.string.explorer_folders_subtitle,
                                icon = R.drawable.symbol_folder,
                                modifier = Modifier.weight(1f),
                                onClick = onFolders
                            )
                        }
                    }
                    if (notice != null) item { Text(notice, color = colors.primary) }
                    item { Text(stringResource(R.string.explorer_categories), style = MaterialTheme.typography.titleLarge) }
                    FileCategory.entries.chunked(3).forEach { row ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                row.forEach { category ->
                                    ExplorerTile(
                                        label = category.label,
                                        subtitle = null,
                                        icon = category.icon,
                                        modifier = Modifier.weight(1f),
                                        compact = true
                                    ) {
                                        onCategory(category)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item { Text(stringResource(page.label), style = MaterialTheme.typography.titleLarge) }
                    if (notice != null) item { Text(notice, color = colors.primary) }
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
private fun ExplorerTile(
    label: Int,
    subtitle: Int?,
    icon: Int,
    modifier: Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
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
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(3.dp, colors.primary), shape = shape)
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 64.dp else 80.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(if (compact) 34.dp else 44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 28.dp else 36.dp),
                    tint = colors.primary
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(label),
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        stringResource(subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
    val restingBorder = Border(BorderStroke(1.dp, if (selected) colors.primary else colors.border), shape = VireoPillShape)
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
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.02f, focusedSelectedScale = 1.02f),
        border = SelectableSurfaceDefaults.border(
            border = restingBorder,
            selectedBorder = restingBorder,
            focusedBorder = focus,
            focusedSelectedBorder = focus
        )
    ) {
        Row(
            Modifier.heightIn(min = 44.dp).padding(start = if (selected) 8.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Box(
                    Modifier.size(26.dp).background(colors.primary, VireoPillShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = colors.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                leadingIcon?.invoke()
            }
            content()
        }
    }
}
