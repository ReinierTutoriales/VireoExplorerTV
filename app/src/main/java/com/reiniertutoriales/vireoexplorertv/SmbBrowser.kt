package com.reiniertutoriales.vireoexplorertv

import android.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import android.text.InputType
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import java.util.EnumSet
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

internal class SmbEndpoint(val host: String, val share: String, val user: String, val domain: String, val password: CharArray)
private data class SmbEntry(val name: String, val directory: Boolean, val bytes: Long)

private suspend fun <T> withSmbShare(endpoint: SmbEndpoint, operation: suspend (DiskShare) -> T): T = withContext(Dispatchers.IO) {
    val config = SmbConfig.builder().withTimeout(10, TimeUnit.SECONDS)
        .withSoTimeout(10, TimeUnit.SECONDS).withSigningRequired(true).build()
    val password = endpoint.password.copyOf()
    try {
        SMBClient(config).use { client ->
            client.connect(endpoint.host).use { connection ->
                val session = connection.authenticate(AuthenticationContext(endpoint.user, password, endpoint.domain))
                (session.connectShare(endpoint.share) as DiskShare).use { share -> operation(share) }
            }
        }
    } finally { password.fill('\u0000') }
}

private suspend fun listSmb(endpoint: SmbEndpoint, path: String): List<SmbEntry> = withSmbShare(endpoint) { share ->
    share.list(path).asSequence().filter { it.fileName != "." && it.fileName != ".." }
        .take(5001).map { SmbEntry(it.fileName, it.fileAttributes and 16L != 0L, it.endOfFile) }
        .toList().also { check(it.size <= 5000) { "Directory limit exceeded" } }
        .sortedWith(compareByDescending<SmbEntry> { it.directory }.thenBy { it.name.lowercase(java.util.Locale.ROOT) })
}

private suspend fun downloadSmb(endpoint: SmbEndpoint, path: String, expected: Long, resolver: android.content.ContentResolver, target: android.net.Uri) = withSmbShare(endpoint) { share ->
    require(expected >= 0)
    val source = share.openFile(path, EnumSet.of(AccessMask.GENERIC_READ), null,
        EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ), SMB2CreateDisposition.FILE_OPEN,
        EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)).use { file ->
        file.inputStream.use { input ->
            checkNotNull(resolver.openOutputStream(target, "w")).use { output -> transferDigest(input, output, expected) }
        }
    }
    val verified = checkNotNull(resolver.openInputStream(target)).use { transferDigest(it, expected = expected) }
    check(source.matches(verified)) { "Destination verification failed" }
}

private suspend fun uploadSmb(endpoint: SmbEndpoint, folder: List<String>, resolver: android.content.ContentResolver, source: android.net.Uri) = withSmbShare(endpoint) { share ->
    val name = checkNotNull(resolver.query(source, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)).use {
        check(it.moveToFirst())
        it.getString(0)
    }
    require(validSmbName(name))
    val path = (folder + name).joinToString("\\")
    val original = share.openFile(path, EnumSet.of(AccessMask.GENERIC_WRITE), null,
        EnumSet.noneOf(SMB2ShareAccess::class.java), SMB2CreateDisposition.FILE_CREATE,
        EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)).use { file ->
        checkNotNull(resolver.openInputStream(source)).use { input ->
            file.outputStream.use { output -> transferDigest(input, output) }
        }
    }
    val verified = share.openFile(path, EnumSet.of(AccessMask.GENERIC_READ), null,
        EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ), SMB2CreateDisposition.FILE_OPEN,
        EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)).use { file ->
        file.inputStream.use { transferDigest(it, expected = original.bytes) }
    }
    check(original.matches(verified)) { "Remote verification failed" }
}

internal fun validSmbName(name: String): Boolean =
    validName(name) && name.none { it in ":*?\"<>|" || it.code < 32 } &&
        !name.endsWith('.') && !name.endsWith(' ')

private suspend fun createSmbFolder(endpoint: SmbEndpoint, folder: List<String>, name: String) = withSmbShare(endpoint) { share ->
    require(validSmbName(name))
    share.mkdir((folder + name).joinToString("\\"))
}

private suspend fun renameSmbEntry(endpoint: SmbEndpoint, folder: List<String>, entry: SmbEntry, name: String) = withSmbShare(endpoint) { share ->
    require(validSmbName(name) && validSmbName(entry.name))
    val source = (folder + entry.name).joinToString("\\")
    val target = (folder + name).joinToString("\\")
    require(!source.equals(target, ignoreCase = true))
    val options = if (entry.directory) SMB2CreateOptions.FILE_DIRECTORY_FILE else SMB2CreateOptions.FILE_NON_DIRECTORY_FILE
    if (entry.directory) {
        share.openDirectory(source, EnumSet.of(AccessMask.DELETE), null, EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN, EnumSet.of(options)).use { it.rename(target, false) }
    } else {
        share.openFile(source, EnumSet.of(AccessMask.DELETE), null, EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN, EnumSet.of(options)).use { it.rename(target, false) }
    }
}

