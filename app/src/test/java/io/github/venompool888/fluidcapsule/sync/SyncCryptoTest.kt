package io.github.venompool888.fluidcapsule.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SyncCryptoTest {
    private val sender = keyPair()
    private val receiver = keyPair()
    private val senderKey = Base64.getEncoder().encodeToString(sender.public.encoded)
    private val context = EnvelopeContext("phone:event-123", "fluid-target", senderKey)
    private val payload = JSONObject().put("kind", "sms").put("type", 1)
        .put("address", "+61412345678").put("body", "验证码 482913，5 分钟内有效 🔒")
        .put("timestamp", 1794900000000L).put("subscriptionId", 2)

    @Test fun decryptsExactReferenceEnvelopeWithJvmPrivateKey() {
        val decrypted = SyncEnvelope.decrypt(receiver.private, context, encrypt())
        assertEquals(payload.toString(), decrypted.toString())
    }

    @Test fun rejectsSenderKeyDifferentFromServerRegistration() {
        val impostorKey = Base64.getEncoder().encodeToString(keyPair().public.encoded)
        val error = assertThrows(SecurityException::class.java) {
            SyncEnvelope.decrypt(receiver.private, context.copy(registeredSourceKey = impostorKey), encrypt())
        }
        assertEquals("发送设备公钥与服务器记录不一致", error.message)
    }

    @Test fun rejectsChangedEventIdAndWrongRecipientPrivateKey() {
        val envelope = encrypt()
        assertThrows(AEADBadTagException::class.java) {
            SyncEnvelope.decrypt(receiver.private, context.copy(eventId = "changed-event"), envelope)
        }
        assertThrows(AEADBadTagException::class.java) {
            SyncEnvelope.decrypt(keyPair().private, context, envelope)
        }
    }

    @Test fun rejectsRecipientIdRebindingAndTamperedCiphertext() {
        val rebound = encrypt()
        rebound.getJSONObject("wrappedKeys").put("another-target",
            rebound.getJSONObject("wrappedKeys").getJSONObject(context.deviceId))
        assertThrows(AEADBadTagException::class.java) {
            SyncEnvelope.decrypt(receiver.private, context.copy(deviceId = "another-target"), rebound)
        }
        val tampered = encrypt()
        val ciphertext = Base64.getDecoder().decode(tampered.getString("ciphertext"))
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        tampered.put("ciphertext", encode(ciphertext))
        assertThrows(AEADBadTagException::class.java) { SyncEnvelope.decrypt(receiver.private, context, tampered) }
    }

    @Test fun rejectsUnsupportedEnvelopeVersionAndAlgorithm() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncEnvelope.decrypt(receiver.private, context, encrypt().put("version", 2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncEnvelope.decrypt(receiver.private, context, encrypt().put("algorithm", "A256GCM"))
        }
    }

    // This encryptor mirrors CloudCrypto.java independently of the production decryption helpers.
    private fun encrypt(): JSONObject {
        val contentKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val payloadIv = iv()
        val payloadCiphertext = aes(contentKey, payloadIv, context.eventId, payload.toString().toByteArray(Charsets.UTF_8))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(sender.private)
        agreement.doPhase(receiver.public, true)
        val shared = agreement.generateSecret()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("capsule-wrap-v1\n".toByteArray(Charsets.UTF_8))
        digest.update(shared)
        digest.update("\n${context.eventId}\n${context.deviceId}".toByteArray(Charsets.UTF_8))
        val wrappingKey = SecretKeySpec(digest.digest(), "AES")
        val wrapIv = iv()
        val wrapped = aes(wrappingKey, wrapIv, "${context.eventId}\n${context.deviceId}", contentKey.encoded)
        return JSONObject().put("version", 1).put("algorithm", "ECDH-P256+A256GCM").put("senderPublicKey", senderKey)
            .put("payloadIv", encode(payloadIv)).put("ciphertext", encode(payloadCiphertext))
            .put("wrappedKeys", JSONObject().put(context.deviceId, JSONObject().put("iv", encode(wrapIv)).put("ciphertext", encode(wrapped))))
    }

    private fun aes(key: SecretKey, nonce: ByteArray, aad: String, plaintext: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            updateAAD(aad.toByteArray(Charsets.UTF_8))
            doFinal(plaintext)
        }
    private fun iv() = ByteArray(12).also { SecureRandom().nextBytes(it) }
    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
}
