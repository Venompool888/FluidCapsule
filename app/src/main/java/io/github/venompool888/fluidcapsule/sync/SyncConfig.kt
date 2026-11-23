package io.github.venompool888.fluidcapsule.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SyncConfig(context: Context) {
    private val httpApprovals = context.getSharedPreferences("sms_sync_http_approvals", Context.MODE_PRIVATE)
    private val prefs = context.getSharedPreferences("sms_sync", Context.MODE_PRIVATE)
    val server: String get() = prefs.getString("server", "").orEmpty()
    val deviceId: String get() = prefs.getString("device_id", "").orEmpty()
    val account: String get() = prefs.getString("account", "").orEmpty()
    val lastError: String get() = prefs.getString("last_error", "").orEmpty()
    val receiveFrom: Set<String> get() = prefs.getStringSet("receive_from", emptySet()).orEmpty().toSet()
    val cachedState: String get() = prefs.getString("state", "").orEmpty()
    val isConnected: Boolean get() = server.isNotBlank() && deviceId.isNotBlank() && token().isNotBlank()

    // An unreadable token means disconnected; expose the key/storage failure in sync-status.
    fun token(): String {
        val ciphertext = prefs.getString("token_ciphertext", "").orEmpty()
        if (ciphertext.isBlank()) return ""
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, tokenKey(), GCMParameterSpec(128,
                    Base64.getDecoder().decode(prefs.getString("token_iv", ""))))
                String(doFinal(Base64.getDecoder().decode(ciphertext)), Charsets.UTF_8)
            }
        } catch (error: Exception) {
            recordError("无法读取设备令牌：${error.javaClass.simpleName}，请重新连接")
            ""
        }
    }

    fun storeConnection(server: String, response: org.json.JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, tokenKey()) }
        val encrypted = cipher.doFinal(response.getString("deviceToken").toByteArray(Charsets.UTF_8))
        synchronized(connectionLock) {
            check(prefs.edit().clear().putString("server", server)
                .putString("device_id", response.getString("deviceId"))
                .putString("account", response.optString("account"))
                .putString("token_iv", Base64.getEncoder().encodeToString(cipher.iv))
                .putString("token_ciphertext", Base64.getEncoder().encodeToString(encrypted)).commit()) {
                "无法安全保存设备令牌"
            }
        }
    }

    fun storeState(json: org.json.JSONObject, state: SyncDeviceState) {
        prefs.edit().putString("state", json.toString()).putStringSet("receive_from", state.receiveFrom).apply()
    }

    fun requiresHttpApproval(server: String): Boolean = SyncApi.needsHttpConfirmation(server) && !httpApprovals.getBoolean(server, false)
    fun approveHttp(server: String) { httpApprovals.edit().putBoolean(server, true).apply() }

    fun recordError(message: String?) { prefs.edit().putString("last_error", message).apply() }

    fun recordRealtimeError(deviceId: String, message: String) {
        synchronized(connectionLock) {
            if (this.deviceId == deviceId) recordError(message)
        }
    }

    fun clear() {
        synchronized(connectionLock) { check(prefs.edit().clear().commit()) { "无法清除同步设置" } }
    }

    fun processedIds(): LinkedHashSet<String> {
        val ids = JSONArray(prefs.getString("processed", "[]"))
        return (0 until ids.length()).mapTo(linkedSetOf()) { ids.getString(it) }
    }

    fun rememberProcessed(ids: LinkedHashSet<String>, eventId: String) {
        ids.add(eventId)
        while (ids.size > 500) ids.remove(ids.first())
        // Persist before acknowledging so a lost HTTP response does not duplicate a capsule.
        check(prefs.edit().putString("processed", JSONArray(ids.toList()).toString()).commit()) {
            "无法保存同步去重记录"
        }
    }

    private fun tokenKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(TOKEN_ALIAS)) return store.getKey(TOKEN_ALIAS, null) as SecretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(TOKEN_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }

    companion object {
        private const val TOKEN_ALIAS = "fluidcapsule_sync_token_v1"
        private val connectionLock = Any()
    }
}
