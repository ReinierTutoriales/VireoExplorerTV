package com.reiniertutoriales.vireoexplorertv

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val appIconDispatcher = Dispatchers.IO.limitedParallelism(2)

@Composable
internal fun InstalledAppIcon(packageName: String, revision: Int) {
    val pm = LocalContext.current.packageManager
    val bitmap by produceState<android.graphics.Bitmap?>(null, packageName, revision) {
        value = try {
            withContext(appIconDispatcher) {
                pm.getApplicationIcon(packageName).toBitmap(width = 96, height = 96)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    val icon = bitmap
    if (icon != null) Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp))
    else ExplorerIcon(R.drawable.symbol_android, Modifier.size(48.dp))
}

@Composable
internal fun AppActionsDialog(
    name: String,
    packageName: String,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = VireoCardShape,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InstalledAppIcon(packageName, 0)
                    Text(name, style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = onOpen, modifier = Modifier.focusRequester(focus)) {
                    Text(stringResource(R.string.manager_open))
                }
                OutlinedButton(onClick = onSettings) { Text(stringResource(R.string.manager_settings)) }
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.explorer_back)) }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
