package com.xymonitor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertChannelsTest {
    @Test
    fun blankUriUsesStableDefaultChannel() {
        assertEquals("monitor_alarm_default", AlertChannels.alertChannelId(""))
    }

    @Test
    fun differentUrisGetDifferentChannels() {
        val a = AlertChannels.alertChannelId("content://media/internal/audio/media/1")
        val b = AlertChannels.alertChannelId("content://media/internal/audio/media/2")
        assertTrue(a.startsWith("monitor_alarm_"))
        assertNotEquals(a, b)
        assertEquals(a, AlertChannels.alertChannelId("content://media/internal/audio/media/1"))
    }

    @Test
    fun errorChannelIsSilentId() {
        assertEquals("monitor_error_mute", AlertChannels.ERROR_MUTE)
    }
}
