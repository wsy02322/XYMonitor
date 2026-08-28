package com.xymonitor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpsClientTest {
    @Test
    fun joinTrimsSlash() {
        assertEquals("http://1.2.3.4:8787/pending", VpsClient.join("http://1.2.3.4:8787/", "/pending"))
        assertEquals("https://example.com/start", VpsClient.join("https://example.com", "start"))
    }

    @Test
    fun parsePendingAlert() {
        val snap = VpsSnapshot.parse(
            """{"ok":true,"running":true,"userId":"1","firstId":"222","kind":"changed",
                "changed":true,"baseline":false,"pendingAlert":true,"pendingError":false,
                "error":"","status":"第一件变为 222","checkedAt":1,"itemId":"222"}""",
        )
        assertTrue(snap.pendingAlert)
        assertFalse(snap.pendingError)
        assertEquals("222", snap.itemId)
        assertEquals("第一件变为 222", snap.status)
    }

    @Test
    fun inboxIsShorterThanDefaultServerInterval() {
        assertEquals(30, Inbox.A)
        assertEquals(50, Inbox.B)
        repeat(20) {
            val delay = Inbox.nextDelayMs()
            assertTrue(delay in 30_000..50_000)
        }
    }
}
