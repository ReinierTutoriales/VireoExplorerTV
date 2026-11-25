package com.reiniertutoriales.vireoexplorertv

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.*
import java.io.FilterInputStream
import java.io.InputStream
import java.util.EnumSet
import java.util.Locale

internal class SmbEndpoint(val host: String, val share: String, val user: String, val domain: String, val password: CharArray)
private data class SmbEntry(val name: String, val directory: Boolean, val bytes: Long)

private suspend fun <T> withSmbShare(session: SmbShareSession, operation: suspend (DiskShare) -> T): T =
    session.executeSuspend(operation)

internal fun formatSmbUriAuthority(host: String): String {
    val clean = host.substringBefore('%')
    return if (':' in clean && !clean.startsWith('[')) "[$clean]" else clean
}

private suspend fun listSmb(session: SmbShareSession, path: String): List<SmbEntry> = withSmbShare(session) { share ->
    share.list(path).asSequence().filter { it.fileName != "." && it.fileName != ".." }
        .take(5001).map { SmbEntry(it.fileName, it.fileAttributes and 16L != 0L, it.endOfFile) }
        .toList().also { check(it.size <= 5000) { "Directory limit exceeded" } }
        .sortedWith(compareByDescending<SmbEntry> { it.directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.name })
}

private suspend fun downloadSmb(
    session: SmbShareSession,
    path: String,
    expected: Long,
    resolver: android.content.ContentResolver,
    target: android.net.Uri
) = withSmbShare(session) { share ->
    require(expected >= 0)
    val source = share.openFile(
        path,
        EnumSet.of(AccessMask.GENERIC_READ),
        null,
        EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
        SMB2CreateDisposition.FILE_OPEN,
        EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
    ).use { file ->
        file.inputStream.use { input ->
            checkNotNull(resolver.openOutputStream(target, "w")).use { output -> transferDigest(input, output, expected) }
        }
    }
    val verified = checkNotNull(resolver.openInputStream(target)).use { transferDigest(it, expected = expected) }
    check(source.matches(verified)) { "Destination verification failed" }
}

private suspend fun uploadSmb(
    session: SmbShareSession,
    folder: List<String>,
    resolver: android.content.ContentResolver,
    source: android.net.Uri
) = withSmbShare(session) { share ->
    val name = checkNotNull(resolver.query(source, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)).use {
        check(it.moveToFirst())
        it.getString(0)
    }
    require(validSmbName(name))
    val path = (folder + name).joinToString("\\")
    val original = share.openFile(
        path,
        EnumSet.of(AccessMask.GENERIC_WRITE),
        null,
        EnumSet.noneOf(SMB2ShareAccess::class.java),
        SMB2CreateDisposition.FILE_CREATE,
        EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
    ).use { file ->
        checkNotNull(resolver.openInputStream(source)).use { input ->
            file.outputStream.use { output -> transferDigest(input, output) }
        }
    }
    val verified = share.openFile(
        path,
        EnumSet.of(AccessMask.GENERIC_READ),
        null,
        EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
        SMB2CreateDisposition.FILE_OPEN,
        EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
    ).use { file ->
        file.inputStream.use { transferDigest(it, expected = original.bytes) }
    }
    check(original.matches(verified)) { "Remote verification failed" }
}

internal fun validSmbName(name: String): Boolean =
    validName(name) && name.none { it in ":*?\"<>|" || it.code < 32 } &&
        !name.endsWith('.') && !name.endsWith(' ')

private suspend fun createSmbFolder(session: SmbShareSession, folder: List<String>, name: String) = withSmbShare(session) { share ->
    require(validSmbName(name))
    share.mkdir((folder + name).joinToString("\\"))
}

private suspend fun renameSmbEntry(session: SmbShareSession, folder: List<String>, entry: SmbEntry, name: String) = withSmbShare(session) { share ->
    require(validSmbName(name) && validSmbName(entry.name))
    val source = (folder + entry.name).joinToString("\\")
    val target = (folder + name).joinToString("\\")
    require(!source.equals(target, ignoreCase = true))
    val options = if (entry.directory) SMB2CreateOptions.FILE_DIRECTORY_FILE else SMB2CreateOptions.FILE_NON_DIRECTORY_FILE
    if (entry.directory) {
        share.openDirectory(
            source,
            EnumSet.of(AccessMask.DELETE),
            null,
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.of(options)
        ).use { it.rename(target, false) }
    } else {
        share.openFile(
            source,
            EnumSet.of(AccessMask.DELETE),
            null,
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.of(options)
        ).use { it.rename(target, false) }
    }
}

