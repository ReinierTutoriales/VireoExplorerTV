package com.reiniertutoriales.vireoexplorertv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SmbCredentials(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("smb_credentials", Context.MODE_PRIVATE)
    private val alias = "vireo.smb.credentials.v1"
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Credential key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    suspend fun save(endpoint: SmbEndpoint) = withContext(Dispatchers.IO) {
        val data = JSONObject().put("host", endpoint.host).put("share", endpoint.share)
            .put("user", endpoint.user).put("domain", endpoint.domain).put("password", String(endpoint.password))
            .toString().toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(true))
            val encrypted = cipher.doFinal(data)
            check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
        } finally { data.fill(0) }
    }
    suspend fun load(): SmbEndpoint? = withContext(Dispatchers.IO) {
        val encoded = prefs.getString("data", null) ?: return@withContext null
        val iv = Base64.decode(checkNotNull(prefs.getString("iv", null)), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, iv))
        val data = cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP))
        try {
            val json = JSONObject(String(data, Charsets.UTF_8))
            SmbEndpoint(json.getString("host"), json.getString("share"), json.getString("user"),
                json.getString("domain"), json.getString("password").toCharArray())
        } finally { data.fill(0) }
    }
    suspend fun forget() = withContext(Dispatchers.IO) {
        check(prefs.edit().clear().commit())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}
