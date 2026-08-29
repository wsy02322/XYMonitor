package com.xymonitor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpsClientTest {
    @Test
    fun parseIpv4AndPortDoesNotTreatAddressAsIpv6() {
        val endpoint = VpsClient.parseEndpoint("http://78.47.152.85:18787")
        assertEquals("http", endpoint.protocol)
        assertEquals("78.47.152.85", endpoint.host)
        assertEquals(18787, endpoint.port)
        assertEquals("http://78.47.152.85:18787", endpoint.display())
        val url = endpoint.url("/start")
        assertEquals("http", url.protocol)
        assertEquals("78.47.152.85", url.host)
        assertEquals(18787, url.port)
        assertEquals("/start", url.path)
    }

    @Test
    fun parseBareHostPortAddsHttp() {
        val endpoint = VpsClient.parseEndpoint("78.47.152.85:18787")
        assertEquals("http://78.47.152.85:18787", endpoint.display())
    }

    @Test
    fun joinUsesParsedEndpoint() {
        assertEquals("http://78.47.152.85:18787/pending", VpsClient.join("http://78.47.152.85:18787/", "/pending"))
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