private suspend fun deleteSmbEntry(session: SmbShareSession, folder: List<String>, entry: SmbEntry) = withSmbShare(session) { share ->
    require(validSmbName(entry.name))
    val parent = folder.joinToString("\\")
    val current = share.list(parent).singleOrNull { it.fileName == entry.name }
        ?: error("Remote item no longer exists")
    check((current.fileAttributes and 16L != 0L) == entry.directory)
    check(entry.directory || current.endOfFile == entry.bytes) { "Remote file changed" }
    val target = (folder + entry.name).joinToString("\\")
    if (entry.directory) {
        check(share.list(target).none { it.fileName != "." && it.fileName != ".." }) { "Directory is not empty" }
        share.rmdir(target, false)
    } else {
        share.rm(target)
    }
}

private fun openSmbInputStream(session: SmbShareSession, remotePath: String): InputStream {
    val file = session.openReadOnlyFile(remotePath)
    return object : FilterInputStream(file.inputStream) {
        override fun close() {
            try { super.close() } finally { runCatching { file.close() } }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun SmbBrowser(onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var endpoint by remember { mutableStateOf<SmbEndpoint?>(null) }
    val session = remember(endpoint) { endpoint?.let { SmbShareSession(it) } }
    var path by remember { mutableStateOf<List<String>>(emptyList()) }
    var entries by remember { mutableStateOf<List<SmbEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val credentials = remember(context) { SmbCredentials(context) }
    var credentialBusy by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    var services by remember { mutableStateOf<List<SmbService>>(emptyList()) }
    var discoveredHost by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var showConnectDialog by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<SmbEntry?>(null) }
    var renameTarget by remember { mutableStateOf<SmbEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<SmbEntry?>(null) }

    LaunchedEffect(discovering) {
        if (discovering) {
            services = emptyList()
            status = null
            try {
                withTimeoutOrNull(15000) { discoverSmb(context).collect { services = it } }
                if (services.isEmpty()) status = "No SMB servers found automatically. Use Manual SMB connection."
            }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { status = "Unable to discover SMB servers" }
            finally { discovering = false }
        }
    }

    var pending by remember { mutableStateOf<Pair<String, SmbEntry>?>(null) }
    var transfer by remember { mutableStateOf<Job?>(null) }
    var playback by remember { mutableStateOf<Pair<String, SmbEntry>?>(null) }
    var imageViewing by remember { mutableStateOf<Pair<String, SmbEntry>?>(null) }
    val server = endpoint
    val activeSession = session
    val primaryFocus = remember { FocusRequester() }

    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val request = pending
        pending = null
        if (uri != null && request != null && activeSession != null) {
            transfer = scope.launch {
                try {
                    downloadSmb(activeSession, request.first, request.second.bytes, context.contentResolver, uri)
                    status = "Download completed and verified (SHA-256)"
                } catch (e: CancellationException) {
                    status = "Download cancelled; destination may contain partial data"
                    throw e
                } catch (_: Exception) {
                    status = "Download failed; destination may contain partial data"
                } finally { transfer = null }
            }
        }
    }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && activeSession != null && transfer == null) {
            val folder = path.toList()
            transfer = scope.launch {
                try {
                    uploadSmb(activeSession, folder, context.contentResolver, uri)
                    status = "Upload completed and verified (SHA-256)"
                } catch (e: CancellationException) {
                    status = "Upload cancelled; a partial remote file may remain"
                    throw e
                } catch (_: Exception) {
                    status = "Upload failed (existing file, access or connection)"
                } finally { transfer = null; refresh++ }
            }
        }
    }
    DisposableEffect(server, activeSession) {
        onDispose {
            activeSession?.closeAsync()
            server?.password?.fill('\u0000')
        }
    }
    fun back() {
        if (transfer != null) return
        when {
            path.isNotEmpty() -> path = path.dropLast(1)
            server != null -> { endpoint = null; status = null }
            else -> onClose()
        }
    }
    BackHandler { back() }
    LaunchedEffect(activeSession, path, refresh) {
        entries = emptyList(); error = null
        if (activeSession != null) {
            loading = true
            try { entries = listSmb(activeSession, path.joinToString("\\")) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "SMB connection or access failed" }
            finally { loading = false }
        }
    }

    fun remoteUri(host: String, share: String, remotePath: String) =
        android.net.Uri.Builder().scheme("smb").encodedAuthority(formatSmbUriAuthority(host)).appendPath(share).appendPath(remotePath).build()

    val media = playback
    if (media != null && server != null && activeSession != null) {
        val uri = remoteUri(server.host, server.share, media.first)
        val mime = mediaMime(media.second.name)
        val remoteSidecars = remember(server, media, entries, path) {
            entries.filter { !it.directory && matchesSubtitle(media.second.name, it.name) && validSmbName(it.name) }.take(16).map { item ->
                val remotePath = (path + item.name).joinToString("\\")
                Entry(remoteUri(server.host, server.share, remotePath), item.name, subtitleMimeForName(item.name)!!, item.bytes, 0) to remotePath
            }
        }
        val factory = remember(activeSession, media, remoteSidecars) {
            val allowed = mapOf(uri to media.first) + remoteSidecars.associate { it.first.uri to it.second }
            androidx.media3.datasource.DefaultDataSource.Factory(
                context,
                androidx.media3.datasource.DataSource.Factory { SmbDataSource(activeSession, allowed) }
            )
        }
        MediaViewer(
            Entry(uri, media.second.name, mime, media.second.bytes, 0),
            factory,
            sidecars = remoteSidecars.map { it.first },
            onClose = { playback = null }
        )
        return
    }

    val imageTarget = imageViewing
    if (imageTarget != null && server != null && activeSession != null) {
        val imageSiblings = remember(server, entries, path) {
            entries.filter { !it.directory && mediaMime(it.name).startsWith("image/") && validSmbName(it.name) }
                .map { item ->
                    val remotePath = (path + item.name).joinToString("\\")
                    val uri = remoteUri(server.host, server.share, remotePath)
                    Entry(uri, item.name, mediaMime(item.name), item.bytes, 0) to remotePath
                }
        }
        val allowedImageMap = remember(imageSiblings) { imageSiblings.associate { it.first.uri to it.second } }
        val targetUri = remoteUri(server.host, server.share, imageTarget.first)
        val targetEntry = Entry(targetUri, imageTarget.second.name, mediaMime(imageTarget.second.name), imageTarget.second.bytes, 0)
        ImageViewer(
            entry = targetEntry,
            resolver = null,
            siblings = imageSiblings.map { it.first },
            openStream = { item ->
                val remotePath = allowedImageMap[item.uri] ?: imageTarget.first
                openSmbInputStream(activeSession, remotePath)
            },
            onClose = { imageViewing = null }
        )
        return
    }

    if (showConnectDialog) {
        SmbConnectDialog(
            initialHost = discoveredHost,
            onConnect = { newEndpoint ->
                endpoint = newEndpoint
                path = emptyList()
                status = null
                discovering = false
                showConnectDialog = false
            },
            onDismiss = { showConnectDialog = false }
        )
    }

    if (newFolderDialog && activeSession != null) {
        val folder = path.toList()
        TvInputDialog(
            title = stringResource(R.string.explorer_new_folder),
            label = "Folder name",
            initial = "",
            confirmLabel = "OK",
            validate = { if (!validSmbName(it)) "Invalid SMB name" else null },
            onConfirm = { name ->
                transfer = scope.launch {
                    try { createSmbFolder(activeSession, folder, name); status = "Folder created" }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { status = "Operation failed: name conflict, access or connection" }
                    finally { transfer = null; refresh++ }
                }
            },
            onDismiss = { newFolderDialog = false }
        )
    }

    val selectedSmbItem = actionTarget
    if (selectedSmbItem != null && server != null) {
        val actionFocus = remember { FocusRequester() }
        val remotePath = (path + selectedSmbItem.name).joinToString("\\")
        val mime = mediaMime(selectedSmbItem.name)
        val playable = !selectedSmbItem.directory && (mime.startsWith("video/") || mime.startsWith("audio/"))
        val viewableImage = !selectedSmbItem.directory && mime.startsWith("image/")
        val canDownload = !selectedSmbItem.directory && !loading && transfer == null && pending == null && selectedSmbItem.bytes >= 0
        LaunchedEffect(selectedSmbItem.name) { actionFocus.requestFocus() }
        Dialog(onDismissRequest = { actionTarget = null }) {
            Surface(
                shape = VireoCardShape,
                colors = SurfaceDefaults.colors(
                    containerColor = colors.surface,
                    contentColor = colors.onSurface
                ),
                border = Border(BorderStroke(2.dp, colors.primary), shape = VireoCardShape)
            ) {
                Column(
                    modifier = Modifier.widthIn(min = 400.dp, max = 540.dp).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(selectedSmbItem.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!selectedSmbItem.directory && selectedSmbItem.bytes >= 0) {
                        Text(Formatter.formatFileSize(context, selectedSmbItem.bytes), color = colors.onSurfaceVariant)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.focusRequester(actionFocus),
                            onClick = { actionTarget = null }
                        ) { Text(stringResource(R.string.explorer_back)) }
                        if (selectedSmbItem.directory || playable || viewableImage) {
                            Button(onClick = {
                                actionTarget = null
                                when {
                                    selectedSmbItem.directory -> path = path + selectedSmbItem.name
                                    playable -> playback = remotePath to selectedSmbItem
                                    viewableImage -> imageViewing = remotePath to selectedSmbItem
                                }
                            }) { Text("Open") }
                        }
                        if (!selectedSmbItem.directory) {
                            Button(enabled = canDownload, onClick = {
                                actionTarget = null
                                pending = remotePath to selectedSmbItem
                                try { destination.launch(selectedSmbItem.name) }
                                catch (_: Exception) { pending = null; status = "System destination picker unavailable" }
                            }) { Text("Download") }
                        }
                        Button(enabled = transfer == null && !loading, onClick = {
                            actionTarget = null
                            renameTarget = selectedSmbItem
                        }) { Text("Rename") }
                        Button(enabled = transfer == null && !loading, onClick = {
                            actionTarget = null
                            deleteTarget = selectedSmbItem
                        }) { Text("Delete") }
                    }
                }
            }
        }
    }

    val renaming = renameTarget
    if (renaming != null && activeSession != null) {
        val folder = path.toList()
        TvInputDialog(
            title = "Rename remote item",
            label = "New name",
            initial = renaming.name,
            confirmLabel = "OK",
            validate = { if (!validSmbName(it)) "Invalid SMB name" else null },
            onConfirm = { name ->
                transfer = scope.launch {
                    try { renameSmbEntry(activeSession, folder, renaming, name); status = "Renamed" }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { status = "Operation failed: name conflict, access or connection" }
                    finally { transfer = null; refresh++ }
                }
            },
            onDismiss = { renameTarget = null }
        )
    }

    val deleting = deleteTarget
    if (deleting != null && activeSession != null) {
        val folder = path.toList()
        TvConfirmDialog(
            title = "Permanently delete remote item?",
            message = deleting.name + if (deleting.directory) "\nOnly empty folders can be deleted." else "\nThis cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                transfer = scope.launch {
                    try {
                        deleteSmbEntry(activeSession, folder, deleting)
                        status = "Remote item deleted"
                        primaryFocus.requestFocus()
                    }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { status = "Delete failed: item changed, folder not empty, access or connection" }
                    finally { transfer = null; refresh++ }
                }
            },
            onDismiss = { deleteTarget = null }
        )
    }

    val restingBorder = Border(BorderStroke(1.dp, colors.borderVariant), shape = VireoCardShape)
    val focusBorder = Border(BorderStroke(2.dp, colors.primary), shape = VireoCardShape)
    val folderTypeLabel = stringResource(R.string.explorer_type_folder)

    LaunchedEffect(server, path) { primaryFocus.requestFocus() }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExplorerOption(
                selected = false,
                enabled = transfer == null,
                onClick = { back() }
            ) {
                Text(if (server != null && path.isEmpty()) stringResource(R.string.smb_disconnect) else stringResource(R.string.explorer_back))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    server?.let { path.lastOrNull() ?: "${it.host} / ${it.share}" }
                        ?: stringResource(R.string.smb_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    server?.let { (listOf(it.host, it.share) + path).joinToString(" / ") }
                        ?: stringResource(R.string.smb_subtitle_disconnected),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (server == null) {
                ExplorerOption(
                    selected = false,
                    enabled = !credentialBusy && transfer == null,
                    onClick = {
                        credentialBusy = true
                        scope.launch {
                            try { credentials.forget(); status = "Saved connection removed" }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { status = "Could not remove saved connection" }
                            finally { credentialBusy = false }
                        }
                    }
                ) {
                    Text(stringResource(R.string.smb_forget))
                }
            }
        }

        if (server != null) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExplorerOption(
                    selected = false,
                    enabled = !loading && transfer == null,
                    onClick = { newFolderDialog = true },
                    leadingIcon = { ExplorerIcon(R.drawable.symbol_folder) }
                ) {
                    Text(stringResource(R.string.explorer_new_folder))
                }
                ExplorerOption(
                    selected = false,
                    enabled = !loading && transfer == null && pending == null,
                    onClick = {
                        try { upload.launch(arrayOf("*/*")) }
                        catch (_: Exception) { status = "System file picker unavailable" }
                    },
                    leadingIcon = { ExplorerIcon(R.drawable.symbol_swap_horiz) }
                ) {
                    Text(stringResource(R.string.smb_upload))
                }
                ExplorerOption(
                    selected = false,
                    enabled = !loading && transfer == null && !credentialBusy,
                    onClick = {
                        val snapshot = SmbEndpoint(server.host, server.share, server.user, server.domain, server.password.copyOf())
                        credentialBusy = true
                        scope.launch {
                            try { credentials.save(snapshot); status = "Connection saved encrypted" }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { status = "Could not save credentials" }
                            finally { snapshot.password.fill('\u0000'); credentialBusy = false }
                        }
                    },
                    leadingIcon = { ExplorerIcon(R.drawable.symbol_star) }
                ) {
                    Text(stringResource(R.string.smb_remember))
                }
                ExplorerOption(
                    selected = false,
                    enabled = !loading && transfer == null,
                    onClick = { refresh++ }
                ) {
                    Text(stringResource(R.string.explorer_refresh))
                }
                ExplorerOption(
                    selected = false,
                    enabled = !loading && transfer == null,
                    onClick = { showConnectDialog = true },
                    leadingIcon = { ExplorerIcon(R.drawable.symbol_lan) }
                ) {
                    Text(stringResource(R.string.smb_connect_card_title))
                }
            }
        }

        status?.let { Text(it, color = colors.primary, style = MaterialTheme.typography.bodyMedium) }
        error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
        if (transfer != null) {
            ExplorerOption(selected = false, onClick = { transfer?.cancel() }) {
                Text(stringResource(R.string.explorer_cancel_transfer))
            }
        }
        if (loading) Text(stringResource(R.string.explorer_working), color = colors.onSurfaceVariant)

        if (server == null) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Card(
                            onClick = { showConnectDialog = true },
                            modifier = Modifier.weight(1f).focusRequester(primaryFocus),
                            shape = CardDefaults.shape(VireoCardShape),
                            colors = CardDefaults.colors(
                                containerColor = colors.surface,
                                contentColor = colors.onSurface,
                                focusedContainerColor = colors.primaryContainer,
                                focusedContentColor = colors.onPrimaryContainer
                            ),
                            border = CardDefaults.border(border = restingBorder, focusedBorder = focusBorder),
                            scale = CardDefaults.scale(focusedScale = 1.02f)
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
                                    ExplorerIcon(R.drawable.symbol_lan)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        stringResource(R.string.smb_connect_card_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        stringResource(R.string.smb_connect_card_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Card(
                            onClick = {
                                if (!credentialBusy && transfer == null) {
                                    credentialBusy = true
                                    scope.launch {
                                        try {
                                            val saved = credentials.load()
                                            if (saved == null) status = "No saved SMB connection found"
                                            else { endpoint = saved; path = emptyList(); status = null; discovering = false }
                                        } catch (e: CancellationException) { throw e }
                                        catch (_: Exception) { status = "Saved credentials unavailable; forget and reconnect" }
                                        finally { credentialBusy = false }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = CardDefaults.shape(VireoCardShape),
                            colors = CardDefaults.colors(
                                containerColor = colors.surface,
                                contentColor = colors.onSurface,
                                focusedContainerColor = colors.primaryContainer,
                                focusedContentColor = colors.onPrimaryContainer
                            ),
                            border = CardDefaults.border(border = restingBorder, focusedBorder = focusBorder),
                            scale = CardDefaults.scale(focusedScale = 1.02f)
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
                                    ExplorerIcon(R.drawable.symbol_star)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        stringResource(R.string.smb_saved_card_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        stringResource(R.string.smb_saved_card_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        onClick = { if (transfer == null && !resolving) discovering = !discovering },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardDefaults.shape(VireoCardShape),
                        colors = CardDefaults.colors(
                            containerColor = colors.surface,
                            contentColor = colors.onSurface,
                            focusedContainerColor = colors.primaryContainer,
                            focusedContentColor = colors.onPrimaryContainer
                        ),
                        border = CardDefaults.border(border = restingBorder, focusedBorder = focusBorder),
                        scale = CardDefaults.scale(focusedScale = 1.01f)
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
                                ExplorerIcon(R.drawable.symbol_search)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (discovering) stringResource(R.string.smb_discover_stop)
                                    else stringResource(R.string.smb_discover_card_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    stringResource(R.string.smb_discover_card_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (services.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.smb_discovered_servers, services.size),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(services, key = { it.name }) { service ->
                        ListItem(
                            selected = discoveredHost.isNotEmpty() && service.name.contains(discoveredHost, ignoreCase = true),
                            enabled = !resolving && transfer == null,
                            onClick = {
                                resolving = true
                                scope.launch {
                                    try {
                                        discoveredHost = withTimeout(10000) { resolveSmb(context, service) }
                                        showConnectDialog = true
                                    } catch (_: TimeoutCancellationException) {
                                        status = "Server resolution timed out"
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        status = "Server resolution failed"
                                    } finally {
                                        resolving = false
                                    }
                                }
                            },
                            leadingContent = {
                                Box(
                                    Modifier.size(44.dp).background(colors.surfaceVariant, VireoPillShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ExplorerIcon(R.drawable.symbol_lan)
                                }
                            },
                            headlineContent = {
                                Text(service.name, style = MaterialTheme.typography.titleMedium)
                            },
                            supportingContent = {
                                Text("SMB2 / SMB3 (_smb._tcp)", color = colors.onSurfaceVariant)
                            },
                            shape = ListItemDefaults.shape(VireoCardShape),
                            border = ListItemDefaults.border(border = restingBorder, focusedBorder = focusBorder)
                        )
                    }
                }
            }
        } else {
            if (!loading && error == null && entries.isEmpty()) {
                Text(stringResource(R.string.smb_empty_share), color = colors.onSurfaceVariant)
            }
            LazyColumn(
                Modifier.weight(1f).focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(entries, key = { it.name }, contentType = { if (it.directory) "folder" else "file" }) { entry ->
                    val mime = mediaMime(entry.name)
                    val video = mime.startsWith("video/")
                    val audio = mime.startsWith("audio/")
                    val image = mime.startsWith("image/")
                    val playable = !entry.directory && (video || audio)
                    val viewableImage = !entry.directory && image
                    val remotePath = (path + entry.name).joinToString("\\")
                    val entryAvailable = !loading && transfer == null
                    val canDownload = entryAvailable && pending == null && entry.bytes >= 0
                    val sizeText = if (!entry.directory && entry.bytes >= 0) {
                        Formatter.formatFileSize(context, entry.bytes)
                    } else null
                    val extLabel = if (entry.directory) folderTypeLabel else {
                        val ext = entry.name.substringAfterLast('.', "").uppercase(Locale.ROOT)
                        if (ext.isNotEmpty() && ext.length <= 8) ext else "FILE"
                    }
                    val subtitle = if (sizeText != null) "$extLabel · $sizeText" else extLabel

                    fun download() {
                        pending = remotePath to entry
                        try { destination.launch(entry.name) }
                        catch (_: Exception) { pending = null; status = "System destination picker unavailable" }
                    }

                    ListItem(
                        selected = false,
                        enabled = entryAvailable && (entry.directory || playable || viewableImage || canDownload),
                        modifier = Modifier.fillMaxWidth(),
                        shape = ListItemDefaults.shape(VireoCardShape),
                        colors = ListItemDefaults.colors(
                            containerColor = colors.surface,
                            contentColor = colors.onSurface,
                            focusedContainerColor = colors.surfaceVariant,
                            focusedContentColor = colors.onSurface
                        ),
                        border = ListItemDefaults.border(border = restingBorder, focusedBorder = focusBorder),
                        scale = ListItemDefaults.scale(focusedScale = 1.01f),
                        onClick = {
                            when {
                                entry.directory -> path = path + entry.name
                                playable -> playback = remotePath to entry
                                viewableImage -> imageViewing = remotePath to entry
                                else -> download()
                            }
                        },
                        onLongClick = if (entryAvailable) ({ actionTarget = entry }) else null,
                        headlineContent = {
                            Text(
                                entry.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                subtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Box(
                                Modifier.size(44.dp).background(colors.surfaceVariant, VireoPillShape),
                                contentAlignment = Alignment.Center
                            ) {
                                ExplorerIcon(
                                    when {
                                        entry.directory -> R.drawable.symbol_folder
                                        video -> R.drawable.symbol_movie
                                        audio -> R.drawable.symbol_music_note
                                        image -> R.drawable.symbol_image
                                        else -> R.drawable.symbol_description
                                    }
                                )
                            }
                        },
                        trailingContent = sizeText?.let {
                            { Text(it, style = MaterialTheme.typography.labelLarge, color = colors.primary) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmbConnectDialog(
    initialHost: String,
    onConnect: (SmbEndpoint) -> Unit,
    onDismiss: () -> Unit
) {
    val hostState = rememberTextFieldState(initialHost)
    val shareState = rememberTextFieldState()
    val userState = rememberTextFieldState()
    val domainState = rememberTextFieldState()
    val passwordState = rememberTextFieldState()
    var error by remember { mutableStateOf<String?>(null) }
    val hostFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { hostFocus.requestFocus() }
    LaunchedEffect(hostState, shareState) {
        snapshotFlow { hostState.text.toString() to shareState.text.toString() }.collect { error = null }
    }

    fun submit() {
        val cleanHost = hostState.text.toString().trim()
        val cleanShare = shareState.text.toString().trim()
        when {
            cleanHost.isBlank() || cleanHost.contains('/') || cleanHost.contains('\\') -> {
                error = "Enter a server hostname or IP"
            }
            !validSmbName(cleanShare) -> {
                error = "Enter a valid share name"
            }
            else -> {
                val pwdSeq = passwordState.text
                val chars = CharArray(pwdSeq.length) { pwdSeq[it] }
                passwordState.clearText()
                onConnect(
                    SmbEndpoint(
                        cleanHost,
                        cleanShare,
                        userState.text.toString(),
                        domainState.text.toString(),
                        chars
                    )
                )
            }
        }
    }

    Dialog(onDismissRequest = { passwordState.clearText(); onDismiss() }) {
        Surface(
            shape = VireoCardShape,
            colors = SurfaceDefaults.colors(
                containerColor = colors.surface,
                contentColor = colors.onSurface
            ),
            border = Border(BorderStroke(2.dp, colors.primary), shape = VireoCardShape)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 420.dp, max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.smb_connect_card_title), style = MaterialTheme.typography.titleLarge)
                TvTextField(
                    state = hostState,
                    label = "Server host or IP",
                    inputFocusRequester = hostFocus
                )
                TvTextField(
                    state = shareState,
                    label = "Share name"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvTextField(
                        state = userState,
                        label = "Username",
                        modifier = Modifier.weight(1f)
                    )
                    TvTextField(
                        state = domainState,
                        label = "Domain (optional)",
                        modifier = Modifier.weight(1f)
                    )
                }
                TvTextField(
                    state = passwordState,
                    label = "Password",
                    password = true,
                    imeAction = ImeAction.Done,
                    onDone = ::submit,
                    downFocusRequester = connectFocus
                )
                error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = { passwordState.clearText(); onDismiss() }) {
                        Text(stringResource(R.string.explorer_back))
                    }
                    Button(onClick = ::submit, modifier = Modifier.focusRequester(connectFocus)) { Text("Connect") }
                }
            }
        }
    }
}
