package io.github.venompool888.fluidcapsule.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HistoryRetentionPolicyTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun legacyDaysMigrateWithoutOldThirtyDayClamp() {
        assertEquals(
            HistoryRetentionPolicy(45, HistoryRetentionUnit.DAYS),
            HistoryRetentionPolicy.fromStored(7, null, legacyDays = 45),
        )
    }

    @Test
    fun monthRetentionUsesCalendarMonths() {
        val now = Instant.parse("2026-03-31T12:00:00Z").toEpochMilli()
        val cutoff = HistoryRetentionPolicy(1, HistoryRetentionUnit.MONTHS)
            .cutoffMillis(now, utc)

        assertEquals(Instant.parse("2026-02-28T12:00:00Z").toEpochMilli(), cutoff)
    }

    @Test
    fun yearRetentionHandlesLeapDay() {
        val now = Instant.parse("2028-02-29T12:00:00Z").toEpochMilli()
        val cutoff = HistoryRetentionPolicy(1, HistoryRetentionUnit.YEARS)
            .cutoffMillis(now, utc)

        assertEquals(Instant.parse("2027-02-28T12:00:00Z").toEpochMilli(), cutoff)
    }

    @Test
    fun foreverRetentionHasNoCutoff() {
        assertNull(HistoryRetentionPolicy(0, HistoryRetentionUnit.FOREVER).cutoffMillis())
    }

    @Test
    fun invalidStoredValuesAreNormalized() {
        assertEquals(
            HistoryRetentionPolicy(999, HistoryRetentionUnit.YEARS),
            HistoryRetentionPolicy.fromStored(5_000, "years"),
        )
        assertEquals(
            HistoryRetentionPolicy(0, HistoryRetentionUnit.FOREVER),
            HistoryRetentionPolicy.fromStored(12, "forever"),
        )
    }
}
