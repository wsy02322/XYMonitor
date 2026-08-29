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
    fun watchdogCoversWarmupAndRetries() {
        assertEquals(45_000L, InspectPlan.WATCHDOG_MS)
        assertTrue(InspectPlan.WATCHDOG_MS > CellularFallback.REQUEST_MS)
        assertTrue(InspectPlan.WATCHDOG_MS > XianyuClient.CONNECT_TIMEOUT_MS * 3)
        assertTrue(InspectPlan.WATCHDOG_MS >= NetworkWait.MAX_WAIT_MS + VpsClient.CONNECT_TIMEOUT_MS * 3)
        assertTrue(NetworkWait.canUseDefault("已验证/流量"))
        assertTrue(NetworkWait.canUseDefault("有网未验证/WiFi"))
        assertFalse(NetworkWait.canUseDefault("无网络"))
    }
}
