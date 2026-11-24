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
import kotlinx.coroutines.launch

private data class InstalledApp(val name: String, val pkg: String, val system: Boolean)

@Composable
internal fun AppManager(onClose: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var cleaning by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<String?>(null) }
    var statsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
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
        Button(onClick = { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Authorize usage statistics") }
        Button(enabled = !cleaning, onClick = {
            android.app.AlertDialog.Builder(context).setTitle("Clear explorer analysis cache?")
                .setMessage("Only the explorer's dedicated analysis cache is affected.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    cleaning = true
                    scope.launch {
                        try {
                            val bytes = withContext(Dispatchers.IO) { clearAnalysisCache(context.cacheDir) }
                            stats = "Removed $bytes bytes from analysis cache"
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = "Cache cleanup incomplete" }
                        finally { cleaning = false }
                    }
                }.show()
        }) { Text("Clear analysis cache") }
        stats?.let { Text(it) }
        Text("Visible applications only. Clear cache, clear data and uninstall through Android settings.")
        error?.let { Text(it) }
        if (loading) Text("Loading applications…")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(apps.filter { showSystem || !it.system }, key = { it.pkg }) { app ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null))) }, modifier = Modifier.weight(1f)) {
                    Column { Text(app.name); Text(app.pkg) }
                }
                Button(enabled = android.os.Build.VERSION.SDK_INT >= 26, onClick = {
                    statsJob?.cancel()
                    statsJob = scope.launch {
                        stats = try { withContext(Dispatchers.IO) { applicationStorage(context, app.pkg) } }
                        catch (e: CancellationException) { throw e }
                        catch (_: SecurityException) { "Usage access required" }
                        catch (_: Exception) { "Storage statistics unavailable" }
                    }
                }) { Text("Size") }
                }
            }
        }
    }
}

private fun applicationStorage(context: android.content.Context, pkg: String): String {
    if (android.os.Build.VERSION.SDK_INT < 26) return "Storage statistics require Android 8"
    val info = context.packageManager.getApplicationInfo(pkg, 0)
    val manager = context.getSystemService(android.app.usage.StorageStatsManager::class.java)
    val stats = manager.queryStatsForPackage(info.storageUuid, pkg, android.os.Process.myUserHandle())
    return "$pkg: app ${stats.appBytes / 1048576} MiB · data ${stats.dataBytes / 1048576} MiB (includes cache) · cache ${stats.cacheBytes / 1048576} MiB"
}

// Reserved exclusively for inactive analysis artifacts; never clear playback or transfer files.
private fun clearAnalysisCache(cache: java.io.File): Long {
    val directory = java.io.File(cache, "analysis-inactive")
    if (!directory.exists()) return 0
    check(directory.canonicalFile == directory.absoluteFile)
    var removed = 0L
    for (file in directory.listFiles() ?: error("Cache unavailable")) {
        if (!file.isFile || file.canonicalFile != file.absoluteFile) continue
        val size = file.length()
        if (file.delete()) removed += size else error("Cache cleanup incomplete")
    }
    return removed
}
