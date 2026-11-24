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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        val preferences = remember { ExplorerPreferences(applicationContext) }
        val settings by preferences.settings.collectAsStateWithLifecycle(initialValue = ExplorerSettings())
        val gridView = settings.grid
        val sortIndex = settings.sort
        val settingsScope = rememberCoroutineScope()
        fun updateSettings(change: suspend () -> Unit) {
            settingsScope.launch {
                try { change() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { model.error = "Cannot save explorer preferences" }
            }
        }
        val sortLabels = listOf(
            stringResource(R.string.explorer_sort_name_ascending),
            stringResource(R.string.explorer_sort_name_descending),
            stringResource(R.string.explorer_sort_largest),
            stringResource(R.string.explorer_sort_smallest)
        )
        var page by rememberSaveable { mutableStateOf(ExplorerPage.HOME) }
        var category by rememberSaveable { mutableStateOf<FileCategory?>(null) }
        var homeSearch by rememberSaveable { mutableStateOf("") }
        var usbOnly by rememberSaveable { mutableStateOf(false) }
        var folderPickerNotice by remember { mutableStateOf<String?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode != android.app.Activity.RESULT_OK || uri == null) {
                if (uri == null) {
                    folderPickerNotice = "No folder authorized. If your TV lacks a folder picker, use SMB network."
                }
                return@rememberLauncherForActivityResult
            }
            try {
                folderPickerNotice = null
                val granted = (result.data?.flags ?: 0) and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                check(granted and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) { "Read access not granted" }
                if (granted and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } else contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                model.roots = contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }
                model.path = listOf(storage.root(uri) to DocumentsContract.getTreeDocumentId(uri))
            } catch (e: Exception) { model.error = e.localizedMessage ?: "Permission unavailable" }
        }
        var writableRoots by remember { mutableStateOf(contentResolver.persistedUriPermissions.filter { it.isWritePermission }.map { it.uri }.toSet()) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            val grants = contentResolver.persistedUriPermissions
            model.roots = grants.filter { it.isReadPermission }.map { it.uri }
            writableRoots = grants.filter { it.isWritePermission }.map { it.uri }.toSet()
            if (model.path.isNotEmpty() && model.roots.none { storage.root(it) == model.path.first().first }) {
                model.path = emptyList()
                model.selected = null
                model.viewing = null
                model.scanOpen = false
                model.error = "Storage access revoked"
            }
        }
        LaunchedEffect(model.roots) {
            writableRoots = contentResolver.persistedUriPermissions.filter { it.isWritePermission }.map { it.uri }.toSet()
        }
        val volumeFlow = remember { observeVolumes(applicationContext) }
        val volumes by volumeFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        fun pickFolder(volume: VolumeInfo? = null) {
            folderPickerNotice = null
            model.error = null
            try {
                val intent = if (volume != null && android.os.Build.VERSION.SDK_INT >= 29) {
                    volume.volume.createOpenDocumentTreeIntent()
                } else Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                picker.launch(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
            } catch (_: android.content.ActivityNotFoundException) {
                folderPickerNotice = "Folder picker unavailable on this TV. Use SMB network."
            } catch (_: SecurityException) {
                model.error = "Folder picker access denied"
            }
        }
        fun openRoot(uri: Uri) {
            model.path = listOf(storage.root(uri) to DocumentsContract.getTreeDocumentId(uri))
        }
        val parent = model.path.lastOrNull()?.first
        LaunchedEffect(parent) { if (parent != null) updateSettings { preferences.recordFolder(parent.toString()) } }
        fun openSavedFolder(value: String) {
            try {
                val uri = Uri.parse(value)
                val root = model.roots.map { storage.root(it) }.firstOrNull { it.authority == uri.authority && DocumentsContract.getTreeDocumentId(it) == DocumentsContract.getTreeDocumentId(uri) }
                if (root == null) { model.error = "Folder access revoked"; return }
                model.path = if (root == uri) listOf(root to DocumentsContract.getDocumentId(root)) else listOf(root to DocumentsContract.getDocumentId(root), uri to DocumentsContract.getDocumentId(uri))
            } catch (_: Exception) { model.error = "Saved folder unavailable" }
        }
        val writable = model.roots.any { it in writableRoots && storage.root(it) == model.path.firstOrNull()?.first }
        LaunchedEffect(parent, model.revision) { model.load(parent) }
        var cleanupRoot by remember { mutableStateOf<Uri?>(null) }
        val listState = rememberLazyListState()
        val gridState = rememberLazyGridState()
        val returnEntries = remember { mutableMapOf<Uri, Uri>() }
        val selectedCleanupRoot = cleanupRoot
        if (model.appsOpen && selectedCleanupRoot != null && selectedCleanupRoot in model.roots) {
            StorageAnalysis(storage, storage.root(selectedCleanupRoot), selectedCleanupRoot in writableRoots, onClose = { cleanupRoot = null })
            return
        }
        if (model.scanOpen && parent != null) {
            StorageAnalysis(storage, parent, writable, onClose = { model.scanOpen = false })
            return
        }
        if (model.networkOpen) {
            SmbBrowser { model.networkOpen = false }
            return
        }
        if (model.appsOpen) {
            AppManager(onClose = { cleanupRoot = null; model.appsOpen = false }, onAnalyze = { cleanupRoot = it })
            return
        }
        val video = model.viewing?.let { it.copy(mime = mediaMime(it.name, it.mime)) }?.takeIf { it.mime.startsWith("video/") || it.mime.startsWith("audio/") }
        if (video != null) {
            MediaViewer(video, sidecars = model.entries, onClose = { model.viewing = null })
            return
        }
        val image = model.viewing
        if (image != null) {
            ImageViewer(image, contentResolver, onClose = { model.viewing = null })
            return
        }
        fun openEntry(entry: Entry) {
            val mime = mediaMime(entry.name, entry.mime)
            val internalViewer = mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")
            if (parent != null && (entry.directory || internalViewer)) returnEntries[parent] = entry.uri
            model.selected = null
            model.error = null
            when {
                entry.directory -> model.path = model.path + (entry.uri to entry.name)
                internalViewer -> model.viewing = entry
                else -> try {
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(entry.uri, entry.mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (_: android.content.ActivityNotFoundException) {
                    model.error = "No compatible viewer installed"
                } catch (_: SecurityException) {
                    model.error = "File access unavailable"
                }
            }
        }
        fun navigate(destination: ExplorerPage) {
            if (model.mutating) return
            category = null
            homeSearch = ""
            usbOnly = false
            folderPickerNotice = null
            model.error = null
            when (destination) {
                ExplorerPage.NETWORK -> model.networkOpen = true
                ExplorerPage.CLEANER -> model.appsOpen = true
                else -> page = destination
            }
        }
        if (parent == null) {
            BackHandler(page != ExplorerPage.HOME && !model.mutating) { navigate(ExplorerPage.HOME) }
            val usbUnavailable = stringResource(R.string.explorer_usb_unavailable)
            val categoryLabel = category?.let { stringResource(it.label) }
            val visibleVolumes = volumes.filter { !usbOnly || it.removable }
            val authorizedFolders = model.roots.filter { uri ->
                !usbOnly || visibleVolumes.any { volume ->
                    volume.volume.uuid?.equals(DocumentsContract.getTreeDocumentId(uri).substringBefore(':'), true) == true
                }
            }
            ExplorerHome(
                page = page,
                notice = model.error ?: folderPickerNotice,
                onPage = ::navigate,
                onSearch = {
                    searchDialog(homeSearch) { value ->
                        navigate(ExplorerPage.FILES)
                        homeSearch = value
                    }
                },
                onInternal = {
                    val root = model.roots.firstOrNull { uri ->
                        uri.authority == "com.android.externalstorage.documents" &&
                            DocumentsContract.getTreeDocumentId(uri).substringBefore(':') == "primary"
                    }
                    if (root != null) openRoot(root)
                    else pickFolder(volumes.firstOrNull { !it.removable })
                },
                onUsb = {
                    navigate(ExplorerPage.FILES)
                    usbOnly = true
                    if (volumes.none { it.removable }) folderPickerNotice = usbUnavailable
                },
                onFolders = { navigate(ExplorerPage.FILES) },
                onCategory = {
                    navigate(ExplorerPage.FILES)
                    category = it
                }
            ) {
                when (page) {
                    ExplorerPage.FILES -> {
                        if (categoryLabel != null || homeSearch.isNotEmpty()) item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(categoryLabel ?: homeSearch, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.explorer_category_scope))
                            }
                        }
                        if (usbOnly) {
                            items(visibleVolumes, key = { it.volume.uuid ?: it.label }) { volume ->
                                Button(
                                    enabled = volume.state == android.os.Environment.MEDIA_MOUNTED ||
                                        volume.state == android.os.Environment.MEDIA_MOUNTED_READ_ONLY,
                                    onClick = { pickFolder(volume) }
                                ) { Text(volume.label) }
                            }
                        } else {
                            item {
                                Button(onClick = { pickFolder() }) { Text(stringResource(R.string.explorer_add_folder)) }
                            }
                            if (authorizedFolders.isEmpty()) item { Text(stringResource(R.string.explorer_folder_access)) }
                        }
                        if (authorizedFolders.isNotEmpty()) item {
                            Text(stringResource(R.string.explorer_authorized), style = MaterialTheme.typography.titleMedium)
                        }
                        items(authorizedFolders, key = { it.toString() }) { uri ->
                            Button(onClick = { openRoot(uri) }) { Text(DocumentsContract.getTreeDocumentId(uri)) }
                        }
                        if (!usbOnly && settings.recent.isNotEmpty()) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(stringResource(R.string.explorer_recent), style = MaterialTheme.typography.titleMedium)
                                    Button(onClick = { updateSettings { preferences.clearRecent() } }) {
                                        Text(stringResource(R.string.explorer_clear_recent))
                                    }
                                }
                            }
                            items(settings.recent, key = { "recent:" + it }) { value ->
                                Button(onClick = { openSavedFolder(value) }) {
                                    Text(Uri.parse(value).lastPathSegment ?: value, maxLines = 2)
                                }
                            }
                        }
                    }
                    ExplorerPage.FAVORITES -> {
                        if (settings.favorites.isEmpty()) item { Text(stringResource(R.string.explorer_no_favorites)) }
                        items(settings.favorites, key = { it }) { value ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { openSavedFolder(value) }, modifier = Modifier.weight(1f)) {
                                    Text(Uri.parse(value).lastPathSegment ?: value, maxLines = 2)
                                }
                                Button(onClick = { updateSettings { preferences.toggleFavorite(value) } }) {
                                    Text(stringResource(R.string.explorer_remove_favorite))
                                }
                            }
                        }
                    }
                    ExplorerPage.TRANSFERS -> {
                        val pending = model.clipboard
                        if (pending == null && model.transferJob == null) item {
                            Text(stringResource(R.string.explorer_no_transfer))
                        }
                        if (pending != null) item {
                            val label = if (pending.second) R.plurals.explorer_move_queue else R.plurals.explorer_copy_queue
                            Text(pluralStringResource(label, pending.first.size, pending.first.size))
                            Button(onClick = { model.clipboard = null }, enabled = !model.mutating) {
                                Text(stringResource(R.string.explorer_clear_queue))
                            }
                        }
                        if (model.transferJob != null) item {
                            val bytes by model.progress.collectAsStateWithLifecycle()
                            Text(stringResource(R.string.explorer_transfer_progress,
                                android.text.format.Formatter.formatFileSize(this@MainActivity, bytes)))
                            Button(onClick = { model.transferJob?.cancel() }) {
                                Text(stringResource(R.string.explorer_cancel_transfer))
                            }
                        }
                    }
                    ExplorerPage.SETTINGS -> {
                        item {
                            ListItem(
                                selected = gridView,
                                onClick = { updateSettings { preferences.setGrid(!gridView) } },
                                headlineContent = { Text(stringResource(R.string.explorer_grid)) },
                                supportingContent = {
                                    Text(stringResource(if (gridView) R.string.explorer_grid_active else R.string.explorer_list_active))
                                },
                                trailingContent = { Switch(checked = gridView, onCheckedChange = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = ListItemDefaults.shape(VireoCardShape)
                            )
                        }
                        item {
                            Text(stringResource(R.string.explorer_sort_by), style = MaterialTheme.typography.titleMedium)
                        }
                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                sortLabels.forEachIndexed { index, label ->
                                    ExplorerOption(
                                        selected = sortIndex == index,
                                        onClick = { updateSettings { preferences.setSort(index) } }
                                    ) { Text(label) }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
            return
        }
        fun matchesFilter(entry: Entry): Boolean =
            (category?.matches(entry) != false) &&
                (entry.directory || entry.name.contains(homeSearch, ignoreCase = true)) &&
                entry.name.contains(model.search, ignoreCase = true)

        val primaryFocus = remember { FocusRequester() }
        var folderActions by remember(parent) { mutableStateOf(false) }
        val actionsOpen = model.selected != null
        LaunchedEffect(parent, model.revision, folderActions) {
            if (folderActions) model.loadAvailableSpace(parent)
        }
        fun backFromFolder() {
            if (model.mutating) return
            when {
                actionsOpen -> model.selected = null
                folderActions -> {
                    folderActions = false
                    primaryFocus.requestFocus()
                }
                model.multi -> { model.multi = false; model.marked = emptySet() }
                else -> model.path = model.path.dropLast(1)
            }
        }
        LaunchedEffect(parent, actionsOpen) { primaryFocus.requestFocus() }
        BackHandler { backFromFolder() }
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!actionsOpen) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(modifier = Modifier.focusRequester(primaryFocus),
                        enabled = !model.mutating, onClick = ::backFromFolder) { Text("Back") }
                    Column(Modifier.weight(1f)) {
                        Text(model.path.last().second, style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (model.path.size > 1) {
                            Text(model.path.dropLast(1).joinToString(" / ") { it.second },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(enabled = !model.mutating,
                        onClick = { searchDialog(model.search) { model.search = it } }) {
                        ExplorerIcon(R.drawable.symbol_search, description = stringResource(R.string.explorer_search))
                    }
                    OutlinedButton(enabled = !model.mutating, onClick = { folderActions = !folderActions }) {
                        Text(if (folderActions) "Hide tools" else "Folder tools")
                    }
                }
                if (folderActions) FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isFavorite = parent.toString() in settings.favorites
                    OutlinedButton(enabled = !model.mutating, onClick = { model.error = null; model.revision++ }) { Text("Refresh") }
                    ExplorerOption(
                        selected = isFavorite,
                        enabled = !model.mutating,
                        onClick = { updateSettings { preferences.toggleFavorite(parent.toString()) } },
                        leadingIcon = { ExplorerIcon(R.drawable.symbol_star) }
                    ) { Text(stringResource(R.string.explorer_favorites)) }
                    OutlinedButton(enabled = !model.mutating, onClick = { updateSettings { preferences.toggleGrid() } }) {
                        Text(stringResource(if (gridView) R.string.explorer_list else R.string.explorer_grid))
                    }
                    OutlinedButton(enabled = !model.mutating, onClick = { updateSettings { preferences.nextSort() } }) { Text(sortLabels[sortIndex]) }
                    OutlinedButton(enabled = !model.mutating && !model.busy, onClick = { model.scanOpen = true }) { Text("Analyze") }
                    OutlinedButton(enabled = !model.mutating && !model.busy, onClick = {
                        model.multi = !model.multi
                        model.marked = emptySet()
                        folderActions = false
                        primaryFocus.requestFocus()
                    }) { Text(if (model.multi) "Cancel selection" else "Select multiple") }
                    Button(enabled = writable && !model.mutating && !model.busy,
                        onClick = { nameDialog("New folder", "") { name -> model.mutate { storage.create(parent, name) } } }) {
                        Text("New folder")
                    }
                }
            }
            if (!actionsOpen && (category != null || homeSearch.isNotEmpty() || model.search.isNotEmpty())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    category?.let { active ->
                        ClearFilterButton(stringResource(active.label), active.icon, enabled = !model.mutating) {
                            category = null
                            primaryFocus.requestFocus()
                        }
                    }
                    if (homeSearch.isNotEmpty()) {
                        ClearFilterButton(homeSearch, R.drawable.symbol_search, enabled = !model.mutating) {
                            homeSearch = ""
                            primaryFocus.requestFocus()
                        }
                    }
                    if (model.search.isNotEmpty()) {
                        ClearFilterButton(model.search, R.drawable.symbol_search, enabled = !model.mutating) {
                            model.search = ""
                            primaryFocus.requestFocus()
                        }
                    }
                }
            }
            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (folderActions) model.freeBytes?.let {
                Text("Available: " + android.text.format.Formatter.formatFileSize(this@MainActivity, it))
            }
            if(model.busy) Text("Reading storage…")
            if(model.mutating) Text("Working…")
            val pending = model.clipboard
            if (pending != null) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text((if (pending.second) "Move: " else "Copy: ") + pending.first.joinToString(limit = 2) { it.name })
                    Button(enabled = writable && !model.mutating && !model.busy, onClick = { model.paste(parent) }) { Text("Paste") }
                    Button(enabled = !model.mutating, onClick = { model.conflictPolicy = ConflictPolicy.entries[(model.conflictPolicy.ordinal + 1) % ConflictPolicy.entries.size] }) { Text(model.conflictPolicy.label) }
                    Button(enabled = !model.mutating, onClick = { model.clipboard = null }) { Text("Clear") }
                }
            }
            if (model.transferJob != null) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val bytes by model.progress.collectAsStateWithLifecycle()
                    Text("${bytes / 1024} KiB")
                    Button(onClick = { model.transferJob?.cancel() }) { Text("Cancel transfer") }
                }
            }
            if (model.multi) {
                val batch = model.entries.filter { it.uri in model.marked }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${batch.size} selected")
                    Button(enabled = !model.mutating, onClick = { model.marked = model.entries.filter(::matchesFilter).map { it.uri }.toSet() }) { Text("Select all visible") }
                    Button(enabled = !model.mutating && batch.isNotEmpty(), onClick = { model.clipboard = batch to false; model.multi = false; model.marked = emptySet() }) { Text("Copy selected") }
                    Button(enabled = !model.mutating && writable && batch.isNotEmpty() && batch.all { if (it.directory) it.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0 else it.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0 }, onClick = { model.clipboardParent = parent; model.clipboard = batch to true; model.multi = false; model.marked = emptySet() }) { Text("Move selected") }
                }
            }
            val item = model.selected
            if (item != null) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if(item.size >= 0) "${item.size} bytes" else item.mime)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.focusRequester(primaryFocus),
                        enabled = !model.mutating, onClick = ::backFromFolder) { Text("Back to files") }
                    Button(enabled = !model.mutating, onClick = { openEntry(item) }) { Text("Open") }
                    if (writable && item.flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0) {
                        Button(enabled = !model.mutating, onClick = { nameDialog("Rename", item.name) { name -> model.mutate { storage.rename(item, name) } } }) { Text("Rename") }
                    }
                    if (writable && item.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                        Button(enabled = !model.mutating, onClick = {
                            AlertDialog.Builder(this@MainActivity).setTitle(if (item.directory) "Delete empty folder?" else "Delete file?")
                                .setMessage(item.name).setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete") { _, _ -> model.mutate { storage.delete(item) } }
                                .show().getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()
                        }) { Text("Delete") }
                    }
                    if (writable) {
                        Button(enabled = !model.mutating, onClick = {
                            AlertDialog.Builder(this@MainActivity).setTitle("Create ZIP?")
                                .setMessage(item.name + ".zip")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Create") { _, _ -> model.archive { progress -> storage.createZip(item, parent, progress) } }.show()
                        }) { Text("Create ZIP") }
                    }
                    if (writable && !item.directory && item.name.endsWith(".zip", true)) {
                        Button(enabled = !model.mutating, onClick = {
                            AlertDialog.Builder(this@MainActivity).setTitle("Extract ZIP?")
                                .setMessage("Extract into a new folder. Completed files may remain if extraction fails.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Extract") { _, _ -> model.archive { progress -> storage.extractZip(item, parent, progress) } }.show()
                        }) { Text("Extract ZIP") }
                    }
                    Button(enabled = !model.mutating, onClick = { model.clipboard = listOf(item) to false; model.selected = null }) { Text("Copy") }
                    if (writable && (if (item.directory) item.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0 else item.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0)) {
                        Button(enabled = !model.mutating && (!item.directory || item.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0), onClick = { model.clipboardParent = parent; model.clipboard = listOf(item) to true; model.selected = null }) { Text("Move") }
                    }
                }
            } else {
                val visible = remember(model.entries, model.search, category, homeSearch, sortIndex) {
                    val names = compareBy<Entry, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.name }
                    val order = when (sortIndex) {
                        1 -> names.reversed()
                        2 -> compareByDescending<Entry> { it.size }.then(names)
                        3 -> compareBy<Entry> { if (it.size < 0) Long.MAX_VALUE else it.size }.then(names)
                        else -> names
                    }
                    model.entries.filter(::matchesFilter)
                        .sortedWith(compareBy<Entry> { !it.directory }.then(order))
                }
                val select: (Entry) -> Unit = { entry ->
                    if (model.multi) model.marked = if (entry.uri in model.marked) model.marked - entry.uri else model.marked + entry.uri
                    else openEntry(entry)
                }
                if (!model.busy && visible.isEmpty() && model.error == null) {
                    Text(when {
                        model.search.isNotEmpty() || homeSearch.isNotEmpty() -> "No files match your search."
                        category != null -> stringResource(R.string.explorer_no_category_files)
                        else -> "This folder is empty."
                    })
                }
                if (visible.isNotEmpty() && !model.multi) Text("Press OK to open. Hold OK for file actions.")
                val entryFocus = remember { FocusRequester() }
                val restoreEntry = remember(parent, gridView) { returnEntries[parent] }
                val restoreIndex = if (restoreEntry == null) -1 else visible.indexOfFirst { it.uri == restoreEntry }
                LaunchedEffect(parent, restoreIndex, gridView) {
                    if (restoreIndex >= 0) {
                        if (gridView) gridState.scrollToItem(restoreIndex)
                        else listState.scrollToItem(restoreIndex)
                    }
                }
                val renderEntry: @Composable (Entry) -> Unit = { entry ->
                    val restoring = entry.uri == restoreEntry
                    if (restoring) LaunchedEffect(entry.uri, model.busy) {
                        if (!model.busy && entryFocus.requestFocus()) returnEntries.remove(parent)
                    }
                    val action = if (model.multi) null else ({
                        returnEntries[parent] = entry.uri
                        folderActions = false
                        model.selected = entry
                    })
                    FolderEntry(
                        entry = entry,
                        revision = model.revision,
                        grid = gridView,
                        selected = model.multi && entry.uri in model.marked,
                        enabled = !model.mutating && !model.busy,
                        onClick = { select(entry) },
                        onLongClick = action,
                        modifier = Modifier.fillMaxWidth().then(
                            if (restoring) Modifier.focusRequester(entryFocus) else Modifier
                        )
                    )
                }
                val padding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                if (gridView) {
                    LazyVerticalGrid(
                        state = gridState, columns = GridCells.Adaptive(180.dp),
                        modifier = Modifier.weight(1f).focusRestorer(), contentPadding = padding,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visible, key = { it.uri.toString() }) { renderEntry(it) }
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).focusRestorer(), state = listState, contentPadding = padding,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visible, key = { it.uri.toString() }) { renderEntry(it) }
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

@Composable
private fun ClearFilterButton(label: String, icon: Int, enabled: Boolean, onClear: () -> Unit) {
    OutlinedButton(enabled = enabled, onClick = onClear) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            ExplorerIcon(icon)
            Text(stringResource(R.string.explorer_clear_filter, label),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
