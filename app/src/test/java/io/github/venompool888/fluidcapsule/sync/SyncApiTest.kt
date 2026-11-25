package io.github.venompool888.fluidcapsule.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncApiTest {
    @Test fun normalizesWhitespaceAndTrailingSlashesAndPreservesBasePath() {
        assertEquals("https://example.com", SyncApi.normalizeServer("  https://example.com///  "))
        assertEquals("http://localhost:8080/sync", SyncApi.normalizeServer("http://localhost:8080/sync/", loopbackOnly))
        assertEquals("HTTPS://example.com", SyncApi.normalizeServer("HTTPS://example.com/"))
    }

    @Test fun rejectsMissingHostSchemeCredentialsQueryAndFragment() {
        listOf("", "example.com", "ftp://example.com", "https:///", "https://user:secret@example.com",
            "https://example.com?token=x", "https://example.com/#fragment", "https://bad host").forEach { server ->
            assertThrows(server, IllegalArgumentException::class.java) { SyncApi.normalizeServer(server) }
        }
    }

    @Test fun httpIsRejectedWhereCleartextIsNotPermitted() {
        assertEquals("http://127.0.0.1:8787", SyncApi.normalizeServer("http://127.0.0.1:8787", loopbackOnly))
        listOf("HTTP://example.com", "http://localhost.example.com", "http://192.168.1.2").forEach { server ->
            assertThrows(server, IllegalArgumentException::class.java) { SyncApi.normalizeServer(server, loopbackOnly) }
        }
        // HTTPS never consults the cleartext policy.
        assertEquals("https://example.com", SyncApi.normalizeServer("https://example.com") { error("not consulted") })
    }

    private val loopbackOnly: (String) -> Boolean = { it == "localhost" || it == "127.0.0.1" }
}
