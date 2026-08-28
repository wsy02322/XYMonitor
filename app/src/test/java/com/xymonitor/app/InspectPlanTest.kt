package com.xymonitor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectPlanTest {
    @Test
    fun nextAtAddsDelay() {
        assertEquals(1_000_045L, InspectPlan.nextAt(1_000_000L, 45L))
        assertEquals(1_000_000L, InspectPlan.nextAt(1_000_000L, -10L))
    }

    @Test
    fun remainingClampsToZero() {
        assertEquals(0L, InspectPlan.remainingMs(100L, 50L))
        assertEquals(40L, InspectPlan.remainingMs(10L, 50L))
        assertEquals(0L, InspectPlan.remainingMs(10L, 0L))
    }

    @Test
    fun overdueAfterGrace() {
        assertFalse(InspectPlan.overdue(1_000_000L, 1_000_000L))
        assertFalse(InspectPlan.overdue(1_010_000L, 1_000_000L))
        assertTrue(InspectPlan.overdue(1_020_000L, 1_000_000L))
        assertFalse(InspectPlan.overdue(1_000_000L, 0L))
    }

    @Test
    fun watchdogIsShortAndAfterConnectTimeout() {
        assertEquals(12_000L, InspectPlan.WATCHDOG_MS)
        assertTrue(InspectPlan.WATCHDOG_MS > XianyuClient.CONNECT_TIMEOUT_MS)
        assertTrue(InspectPlan.WATCHDOG_MS < 30_000L)
    }
}
