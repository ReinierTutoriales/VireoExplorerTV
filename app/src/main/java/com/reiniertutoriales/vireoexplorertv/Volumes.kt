package com.reiniertutoriales.vireoexplorertv

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.os.storage.StorageManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

internal data class VolumeInfo(val label: String, val state: String, val removable: Boolean)

internal fun observeVolumes(context: Context) = callbackFlow<List<VolumeInfo>> {
    val manager = context.getSystemService(StorageManager::class.java)
    fun refresh() {
        trySend(manager.storageVolumes.map { VolumeInfo(it.getDescription(context), it.state, it.isRemovable) })
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
    if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    else context.registerReceiver(receiver, filter)
    refresh()
    awaitClose { context.unregisterReceiver(receiver) }
}.conflate()
