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

private class SmbEndpoint(val host: String, val share: String, val user: String, val domain: String, val password: CharArray)
private data class SmbEntry(val name: String, val directory: Boolean, val bytes: Long)

private suspend fun listSmb(endpoint: SmbEndpoint, path: String): List<SmbEntry> = withContext(Dispatchers.IO) {
    val config = SmbConfig.builder().withTimeout(10, TimeUnit.SECONDS)
        .withSoTimeout(10, TimeUnit.SECONDS).withSigningRequired(true).build()
    SMBClient(config).use { client ->
        client.connect(endpoint.host).use { connection ->
            val password = endpoint.password.copyOf()
            val session = try { connection.authenticate(AuthenticationContext(endpoint.user, password, endpoint.domain)) }
            finally { password.fill('\u0000') }
            (session.connectShare(endpoint.share) as DiskShare).use { share ->
                share.list(path).asSequence().filter { it.fileName != "." && it.fileName != ".." }
                    .take(5001).map { SmbEntry(it.fileName, it.fileAttributes and 16L != 0L, it.endOfFile) }
                    .toList().also { check(it.size <= 5000) { "Directory limit exceeded" } }
                    .sortedWith(compareByDescending<SmbEntry> { it.directory }.thenBy { it.name.lowercase(java.util.Locale.ROOT) })
            }
        }
    }
}

private suspend fun downloadSmb(endpoint: SmbEndpoint, path: String, expected: Long, resolver: android.content.ContentResolver, target: android.net.Uri) = withContext(Dispatchers.IO) {
    val config = SmbConfig.builder().withTimeout(10, TimeUnit.SECONDS)
        .withSoTimeout(10, TimeUnit.SECONDS).withSigningRequired(true).build()
    val password = endpoint.password.copyOf()
    try {
        SMBClient(config).use { client ->
            client.connect(endpoint.host).use { connection ->
                val session = connection.authenticate(AuthenticationContext(endpoint.user, password, endpoint.domain))
                (session.connectShare(endpoint.share) as DiskShare).use { share ->
                    share.openFile(path, EnumSet.of(AccessMask.GENERIC_READ), null,
                        EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ), SMB2CreateDisposition.FILE_OPEN,
                        EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)).use { file ->
                        file.inputStream.use { input ->
                            checkNotNull(resolver.openOutputStream(target, "w")).use { output ->
                                val buffer = ByteArray(128 * 1024)
                                var total = 0L
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    check(total <= expected) { "Remote file changed" }
                                    output.write(buffer, 0, n)
                                }
                                check(total == expected) { "Incomplete transfer" }
                            }
                        }
                    }
                }
            }
        }
    } finally { password.fill('\u0000') }
}

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
    var pending by remember { mutableStateOf<Pair<String, SmbEntry>?>(null) }
    var transfer by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val server = endpoint
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val request = pending
        pending = null
        if (uri != null && request != null && server != null) {
            transfer = scope.launch {
                try {
                    downloadSmb(server, request.first, request.second.bytes, context.contentResolver, uri)
                    status = "Download completed"
                } catch (e: CancellationException) {
                    status = "Download cancelled; destination may contain partial data"
                    throw e
                } catch (_: Exception) {
                    status = "Download failed; destination may contain partial data"
                } finally { transfer = null }
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
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SMB 2/3 · browse and download")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { back() }) { Text("Back") }
            Button(enabled = !loading && transfer == null, onClick = {
                val fields = listOf("Server host or IP", "Share name", "Username", "Domain (optional)", "Password").map { label ->
                    EditText(context).apply { hint = label; setSingleLine(true) }
                }
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
        }
        Text(path.joinToString(" / ").ifEmpty { server?.share ?: "Enter server credentials" })
        status?.let { Text(it) }
        if (transfer != null) Button(onClick = { transfer?.cancel() }) { Text("Cancel download") }
        if (loading) Text("Connecting…")
        error?.let { Text(it) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.name }) { entry ->
                if (entry.directory) Button(enabled = transfer == null, onClick = { path = path + entry.name }, modifier = Modifier.fillMaxWidth()) { Text(entry.name) }
                else Button(enabled = transfer == null && pending == null && entry.bytes >= 0, onClick = {
                    pending = (path + entry.name).joinToString("\\") to entry
                    try { destination.launch(entry.name) }
                    catch (_: Exception) { pending = null; status = "System destination picker unavailable" }
                }, modifier = Modifier.fillMaxWidth()) { Text("Download ${entry.name} · ${entry.bytes / 1024} KiB") }
            }
        }
    }
}
