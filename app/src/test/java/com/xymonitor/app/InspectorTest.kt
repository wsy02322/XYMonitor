package com.xymonitor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorTest {
    @Test
    fun firstSuccessIsBaselineAndDoesNotAlert() {
        val result = Inspector.compare("", "111")
        assertTrue(result.ok)
        assertTrue(result.baseline)
        assertFalse(result.changed)
        assertEquals("111", result.firstId)
    }

    @Test
    fun firstIdChangeAlerts() {
        val result = Inspector.compare("111", "222")
        assertTrue(result.ok)
        assertFalse(result.baseline)
        assertTrue(result.changed)
        assertEquals("222", result.firstId)
    }

    @Test
    fun sameFirstIdDoesNotAlert() {
        val result = Inspector.compare("111", "111")
        assertTrue(result.ok)
        assertFalse(result.changed)
    }

    @Test
    fun relistOldIdToFirstStillAlerts() {
        val result = Inspector.compare("222", "111")
        assertTrue(result.changed)
        assertEquals("111", result.firstId)
    }

    @Test
    fun emptyFirstPageIsFailure() {
        val result = Inspector.compare("111", "")
        assertFalse(result.ok)
        assertFalse(result.changed)
        assertEquals("第一页没有商品", result.error)
    }

    @Test
    fun failDoesNotLookLikeAChange() {
        val fail = Inspector.fail("timeout")
        assertFalse(fail.ok)
        assertFalse(fail.changed)
        assertEquals("timeout", fail.error)
    }
}
