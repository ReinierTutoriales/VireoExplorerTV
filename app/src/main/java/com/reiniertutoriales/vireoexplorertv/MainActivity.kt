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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import android.app.AlertDialog
import android.widget.EditText
import android.text.InputType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VireoTheme { Explorer() } }
    }

    @Composable private fun Explorer() {
        val scope = rememberCoroutineScope()
        val storage = remember { Storage(contentResolver) }
        var path by remember { mutableStateOf<List<Pair<Uri,String>>>(emptyList()) }
        var roots by remember { mutableStateOf(contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }) }
        var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        var revision by remember { mutableIntStateOf(0) }
        var selected by remember { mutableStateOf<Entry?>(null) }
        var viewing by remember { mutableStateOf<Entry?>(null) }
        var clipboard by remember { mutableStateOf<Pair<Entry, Boolean>?>(null) }
        var transferJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var transferred by remember { mutableLongStateOf(0L) }
        var mutating by remember { mutableStateOf(false) }
        fun mutate(action: suspend () -> Unit) {
            if (mutating) return
            mutating = true
            scope.launch {
                try { action(); selected = null; revision++ }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.localizedMessage ?: "Operation failed" }
                finally { mutating = false }
            }
        }
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
        val video = viewing?.takeIf { it.mime.startsWith("video/") || it.mime.startsWith("audio/") }
        if (video != null) {
            MediaViewer(video, onClose = { viewing = null })
            return
        }
        val image = viewing
        if (image != null) {
            ImageViewer(image, contentResolver, onClose = { viewing = null })
            return
        }
        BackHandler(mutating || selected != null || path.isNotEmpty()) {
            if (mutating) return@BackHandler
            if(selected != null) selected = null else path = path.dropLast(1)
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("VireoExplorerTV", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(path.joinToString(" / ") { it.second }.ifEmpty { "Storage locations" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !mutating, onClick = { try { picker.launch(null) } catch (e: Exception) { error = "System folder picker unavailable" } }) { Text("Add folder") }
                if(parent != null) {
                    Button(enabled = !mutating, onClick = { path = path.dropLast(1) }) { Text("Back") }
                    Button(enabled = !mutating, onClick = { revision++ }) { Text("Refresh") }
                    Button(enabled = !mutating && !busy, onClick = { nameDialog("New folder", "") { name -> mutate { storage.create(parent, name) } } }) { Text("New folder") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if(busy) Text("Reading storage…")
            if(mutating) Text("Working…")
            val pending = clipboard
            if (pending != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text((if (pending.second) "Move: " else "Copy: ") + pending.first.name)
                    Button(enabled = parent != null && !mutating && !busy, onClick = {
                        val destination = parent ?: return@Button
                        mutating = true
                        transferred = 0
                        transferJob = scope.launch {
                            try {
                                storage.transfer(pending.first, destination, pending.second) { bytes ->
                                    if (bytes - transferred >= 1024 * 1024) transferred = bytes
                                }
                                clipboard = null
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.localizedMessage ?: "Transfer failed" }
                            finally { mutating = false; transferJob = null; revision++ }
                        }
                    }) { Text("Paste") }
                    Button(enabled = !mutating, onClick = { clipboard = null }) { Text("Clear") }
                }
            }
            if (transferJob != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${transferred / 1024} KiB")
                    Button(onClick = { transferJob?.cancel() }) { Text("Cancel transfer") }
                }
            }
            val item = selected
            if(item != null) {
                Text(item.name)
                Text(if(item.size >= 0) "${item.size} bytes" else item.mime)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        if (item.mime.startsWith("image/") || item.mime.startsWith("video/") || item.mime.startsWith("audio/")) viewing = item
                        else try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri,item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        catch(e: Exception) { error = "No compatible viewer installed" }
                    }) { Text("Open") }
                    if (item.flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0) {
                        Button(enabled = !mutating, onClick = { nameDialog("Rename", item.name) { name -> mutate { storage.rename(item, name) } } }) { Text("Rename") }
                    }
                    if (item.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                        Button(enabled = !mutating, onClick = {
                            AlertDialog.Builder(this@MainActivity).setTitle("Delete file?")
                                .setMessage(item.name).setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete") { _, _ -> mutate { storage.delete(item) } }.show()
                        }) { Text("Delete") }
                    }
                    Button(enabled = !mutating, onClick = { clipboard = item to false; selected = null }) { Text("Copy") }
                    if (item.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                        Button(enabled = !mutating, onClick = { clipboard = item to true; selected = null }) { Text("Move") }
                    }
                    Button(enabled = !mutating, onClick = { selected = null }) { Text("Close") }
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
                        Button(enabled = !mutating, onClick = { if(entry.directory) path = path + (entry.uri to entry.name) else selected = entry }, modifier = Modifier.fillMaxWidth()) {
                            Text((if(entry.directory) "▸  " else "") + entry.name)
                        }
                    }
                }
            }
        }
    }
    private fun nameDialog(title: String, initial: String, onConfirm: (String) -> Unit) {
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(initial)
            selectAll()
        }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(field)
            .setNegativeButton("Cancel", null).setPositiveButton("OK", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = field.text.toString()
                if (!validName(name)) field.error = "Invalid name"
                else { dialog.dismiss(); onConfirm(name) }
            }
        }
        dialog.show()
    }
}
