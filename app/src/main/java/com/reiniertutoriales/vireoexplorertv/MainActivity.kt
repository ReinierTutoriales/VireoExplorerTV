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
import androidx.lifecycle.ViewModelProvider
import android.app.AlertDialog
import android.widget.EditText
import android.text.InputType

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[ExplorerModel::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VireoTheme { Explorer() } }
    }

    @Composable private fun Explorer() {
        val storage = model.storage
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) try {
                val granted = (result.data?.flags ?: 0) and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                check(granted and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) { "Read access not granted" }
                if (granted and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } else contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                model.roots = contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }
                model.path = listOf(storage.root(uri) to "Storage")
            } catch (e: Exception) { model.error = e.localizedMessage ?: "Permission unavailable" }
        }
        var writableRoots by remember { mutableStateOf(contentResolver.persistedUriPermissions.filter { it.isWritePermission }.map { it.uri }.toSet()) }
        DisposableEffect(Unit) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    val grants = contentResolver.persistedUriPermissions
                    model.roots = grants.filter { it.isReadPermission }.map { it.uri }
                    writableRoots = grants.filter { it.isWritePermission }.map { it.uri }.toSet()
                    if (model.path.isNotEmpty() && model.roots.none { storage.root(it) == model.path.first().first }) {
                        model.path = emptyList(); model.selected = null; model.viewing = null; model.scanOpen = false
                        model.error = "Storage access revoked"
                    }
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(model.roots) { writableRoots = contentResolver.persistedUriPermissions.filter { it.isWritePermission }.map { it.uri }.toSet() }
        var volumes by remember { mutableStateOf<List<VolumeInfo>>(emptyList()) }
        LaunchedEffect(Unit) {
            observeVolumes(this@MainActivity).collect { volumes = it }
        }
        val parent = model.path.lastOrNull()?.first
        val writable = model.roots.any { it in writableRoots && storage.root(it) == model.path.firstOrNull()?.first }
        LaunchedEffect(parent, model.revision) { model.load(parent) }
        if (model.scanOpen && parent != null) {
            StorageAnalysis(storage, parent, onClose = { model.scanOpen = false })
            return
        }
        if (model.networkOpen) {
            SmbBrowser { model.networkOpen = false }
            return
        }
        if (model.appsOpen) {
            AppManager(onClose = { model.appsOpen = false })
            return
        }
        val video = model.viewing?.takeIf { it.mime.startsWith("video/") || it.mime.startsWith("audio/") }
        if (video != null) {
            MediaViewer(video, onClose = { model.viewing = null })
            return
        }
        val image = model.viewing
        if (image != null) {
            ImageViewer(image, contentResolver, onClose = { model.viewing = null })
            return
        }
        BackHandler(model.mutating || model.multi || model.selected != null || model.path.isNotEmpty()) {
            if (model.mutating) return@BackHandler
            if (model.multi) { model.multi = false; model.marked = emptySet() } else if(model.selected != null) model.selected = null else model.path = model.path.dropLast(1)
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("VireoExplorerTV", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(model.path.joinToString(" / ") { it.second }.ifEmpty { "Storage locations" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !model.mutating, onClick = { try { picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)) } catch (e: Exception) { model.error = "System folder picker unavailable" } }) { Text("Add folder") }
                if (parent == null) {
                    Button(enabled = !model.mutating, onClick = { model.appsOpen = true }) { Text("Apps & storage") }
                    Button(enabled = !model.mutating, onClick = { model.networkOpen = true }) { Text("SMB network") }
                }
                if(parent != null) {
                    Button(enabled = !model.mutating, onClick = { model.path = model.path.dropLast(1) }) { Text("Back") }
                    Button(enabled = !model.mutating, onClick = { model.revision++ }) { Text("Refresh") }
                    Button(enabled = !model.mutating && !model.busy, onClick = { model.scanOpen = true }) { Text("Analyze") }
                    Button(enabled = !model.mutating, onClick = { searchDialog(model.search) { model.search = it } }) { Text("Search") }
                    Button(enabled = !model.mutating && !model.busy && model.selected == null, onClick = { model.multi = !model.multi; model.marked = emptySet() }) { Text(if (model.multi) "Cancel selection" else "Select multiple") }
                    Button(enabled = writable && !model.mutating && !model.busy, onClick = { nameDialog("New folder", "") { name -> model.mutate { storage.create(parent, name) } } }) { Text("New folder") }
                }
            }
            if (model.search.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Search: ${model.search}")
                    Button(onClick = { model.search = "" }) { Text("Clear search") }
                }
            }
            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            model.freeBytes?.let { Text("Available: " + android.text.format.Formatter.formatFileSize(this@MainActivity, it)) }
            if(model.busy) Text("Reading storage…")
            if(model.mutating) Text("Working…")
            val pending = model.clipboard
            if (pending != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text((if (pending.second) "Move: " else "Copy: ") + pending.first.joinToString(limit = 2) { it.name })
                    Button(enabled = parent != null && writable && !model.mutating && !model.busy, onClick = {
                        val destination = parent ?: return@Button
                        model.paste(destination)
                    }) { Text("Paste") }
                    Button(enabled = !model.mutating, onClick = { model.conflictPolicy = ConflictPolicy.entries[(model.conflictPolicy.ordinal + 1) % ConflictPolicy.entries.size] }) { Text(model.conflictPolicy.label) }
                    Button(enabled = !model.mutating, onClick = { model.clipboard = null }) { Text("Clear") }
                }
            }
            if (model.transferJob != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val bytes by model.progress.collectAsState()
                    Text("${bytes / 1024} KiB")
                    Button(onClick = { model.transferJob?.cancel() }) { Text("Cancel transfer") }
                }
            }
            if (model.multi) {
                val batch = model.entries.filter { it.uri in model.marked }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${batch.size} selected")
                    Button(enabled = !model.mutating, onClick = { model.marked = model.entries.filter { it.name.contains(model.search, ignoreCase = true) }.map { it.uri }.toSet() }) { Text("Select all visible") }
                    Button(enabled = !model.mutating && batch.isNotEmpty(), onClick = { model.clipboard = batch to false; model.multi = false; model.marked = emptySet() }) { Text("Copy selected") }
                    Button(enabled = !model.mutating && writable && batch.isNotEmpty() && batch.all { if (it.directory) it.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0 else it.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0 }, onClick = { model.clipboardParent = parent; model.clipboard = batch to true; model.multi = false; model.marked = emptySet() }) { Text("Move selected") }
                }
            }
            val item = model.selected
            if(item != null) {
                Text(item.name)
                Text(if(item.size >= 0) "${item.size} bytes" else item.mime)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !model.mutating, onClick = {
                        if (item.directory) { model.path = model.path + (item.uri to item.name); model.selected = null }
                        else if (item.mime.startsWith("image/") || item.mime.startsWith("video/") || item.mime.startsWith("audio/")) model.viewing = item
                        else try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri,item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        catch(e: Exception) { model.error = "No compatible viewer installed" }
                    }) { Text("Open") }
                    if (writable && item.flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0) {
                        Button(enabled = !model.mutating, onClick = { nameDialog("Rename", item.name) { name -> model.mutate { storage.rename(item, name) } } }) { Text("Rename") }
                    }
                    if (writable && item.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                        Button(enabled = !model.mutating, onClick = {
                            AlertDialog.Builder(this@MainActivity).setTitle(if (item.directory) "Delete empty folder?" else "Delete file?")
                                .setMessage(item.name).setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete") { _, _ -> model.mutate { storage.delete(item) } }.show()
                        }) { Text("Delete") }
                    }
                    Button(enabled = !model.mutating, onClick = { model.clipboard = listOf(item) to false; model.selected = null }) { Text("Copy") }
                    if (writable && (if (item.directory) item.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0 else item.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0)) {
                        Button(enabled = !model.mutating && (!item.directory || item.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0), onClick = { model.clipboardParent = parent; model.clipboard = listOf(item) to true; model.selected = null }) { Text("Move") }
                    }
                    Button(enabled = !model.mutating, onClick = { model.selected = null }) { Text("Close") }
                }
            } else if(parent == null) {
                volumes.forEach { volume ->
                    Button(enabled = !model.mutating && (volume.state == android.os.Environment.MEDIA_MOUNTED || volume.state == android.os.Environment.MEDIA_MOUNTED_READ_ONLY), onClick = {
                        try {
                            val intent = if (android.os.Build.VERSION.SDK_INT >= 29) volume.volume.createOpenDocumentTreeIntent()
                                else Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            picker.launch(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
                        } catch (_: Exception) { model.error = "Volume picker unavailable; use Add folder" }
                    }) { Text(volume.label + " · " + volume.state + if (volume.removable) " · Removable" else "") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(model.roots, key = { it.toString() }) { uri ->
                        Button(onClick = { model.path = listOf(storage.root(uri) to DocumentsContract.getTreeDocumentId(uri)) }) { Text(DocumentsContract.getTreeDocumentId(uri)) }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(model.entries.filter { it.name.contains(model.search, ignoreCase = true) }, key = { it.uri.toString() }) { entry ->
                        Button(enabled = !model.mutating, onClick = { if (model.multi) model.marked = if (entry.uri in model.marked) model.marked - entry.uri else model.marked + entry.uri else model.selected = entry }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (model.multi) { if (entry.uri in model.marked) "✓  " else "□  " } else "") + (if(entry.directory) "▸  " else "") + entry.name)
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
