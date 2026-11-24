package com.reiniertutoriales.vireoexplorertv

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.TimeUnit

internal class SmbShareSession(endpoint: SmbEndpoint) : Closeable {
    private val host = endpoint.host
    private val shareName = endpoint.share
    private val user = endpoint.user
    private val domain = endpoint.domain
    private val secret = endpoint.password.copyOf()

    private val lock = Any()
    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null
    private var closed = false

    private fun connectLocked(): DiskShare {
        check(!closed) { "SMB session closed" }
        val existing = share
        if (existing != null && existing.isConnected && connection?.isConnected == true) {
            return existing
        }
        closeResourcesLocked()
        val password = secret.copyOf()
        try {
            val smbClient = SMBClient(
                SmbConfig.builder()
                    .withTimeout(10, TimeUnit.SECONDS)
                    .withSoTimeout(10, TimeUnit.SECONDS)
                    .withSigningRequired(true)
                    .build()
            )
            client = smbClient
            val conn = smbClient.connect(host)
            connection = conn
            val sess = conn.authenticate(AuthenticationContext(user, password, domain))
            session = sess
            val disk = sess.connectShare(shareName) as DiskShare
            share = disk
            return disk
        } catch (e: Exception) {
            closeResourcesLocked()
            throw e
        } finally {
            password.fill('\u0000')
        }
    }

    private fun acquireShare(): DiskShare = synchronized(lock) { connectLocked() }

    private fun reconnectAfterFailure(failedShare: DiskShare): DiskShare? = synchronized(lock) {
        if (closed) return null
        val current = share
        if (current != null && current !== failedShare && current.isConnected && connection?.isConnected == true) {
            return current
        }
        if (current == null || !current.isConnected || connection?.isConnected != true) {
            closeResourcesLocked()
            connectLocked()
        } else null
    }

    fun <T> execute(block: (DiskShare) -> T): T {
        val active = acquireShare()
        return try {
            block(active)
        } catch (first: Exception) {
            val reconnect = reconnectAfterFailure(active) ?: throw first
            block(reconnect)
        }
    }

    suspend fun <T> executeSuspend(block: suspend (DiskShare) -> T): T = withContext(Dispatchers.IO) {
        val active = acquireShare()
        try {
            block(active)
        } catch (c: CancellationException) {
            throw c
        } catch (first: Exception) {
            val reconnect = reconnectAfterFailure(active) ?: throw first
            block(reconnect)
        }
    }

    fun openReadOnlyFile(remotePath: String): File = execute { disk ->
        disk.openFile(
            remotePath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )
    }

    private fun closeResourcesLocked() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        share = null
        session = null
        connection = null
        client = null
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            secret.fill('\u0000')
            closeResourcesLocked()
        }
    }

    fun closeAsync() {
        Thread { runCatching { close() } }.apply {
            name = "vireo-smb-close"
            isDaemon = true
            start()
        }
    }
}

@UnstableApi
internal class SmbDataSource(
    private val session: SmbShareSession,
    private val allowedPaths: Map<Uri, String>
) : BaseDataSource(true) {
    private var file: File? = null
    private var uri: Uri? = null
    private var offset = 0L
    private var remaining = 0L
    private var started = false

    override fun open(dataSpec: DataSpec): Long {
        val path = allowedPaths[dataSpec.uri] ?: throw IOException("SMB URI outside playback session")
        transferInitializing(dataSpec)
        try {
            val f = session.openReadOnlyFile(path)
            file = f
            val size = f.fileInformation.standardInformation.endOfFile
            offset = dataSpec.position
            if (offset > size) throw IOException("Position outside file")
            remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) size - offset else minOf(dataSpec.length, size - offset)
            uri = dataSpec.uri
            started = true
            transferStarted(dataSpec)
            return remaining
        } catch (e: Exception) {
            close()
            throw IOException("SMB open failed", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val n = try {
            checkNotNull(file).read(buffer, this.offset, offset, minOf(length.toLong(), remaining).toInt())
        } catch (e: Exception) {
            throw IOException("SMB read failed", e)
        }
        if (n <= 0) throw IOException("Unexpected SMB end of file")
        this.offset += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { file?.close() } catch (_: Exception) {}
        file = null
        uri = null
        if (started) {
            started = false
            transferEnded()
        }
    }
}
