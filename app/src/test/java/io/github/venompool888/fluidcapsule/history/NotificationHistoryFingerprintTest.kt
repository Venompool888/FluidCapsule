package io.github.venompool888.fluidcapsule.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationHistoryFingerprintTest {
    @Test
    fun identicalNotificationProducesStableFingerprint() {
        val first = NotificationHistoryFingerprint.create("key", 123L, "title", "body")
        val second = NotificationHistoryFingerprint.create("key", 123L, "title", "body")

        assertEquals(first, second)
    }

    @Test
    fun changedNotificationBodyProducesNewContentFingerprint() {
        val first = NotificationHistoryFingerprint.create("key", 123L, "title", "body")
        val updated = NotificationHistoryFingerprint.create("key", 123L, "title", "updated body")

        assertNotEquals(first, updated)
    }

}
