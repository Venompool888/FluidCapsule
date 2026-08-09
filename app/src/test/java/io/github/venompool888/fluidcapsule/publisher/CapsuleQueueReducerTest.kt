package io.github.venompool888.fluidcapsule.publisher

import io.github.venompool888.fluidcapsule.core.CapsuleAction
import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.core.CapsuleKind
import io.github.venompool888.fluidcapsule.core.CapsulePrivacy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleQueueReducerTest {
    @Test
    fun latestMessagePreemptsAndPreviousMessageReturnsAfterConsume() {
        val queue = CapsuleQueueReducer()
        queue.submit(event("telegram", CapsuleKind.NOTIFICATION, 0), 0)

        assertEquals("wechat", queue.submit(event("wechat", CapsuleKind.NOTIFICATION, 1), 1)?.eventId)
        assertEquals("telegram", queue.removeEvent("wechat", 2)?.eventId)
    }

    @Test
    fun otpPreemptsMessageAndMessageReturnsAfterCopy() {
        val queue = CapsuleQueueReducer()
        queue.submit(event("wechat", CapsuleKind.NOTIFICATION, 0), 0)

        assertEquals("otp", queue.submit(event("otp", CapsuleKind.OTP, 1), 1)?.eventId)
        assertEquals("wechat", queue.removeEvent("otp", 2)?.eventId)
    }

    @Test
    fun removedPendingSourceIsNotRestored() {
        val queue = CapsuleQueueReducer()
        queue.submit(event("telegram", CapsuleKind.NOTIFICATION, 0), 0)
        queue.submit(event("wechat", CapsuleKind.NOTIFICATION, 1), 1)

        queue.removeEvent("telegram", 2)
        assertEquals(null, queue.removeEvent("wechat", 3))
    }

    @Test
    fun updatingSameSourceDoesNotCreateDuplicate() {
        val queue = CapsuleQueueReducer()
        queue.submit(event("telegram", CapsuleKind.NOTIFICATION, 0, dedupe = "source-key"), 0)
        queue.submit(event("telegram", CapsuleKind.NOTIFICATION, 1, dedupe = "source-key"), 1)

        assertEquals(listOf("telegram"), queue.activeEventIds(2))
    }

    @Test
    fun expiredCurrentRestoresStillValidPreviousEvent() {
        val queue = CapsuleQueueReducer()
        queue.submit(event("telegram", CapsuleKind.NOTIFICATION, 0, expiresAt = 300_000), 0)
        queue.submit(event("wechat", CapsuleKind.NOTIFICATION, 1, expiresAt = 10), 1)

        assertEquals("telegram", queue.current(10)?.eventId)
    }

    @Test
    fun queueKeepsOnlyEightNewestEvents() {
        val queue = CapsuleQueueReducer()
        repeat(9) { index ->
            queue.submit(event("event-$index", CapsuleKind.NOTIFICATION, index.toLong()), index.toLong())
        }

        val ids = queue.activeEventIds(10)
        assertEquals(8, ids.size)
        assertFalse(ids.contains("event-0"))
        assertTrue(ids.contains("event-8"))
    }

    private fun event(
        id: String,
        kind: CapsuleKind,
        createdAt: Long,
        expiresAt: Long = 300_000,
        dedupe: String = id,
    ) = CapsuleEvent(
        sourcePackage = "test.package",
        eventId = id,
        kind = kind,
        title = id,
        shortText = id,
        body = id,
        action = CapsuleAction.None,
        privacy = CapsulePrivacy.SHOW_FULL,
        createdAtMillis = createdAt,
        expiresAtMillis = expiresAt,
        dedupeKey = dedupe,
    )
}
