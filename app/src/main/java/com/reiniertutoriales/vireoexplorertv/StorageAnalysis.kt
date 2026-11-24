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

@Composable
internal fun StorageAnalysis(storage: Storage, root: Uri, onClose: () -> Unit) {
    var result by remember(root) { mutableStateOf<ScanResult?>(null) }
    var error by remember(root) { mutableStateOf<String?>(null) }
    var duplicates by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    LaunchedEffect(root) {
        try { result = storage.scan(root) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.localizedMessage ?: "Analysis failed" }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Storage analysis")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onClose) { Text("Back / Cancel") }
            Button(enabled = result != null, onClick = { duplicates = false }) { Text("Largest files") }
            Button(enabled = result != null, onClick = { duplicates = true }) { Text("Possible duplicates") }
        }
        val report = result
        if (report == null) Text(error ?: "Analyzing accessible files…")
        else {
            Text("${report.count} files · ${report.bytes / 1048576} MiB (known sizes)")
            if (report.limited || report.failures > 0) Text("Partial analysis: scan limits or inaccessible files")
            if (duplicates) {
                Text("Matching SHA-256 candidates; no files deleted")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(report.possibleDuplicates) { group ->
                        Text("${group.size} matching files · ${group.first().size} bytes each")
                        group.forEach { Text(it.name) }
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
