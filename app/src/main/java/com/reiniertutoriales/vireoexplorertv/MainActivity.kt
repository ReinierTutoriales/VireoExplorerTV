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
        var clipboard by remember { mutableStateOf<Pair<List<Entry>, Boolean>?>(null) }
        var clipboardParent by remember { mutableStateOf<Uri?>(null) }
        var multi by remember { mutableStateOf(false) }
        var marked by remember { mutableStateOf<Set<Uri>>(emptySet()) }
        var transferJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var transferred by remember { mutableLongStateOf(0L) }
        var search by remember { mutableStateOf("") }
        var scanOpen by remember { mutableStateOf(false) }
        var networkOpen by remember { mutableStateOf(false) }
        var appsOpen by remember { mutableStateOf(false) }
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
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) try {
                val granted = (result.data?.flags ?: 0) and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                check(granted and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) { "Read access not granted" }
                if (granted and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } else contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                roots = contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }
                path = listOf(storage.root(uri) to "Storage")
            } catch (e: Exception) { error = e.localizedMessage ?: "Permission unavailable" }
        }
        var writableRoots by remember { mutableStateOf(contentResolver.persistedUriPermissions.filter { it.isWritePermission }.map { it.uri }.toSet()) }
        DisposableEffect(Unit) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    val grants = contentResolver.persistedUriPermissions
                    roots = grants.filter { it.isReadPermission }.map { it.uri }
                    writableRoots = grants.filter { it.isWritePermission }.map { it.uri }.toSet()
                    if (path.isNotEmpty() && roots.none { storage.root(it) == path.first().first }) {
                        path = emptyList(); selected = null; viewing = null; scanOpen = false
                        error = "Storage access revoked"
                    }
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(roots) { writableRoots = contentResolver.persistedUriPermissions.filter { it.isWritePermission }.map { it.uri }.toSet() }
        var volumes by remember { mutableStateOf<List<VolumeInfo>>(emptyList()) }
        LaunchedEffect(Unit) {
            observeVolumes(this@MainActivity).collect { volumes = it }
        }
        val parent = path.lastOrNull()?.first
        val writable = roots.any { it in writableRoots && storage.root(it) == path.firstOrNull()?.first }
        LaunchedEffect(parent, revision) {
            search = ""
            multi = false
            marked = emptySet()
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
        if (scanOpen && parent != null) {
            StorageAnalysis(storage, parent, onClose = { scanOpen = false })
            return
        }
        if (networkOpen) {
            SmbBrowser { networkOpen = false }
            return
        }
        if (appsOpen) {
            AppManager(onClose = { appsOpen = false })
            return
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
        BackHandler(mutating || multi || selected != null || path.isNotEmpty()) {
            if (mutating) return@BackHandler
            if (multi) { multi = false; marked = emptySet() } else if(selected != null) selected = null else path = path.dropLast(1)
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("VireoExplorerTV", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(path.joinToString(" / ") { it.second }.ifEmpty { "Storage locations" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !mutating, onClick = { try { picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)) } catch (e: Exception) { error = "System folder picker unavailable" } }) { Text("Add folder") }
                if (parent == null) {
                    Button(enabled = !mutating, onClick = { appsOpen = true }) { Text("Apps & storage") }
                    Button(enabled = !mutating, onClick = { networkOpen = true }) { Text("SMB network") }
                }
                if(parent != null) {
                    Button(enabled = !mutating, onClick = { path = path.dropLast(1) }) { Text("Back") }
                    Button(enabled = !mutating, onClick = { revision++ }) { Text("Refresh") }
                    Button(enabled = !mutating && !busy, onClick = { scanOpen = true }) { Text("Analyze") }
                    Button(enabled = !mutating, onClick = { searchDialog(search) { search = it } }) { Text("Search") }
                    Button(enabled = !mutating && !busy && selected == null, onClick = { multi = !multi; marked = emptySet() }) { Text(if (multi) "Cancel selection" else "Select multiple") }
                    Button(enabled = writable && !mutating && !busy, onClick = { nameDialog("New folder", "") { name -> mutate { storage.create(parent, name) } } }) { Text("New folder") }
                }
            }
            if (search.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Search: $search")
                    Button(onClick = { search = "" }) { Text("Clear search") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if(busy) Text("Reading storage…")
            if(mutating) Text("Working…")
            val pending = clipboard
            if (pending != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text((if (pending.second) "Move: " else "Copy: ") + pending.first.joinToString(limit = 2) { it.name })
                    Button(enabled = parent != null && writable && !mutating && !busy, onClick = {
                        val destination = parent ?: return@Button
                        mutating = true
                        transferred = 0
                        transferJob = scope.launch {
                            try {
                                val report: (Long) -> Unit = { bytes ->
                                    if (bytes - transferred >= 1024 * 1024) transferred = bytes
                                }
                                var completed = 0
                                for (entry in pending.first) {
                                    transferred = 0
                                    try {
                                        if (entry.directory && pending.second) storage.moveDirectory(entry, checkNotNull(clipboardParent), destination)
                                        else if (entry.directory) storage.copyDirectory(entry, destination, report)
                                        else storage.transfer(entry, destination, pending.second, report)
                                        completed++
                                        clipboard = pending.first.drop(completed).takeIf { it.isNotEmpty() }?.let { it to pending.second }
                                    } catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { throw IllegalStateException("$completed of ${pending.first.size} items completed: ${e.localizedMessage}", e) }
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
            if (multi) {
                val batch = entries.filter { it.uri in marked }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${batch.size} selected")
                    Button(enabled = !mutating, onClick = { marked = entries.filter { it.name.contains(search, ignoreCase = true) }.map { it.uri }.toSet() }) { Text("Select all visible") }
                    Button(enabled = !mutating && batch.isNotEmpty(), onClick = { clipboard = batch to false; multi = false; marked = emptySet() }) { Text("Copy selected") }
                    Button(enabled = !mutating && writable && batch.isNotEmpty() && batch.all { if (it.directory) it.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0 else it.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0 }, onClick = { clipboardParent = parent; clipboard = batch to true; multi = false; marked = emptySet() }) { Text("Move selected") }
                }
            }
            val item = selected
            if(item != null) {
                Text(item.name)
                Text(if(item.size >= 0) "${item.size} bytes" else item.mime)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !mutating, onClick = {
                        if (item.directory) { path = path + (item.uri to item.name); selected = null }
                        else if (item.mime.startsWith("image/") || item.mime.startsWith("video/") || item.mime.startsWith("audio/")) viewing = item
                        else try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri,item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        catch(e: Exception) { error = "No compatible viewer installed" }
                    }) { Text("Open") }
                    if (writable && item.flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0) {
                        Button(enabled = !mutating, onClick = { nameDialog("Rename", item.name) { name -> mutate { storage.rename(item, name) } } }) { Text("Rename") }
                    }
                    if (writable && item.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                        Button(enabled = !mutating, onClick = {
                            AlertDialog.Builder(this@MainActivity).setTitle(if (item.directory) "Delete empty folder?" else "Delete file?")
                                .setMessage(item.name).setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete") { _, _ -> mutate { storage.delete(item) } }.show()
                        }) { Text("Delete") }
                    }
                    Button(enabled = !mutating, onClick = { clipboard = listOf(item) to false; selected = null }) { Text("Copy") }
                    if (item.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                        Button(enabled = !mutating && (!item.directory || item.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0), onClick = { clipboardParent = parent; clipboard = listOf(item) to true; selected = null }) { Text("Move") }
                    }
                    Button(enabled = !mutating, onClick = { selected = null }) { Text("Close") }
                }
            } else if(parent == null) {
                volumes.forEach { volume ->
                    Button(enabled = !mutating && (volume.state == android.os.Environment.MEDIA_MOUNTED || volume.state == android.os.Environment.MEDIA_MOUNTED_READ_ONLY), onClick = {
                        try {
                            val intent = if (android.os.Build.VERSION.SDK_INT >= 29) volume.volume.createOpenDocumentTreeIntent()
                                else Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            picker.launch(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
                        } catch (_: Exception) { error = "Volume picker unavailable; use Add folder" }
                    }) { Text(volume.label + " · " + volume.state + if (volume.removable) " · Removable" else "") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(roots, key = { it.toString() }) { uri ->
                        Button(onClick = { path = listOf(storage.root(uri) to DocumentsContract.getTreeDocumentId(uri)) }) { Text(DocumentsContract.getTreeDocumentId(uri)) }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries.filter { it.name.contains(search, ignoreCase = true) }, key = { it.uri.toString() }) { entry ->
                        Button(enabled = !mutating, onClick = { if (multi) marked = if (entry.uri in marked) marked - entry.uri else marked + entry.uri else selected = entry }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (multi) { if (entry.uri in marked) "✓  " else "□  " } else "") + (if(entry.directory) "▸  " else "") + entry.name)
                        }
                    }
                }
            }
        }
    }
    private fun searchDialog(initial: String, onConfirm: (String) -> Unit) {
        val field = EditText(this).apply { setSingleLine(true); setText(initial) }
        AlertDialog.Builder(this).setTitle("Search folder").setView(field)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Search") { _, _ -> onConfirm(field.text.toString().trim()) }.show()
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
