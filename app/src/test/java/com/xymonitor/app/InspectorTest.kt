package com.xymonitor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorTest {
    @Test
    fun firstSuccessIsBaselineAndDoesNotAlert() {
        val result = Inspector.compare(emptySet(), listOf("1", "2", "3"))
        assertTrue(result.ok)
        assertTrue(result.baseline)
        assertTrue(result.newIds.isEmpty())
        assertEquals(3, result.knownCount)
    }

    @Test
    fun laterNewIdIsDetected() {
        val result = Inspector.compare(setOf("1", "2"), listOf("3", "2", "1"))
        assertTrue(result.ok)
        assertFalse(result.baseline)
        assertEquals(listOf("3"), result.newIds)
        assertEquals(3, result.knownCount)
    }

    @Test
    fun missingOldIdIsNotTreatedAsNew() {
        val result = Inspector.compare(setOf("1", "2", "3"), listOf("2", "3"))
        assertTrue(result.newIds.isEmpty())
        assertEquals(3, result.knownCount)
    }

    @Test
    fun failKeepsKnownUntouched() {
        val fail = Inspector.fail("timeout")
        assertFalse(fail.ok)
        assertTrue(fail.newIds.isEmpty())
        assertEquals("timeout", fail.error)
    }
}
