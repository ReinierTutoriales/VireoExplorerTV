package com.reiniertutoriales.vireoexplorertv

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException

@Composable
internal fun NetworkBrowser(onClose: () -> Unit) {
    val context = LocalContext.current
    val storage = remember { Storage(context.contentResolver) }
    var path by remember { mutableStateOf<List<Pair<Uri,String>>>(emptyList()) }
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                path = listOf(storage.root(uri) to "Provider")
            } catch (e: Exception) { error = e.localizedMessage ?: "Provider unavailable" }
        }
    }
    val current = path.lastOrNull()?.first
    BackHandler { if (path.size > 1) path = path.dropLast(1) else onClose() }
    LaunchedEffect(current) {
        entries = emptyList()
        error = null
        if (current != null) {
            loading = true
            try { entries = storage.children(current) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.localizedMessage ?: "Provider unavailable" }
            finally { loading = false }
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Network document providers")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (path.size > 1) path = path.dropLast(1) else onClose() }) { Text("Back") }
            Button(onClick = { try { picker.launch(null) } catch (_: Exception) { error = "System picker unavailable" } }) { Text("Select provider") }
        }
        Text("Requires an installed network document provider. Direct SMB and other protocols are not yet available.")
        if (loading) Text("Reading…")
        error?.let { Text(it) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.uri.toString() }) { entry ->
                Button(onClick = {
                    if (entry.directory) path = path + (entry.uri to entry.name)
                    else try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(entry.uri, entry.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    catch (_: Exception) { error = "No compatible viewer" }
                }, modifier = Modifier.fillMaxWidth()) { Text(entry.name) }
            }
        }
    }
}
