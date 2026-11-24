package com.reiniertutoriales.vireoexplorertv

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.app.AlertDialog

@Composable
internal fun StorageAnalysis(storage: Storage, root: Uri, onClose: () -> Unit) {
    var result by remember(root) { mutableStateOf<ScanResult?>(null) }
    var error by remember(root) { mutableStateOf<String?>(null) }
    var mode by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    BackHandler { if (!deleting) onClose() }
    LaunchedEffect(root, revision) {
        result = null
        try { result = storage.scan(root) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.localizedMessage ?: "Analysis failed" }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Storage analysis")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !deleting, onClick = onClose) { Text("Back / Cancel") }
            Button(enabled = result != null, onClick = { mode = 0 }) { Text("Largest files") }
            Button(enabled = result != null, onClick = { mode = 1 }) { Text("Possible duplicates") }
            Button(enabled = result != null && !deleting, onClick = { mode = 2 }) { Text("Cleanup review") }
        }
        error?.let { Text(it) }
        val report = result
        if (report == null) Text(error ?: "Analyzing accessible files…")
        else {
            Text("${report.count} files · ${report.bytes / 1048576} MiB (known sizes)")
            if (report.limited || report.failures > 0) Text("Partial analysis: scan limits or inaccessible files")
            if (mode == 2) {
                Text("Review individually. Candidates may still be needed.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(report.candidates, key = { it.uri.toString() }) { entry ->
                        Button(enabled = !deleting && entry.flags and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0, onClick = {
                            AlertDialog.Builder(context).setTitle("Permanently delete?")
                                .setMessage(entry.name + "\n" + (if (entry.directory) "Empty folder" else cleanupReason(entry.name)))
                                .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                                    deleting = true
                                    scope.launch {
                                        try { storage.cleanCandidate(entry); error = null }
                                        catch (e: CancellationException) { throw e }
                                        catch (e: Exception) { error = e.localizedMessage ?: "Deletion failed" }
                                        finally { deleting = false; revision++ }
                                    }
                                }.show()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column { Text(entry.name); Text(if (entry.directory) "Empty folder" else cleanupReason(entry.name).orEmpty()) }
                        }
                    }
                }
            } else if (mode == 1) {
                Text("Review matching files. First copy is retained; contents compared before deletion.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(report.possibleDuplicates) { group ->
                        Text("${group.size} matching files · ${group.first().size} bytes each")
                        val keep = group.first()
                        Text("Keep: ${keep.name} · ${keep.uri}")
                        group.drop(1).forEach { entry ->
                            Button(enabled = !deleting && entry.flags and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0, onClick = {
                                AlertDialog.Builder(context).setTitle("Delete duplicate?")
                                    .setMessage("Delete ${entry.name} and retain ${keep.name}?")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Delete") { _, _ ->
                                        deleting = true
                                        scope.launch {
                                            try { storage.removeDuplicate(entry, keep); error = null }
                                            catch (e: CancellationException) { throw e }
                                            catch (e: Exception) { error = e.localizedMessage ?: "Duplicate deletion failed" }
                                            finally { deleting = false; revision++ }
                                        }
                                    }.show()
                            }) { Text("Review: ${entry.name} · ${entry.uri}") }
                        }
                    }
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(report.largest, key = { it.uri.toString() }) { entry ->
                    Text("${entry.name} · ${entry.size / 1048576} MiB")
                }
            }
        }
    }
}
