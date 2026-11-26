package com.reiniertutoriales.vireoexplorertv

import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun StorageAnalysis(storage: Storage, root: Uri, writable: Boolean, onClose: () -> Unit) {
    var result by remember(root) { mutableStateOf<ScanResult?>(null) }
    var error by remember(root) { mutableStateOf<String?>(null) }
    var mode by remember { mutableIntStateOf(2) }
    var revision by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf(false) }
    var selected by remember(root, revision) { mutableStateOf<Set<Uri>>(emptySet()) }
    var removed by remember(root) { mutableIntStateOf(0) }
    var removedBytes by remember(root) { mutableLongStateOf(0L) }
    var failed by remember(root) { mutableIntStateOf(0) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var confirmDuplicateDelete by remember { mutableStateOf<Pair<Entry, Entry>?>(null) }
    var confirmLargestDelete by remember { mutableStateOf<Entry?>(null) }

    fun recordRemoval(entry: Entry) {
        removed++
        if (!entry.directory && entry.size >= 0) {
            removedBytes += entry.size
        }
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }
    BackHandler { if (!deleting) onClose() }
    LaunchedEffect(root, revision) {
        result = null
        try {
            val scanned = storage.scan(root)
            result = scanned
            if (scanned.candidates.isEmpty() && scanned.largest.isNotEmpty() && mode == 2) {
                mode = 0
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Analysis failed"
        }
    }

    val report = result
    val targets = remember(report, selected) {
        report?.candidates.orEmpty().filter {
            it.uri in selected && it.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0
        }
    }

    if (confirmBatchDelete && targets.isNotEmpty()) {
        TvConfirmDialog(
            title = "Delete selected items?",
            message = stringResource(
                R.string.analysis_confirm_selected,
                targets.size,
                Formatter.formatFileSize(context, targets.sumOf { it.size.coerceAtLeast(0L) })
            ),
            confirmLabel = "Delete",
            onConfirm = {
                deleting = true
                scope.launch {
                    var batchFailures = 0
                    try {
                        for (entry in targets) {
                            try {
                                storage.cleanCandidate(entry)
                                recordRemoval(entry)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                failed++
                                batchFailures++
                            }
                        }
                        error = if (batchFailures > 0) "$batchFailures selected items could not be deleted" else null
                    } finally {
                        deleting = false
                        revision++
                    }
                }
            },
            onDismiss = { confirmBatchDelete = false }
        )
    }

    val duplicatePair = confirmDuplicateDelete
    if (duplicatePair != null) {
        val (entry, keep) = duplicatePair
        TvConfirmDialog(
            title = "Delete duplicate copy?",
            message = "Delete ${entry.name} and keep ${keep.name}?",
            confirmLabel = "Delete",
            onConfirm = {
                deleting = true
                scope.launch {
                    try {
                        storage.removeDuplicate(entry, keep)
                        recordRemoval(entry)
                        error = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                        error = e.localizedMessage ?: "Duplicate deletion failed"
                    } finally {
                        deleting = false
                        revision++
                    }
                }
            },
            onDismiss = { confirmDuplicateDelete = null }
        )
    }

    val singleLargest = confirmLargestDelete
    if (singleLargest != null) {
        TvConfirmDialog(
            title = "Delete large file?",
            message = "${singleLargest.name} (${Formatter.formatFileSize(context, singleLargest.size.coerceAtLeast(0L))})",
            confirmLabel = "Delete",
            onConfirm = {
                deleting = true
                scope.launch {
                    try {
                        storage.delete(singleLargest)
                        recordRemoval(singleLargest)
                        error = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                        error = e.localizedMessage ?: "Delete failed"
                    } finally {
                        deleting = false
                        revision++
                    }
                }
            },
            onDismiss = { confirmLargestDelete = null }
        )
    }

    val restingBorder = Border(BorderStroke(1.dp, colors.borderVariant), shape = VireoCardShape)
    val selectedBorder = Border(BorderStroke(1.dp, colors.primary), shape = VireoCardShape)
    val focusBorder = Border(BorderStroke(2.dp, colors.primary), shape = VireoCardShape)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.focusRequester(backFocus),
                enabled = !deleting,
                onClick = onClose
            ) {
                Text(stringResource(R.string.explorer_back))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.analysis_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (report != null) {
                    Text(
                        stringResource(
                            R.string.analysis_summary,
                            report.count,
                            Formatter.formatFileSize(context, report.bytes)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            if (report != null) {
                ExplorerOption(
                    selected = mode == 2,
                    enabled = !deleting,
                    onClick = { mode = 2 }
                ) {
                    Text(stringResource(R.string.analysis_tab_candidates, report.candidates.size))
                }
                ExplorerOption(
                    selected = mode == 0,
                    enabled = !deleting,
                    onClick = { mode = 0 }
                ) {
                    Text(stringResource(R.string.analysis_tab_largest, report.largest.size))
                }
                ExplorerOption(
                    selected = mode == 1,
                    enabled = !deleting,
                    onClick = { mode = 1 }
                ) {
                    Text(stringResource(R.string.analysis_tab_duplicates, report.possibleDuplicates.size))
                }
            }
        }

        if (removed > 0 || failed > 0) {
            Text(
                "Freed: ${Formatter.formatFileSize(context, removedBytes)} ($removed deleted" +
                    (if (failed > 0) ", $failed failed)" else ")"),
                color = colors.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        error?.let { Text(it, color = colors.error) }
        if (deleting) Text(stringResource(R.string.explorer_working), color = colors.primary)
        if (report == null && error == null) {
            Surface(
                shape = VireoCardShape,
                colors = SurfaceDefaults.colors(containerColor = colors.surface, contentColor = colors.onSurface),
                border = restingBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.analysis_scanning),
                    Modifier.padding(24.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (report != null) {
            when (mode) {
                2 -> {
                    if (report.candidates.isNotEmpty() && writable) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ExplorerOption(
                                selected = selected.size == report.candidates.size,
                                enabled = !deleting,
                                onClick = {
                                    selected = if (selected.size == report.candidates.size) emptySet()
                                    else report.candidates.map { it.uri }.toSet()
                                }
                            ) {
                                Text(
                                    if (selected.size == report.candidates.size) stringResource(R.string.analysis_clear_all)
                                    else stringResource(R.string.analysis_select_all, report.candidates.size)
                                )
                            }
                            if (targets.isNotEmpty()) {
                                Button(
                                    enabled = !deleting,
                                    onClick = { confirmBatchDelete = true }
                                ) {
                                    Text(stringResource(R.string.analysis_delete_selected, targets.size))
                                }
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (report.candidates.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.analysis_no_candidates),
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                        items(report.candidates, key = { it.uri.toString() }) { entry ->
                            val isSelected = entry.uri in selected
                            val canDelete = writable && !deleting &&
                                (entry.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0)
                            ListItem(
                                selected = isSelected,
                                enabled = canDelete,
                                onClick = {
                                    selected = if (isSelected) selected - entry.uri else selected + entry.uri
                                },
                                leadingContent = {
                                    Box(
                                        Modifier.size(44.dp).background(colors.surfaceVariant, VireoPillShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FileTypeIcon(entry)
                                    }
                                },
                                headlineContent = {
                                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(
                                        if (entry.directory) stringResource(R.string.analysis_empty_folder)
                                        else cleanupReason(entry.name).orEmpty(),
                                        color = colors.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!entry.directory && entry.size >= 0) {
                                            Text(
                                                Formatter.formatFileSize(context, entry.size),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = colors.primary
                                            )
                                        }
                                        if (isSelected) {
                                            Box(
                                                Modifier.size(26.dp).background(colors.primary, VireoPillShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("✓", color = colors.onPrimary, style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                },
                                shape = ListItemDefaults.shape(VireoCardShape),
                                border = ListItemDefaults.border(
                                    border = restingBorder,
                                    selectedBorder = selectedBorder,
                                    focusedBorder = focusBorder,
                                    focusedSelectedBorder = focusBorder
                                )
                            )
                        }
                    }
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (report.possibleDuplicates.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.analysis_no_duplicates),
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                        items(report.possibleDuplicates) { group ->
                            val keep = group.first()
                            val eachSize = Formatter.formatFileSize(context, keep.size.coerceAtLeast(0L))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "${group.size} identical copies · $eachSize each (keeping ${keep.name})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.primary
                                )
                                group.drop(1).forEach { entry ->
                                    ListItem(
                                        selected = false,
                                        enabled = writable && !deleting &&
                                            (entry.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0),
                                        onClick = { confirmDuplicateDelete = entry to keep },
                                        leadingContent = {
                                            Box(
                                                Modifier.size(40.dp).background(colors.surfaceVariant, VireoPillShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                FileTypeIcon(entry)
                                            }
                                        },
                                        headlineContent = {
                                            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        supportingContent = {
                                            Text(entry.uri.path ?: entry.uri.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        trailingContent = {
                                            Text(eachSize, color = colors.primary, style = MaterialTheme.typography.labelLarge)
                                        },
                                        shape = ListItemDefaults.shape(VireoCardShape),
                                        border = ListItemDefaults.border(border = restingBorder, focusedBorder = focusBorder)
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (report.largest.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.analysis_no_files),
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                        items(report.largest, key = { it.uri.toString() }) { entry ->
                            val canDelete = writable && !deleting &&
                                (entry.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0)
                            ListItem(
                                selected = false,
                                enabled = canDelete,
                                onClick = { if (canDelete) confirmLargestDelete = entry },
                                leadingContent = {
                                    Box(
                                        Modifier.size(44.dp).background(colors.surfaceVariant, VireoPillShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FileTypeIcon(entry)
                                    }
                                },
                                headlineContent = {
                                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(
                                        entry.uri.path ?: entry.mime,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = colors.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        Formatter.formatFileSize(context, entry.size.coerceAtLeast(0L)),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.primary
                                    )
                                },
                                shape = ListItemDefaults.shape(VireoCardShape),
                                border = ListItemDefaults.border(border = restingBorder, focusedBorder = focusBorder)
                            )
                        }
                    }
                }
            }
        }
    }
}
