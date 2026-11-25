package com.reiniertutoriales.vireoexplorertv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.io.File

internal data class VolumeInfo(
    val label: String,
    val state: String,
    val removable: Boolean,
    val volume: StorageVolume,
    val directory: File?
) {
    val isMounted: Boolean
        get() = state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY
    val isWritable: Boolean
        get() = state == Environment.MEDIA_MOUNTED && (directory?.canWrite() != false)
    val directUri: Uri?
        get() = directory?.takeIf { isMounted }?.let { Uri.fromFile(it) }
}

internal fun hasDirectStorageAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

internal fun hasDirectStorageWriteAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

internal fun primaryDirectStorageDir(): File? {
    val dir = Environment.getExternalStorageDirectory()
    return dir?.takeIf { it.exists() }
}

private fun resolveVolumeDirectory(volume: StorageVolume): File? {
    if (Build.VERSION.SDK_INT >= 30) {
        volume.directory?.let { return it }
    }
    if (volume.isPrimary) {
        return Environment.getExternalStorageDirectory()
    }
    val uuid = volume.uuid
    if (!uuid.isNullOrBlank()) {
        val candidate = File("/storage", uuid)
        if (candidate.exists()) return candidate
    }
    return null
}

internal fun observeVolumes(context: Context) = callbackFlow<List<VolumeInfo>> {
    val manager = context.getSystemService(StorageManager::class.java)
    fun refresh() {
        trySend(
            manager.storageVolumes.map { volume ->
                VolumeInfo(
                    label = volume.getDescription(context),
                    state = volume.state,
                    removable = volume.isRemovable,
                    volume = volume,
                    directory = resolveVolumeDirectory(volume)
                )
            }
        )
    }
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { refresh() }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_MEDIA_MOUNTED)
        addAction(Intent.ACTION_MEDIA_UNMOUNTED)
        addAction(Intent.ACTION_MEDIA_REMOVED)
        addAction(Intent.ACTION_MEDIA_EJECT)
        addDataScheme("file")
    }
    if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    else context.registerReceiver(receiver, filter)
    refresh()
    awaitClose { context.unregisterReceiver(receiver) }
}.conflate()
