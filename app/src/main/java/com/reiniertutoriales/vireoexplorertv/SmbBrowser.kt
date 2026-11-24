package com.reiniertutoriales.vireoexplorertv

import android.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import android.text.InputType
import androidx.activity.compose.BackHandler
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

@Composable
internal fun SmbBrowser(onClose: () -> Unit) {
    val context = LocalContext.current
    var endpoint by remember { mutableStateOf<SmbEndpoint?>(null) }
    var path by remember { mutableStateOf<List<String>>(emptyList()) }
    var entries by remember { mutableStateOf<List<SmbEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val server = endpoint
    DisposableEffect(server) { onDispose { server?.password?.fill('\u0000') } }
    fun back() { if (path.isNotEmpty()) path = path.dropLast(1) else onClose() }
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
        Text("SMB 2/3 · read-only")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { back() }) { Text("Back") }
            Button(enabled = !loading, onClick = {
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
            Button(enabled = server != null && !loading, onClick = { refresh++ }) { Text("Refresh") }
        }
        Text(path.joinToString(" / ").ifEmpty { server?.share ?: "Enter server credentials" })
        if (loading) Text("Connecting…")
        error?.let { Text(it) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.name }) { entry ->
                if (entry.directory) Button(onClick = { path = path + entry.name }, modifier = Modifier.fillMaxWidth()) { Text(entry.name) }
                else Text("${entry.name} · ${entry.bytes / 1024} KiB")
            }
        }
    }
}
