package com.reiniertutoriales.vireoexplorertv

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Explorer() } }
    }

    @Composable private fun Explorer() {
        val storage = remember { Storage(contentResolver) }
        val scope = rememberCoroutineScope()
        var path by remember { mutableStateOf<List<Pair<Uri,String>>>(emptyList()) }
        var roots by remember { mutableStateOf(contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }) }
        var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        var revision by remember { mutableIntStateOf(0) }
        var selected by remember { mutableStateOf<Entry?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                roots = contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }
                path = listOf(storage.root(uri) to "Storage")
            } catch (e: Exception) { error = e.localizedMessage ?: "Permission unavailable" }
        }
        val parent = path.lastOrNull()?.first
        LaunchedEffect(parent, revision) {
            entries = emptyList()
            selected = null
            if (parent != null) {
                busy = true
                error = null
                try { entries = storage.children(parent) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.localizedMessage ?: "Cannot read folder" }
                finally { busy = false }
            }
        }
        BackHandler(selected != null || path.isNotEmpty()) {
            if(selected != null) selected = null else path = path.dropLast(1)
        }
        Column(Modifier.fillMaxSize().background(Color(0xFF080A0C)).padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("VireoExplorerTV", style = MaterialTheme.typography.headlineMedium)
            Text(path.joinToString(" / ") { it.second }.ifEmpty { "Storage locations" })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { try { picker.launch(null) } catch (e: Exception) { error = "System folder picker unavailable" } }) { Text("Add folder") }
                if(parent != null) {
                    Button(onClick = { path = path.dropLast(1) }) { Text("Back") }
                    Button(onClick = { revision++ }) { Text("Refresh") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if(busy) Text("Reading storage…")
            val item = selected
            if(item != null) {
                Text(item.name)
                Text(if(item.size >= 0) "${item.size} bytes" else item.mime)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri,item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        catch(e: Exception) { error = "No compatible viewer installed" }
                    }) { Text("Open") }
                    Button(onClick = { selected = null }) { Text("Close") }
                }
            } else if(parent == null) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(roots, key = { it.toString() }) { uri ->
                        Button(onClick = { path = listOf(storage.root(uri) to DocumentsContract.getTreeDocumentId(uri)) }) { Text(DocumentsContract.getTreeDocumentId(uri)) }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.uri.toString() }) { entry ->
                        Button(onClick = { if(entry.directory) path = path + (entry.uri to entry.name) else selected = entry }, modifier = Modifier.fillMaxWidth()) {
                            Text((if(entry.directory) "▸  " else "") + entry.name)
                        }
                    }
                }
            }
        }
    }
}
