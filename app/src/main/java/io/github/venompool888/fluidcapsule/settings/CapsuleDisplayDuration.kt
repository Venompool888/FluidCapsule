package io.github.venompool888.fluidcapsule.settings

object CapsuleDisplayDuration {
    const val minMinutes = 1
    const val maxMinutes = 30
    const val defaultMinutes = 5

    fun normalizeMinutes(value: Int): Int =
        value.takeIf { it in minMinutes..maxMinutes } ?: defaultMinutes

    fun toMillis(minutes: Int): Long = normalizeMinutes(minutes) * 60_000L
}
