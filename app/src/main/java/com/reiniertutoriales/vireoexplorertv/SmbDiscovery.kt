package com.reiniertutoriales.vireoexplorertv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

internal data class SmbService(val name: String, val info: NsdServiceInfo)
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
                if (services.size < 100) services[info.serviceName] = SmbService(info.serviceName, info)
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
        val info = service.info
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            resolveModern(manager, info, continuation)
            return@suspendCancellableCoroutine
        }
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

@android.annotation.TargetApi(34)
private fun resolveModern(manager: NsdManager, info: NsdServiceInfo, continuation: kotlinx.coroutines.CancellableContinuation<String>) {
    val finished = java.util.concurrent.atomic.AtomicBoolean(false)
    val callback = object : NsdManager.ServiceInfoCallback {
        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
            if (finished.compareAndSet(false, true)) continuation.resumeWith(Result.failure(IllegalStateException("Resolution registration failed: $errorCode")))
        }
        override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.hostAddresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress
                ?: serviceInfo.hostAddresses.firstOrNull()?.hostAddress
            if (host != null && serviceInfo.port == 445) {
                if (finished.compareAndSet(false, true)) {
                    runCatching { manager.unregisterServiceInfoCallback(this) }
                    continuation.resumeWith(Result.success(host))
                }
            }
        }
        override fun onServiceLost() {
            if (finished.compareAndSet(false, true)) continuation.resumeWith(Result.failure(IllegalStateException("Service lost")))
            runCatching { manager.unregisterServiceInfoCallback(this) }
        }
        override fun onServiceInfoCallbackUnregistered() {}
    }
    manager.registerServiceInfoCallback(info, java.util.concurrent.Executor { it.run() }, callback)
    continuation.invokeOnCancellation { finished.set(true); runCatching { manager.unregisterServiceInfoCallback(callback) } }
}
