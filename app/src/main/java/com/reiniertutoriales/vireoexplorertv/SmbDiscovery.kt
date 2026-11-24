package com.reiniertutoriales.vireoexplorertv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

internal data class SmbService(val name: String, val type: String)
internal fun discoverSmb(context: Context) = callbackFlow<List<SmbService>> {
    val manager = context.getSystemService(NsdManager::class.java)
    val services = linkedMapOf<String, SmbService>()
    val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) {}
        override fun onDiscoveryStopped(type: String) { close() }
        override fun onStartDiscoveryFailed(type: String, error: Int) { close(IllegalStateException("Discovery failed: $error")) }
        override fun onStopDiscoveryFailed(type: String, error: Int) { close() }
        override fun onServiceFound(info: NsdServiceInfo) {
            synchronized(services) {
                if (services.size < 100) services[info.serviceName] = SmbService(info.serviceName, info.serviceType)
                trySend(services.values.toList())
            }
        }
        override fun onServiceLost(info: NsdServiceInfo) {
            synchronized(services) { services.remove(info.serviceName); trySend(services.values.toList()) }
        }
    }
    manager.discoverServices("_smb._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
    awaitClose { runCatching { manager.stopServiceDiscovery(listener) } }
}

@Suppress("DEPRECATION")
internal suspend fun resolveSmb(context: Context, service: SmbService): String =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(NsdManager::class.java)
        val info = NsdServiceInfo().apply { serviceName = service.name; serviceType = service.type }
        manager.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("Resolve failed: $error")))
            }
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress
                if (continuation.isActive) continuation.resumeWith(if (host != null && info.port == 445) Result.success(host)
                    else Result.failure(IllegalStateException("Unsupported SMB endpoint")))
            }
        })
    }
