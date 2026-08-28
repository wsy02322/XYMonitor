package com.xymonitor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class IntervalTest {
    @Test
    fun sameBoundsStayFixed() {
        assertEquals(180_000L, Interval.nextDelayMs(180, 180, Random(1)))
    }

    @Test
    fun swappedBoundsStillInRange() {
        val delay = Interval.nextDelayMs(240, 180, Random(2))
        assertTrue(delay in 180_000L..240_000L)
    }

    @Test
    fun clampsTooSmallAndTooLarge() {
        assertEquals(30_000L, Interval.nextDelayMs(1, 1, Random(3)))
        assertEquals(3_600_000L, Interval.nextDelayMs(9999, 9999, Random(4)))
    }

    @Test
    fun millisecondPrecisionInsideRange() {
        val seen = HashSet<Long>()
        val random = Random(42)
        repeat(400) {
            val delay = Interval.nextDelayMs(180, 240, random)
            assertTrue(delay in 180_000L..240_000L)
            seen.add(delay)
        }
        assertTrue(seen.size > 50)
        assertTrue(seen.any { it % 1000L != 0L })
        assertTrue(seen.any { it % 10L != 0L })
    }

    @Test
    fun formatShowsTwoDecimals() {
        assertEquals("203.47", Interval.formatSeconds(203_470L))
        assertEquals("180.00", Interval.formatSeconds(180_000L))
    }
}
