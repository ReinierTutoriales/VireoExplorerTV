package com.reiniertutoriales.vireoexplorertv

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal data class TransferDigest(val bytes: Long, val hash: ByteArray) {
    fun matches(other: TransferDigest): Boolean = bytes == other.bytes && hash.contentEquals(other.hash)
}

internal suspend fun transferDigest(input: InputStream, output: OutputStream? = null, expected: Long? = null, progress: (Long) -> Unit = {}): TransferDigest {
    require(expected == null || expected >= 0)
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(128 * 1024)
    var total = 0L
    while (true) {
        coroutineContext.ensureActive()
        val n = input.read(buffer)
        if (n < 0) break
        check(n > 0) { "Input made no progress" }
        total = Math.addExact(total, n.toLong())
        check(expected == null || total <= expected) { "File size changed" }
        output?.write(buffer, 0, n)
        digest.update(buffer, 0, n)
        progress(total)
    }
    check(expected == null || total == expected) { "Incomplete transfer" }
    return TransferDigest(total, digest.digest())
}
