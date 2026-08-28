package com.xymonitor.app

import java.util.Locale
import kotlin.random.Random

object Interval {
    const val MIN_SECONDS = 30
    const val MAX_SECONDS = 3600
    const val DEFAULT_A = 180
    const val DEFAULT_B = 240

    fun clampSeconds(value: Int): Int = value.coerceIn(MIN_SECONDS, MAX_SECONDS)

    fun nextDelayMs(aSec: Int, bSec: Int, random: Random = Random.Default): Long {
        val minMs = clampSeconds(minOf(aSec, bSec)) * 1000L
        val maxMs = clampSeconds(maxOf(aSec, bSec)) * 1000L
        if (minMs >= maxMs) return minMs
        return random.nextLong(minMs, maxMs + 1)
    }

    fun formatSeconds(ms: Long): String {
        return String.format(Locale.US, "%.2f", ms / 1000.0)
    }
}
