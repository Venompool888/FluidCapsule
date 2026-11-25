package io.github.venompool888.fluidcapsule.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class SyncAttemptTest {
    @Test fun emptySuccessfulPullClearsPreviousError() {
        var lastError: String? = "connection refused"

        val published = runSyncAttempt({ lastError = it }) {
            assertEquals("connection refused", lastError)
            0
        }

        assertEquals(0, published)
        assertNull(lastError)
    }

    @Test fun messageFailureRemainsVisibleUntilNextCleanAttempt() {
        var lastError: String? = "connection refused"
        val recordError: (String?) -> Unit = { lastError = it }

        val published = runSyncAttempt(recordError) { reportMessageError ->
            reportMessageError("envelope could not be decrypted")
            assertEquals("envelope could not be decrypted", lastError)
            1
        }

        assertEquals(1, published)
        assertEquals("envelope could not be decrypted", lastError)
        runSyncAttempt(recordError) { Unit }
        assertNull(lastError)
    }

    @Test fun failedAttemptRethrowsAndReplacesMessageError() {
        var lastError: String? = null
        val failure = IOException("acknowledgement timed out")

        val thrown = assertThrows(IOException::class.java) {
            runSyncAttempt({ lastError = it }) { reportMessageError ->
                reportMessageError("envelope could not be decrypted")
                throw failure
            }
        }

        assertSame(failure, thrown)
        assertEquals("acknowledgement timed out", lastError)
    }

    @Test fun successfulDeviceRefreshDoesNotClearFailedPull() {
        var lastError: String? = null
        val recordError: (String?) -> Unit = { lastError = it }

        assertThrows(IOException::class.java) {
            runSyncAttempt(recordError) { throw IOException("connection refused") }
        }
        runSyncAttempt(recordError, clearErrorOnSuccess = false) { Unit }
        assertEquals("connection refused", lastError)

        runSyncAttempt(recordError) { 0 }
        assertNull(lastError)
    }

    @Test fun skippedMessageOutlivesTheNextCleanPull() {
        var lastError: String? = null
        var lastSkipped = ""
        val recordError: (String?) -> Unit = { lastError = it }

        runSyncAttempt(recordError) { reportMessageError ->
            skipReporter({ lastSkipped = it }, reportMessageError)("message e1 could not be decrypted")
        }
        assertEquals("message e1 could not be decrypted", lastError)

        runSyncAttempt(recordError) { 0 }
        assertNull(lastError)
        assertEquals("message e1 could not be decrypted", lastSkipped)
    }

    @Test fun keystoreTroubleIsRetriedAndJsonErrorsDoNotQuoteTheMessage() {
        assertTrue(isTransientDecryptFailure(java.security.ProviderException("keystore busy")))
        assertTrue(isTransientDecryptFailure(java.security.KeyStoreException("locked")))
        assertFalse(isTransientDecryptFailure(javax.crypto.AEADBadTagException("tag mismatch")))
        assertFalse(isTransientDecryptFailure(SecurityException("sender key mismatch")))
        assertEquals("内容格式无效", decryptFailureReason(org.json.JSONException("Value secret text of type java.lang.String")))
        assertEquals("tag mismatch", decryptFailureReason(javax.crypto.AEADBadTagException("tag mismatch")))
    }

    @Test fun onlyUnauthorizedResponsesCountAsRevoked() {
        assertTrue(isRevokedToken(SyncApi.ApiException(401, "unauthorized")))
        assertTrue(isRevokedToken(java.util.concurrent.ExecutionException(SyncApi.ApiException(403, "revoked"))))
        assertFalse(isRevokedToken(SyncApi.ApiException(503, "unavailable")))
        assertFalse(isRevokedToken(IOException("connection refused")))
    }
}
