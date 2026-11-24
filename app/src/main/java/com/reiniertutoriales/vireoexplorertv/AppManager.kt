package com.reiniertutoriales.vireoexplorertv

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

private data class InstalledApp(val name: String, val pkg: String, val system: Boolean)

@Composable
internal fun AppManager(onClose: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showSystem by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    fun open(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: Exception) { error = "System action unavailable on this device" }
    }
    BackHandler(onBack = onClose)
    LaunchedEffect(revision) {
        loading = true
        try {
            apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(0).map {
                    InstalledApp(pm.getApplicationLabel(it).toString(), it.packageName, it.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Unable to list visible applications" }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Applications and system storage")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onClose) { Text("Back") }
            Button(onClick = { open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }) { Text("System storage") }
            Button(onClick = { open(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) }) { Text("Manage apps") }
            Button(onClick = { showSystem = !showSystem }) { Text(if (showSystem) "Hide system" else "Show system") }
            Button(onClick = { revision++ }) { Text("Refresh") }
        }
        Text("Visible applications only. Clear cache, clear data and uninstall through Android settings.")
        error?.let { Text(it) }
        if (loading) Text("Loading applications…")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(apps.filter { showSystem || !it.system }, key = { it.pkg }) { app ->
                Button(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null))) }, modifier = Modifier.fillMaxWidth()) {
                    Column { Text(app.name); Text(app.pkg) }
                }
            }
        }
    }
}
