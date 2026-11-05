package io.github.muhammedcakirgoz.engram.fsrs

import kotlin.math.roundToLong
import kotlin.time.Duration

/**
 * Short, Anki-style label for an interval: `45s`, `10m`, `3h`, `4d`, `2mo`, `1.5y`.
 * Used for the "next interval" hints on rating buttons.
 */
public fun Duration.toShortLabel(): String {
    val seconds = inWholeSeconds
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3_600 -> "${(seconds / 60.0).roundToLong()}m"
        seconds < 86_400 -> "${(seconds / 3_600.0).roundToLong()}h"
        seconds < 30 * 86_400L -> "${(seconds / 86_400.0).roundToLong()}d"
        seconds < 365 * 86_400L -> "${(seconds / (30 * 86_400.0)).roundToLong()}mo"
        else -> {
            val tenthsOfYear = (seconds / (365 * 86_400.0) * 10).roundToLong()
            if (tenthsOfYear % 10 == 0L) "${tenthsOfYear / 10}y" else "${tenthsOfYear / 10}.${tenthsOfYear % 10}y"
        }
    }
}
