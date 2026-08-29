package com.xymonitor.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTest {
    @Test
    fun lateInspectIsFrozen() {
        assertTrue(Health.frozenHint(200_000, 900_000))
        assertFalse(Health.frozenHint(200_000, 210_000))
        assertFalse(Health.frozenHint(0, 900_000))
    }
}
