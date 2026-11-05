package io.github.muhammedcakirgoz.engram.fsrs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class IntervalFormatTest {
    @Test
    fun formatsEachUnit() {
        assertEquals("45s", 45.seconds.toShortLabel())
        assertEquals("10m", 10.minutes.toShortLabel())
        assertEquals("6m", 5.5.minutes.toShortLabel())
        assertEquals("3h", 3.hours.toShortLabel())
        assertEquals("8d", 8.days.toShortLabel())
        assertEquals("2mo", 60.days.toShortLabel())
        assertEquals("1y", 365.days.toShortLabel())
        assertEquals("1.5y", (365 * 1.5).days.toShortLabel())
    }
}
