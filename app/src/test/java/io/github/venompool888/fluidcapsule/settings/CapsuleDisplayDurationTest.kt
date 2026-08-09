package io.github.venompool888.fluidcapsule.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CapsuleDisplayDurationTest {
    @Test
    fun defaultsToFiveMinutes() {
        assertEquals(5, CapsuleDisplayDuration.defaultMinutes)
        assertEquals(300_000L, CapsuleDisplayDuration.toMillis(5))
    }

    @Test
    fun acceptsEverySliderMinute() {
        (CapsuleDisplayDuration.minMinutes..CapsuleDisplayDuration.maxMinutes).forEach { minutes ->
            assertEquals(minutes, CapsuleDisplayDuration.normalizeMinutes(minutes))
        }
    }

    @Test
    fun rejectsUnsupportedStoredValues() {
        assertEquals(5, CapsuleDisplayDuration.normalizeMinutes(0))
        assertEquals(5, CapsuleDisplayDuration.normalizeMinutes(31))
    }
}
