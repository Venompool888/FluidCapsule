package io.github.venompool888.fluidcapsule.publisher

import io.github.venompool888.fluidcapsule.core.CapsuleEvent
import io.github.venompool888.fluidcapsule.core.CapsuleKind

internal class CapsuleQueueReducer(
    private val maxSize: Int = 8,
) {
    private data class Entry(
        val event: CapsuleEvent,
        val sequence: Long,
    )

    private val entries = linkedMapOf<String, Entry>()
    private var sequence = 0L

    fun submit(event: CapsuleEvent, now: Long): CapsuleEvent? {
        purgeExpired(now)
        entries.entries.removeAll {
            it.value.event.eventId == event.eventId || it.key == event.dedupeKey
        }
        entries[event.dedupeKey] = Entry(event, ++sequence)
        while (entries.size > maxSize) {
            val oldest = entries.minByOrNull { it.value.sequence } ?: break
            entries.remove(oldest.key)
        }
        return current(now)
    }

    fun removeEvent(eventId: String, now: Long): CapsuleEvent? {
        entries.entries.removeAll { it.value.event.eventId == eventId }
        purgeExpired(now)
        return current(now)
    }

    fun current(now: Long): CapsuleEvent? {
        purgeExpired(now)
        return entries.values.maxWithOrNull(
            compareBy<Entry> { priority(it.event.kind) }
                .thenBy { it.sequence },
        )?.event
    }

    fun nextExpiry(now: Long): Long? {
        purgeExpired(now)
        return entries.values.minOfOrNull { it.event.expiresAtMillis }
    }

    fun activeEventIds(now: Long): List<String> {
        purgeExpired(now)
        return entries.values
            .sortedByDescending { it.sequence }
            .map { it.event.eventId }
    }

    private fun purgeExpired(now: Long) {
        entries.entries.removeAll { it.value.event.expiresAtMillis <= now }
    }

    private fun priority(kind: CapsuleKind): Int = when (kind) {
        CapsuleKind.OTP -> 100
        CapsuleKind.CUSTOM -> 70
        CapsuleKind.NOTIFICATION -> 50
    }
}
