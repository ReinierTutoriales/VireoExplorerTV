package com.reiniertutoriales.vireoexplorertv

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.app.AlertDialog

@Composable
internal fun StorageAnalysis(storage: Storage, root: Uri, writable: Boolean, onClose: () -> Unit) {
    var result by remember(root) { mutableStateOf<ScanResult?>(null) }
    var error by remember(root) { mutableStateOf<String?>(null) }
    var mode by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf(false) }
    var selected by remember(root, revision) { mutableStateOf<Set<Uri>>(emptySet()) }
    var removed by remember(root) { mutableIntStateOf(0) }
    var removedBytes by remember(root) { mutableLongStateOf(0L) }
    var unknownSizes by remember(root) { mutableIntStateOf(0) }
    var failed by remember(root) { mutableIntStateOf(0) }
    fun recordRemoval(entry: Entry) {
        removed++
        if (!entry.directory) {
            if (entry.size >= 0) removedBytes += entry.size else unknownSizes++
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }
    BackHandler { if (!deleting) onClose() }
    LaunchedEffect(root, revision) {
        result = null
        try { result = storage.scan(root) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.localizedMessage ?: "Analysis failed" }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Storage analysis", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(modifier = Modifier.focusRequester(backFocus), enabled = !deleting, onClick = onClose) { Text("Back") }
            Button(enabled = result != null && !deleting, onClick = { mode = 0 }) { Text("Largest files") }
            Button(enabled = result != null && !deleting, onClick = { mode = 1 }) { Text("Possible duplicates") }
            Button(enabled = result != null && !deleting, onClick = { mode = 2 }) { Text("Cleanup review") }
        }
        if (removed > 0 || failed > 0) {
            Text("Session: $removed entries deleted · $failed failed · " + android.text.format.Formatter.formatFileSize(context, removedBytes) + " known deleted file sizes")
            if (unknownSizes > 0) Text("$unknownSizes deleted files with unknown size")
            Text("Deleted file sizes do not guarantee equivalent physical space recovered.")
        }
        if (!writable) Text("Read-only folder. You can analyze files here.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val report = result
        if (deleting) Text("Deleting selected files…")
        if (report == null && error == null) Text("Analyzing accessible files…")
        if (report != null) {
            Text("${report.count} files · ${report.bytes / 1048576} MiB (known sizes)")
            if (report.limited || report.failures > 0) Text("Partial analysis: scan limits or inaccessible files")
            if (mode == 2) {
                if (report.candidates.isEmpty()) Text("No cleanup candidates found.")
                else if (writable) Text("Select candidates individually. Files may still be needed.")
                val targets = report.candidates.filter { it.uri in selected && it.flags and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0 }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = writable && !deleting && targets.isNotEmpty(), onClick = {
                        AlertDialog.Builder(context).setTitle("Permanently delete selected entries?")
                            .setMessage("${targets.size} selected entries. Each will be checked again before deletion.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                                deleting = true
                                scope.launch {
                                    var batchFailures = 0
                                    try {
                                        for (entry in targets) {
                                            try {
                                                storage.cleanCandidate(entry)
                                                recordRemoval(entry)
                                            } catch (e: CancellationException) { throw e }
                                            catch (_: Exception) { failed++; batchFailures++ }
                                        }
                                        error = if (batchFailures > 0) "$batchFailures selected entries could not be deleted" else null
                                    } finally { deleting = false; revision++ }
                                }
                            }.show().getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()
                    }) { Text("Delete selected (${targets.size})") }
                    Button(enabled = !deleting && selected.isNotEmpty(), onClick = { selected = emptySet() }) { Text("Clear selection") }
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(report.candidates, key = { it.uri.toString() }) { entry ->
                        Button(enabled = writable && !deleting && entry.flags and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0, onClick = {
                            selected = if (entry.uri in selected) selected - entry.uri else selected + entry.uri
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text((if (entry.uri in selected) "Selected: " else "Select: ") + entry.name)
                                Text(if (entry.directory) "Empty folder" else cleanupReason(entry.name).orEmpty())
                                if (!entry.directory && entry.size >= 0) Text(android.text.format.Formatter.formatFileSize(context, entry.size))
                            }
                        }
                    }
                }
            } else if (mode == 1) {
                if (report.possibleDuplicates.isEmpty()) Text("No duplicate candidates found.")
                else if (writable) Text("Review matching files. The first copy is retained.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(report.possibleDuplicates) { group ->
                        Text("${group.size} matching files · ${group.first().size} bytes each")
                        val keep = group.first()
                        Text("Keep: ${keep.name} · ${keep.uri}")
                        group.drop(1).forEach { entry ->
                            Button(enabled = writable && !deleting && entry.flags and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0, onClick = {
                                AlertDialog.Builder(context).setTitle("Delete duplicate?")
                                    .setMessage("Delete ${entry.name} and retain ${keep.name}?")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Delete") { _, _ ->
                                        deleting = true
                                        scope.launch {
                                            try { storage.removeDuplicate(entry, keep); recordRemoval(entry); error = null }
                                            catch (e: CancellationException) { throw e }
                                            catch (e: Exception) { failed++; error = e.localizedMessage ?: "Duplicate deletion failed" }
                                            finally { deleting = false; revision++ }
                                        }
                                    }.show().getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()
                            }) { Text("Review: ${entry.name} · ${entry.uri}") }
                        }
                    }
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (report.largest.isEmpty()) item { Text("No accessible files found.") }
                items(report.largest, key = { it.uri.toString() }) { entry ->
                    Text("${entry.name} · ${entry.size / 1048576} MiB")
                }
            }
        }
    }
}
