package io.github.venompool888.fluidcapsule.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SyncCrypto {
    private const val KEY_ALIAS = "fluidcapsule_sync_ecdh_v1"

    fun publicKeyBase64(): String = Base64.getEncoder().encodeToString(keyPair().public.encoded)

    fun decrypt(eventId: String, deviceId: String, registeredSourceKey: String, envelope: JSONObject): JSONObject =
        SyncEnvelope.decrypt(keyPair().private, EnvelopeContext(eventId, deviceId, registeredSourceKey), envelope)

    @Synchronized
    private fun keyPair(): KeyPair {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(KEY_ALIAS)) {
            return KeyPair(store.getCertificate(KEY_ALIAS).publicKey, store.getKey(KEY_ALIAS, null) as PrivateKey)
        }
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).build())
        }.generateKeyPair()
    }
}

data class EnvelopeContext(val eventId: String, val deviceId: String, val registeredSourceKey: String)

/** Protocol math is independent of AndroidKeyStore so it can be checked on the JVM. */
object SyncEnvelope {
    fun decrypt(privateKey: PrivateKey, context: EnvelopeContext, envelope: JSONObject): JSONObject {
        require(envelope.optInt("version") == 1 && envelope.optString("algorithm") == "ECDH-P256+A256GCM") {
            "不支持的加密信封"
        }
        val senderKey = envelope.getString("senderPublicKey")
        if (!MessageDigest.isEqual(senderKey.toByteArray(Charsets.UTF_8), context.registeredSourceKey.toByteArray(Charsets.UTF_8))) {
            throw SecurityException("发送设备公钥与服务器记录不一致")
        }
        val wrapped = envelope.getJSONObject("wrappedKeys").getJSONObject(context.deviceId)
        val wrappingKey = wrappingKey(privateKey, senderKey, context)
        val contentKey = decryptBytes(wrappingKey, wrapped.getString("iv"),
            "${context.eventId}\n${context.deviceId}", wrapped.getString("ciphertext"))
        require(contentKey.size == 32) { "内容密钥长度无效" }
        val plaintext = decryptBytes(SecretKeySpec(contentKey, "AES"), envelope.getString("payloadIv"),
            context.eventId, envelope.getString("ciphertext"))
        return JSONObject(String(plaintext, Charsets.UTF_8))
    }

    private fun wrappingKey(privateKey: PrivateKey, senderKey: String, context: EnvelopeContext): SecretKeySpec {
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decode(senderKey)))
        val shared = KeyAgreement.getInstance("ECDH").apply {
            init(privateKey)
            doPhase(publicKey, true)
        }.generateSecret()
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update("capsule-wrap-v1\n".toByteArray(Charsets.UTF_8))
            update(shared)
            update("\n${context.eventId}\n${context.deviceId}".toByteArray(Charsets.UTF_8))
        }
        return SecretKeySpec(digest.digest(), "AES")
    }

    private fun decryptBytes(key: SecretKeySpec, iv: String, aad: String, ciphertext: String): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, decode(iv)))
            updateAAD(aad.toByteArray(Charsets.UTF_8))
            doFinal(decode(ciphertext))
        }

    private fun decode(encoded: String): ByteArray = Base64.getDecoder().decode(encoded)
}