private suspend fun deleteSmbEntry(endpoint: SmbEndpoint, folder: List<String>, entry: SmbEntry) = withSmbShare(endpoint) { share ->
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun SmbBrowser(onClose: () -> Unit) {
    val context = LocalContext.current
    var endpoint by remember { mutableStateOf<SmbEndpoint?>(null) }
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
    LaunchedEffect(discovering) {
        if (discovering) {
            services = emptyList()
            try { withTimeoutOrNull(15000) { discoverSmb(context).collect { services = it } } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { services = emptyList() }
            finally { discovering = false }
        }
    }
    var pending by remember { mutableStateOf<Pair<String, SmbEntry>?>(null) }
    var transfer by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var playback by remember { mutableStateOf<Pair<String, SmbEntry>?>(null) }
    val server = endpoint
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val request = pending
        pending = null
        if (uri != null && request != null && server != null) {
            transfer = scope.launch {
                try {
                    downloadSmb(server, request.first, request.second.bytes, context.contentResolver, uri)
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
        if (uri != null && server != null && transfer == null) {
            val folder = path.toList()
            transfer = scope.launch {
                try {
                    uploadSmb(server, folder, context.contentResolver, uri)
                    status = "Upload completed and verified (SHA-256)"
                } catch (e: CancellationException) {
                    status = "Upload cancelled; a partial remote file may remain"
                    throw e
                } catch (_: Exception) {
                    status = "Upload failed (existing file, access or connection); a partial file may remain"
                } finally { transfer = null; refresh++ }
            }
        }
    }
    DisposableEffect(server) { onDispose { server?.password?.fill('\u0000') } }
    fun back() { if (transfer != null) return; if (path.isNotEmpty()) path = path.dropLast(1) else onClose() }
    BackHandler { back() }
    LaunchedEffect(server, path, refresh) {
        entries = emptyList(); error = null
        if (server != null) {
            loading = true
            try { entries = listSmb(server, path.joinToString("\\")) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "SMB connection or access failed" }
            finally { loading = false }
        }
    }
    fun editName(title: String, initial: String, action: suspend (String) -> Unit) {
        val field = EditText(context).apply { setSingleLine(true); setText(initial); selectAll() }
        val dialog = AlertDialog.Builder(context).setTitle(title).setView(field)
            .setNegativeButton("Cancel", null).setPositiveButton("OK", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = field.text.toString()
                if (!validSmbName(name)) field.error = "Invalid SMB name"
                else {
                    dialog.dismiss()
                    transfer = scope.launch {
                        try { action(name); status = "Operation completed" }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { status = "Operation failed: name conflict, access or connection" }
                        finally { transfer = null; refresh++ }
                    }
                }
            }
        }
        dialog.show()
    }
    val media = playback
    if (media != null && server != null) {
        val uri = android.net.Uri.Builder().scheme("smb").authority(server.host).appendPath(server.share).appendPath(media.first).build()
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(media.second.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)) ?: "video/*"
        val factory = remember(server, media) { androidx.media3.datasource.DataSource.Factory {
            androidx.media3.datasource.DefaultDataSource.Factory(context, androidx.media3.datasource.DataSource.Factory { SmbDataSource(server, media.first) }).createDataSource()
        } }
        MediaViewer(Entry(uri, media.second.name, mime, media.second.bytes, 0), factory, onClose = { playback = null })
        return
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SMB 2/3 · browse and transfer")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { back() }) { Text("Back") }
            Button(enabled = !loading && transfer == null && !credentialBusy, onClick = {
                val fields = listOf("Server host or IP", "Share name", "Username", "Domain (optional)", "Password").map { label ->
                    EditText(context).apply { hint = label; setSingleLine(true) }
                }
                fields.first().setText(discoveredHost)
                fields.last().inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; fields.forEach { addView(it) } }
                AlertDialog.Builder(context).setTitle("Connect SMB").setView(layout).setNegativeButton("Cancel", null)
                    .setPositiveButton("Connect") { _, _ ->
                        val host = fields[0].text.toString().trim()
                        val share = fields[1].text.toString().trim()
                        if (host.isBlank() || share.isBlank() || host.contains('/') || share.contains('/') || share.contains('\\')) {
                            error = "Enter a valid server and share"
                        } else {
                            endpoint = SmbEndpoint(host, share, fields[2].text.toString(), fields[3].text.toString(), fields[4].text.toString().toCharArray())
                            path = emptyList()
                        }
                        fields.last().text.clear()
                    }.show()
            }) { Text("Connect") }
            Button(enabled = server != null && !loading && transfer == null, onClick = { refresh++ }) { Text("Refresh") }
            Button(enabled = server != null && !loading && transfer == null && pending == null, onClick = {
                try { upload.launch(arrayOf("*/*")) } catch (_: Exception) { status = "System file picker unavailable" }
            }) { Text("Upload") }
            Button(enabled = server != null && !loading && transfer == null, onClick = {
                val active = server ?: return@Button
                val folder = path.toList()
                editName("New remote folder", "") { createSmbFolder(active, folder, it) }
            }) { Text("New folder") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = server != null && !loading && transfer == null && !credentialBusy, onClick = {
                val active = server ?: return@Button
                val snapshot = SmbEndpoint(active.host, active.share, active.user, active.domain, active.password.copyOf())
                credentialBusy = true
                scope.launch {
                    try { credentials.save(snapshot); status = "Connection saved encrypted" }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { status = "Could not save credentials" }
                    finally { snapshot.password.fill('\u0000'); credentialBusy = false }
                }
            }) { Text("Remember connection") }
            Button(enabled = !loading && transfer == null && !credentialBusy, onClick = {
                credentialBusy = true
                scope.launch {
                    try {
                        val saved = credentials.load()
                        if (saved == null) status = "No saved connection"
                        else { endpoint = saved; path = emptyList(); status = null }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { status = "Saved credentials unavailable; forget and reconnect" }
                    finally { credentialBusy = false }
                }
            }) { Text("Saved connection") }
            Button(enabled = !credentialBusy && transfer == null, onClick = {
                credentialBusy = true
                scope.launch {
                    try { credentials.forget(); status = "Saved connection removed" }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { status = "Could not remove saved connection" }
                    finally { credentialBusy = false }
                }
            }) { Text("Forget saved") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = transfer == null && !resolving, onClick = { discovering = !discovering }) {
                Text(if (discovering) "Stop discovery" else "Find SMB servers")
            }
            if (discoveredHost.isNotEmpty()) Text("Selected: $discoveredHost · use Connect")
        }
        if (services.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 120.dp)) {
            items(services, key = { it.name }) { service ->
                Button(enabled = !resolving && transfer == null, onClick = {
                    resolving = true
                    scope.launch {
                        try { discoveredHost = withTimeout(10000) { resolveSmb(context, service) }; status = "Server selected; enter share and credentials" }
                        catch (e: TimeoutCancellationException) { status = "Server resolution timed out" }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { status = "Server resolution failed" }
                        finally { resolving = false }
                    }
                }) { Text(service.name) }
            }
        }
        Text(path.joinToString(" / ").ifEmpty { server?.share ?: "Enter server credentials" })
        status?.let { Text(it) }
        if (transfer != null) Button(onClick = { transfer?.cancel() }) { Text("Cancel transfer") }
        if (loading) Text("Connecting…")
        error?.let { Text(it) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.name }) { entry ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.directory) Button(enabled = transfer == null, onClick = { path = path + entry.name }, modifier = Modifier.weight(1f)) { Text(entry.name) }
                else {
                    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(entry.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)).orEmpty()
                    if (mime.startsWith("video/") || mime.startsWith("audio/")) Button(enabled = transfer == null, onClick = { playback = (path + entry.name).joinToString("\\") to entry }) { Text("Play") }
                    Button(enabled = transfer == null && pending == null && entry.bytes >= 0, onClick = {
                    pending = (path + entry.name).joinToString("\\") to entry
                    try { destination.launch(entry.name) }
                    catch (_: Exception) { pending = null; status = "System destination picker unavailable" }
                }, modifier = Modifier.weight(1f)) { Text("Download ${entry.name} · ${entry.bytes / 1024} KiB") }
                }
                Button(enabled = server != null && transfer == null && !loading, onClick = {
                    val active = server ?: return@Button
                    val folder = path.toList()
                    editName("Rename remote item", entry.name) { renameSmbEntry(active, folder, entry, it) }
                }) { Text("Rename") }
                Button(enabled = server != null && transfer == null && !loading, onClick = {
                    val active = server ?: return@Button
                    val folder = path.toList()
                    AlertDialog.Builder(context).setTitle("Permanently delete remote item?")
                        .setMessage(entry.name + if (entry.directory) "\nOnly empty folders can be deleted." else "\nThis cannot be undone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            transfer = scope.launch {
                                try { deleteSmbEntry(active, folder, entry); status = "Remote item deleted" }
                                catch (e: CancellationException) { throw e }
                                catch (_: Exception) { status = "Delete failed: item changed, folder not empty, access or connection" }
                                finally { transfer = null; refresh++ }
                            }
                        }.show()
                }) { Text("Delete") }
                }
            }
        }
    }
}
