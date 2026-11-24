package com.reiniertutoriales.vireoexplorertv

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import android.net.Uri
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.*
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import java.io.IOException

@UnstableApi
internal class SmbDataSource(private val endpoint: SmbEndpoint, private val allowedPaths: Map<Uri, String>) : BaseDataSource(true) {
    private var client: SMBClient? = null
    private var share: DiskShare? = null
    private var file: File? = null
    private var uri: Uri? = null
    private var offset = 0L
    private var remaining = 0L
    private var started = false
    override fun open(dataSpec: DataSpec): Long {
        val path = allowedPaths[dataSpec.uri] ?: throw IOException("SMB URI outside playback session")
        transferInitializing(dataSpec)
        val password = endpoint.password.copyOf()
        try {
            val c = SMBClient(SmbConfig.builder().withTimeout(10, TimeUnit.SECONDS)
                .withSoTimeout(10, TimeUnit.SECONDS).withSigningRequired(true).build())
            client = c
            val session = c.connect(endpoint.host).authenticate(AuthenticationContext(endpoint.user, password, endpoint.domain))
            val disk = session.connectShare(endpoint.share) as DiskShare
            share = disk
            val f = disk.openFile(path, EnumSet.of(AccessMask.GENERIC_READ), null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ), SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE))
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
        } finally { password.fill('\u0000') }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val n = try { checkNotNull(file).read(buffer, this.offset, offset, minOf(length.toLong(), remaining).toInt()) }
        catch (e: Exception) { throw IOException("SMB read failed", e) }
        if (n <= 0) throw IOException("Unexpected SMB end of file")
        this.offset += n
        remaining -= n
        bytesTransferred(n)
        return n
    }
    override fun getUri(): Uri? = uri
    override fun close() {
        try { file?.close() } catch (_: Exception) {}
        try { share?.close() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        file = null; share = null; client = null; uri = null
        if (started) { started = false; transferEnded() }
    }
}
