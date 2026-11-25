package io.github.venompool888.fluidcapsule.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncApiTest {
    @Test fun normalizesWhitespaceAndTrailingSlashesAndPreservesBasePath() {
        assertEquals("https://example.com", SyncApi.normalizeServer("  https://example.com///  "))
        assertEquals("http://localhost:8080/sync", SyncApi.normalizeServer("http://localhost:8080/sync/"))
        assertEquals("HTTPS://example.com", SyncApi.normalizeServer("HTTPS://example.com/"))
    }

    @Test fun rejectsMissingHostSchemeCredentialsQueryAndFragment() {
        listOf("", "example.com", "ftp://example.com", "https:///", "https://user:secret@example.com",
            "https://example.com?token=x", "https://example.com/#fragment", "https://bad host").forEach { server ->
            assertThrows(server, IllegalArgumentException::class.java) { SyncApi.normalizeServer(server) }
        }
    }

    @Test fun onlyLoopbackHttpAndHttpsAvoidCleartextConfirmation() {
        assertFalse(SyncApi.needsHttpConfirmation("http://localhost:8080"))
        assertFalse(SyncApi.needsHttpConfirmation("http://127.0.0.1:8080"))
        assertFalse(SyncApi.needsHttpConfirmation("https://example.com"))
        assertTrue(SyncApi.needsHttpConfirmation("HTTP://example.com"))
        assertTrue(SyncApi.needsHttpConfirmation("http://localhost.example.com"))
        assertTrue(SyncApi.needsHttpConfirmation("http://192.168.1.2"))
    }
}
