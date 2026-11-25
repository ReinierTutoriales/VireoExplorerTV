package com.reiniertutoriales.vireoexplorertv

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[ExplorerModel::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addOnTrimMemoryListener { level -> trimThumbnailCache(level) }
        setContent { VireoTheme { Explorer() } }
    }

    @Composable private fun Explorer() {
        val storage = model.storage
        val colors = MaterialTheme.colorScheme
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
        fun readDirectStorageState(previous: StorageAccessState = StorageAccessState.NEEDS_CONSENT) =
            StorageAccessState.fromPermissions(
                hasDirectStorageAccess(this@MainActivity), hasDirectStorageWriteAccess(this@MainActivity), previous
            )

        var directAccess by remember { mutableStateOf(readDirectStorageState()) }
        val directStorageGranted = directAccess.canRead
        val directStorageWritable = directAccess.canWrite
        var showStorageDiagnostics by rememberSaveable { mutableStateOf(false) }
        var pendingActionAfterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }

        var showHomeSearchDialog by remember { mutableStateOf(false) }
        var showFolderSearchDialog by remember { mutableStateOf(false) }
        var showNewFolderDialog by remember { mutableStateOf(false) }
        var renameItemDialog by remember { mutableStateOf<Entry?>(null) }
        var deleteItemDialog by remember { mutableStateOf<Entry?>(null) }
        var createZipItemDialog by remember { mutableStateOf<Entry?>(null) }
        var extractZipItemDialog by remember { mutableStateOf<Entry?>(null) }
        var installApkConsentDialog by remember { mutableStateOf<Entry?>(null) }
        var apkAwaitingSettings by remember { mutableStateOf<Entry?>(null) }

        fun finishDirectStorageRequest() {
            directAccess = readDirectStorageState(directAccess)
            val action = pendingActionAfterGrant
            pendingActionAfterGrant = null
            if (directAccess.canRead) {
                folderPickerNotice = null
                action?.invoke()
            }
        }

        val storageSettingsLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { finishDirectStorageRequest() }
        val legacyPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { finishDirectStorageRequest() }

        fun requestDirectStoragePermission(requireWrite: Boolean = false, onGranted: (() -> Unit)? = null) {
            directAccess = readDirectStorageState(directAccess)
            if (if (requireWrite) directAccess.canWrite else directAccess.canRead) {
                onGranted?.invoke()
                return
            }
            model.error = null
            folderPickerNotice = null
            pendingActionAfterGrant = onGranted
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    storageSettingsLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                    )
                } catch (_: RuntimeException) {
                    try {
                        storageSettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (_: RuntimeException) {
                        pendingActionAfterGrant = null
                        directAccess = StorageAccessState.UNAVAILABLE
                        folderPickerNotice = getString(R.string.explorer_storage_settings_unavailable)
                        showStorageDiagnostics = true
                    }
                }
            } else {
                legacyPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }

        fun readFolderGrants() = contentResolver.persistedUriPermissions.associate {
            it.uri to StorageAccessState.fromPermissions(it.isReadPermission, it.isWritePermission)
        }

        var folderGrants by remember { mutableStateOf(readFolderGrants()) }

        fun refreshFolderGrants() {
            folderGrants = readFolderGrants()
            model.roots = folderGrants.filterValues { it.canRead }.keys.toList()
        }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != android.app.Activity.RESULT_OK) {
                folderPickerNotice = getString(R.string.explorer_picker_closed)
                return@rememberLauncherForActivityResult
            }
            try {
                val data = checkNotNull(result.data)
                val uri = checkNotNull(data.data)
                check(data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                val grants = if (data.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                } else {
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                contentResolver.takePersistableUriPermission(uri, grants)
                refreshFolderGrants()
                folderPickerNotice = null
                model.path = listOf(storage.root(uri) to DocumentsContract.getTreeDocumentId(uri))
            } catch (_: RuntimeException) {
                folderPickerNotice = getString(R.string.explorer_folder_grant_failed)
            }
        }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            directAccess = readDirectStorageState(directAccess)
            refreshFolderGrants()
            if (model.path.isNotEmpty()) {
                val rootUri = model.path.first().first
                val valid = if (rootUri.scheme == "file") {
                    directAccess.canRead && File(rootUri.path.orEmpty()).exists()
                } else {
                    model.roots.any { storage.root(it) == rootUri }
                }
                if (!valid) {
                    model.path = emptyList()
                    model.selected = null
                    model.viewing = null
                    model.scanOpen = false
                    model.error = getString(R.string.explorer_storage_access_lost)
                }
            }
        }

        val volumeFlow = remember { observeVolumes(applicationContext) }
        val volumes by volumeFlow.collectAsStateWithLifecycle(initialValue = emptyList())

        fun openDirectDirectory(dir: File, label: String) {
            folderPickerNotice = null
            model.error = null
            model.path = listOf(Uri.fromFile(dir) to label)
        }

        fun openInternalStorageDirectly() {
            if (directStorageGranted) {
                val primaryVol = volumes.firstOrNull { !it.removable && it.directUri != null }
                val dir = primaryVol?.directory ?: primaryDirectStorageDir()
                if (dir != null && dir.exists()) {
                    openDirectDirectory(dir, getString(R.string.explorer_internal))
                    return
                }
            }
            val safRoot = model.roots.firstOrNull { uri ->
                uri.authority == "com.android.externalstorage.documents" &&
                    DocumentsContract.getTreeDocumentId(uri).substringBefore(':') == "primary"
            }
            if (safRoot != null) {
                model.path = listOf(storage.root(safRoot) to getString(R.string.explorer_internal))
                return
            }
            requestDirectStoragePermission {
                val dir = primaryDirectStorageDir()
                if (dir != null && dir.exists()) {
                    openDirectDirectory(dir, getString(R.string.explorer_internal))
                }
            }
        }

        fun pickFolder(volume: VolumeInfo? = null) {
            folderPickerNotice = null
            model.error = null
            if (volume != null && directStorageGranted && volume.directory != null && volume.isMounted) {
                openDirectDirectory(volume.directory, volume.label)
                return
            }
            try {
                val intent = if (volume != null && Build.VERSION.SDK_INT >= 29) {
                    volume.volume.createOpenDocumentTreeIntent()
                } else Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                picker.launch(
                    intent.addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                )
            } catch (_: android.content.ActivityNotFoundException) {
                folderPickerNotice = getString(R.string.explorer_picker_unavailable)
            } catch (_: SecurityException) {
                folderPickerNotice = getString(R.string.explorer_picker_unavailable)
            }
        }

        fun openRoot(uri: Uri, customLabel: String? = null) {
            if (uri.scheme == "file") {
                val file = File(uri.path.orEmpty())
                model.path = listOf(uri to (customLabel ?: file.name.ifEmpty { getString(R.string.explorer_internal) }))
            } else {
                model.path = listOf(storage.root(uri) to (customLabel ?: DocumentsContract.getTreeDocumentId(uri)))
            }
        }

        val parent = model.path.lastOrNull()?.first
        LaunchedEffect(parent) { if (parent != null) updateSettings { preferences.recordFolder(parent.toString()) } }

        fun openSavedFolder(value: String) {
            try {
                val uri = Uri.parse(value)
                if (uri.scheme == "file") {
                    if (!directStorageGranted) {
                        requestDirectStoragePermission { openSavedFolder(value) }
                        return
                    }
                    val targetFile = File(uri.path ?: return)
                    if (!targetFile.exists()) { model.error = "Saved folder unavailable"; return }
                    val rootDir = primaryDirectStorageDir()
                    model.path = if (rootDir != null && targetFile.canonicalPath.startsWith(rootDir.canonicalPath.trimEnd(File.separatorChar) + File.separator) && targetFile.canonicalPath != rootDir.canonicalPath) {
                        listOf(
                            Uri.fromFile(rootDir) to getString(R.string.explorer_internal),
                            Uri.fromFile(targetFile) to targetFile.name
                        )
                    } else {
                        listOf(Uri.fromFile(targetFile) to targetFile.name.ifEmpty { getString(R.string.explorer_internal) })
                    }
                    return
                }
                val root = model.roots.map { storage.root(it) }.firstOrNull {
                    it.authority == uri.authority && DocumentsContract.getTreeDocumentId(it) == DocumentsContract.getTreeDocumentId(uri)
                }
                if (root == null) { model.error = "Folder access revoked"; return }
                model.path = if (root == uri) listOf(root to DocumentsContract.getDocumentId(root))
                else listOf(root to DocumentsContract.getDocumentId(root), uri to DocumentsContract.getDocumentId(uri))
            } catch (_: Exception) { model.error = "Saved folder unavailable" }
        }

        val rootUriInPath = model.path.firstOrNull()?.first
        val folderAccess = when {
            rootUriInPath == null -> StorageAccessState.NEEDS_CONSENT
            rootUriInPath.scheme == "file" -> directAccess
            else -> folderGrants.entries.firstOrNull { storage.root(it.key) == rootUriInPath }?.value
                ?: StorageAccessState.REVOKED
        }
        val writeGrant = folderAccess.canWrite
        val canCreateChildren by produceState(false, parent, writeGrant, model.revision) {
            value = false
            value = parent != null && writeGrant && storage.canCreateChildren(parent)
        }
        val writable = writeGrant && (rootUriInPath?.scheme != "file" || canCreateChildren)

        LaunchedEffect(parent, model.revision) { model.load(parent) }
        val activeParentEntries = if (parent != null) model.entriesFor(parent) else emptyList()
        LaunchedEffect(parent, activeParentEntries, model.search, category, homeSearch, sortIndex) {
            model.syncListing(parent, category, homeSearch, sortIndex)
        }

        var cleanupRoot by remember { mutableStateOf<Uri?>(null) }
        val listState = rememberLazyListState()
        val gridState = rememberLazyGridState()
        val returnEntries = remember { mutableMapOf<Uri, Uri>() }
        val selectedCleanupRoot = cleanupRoot

        if (model.appsOpen && selectedCleanupRoot != null) {
            val isFileCleanup = selectedCleanupRoot.scheme == "file"
            val canAnalyze = if (isFileCleanup) directStorageGranted else selectedCleanupRoot in model.roots
            if (canAnalyze) {
                val targetUri = if (isFileCleanup) selectedCleanupRoot else storage.root(selectedCleanupRoot)
                val canWriteCleanup = if (isFileCleanup) directStorageWritable else folderGrants[selectedCleanupRoot]?.canWrite == true
                StorageAnalysis(storage, targetUri, canWriteCleanup, onClose = { cleanupRoot = null })
                return
            }
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
            AppManager(
                onClose = { cleanupRoot = null; model.appsOpen = false },
                onAnalyze = { cleanupRoot = it },
                onRequestStorageAccess = { requestDirectStoragePermission(requireWrite = true) }
            )
            return
        }

        val video = model.viewing?.let { it.copy(mime = mediaMime(it.name, it.mime)) }
            ?.takeIf { it.mime.startsWith("video/") || it.mime.startsWith("audio/") }
        if (video != null) {
            MediaViewer(video, sidecars = activeParentEntries, onClose = { model.viewing = null })
            return
        }
        val image = model.viewing
        if (image != null) {
            val orderedSiblings = if (parent != null) model.visibleFor(parent).ifEmpty { activeParentEntries } else model.entries
            ImageViewer(image, contentResolver, siblings = orderedSiblings, onClose = { model.viewing = null })
            return
        }

        var externalOpenJob by remember { mutableStateOf<Job?>(null) }
        val openingExternalFile = externalOpenJob != null

        fun openExternally(entry: Entry, apk: Boolean) {
            if (externalOpenJob?.isActive == true) return
            if (apk && Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                installApkConsentDialog = entry
                return
            }
            externalOpenJob = settingsScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                var prepared: ExternalFile? = null
                var launched = false
                try {
                    val directRoots = volumes.filter { it.isMounted }.mapNotNull { it.directory }
                    val file = prepareExternalFile(this@MainActivity, entry, directRoots)
                    prepared = file
                    startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(file.uri, if (apk) "application/vnd.android.package-archive" else
                                mediaMime(entry.name, entry.mime))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                    launched = true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: android.content.ActivityNotFoundException) {
                    model.error = getString(if (apk) R.string.explorer_installer_unavailable else R.string.explorer_viewer_unavailable)
                } catch (_: SecurityException) {
                    model.error = getString(R.string.explorer_external_access_denied)
                } catch (e: Exception) {
                    model.error = e.localizedMessage ?: getString(R.string.explorer_export_failed)
                } finally {
                    if (!launched) withContext(kotlinx.coroutines.NonCancellable) { prepared?.discard() }
                    externalOpenJob = null
                }
            }.also { it.start() }
        }

        val apkSettingsLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            val entry = apkAwaitingSettings
            apkAwaitingSettings = null
            if (entry != null && Build.VERSION.SDK_INT >= 26 && packageManager.canRequestPackageInstalls()) {
                openExternally(entry, apk = true)
            }
        }

        fun openEntry(entry: Entry, searchParents: List<Entry>? = null) {
            val mime = mediaMime(entry.name, entry.mime)
            val isApk = FileCategory.APK.matches(entry) && !entry.directory
            val internalViewer = mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")
            if (parent != null && (entry.directory || internalViewer)) returnEntries[parent] = entry.uri
            model.selected = null
            model.error = null
            when {
                entry.directory -> {
                    val base = searchParents?.map { it.uri to it.name } ?: model.path
                    model.path = base + (entry.uri to entry.name)
                }
                internalViewer -> model.viewing = entry
                else -> openExternally(entry, isApk)
            }
        }

        fun navigate(destination: ExplorerPage) {
            if (model.mutating || openingExternalFile) return
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

        if (showHomeSearchDialog) {
            TvInputDialog(
                title = stringResource(R.string.explorer_search),
                label = stringResource(R.string.explorer_search),
                initial = homeSearch,
                confirmLabel = "OK",
                onConfirm = { value ->
                    val clean = value.trim()
                    if (clean.isNotEmpty()) {
                        navigate(ExplorerPage.FILES)
                        homeSearch = clean
                    }
                },
                onDismiss = { showHomeSearchDialog = false }
            )
        }

        if (showStorageDiagnostics) {
            val command = "adb shell appops set --uid $packageName MANAGE_EXTERNAL_STORAGE allow"
            val label = stringResource(R.string.explorer_storage_diagnostics)
            TvConfirmDialog(
                title = label,
                message = stringResource(R.string.explorer_storage_diagnostics_steps, command),
                confirmLabel = stringResource(R.string.explorer_storage_copy_command),
                cancelLabel = stringResource(R.string.explorer_back),
                onConfirm = {
                    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, command))
                },
                onDismiss = { showStorageDiagnostics = false }
            )
        }

        val apkCandidate = installApkConsentDialog
        if (apkCandidate != null) {
            TvConfirmDialog(
                title = "Allow APK installation?",
                message = "To install ${apkCandidate.name}, authorize VireoExplorerTV in Android unknown app sources settings.",
                confirmLabel = "Open settings",
                onConfirm = {
                    if (Build.VERSION.SDK_INT >= 26) {
                        try {
                            apkAwaitingSettings = apkCandidate
                            apkSettingsLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                            )
                        } catch (_: RuntimeException) {
                            apkAwaitingSettings = null
                            model.error = getString(R.string.explorer_apk_settings_unavailable)
                        }
                    }
                },
                onDismiss = { installApkConsentDialog = null }
            )
        }

        val restingBorder = Border(BorderStroke(1.dp, colors.borderVariant), shape = VireoCardShape)
        val focusBorder = Border(BorderStroke(3.dp, colors.primary), shape = VireoCardShape)

        if (parent == null) {
            BackHandler(openingExternalFile || (page != ExplorerPage.HOME && !model.mutating)) {
                if (openingExternalFile) externalOpenJob?.cancel() else navigate(ExplorerPage.HOME)
            }
            val usbUnavailable = stringResource(R.string.explorer_usb_unavailable)
            val categoryLabel = category?.let { stringResource(it.label) }
            val visibleVolumes = volumes.filter { !usbOnly || it.removable }
            val authorizedFolders = model.roots.filter { uri ->
                !usbOnly || visibleVolumes.any { volume ->
                    volume.volume.uuid?.equals(DocumentsContract.getTreeDocumentId(uri).substringBefore(':'), true) == true
                }
            }

            val searching = page == ExplorerPage.FILES && (category != null || homeSearch.isNotEmpty())
            val search = if (searching) {
                rememberExplorerSearch(storage, category, homeSearch, volumes, directStorageGranted, model.roots, model.revision)
            } else null

            ExplorerHome(
                page = page,
                notice = when {
                    openingExternalFile -> stringResource(R.string.explorer_exporting)
                    else -> model.error ?: folderPickerNotice
                },
                onPage = ::navigate,
                onSearch = { showHomeSearchDialog = true },
                onInternal = { openInternalStorageDirectly() },
                onUsb = {
                    val removable = volumes.filter { it.removable && it.isMounted }
                    if (removable.isEmpty()) {
                        navigate(ExplorerPage.FILES)
                        usbOnly = true
                        folderPickerNotice = usbUnavailable
                    } else if (directStorageGranted && removable.size == 1 && removable.first().directory != null) {
                        openDirectDirectory(removable.first().directory!!, removable.first().label)
                    } else {
                        navigate(ExplorerPage.FILES)
                        usbOnly = true
                    }
                },
                onFolders = { navigate(ExplorerPage.FILES) },
                onCategory = { selectedCategory ->
                    navigate(ExplorerPage.FILES)
                    category = selectedCategory
                }
            ) {
                when (page) {
                    ExplorerPage.FILES -> {
                        item(key = "add-folder") {
                            OutlinedButton(onClick = { pickFolder() }, enabled = !openingExternalFile) {
                                Text(stringResource(R.string.explorer_add_folder))
                            }
                        }
                        if (!directStorageWritable) {
                            item {
                                Card(
                                    onClick = { requestDirectStoragePermission(requireWrite = true) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = CardDefaults.shape(VireoCardShape),
                                    colors = CardDefaults.colors(
                                        containerColor = colors.surface,
                                        contentColor = colors.onSurface,
                                        focusedContainerColor = colors.primaryContainer,
                                        focusedContentColor = colors.onPrimaryContainer
                                    ),
                                    border = CardDefaults.border(border = restingBorder, focusedBorder = focusBorder),
                                    scale = CardDefaults.scale(focusedScale = 1.05f)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(48.dp).background(colors.surfaceVariant, VireoPillShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ExplorerIcon(R.drawable.symbol_hard_drive)
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                stringResource(R.string.explorer_grant_storage_title),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = colors.primary
                                            )
                                            Text(
                                                stringResource(R.string.explorer_grant_storage_subtitle),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            stringResource(R.string.explorer_grant_storage_action),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.primary
                                        )
                                    }
                                }
                            }
                        }

                        if (category != null || homeSearch.isNotEmpty()) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    category?.let { activeCat ->
                                        ClearFilterButton(stringResource(activeCat.label), activeCat.icon, enabled = true) {
                                            category = null
                                        }
                                    }
                                    if (homeSearch.isNotEmpty()) {
                                        ClearFilterButton(homeSearch, R.drawable.symbol_search, enabled = true) {
                                            homeSearch = ""
                                        }
                                    }
                                }
                            }
                            if (search != null) {
                                explorerSearchResults(
                                    search = search,
                                    searchingByName = homeSearch.isNotEmpty(),
                                    revision = model.revision,
                                    enabled = !openingExternalFile,
                                    onOpen = { openEntry(it.item, it.parents) },
                                    onShowFolder = { hit ->
                                        val path = hit.parents.map { it.uri to it.name }
                                        path.lastOrNull()?.first?.let { returnEntries[it] = hit.item.uri }
                                        model.path = path
                                    }
                                )
                            }
                        } else {
                            if (directStorageGranted) {
                                items(visibleVolumes.filter { it.isMounted && it.directory != null }, key = { "vol:" + (it.volume.uuid ?: it.label) }) { volume ->
                                    ListItem(
                                        selected = false,
                                        onClick = { openDirectDirectory(volume.directory!!, volume.label) },
                                        leadingContent = {
                                            Box(
                                                Modifier.size(44.dp).background(colors.surfaceVariant, VireoPillShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                ExplorerIcon(if (volume.removable) R.drawable.symbol_usb else R.drawable.symbol_hard_drive)
                                            }
                                        },
                                        headlineContent = {
                                            Text(volume.label, style = MaterialTheme.typography.titleMedium)
                                        },
                                        supportingContent = {
                                            Text(volume.directory?.absolutePath.orEmpty(), color = colors.onSurfaceVariant)
                                        },
                                        shape = ListItemDefaults.shape(VireoCardShape),
                                        border = ListItemDefaults.border(border = restingBorder, focusedBorder = focusBorder)
                                    )
                                }
                            } else if (usbOnly) {
                                items(visibleVolumes, key = { it.volume.uuid ?: it.label }) { volume ->
                                    ListItem(
                                        selected = false,
                                        enabled = volume.isMounted,
                                        onClick = { pickFolder(volume) },
                                        leadingContent = {
                                            Box(
                                                Modifier.size(44.dp).background(colors.surfaceVariant, VireoPillShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                ExplorerIcon(R.drawable.symbol_usb)
                                            }
                                        },
                                        headlineContent = { Text(volume.label) },
                                        shape = ListItemDefaults.shape(VireoCardShape),
                                        border = ListItemDefaults.border(border = restingBorder, focusedBorder = focusBorder)
                                    )
                                }
                            }

                            if (authorizedFolders.isNotEmpty()) {
                                item {
                                    Text(stringResource(R.string.explorer_authorized), style = MaterialTheme.typography.titleMedium)
                                }
                                items(authorizedFolders, key = { it.toString() }) { uri ->
                                    ListItem(
                                        selected = false,
                                        onClick = { openRoot(uri) },
                                        leadingContent = {
                                            Box(
                                                Modifier.size(44.dp).background(colors.surfaceVariant, VireoPillShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                ExplorerIcon(R.drawable.symbol_folder)
                                            }
                                        },
                                        headlineContent = { Text(DocumentsContract.getTreeDocumentId(uri)) },
                                        shape = ListItemDefaults.shape(VireoCardShape),
                                        border = ListItemDefaults.border(border = restingBorder, focusedBorder = focusBorder)
                                    )
                                }
                            }

                            if (!usbOnly && settings.recent.isNotEmpty()) {
                                item {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(R.string.explorer_recent), style = MaterialTheme.typography.titleMedium)
                                        ExplorerOption(
                                            selected = false,
                                            onClick = { updateSettings { preferences.clearRecent() } }
                                        ) {
                                            Text(stringResource(R.string.explorer_clear_recent))
                                        }
                                    }
                                }
                                items(settings.recent, key = { "recent:$it" }) { value ->
                                    val parsed = Uri.parse(value)
                                    val title = parsed.lastPathSegment?.substringAfterLast(':') ?: value
                                    ListItem(
                                        selected = false,
                                        onClick = { openSavedFolder(value) },
                                        leadingContent = {
                                            Box(
                                                Modifier.size(40.dp).background(colors.surfaceVariant, VireoPillShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                ExplorerIcon(R.drawable.symbol_folder)
                                            }
                                        },
                                        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = {
                                            Text(parsed.path ?: value, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant)
                                        },
                                        shape = ListItemDefaults.shape(VireoCardShape),
                                        border = ListItemDefaults.border(border = restingBorder, focusedBorder = focusBorder)
                                    )
                                }
                            }
                        }
                    }
                    ExplorerPage.FAVORITES -> {
                        if (settings.favorites.isEmpty()) {
                            item {
                                Text(stringResource(R.string.explorer_no_favorites), color = colors.onSurfaceVariant)
                            }
                        }
                        items(settings.favorites, key = { it }) { value ->
                            val parsed = Uri.parse(value)
                            val title = parsed.lastPathSegment?.substringAfterLast(':') ?: value
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ListItem(
                                    selected = false,
                                    onClick = { openSavedFolder(value) },
                                    modifier = Modifier.weight(1f),
                                    leadingContent = {
                                        Box(
                                            Modifier.size(40.dp).background(colors.surfaceVariant, VireoPillShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ExplorerIcon(R.drawable.symbol_star)
                                        }
                                    },
                                    headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = {
                                        Text(parsed.path ?: value, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant)
                                    },
                                    shape = ListItemDefaults.shape(VireoCardShape),
                                    border = ListItemDefaults.border(border = restingBorder, focusedBorder = focusBorder)
                                )
                                ExplorerOption(
                                    selected = false,
                                    onClick = { updateSettings { preferences.toggleFavorite(value) } }
                                ) {
                                    Text(stringResource(R.string.explorer_remove_favorite))
                                }
                            }
                        }
                    }
                    ExplorerPage.TRANSFERS -> {
                        val pending = model.clipboard
                        if (pending == null && model.transferJob == null) {
                            item {
                                Text(stringResource(R.string.explorer_no_transfer), color = colors.onSurfaceVariant)
                            }
                        }
                        if (pending != null) {
                            item {
                                Surface(
                                    shape = VireoCardShape,
                                    colors = SurfaceDefaults.colors(containerColor = colors.surface, contentColor = colors.onSurface),
                                    border = restingBorder,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(20.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val label = if (pending.second) R.plurals.explorer_move_queue else R.plurals.explorer_copy_queue
                                        Text(
                                            pluralStringResource(label, pending.first.size, pending.first.size),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        ExplorerOption(
                                            selected = false,
                                            enabled = !model.mutating,
                                            onClick = { model.clipboard = null }
                                        ) {
                                            Text(stringResource(R.string.explorer_clear_queue))
                                        }
                                    }
                                }
                            }
                        }
                        if (model.transferJob != null) {
                            item {
                                val bytes by model.progress.collectAsStateWithLifecycle()
                                Surface(
                                    shape = VireoCardShape,
                                    colors = SurfaceDefaults.colors(containerColor = colors.surface, contentColor = colors.onSurface),
                                    border = restingBorder,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(20.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            stringResource(
                                                R.string.explorer_transfer_progress,
                                                Formatter.formatFileSize(this@MainActivity, bytes)
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colors.primary
                                        )
                                        ExplorerOption(
                                            selected = false,
                                            onClick = { model.transferJob?.cancel() }
                                        ) {
                                            Text(stringResource(R.string.explorer_cancel_transfer))
                                        }
                                    }
                                }
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
                                shape = ListItemDefaults.shape(VireoCardShape),
                                border = ListItemDefaults.border(border = restingBorder, focusedBorder = focusBorder)
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

        val folderEntries = model.entriesFor(parent)
        val visible = model.visibleFor(parent)
        val listingPending = model.listingPending

        if (showFolderSearchDialog) {
            TvInputDialog(
                title = stringResource(R.string.explorer_search),
                label = stringResource(R.string.explorer_search),
                initial = model.search,
                confirmLabel = "OK",
                onConfirm = { model.search = it.trim() },
                onDismiss = { showFolderSearchDialog = false }
            )
        }

        if (showNewFolderDialog) {
            TvInputDialog(
                title = stringResource(R.string.explorer_new_folder),
                label = "Folder name",
                initial = "",
                confirmLabel = "OK",
                validate = { if (!validName(it)) "Invalid name" else null },
                onConfirm = { name -> model.mutate { storage.create(parent, name) } },
                onDismiss = { showNewFolderDialog = false }
            )
        }

        val renaming = renameItemDialog
        if (renaming != null) {
            TvInputDialog(
                title = "Rename",
                label = "New name",
                initial = renaming.name,
                confirmLabel = "OK",
                validate = { if (!validName(it)) "Invalid name" else null },
                onConfirm = { name -> model.mutate { storage.rename(renaming, name) } },
                onDismiss = { renameItemDialog = null }
            )
        }

        val primaryFocus = remember { FocusRequester() }
        val deleting = deleteItemDialog
        if (deleting != null) {
            TvConfirmDialog(
                title = if (deleting.directory) "Delete empty folder?" else "Delete file?",
                message = deleting.name,
                confirmLabel = "Delete",
                onConfirm = {
                    returnEntries.remove(parent)
                    model.mutate {
                        storage.delete(deleting)
                    }
                },
                onDismiss = { deleteItemDialog = null }
            )
        }

        val creatingZip = createZipItemDialog
        if (creatingZip != null) {
            TvConfirmDialog(
                title = "Create ZIP?",
                message = creatingZip.name + ".zip",
                confirmLabel = "Create",
                onConfirm = { model.archive { progress -> storage.createZip(creatingZip, parent, progress) } },
                onDismiss = { createZipItemDialog = null }
            )
        }

        val extractingZip = extractZipItemDialog
        if (extractingZip != null) {
            TvConfirmDialog(
                title = "Extract ZIP?",
                message = "Extract into a new folder.",
                confirmLabel = "Extract",
                onConfirm = { model.archive { progress -> storage.extractZip(extractingZip, parent, progress) } },
                onDismiss = { extractZipItemDialog = null }
            )
        }

        val actionsOpen = model.selected != null
        LaunchedEffect(parent, model.revision) {
            model.loadAvailableSpace(parent)
        }
        fun backFromFolder() {
            if (model.mutating) return
            when {
                openingExternalFile -> externalOpenJob?.cancel()
                actionsOpen -> model.selected = null
                model.multi -> { model.multi = false; model.marked = emptySet() }
                else -> model.path = model.path.dropLast(1)
            }
        }
        LaunchedEffect(parent) { primaryFocus.requestFocus() }
        BackHandler { backFromFolder() }

        val item = model.selected
        if (item != null) {
            val actionFocus = remember { FocusRequester() }
            androidx.compose.ui.window.Dialog(onDismissRequest = { model.selected = null }) {
                LaunchedEffect(item.uri) { actionFocus.requestFocus() }
                var confirmedKeyDown by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier.onPreviewKeyEvent { event ->
                        val key = event.nativeKeyEvent
                        when (key.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER, android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                                if (key.action == android.view.KeyEvent.ACTION_DOWN && key.repeatCount == 0) {
                                    confirmedKeyDown = true
                                    false
                                } else if (key.action == android.view.KeyEvent.ACTION_UP) {
                                    val suppress = !confirmedKeyDown
                                    confirmedKeyDown = false
                                    suppress
                                } else !confirmedKeyDown
                            }
                            else -> false
                        }
                    },
                    shape = VireoCardShape,
                    colors = SurfaceDefaults.colors(
                        containerColor = colors.surface,
                        contentColor = colors.onSurface
                    ),
                    border = Border(BorderStroke(2.dp, colors.primary), shape = VireoCardShape)
                ) {
                    Column(
                        modifier = Modifier.widthIn(min = 420.dp, max = 560.dp).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(item.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (item.size >= 0) Formatter.formatFileSize(this@MainActivity, item.size) else item.mime,
                            color = colors.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier.focusRequester(actionFocus),
                                enabled = !model.mutating,
                                onClick = { model.selected = null }
                            ) { Text(stringResource(R.string.explorer_back)) }
                            Button(enabled = !model.mutating, onClick = { model.selected = null; openEntry(item) }) {
                                Text(if (!item.directory && FileCategory.APK.matches(item)) "Install APK" else "Open")
                            }
                            if (writable && item.flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0) {
                                Button(enabled = !model.mutating, onClick = { model.selected = null; renameItemDialog = item }) { Text("Rename") }
                            }
                            if (writable && item.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                                Button(enabled = !model.mutating, onClick = { model.selected = null; deleteItemDialog = item }) { Text("Delete") }
                            }
                            if (canCreateChildren) {
                                Button(enabled = !model.mutating, onClick = { model.selected = null; createZipItemDialog = item }) { Text("Create ZIP") }
                            }
                            if (canCreateChildren && !item.directory && item.name.endsWith(".zip", true)) {
                                Button(enabled = !model.mutating, onClick = { model.selected = null; extractZipItemDialog = item }) { Text("Extract ZIP") }
                            }
                            Button(enabled = !model.mutating, onClick = { model.clipboard = listOf(item) to false; model.selected = null }) { Text("Copy") }
                            if (writable && (if (item.directory) item.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0 else item.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0)) {
                                Button(
                                    enabled = !model.mutating && (!item.directory || item.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0),
                                    onClick = { model.clipboardParent = parent; model.clipboard = listOf(item) to true; model.selected = null }
                                ) { Text("Move") }
                            }
                        }
                    }
                }
            }
        }

        val isFavorite = parent.toString() in settings.favorites
        var focusedUri by remember(parent) { mutableStateOf<Uri?>(null) }
        var showFolderTools by remember(parent) { mutableStateOf(false) }
        val focusedEntry = visible.firstOrNull { it.uri == focusedUri } ?: visible.firstOrNull()
        val detailsReady = !model.mutating && !model.busy && !listingPending && !model.multi && !openingExternalFile
        val moveSupported = focusedEntry?.let {
            writable && it.flags and (if (it.directory) DocumentsContract.Document.FLAG_SUPPORTS_MOVE
                else DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0
        } == true
        FolderWorkspace(
            enabled = !model.mutating,
            onNavigate = { destination ->
                model.selected = null
                model.multi = false
                model.marked = emptySet()
                model.path = emptyList()
                navigate(destination)
            },
            details = {
                FolderDetails(
                    entry = focusedEntry,
                    revision = model.revision,
                    enabled = detailsReady,
                    canMove = moveSupported,
                    onOpen = { focusedEntry?.let { openEntry(it) } },
                    onCopy = { focusedEntry?.let { model.clipboard = listOf(it) to false } },
                    onMove = {
                        focusedEntry?.let {
                            model.clipboardParent = parent
                            model.clipboard = listOf(it) to true
                        }
                    },
                    onMore = { focusedEntry?.let { model.selected = it } }
                )
            }
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.focusRequester(primaryFocus),
                        enabled = !model.mutating,
                        onClick = ::backFromFolder
                    ) {
                        Text(stringResource(R.string.explorer_back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            model.path.last().second,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val breadcrumb = buildString {
                            if (model.path.size > 1) {
                                append(model.path.dropLast(1).joinToString(" / ") { it.second })
                            }
                            model.freeBytes?.let { free ->
                                if (isNotEmpty()) append(" · ")
                                append(Formatter.formatFileSize(this@MainActivity, free))
                                append(" free")
                            }
                        }
                        if (breadcrumb.isNotEmpty()) {
                            Text(
                                breadcrumb,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        enabled = !model.mutating,
                        onClick = { showFolderSearchDialog = true }
                    ) {
                        ExplorerIcon(R.drawable.symbol_search, description = stringResource(R.string.explorer_search))
                    }
                    IconButton(enabled = !model.mutating, onClick = { showFolderTools = !showFolderTools }) {
                        ExplorerIcon(R.drawable.symbol_settings, description = stringResource(R.string.explorer_folder_tools))
                    }
                }

                if (showFolderTools) Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExplorerOption(
                        selected = false,
                        enabled = !model.mutating,
                        onClick = { updateSettings { preferences.nextSort() } }
                    ) {
                        Text(sortLabels[sortIndex])
                    }
                    ExplorerOption(
                        selected = gridView,
                        enabled = !model.mutating,
                        onClick = { updateSettings { preferences.toggleGrid() } }
                    ) {
                        Text(stringResource(if (gridView) R.string.explorer_grid else R.string.explorer_list))
                    }
                    if (canCreateChildren) {
                        ExplorerOption(
                            selected = false,
                            enabled = !model.mutating && !model.busy,
                            onClick = { showNewFolderDialog = true },
                            leadingIcon = { ExplorerIcon(R.drawable.symbol_folder) }
                        ) {
                            Text(stringResource(R.string.explorer_new_folder))
                        }
                    }
                    ExplorerOption(
                        selected = model.multi,
                        enabled = !model.mutating && !model.busy,
                        onClick = {
                            model.multi = !model.multi
                            model.marked = emptySet()
                        }
                    ) {
                        Text(stringResource(if (model.multi) R.string.explorer_cancel_selection else R.string.explorer_select_multiple))
                    }
                    ExplorerOption(
                        selected = isFavorite,
                        enabled = !model.mutating,
                        onClick = { updateSettings { preferences.toggleFavorite(parent.toString()) } },
                        leadingIcon = { ExplorerIcon(R.drawable.symbol_star) }
                    ) {
                        Text(stringResource(R.string.explorer_favorites))
                    }
                    ExplorerOption(
                        selected = false,
                        enabled = !model.mutating && !model.busy,
                        onClick = { model.scanOpen = true },
                        leadingIcon = { ExplorerIcon(R.drawable.symbol_cleaning_services) }
                    ) {
                        Text(stringResource(R.string.explorer_analyze_folder))
                    }
                    ExplorerOption(
                        selected = false,
                        enabled = !model.mutating,
                        onClick = { model.error = null; model.revision++ }
                    ) {
                        Text(stringResource(R.string.explorer_refresh))
                    }
                }

                if (showFolderTools) Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExplorerOption(selected = category == null, enabled = !model.mutating,
                        onClick = { category = null }) { Text(stringResource(R.string.explorer_filter_all)) }
                    listOf(FileCategory.VIDEO, FileCategory.AUDIO, FileCategory.IMAGE).forEach { filter ->
                        ExplorerOption(selected = category == filter, enabled = !model.mutating,
                            onClick = { category = if (category == filter) null else filter }) {
                            Text(stringResource(filter.label))
                        }
                    }
                }
                if (category != null || homeSearch.isNotEmpty() || model.search.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

                if (openingExternalFile) Text(stringResource(R.string.explorer_exporting))
                model.error?.let { Text(it, color = colors.error) }
                if (folderAccess == StorageAccessState.READ_ONLY) {
                    Text(stringResource(R.string.explorer_storage_read_only), color = colors.onSurfaceVariant)
                }
                if (model.busy) Text(stringResource(R.string.explorer_reading_storage), color = colors.onSurfaceVariant)
                if (model.mutating) Text(stringResource(R.string.explorer_working), color = colors.primary)

                val pending = model.clipboard
                if (pending != null) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (if (pending.second) "Move: " else "Copy: ") + pending.first.joinToString(limit = 2) { it.name },
                            color = colors.primary,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Button(enabled = canCreateChildren && !model.mutating && !model.busy, onClick = { model.paste(parent) }) { Text("Paste here") }
                        ExplorerOption(
                            selected = false,
                            enabled = !model.mutating,
                            onClick = {
                                model.conflictPolicy = ConflictPolicy.entries[(model.conflictPolicy.ordinal + 1) % ConflictPolicy.entries.size]
                            }
                        ) { Text(model.conflictPolicy.label) }
                        ExplorerOption(selected = false, enabled = !model.mutating, onClick = { model.clipboard = null }) { Text("Clear") }
                    }
                }

                if (model.transferJob != null) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bytes by model.progress.collectAsStateWithLifecycle()
                        Text(Formatter.formatFileSize(this@MainActivity, bytes), color = colors.primary)
                        ExplorerOption(selected = false, onClick = { model.transferJob?.cancel() }) {
                            Text(stringResource(R.string.explorer_cancel_transfer))
                        }
                    }
                }

                if (model.multi) {
                    val batch = folderEntries.filter { it.uri in model.marked }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${batch.size} selected", color = colors.primary, style = MaterialTheme.typography.titleSmall)
                        ExplorerOption(
                            selected = batch.size == visible.size && visible.isNotEmpty(),
                            enabled = !model.mutating && !model.busy && !listingPending,
                            onClick = {
                                model.marked = if (batch.size == visible.size) emptySet() else visible.map { it.uri }.toSet()
                            }
                        ) { Text("Select all (${visible.size})") }
                        ExplorerOption(
                            selected = false,
                            enabled = !model.mutating && batch.isNotEmpty(),
                            onClick = { model.clipboard = batch to false; model.multi = false; model.marked = emptySet() }
                        ) { Text("Copy") }
                        ExplorerOption(
                            selected = false,
                            enabled = !model.mutating && writable && batch.isNotEmpty() &&
                                batch.all { if (it.directory) it.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0 else it.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0 },
                            onClick = { model.clipboardParent = parent; model.clipboard = batch to true; model.multi = false; model.marked = emptySet() }
                        ) { Text("Move") }
                    }
                }

                val itemReady = !model.mutating && !model.busy && !listingPending && !openingExternalFile
                val select: (Entry) -> Unit = { entry ->
                    if (itemReady) {
                        if (model.multi) model.marked = if (entry.uri in model.marked) model.marked - entry.uri else model.marked + entry.uri
                        else openEntry(entry)
                    }
                }
                if (!model.busy && !listingPending && visible.isEmpty() && model.error == null) {
                    Text(
                        when {
                            model.search.isNotEmpty() || homeSearch.isNotEmpty() -> stringResource(R.string.explorer_no_search_results)
                            category != null -> stringResource(R.string.explorer_no_category_files)
                            else -> stringResource(R.string.explorer_empty_folder)
                        },
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                val entryFocus = remember { FocusRequester() }
                val restoreEntry = remember(parent, gridView) { returnEntries[parent] }
                val restoreIndex = if (restoreEntry == null) -1 else visible.indexOfFirst { it.uri == restoreEntry }
                val focusTarget = if (restoreIndex >= 0) restoreEntry else visible.firstOrNull()?.uri
                LaunchedEffect(parent, restoreIndex, gridView) {
                    if (restoreIndex >= 0) {
                        if (gridView) gridState.scrollToItem(restoreIndex)
                        else listState.scrollToItem(restoreIndex)
                    }
                }
                val renderEntry: @Composable (Entry) -> Unit = { entry ->
                    val restoring = entry.uri == focusTarget
                    if (restoring) LaunchedEffect(entry.uri, model.busy, listingPending) {
                        if (!model.busy && !listingPending && entryFocus.requestFocus()) returnEntries.remove(parent)
                    }
                    val action = if (model.multi) null else ({
                        if (itemReady) {
                            returnEntries[parent] = entry.uri
                            model.selected = entry
                        }
                    })
                    FolderEntry(
                        entry = entry,
                        revision = model.revision,
                        grid = gridView,
                        selected = model.multi && entry.uri in model.marked,
                        enabled = !model.mutating,
                        onClick = { select(entry) },
                        onLongClick = action,
                        modifier = Modifier.fillMaxWidth().onFocusChanged {
                            if (it.hasFocus) focusedUri = entry.uri
                        }.then(
                            if (restoring) Modifier.focusRequester(entryFocus) else Modifier
                        )
                    )
                }
                val padding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                if (gridView) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(ExplorerGridWidth),
                        modifier = Modifier.weight(1f).focusRestorer(),
                        contentPadding = padding,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visible, key = { it.uri.toString() }, contentType = { if (it.directory) "folder" else "file" }) { renderEntry(it) }
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).focusRestorer(),
                        state = listState,
                        contentPadding = padding,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visible, key = { it.uri.toString() }, contentType = { if (it.directory) "folder" else "file" }) { renderEntry(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClearFilterButton(label: String, icon: Int, enabled: Boolean, onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val restingBorder = Border(BorderStroke(1.dp, colors.primary), shape = VireoPillShape)
    val focusBorder = Border(BorderStroke(2.dp, colors.primary), shape = VireoPillShape)
    Surface(
        selected = true,
        enabled = enabled,
        onClick = onClear,
        shape = SelectableSurfaceDefaults.shape(VireoPillShape),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = colors.surface,
            contentColor = colors.onSurface,
            selectedContainerColor = colors.primaryContainer,
            selectedContentColor = colors.onPrimaryContainer,
            focusedContainerColor = colors.surfaceVariant,
            focusedContentColor = colors.onSurface,
            focusedSelectedContainerColor = colors.primaryContainer,
            focusedSelectedContentColor = colors.onPrimaryContainer
        ),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.02f, focusedSelectedScale = 1.02f),
        border = SelectableSurfaceDefaults.border(
            border = restingBorder,
            selectedBorder = restingBorder,
            focusedBorder = focusBorder,
            focusedSelectedBorder = focusBorder
        )
    ) {
        Row(
            Modifier.heightIn(min = 42.dp).padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(26.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = VireoPillShape,
                    colors = SurfaceDefaults.colors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            androidx.compose.ui.res.painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.onPrimary
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.explorer_clear_filter, label),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge
            )
            Text("✕", style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
    }
}
