package com.xymonitor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class XianyuClientTest {
    @Test
    fun timeoutsAreShort() {
        assertTrue(XianyuClient.CONNECT_TIMEOUT_MS == 5_000)
        assertTrue(XianyuClient.READ_TIMEOUT_MS == 5_000)
        assertTrue(XianyuClient.INSPECT_LOCK_MS >= 50_000L)
        assertTrue(XianyuClient.MAX_ATTEMPTS == 3)
    }

    @Test
    fun connectFailuresAreRetryable() {
        assertTrue(XianyuClient.isRetryable(ConnectException("Failed to connect to h5api.m.goofish.com")))
        assertTrue(XianyuClient.isRetryable(SocketTimeoutException("timeout")))
        assertTrue(XianyuClient.isRetryable(java.net.SocketException("Socket closed")))
        assertTrue(XianyuClient.isRetryable(RuntimeException("Failed to connect to host/1.2.3.4:443")))
        assertFalse(XianyuClient.isRetryable(IllegalStateException("令牌无效")))
        assertFalse(XianyuClient.isRetryable(InterruptedException()))
    }

    @Test
    fun dnsFailuresAreDetected() {
        assertTrue(XianyuClient.isDnsFailure(UnknownHostException("Unable to resolve host")))
        assertTrue(XianyuClient.isDnsFailure(RuntimeException("No address associated with hostname")))
        assertFalse(XianyuClient.isDnsFailure(ConnectException("failed to connect after 5000ms")))
    }
}

class Ipv4Test {
    @Test
    fun pickPrefersIpv4() {
        val loopback = InetAddress.getByName("127.0.0.1")
        val picked = Ipv4.pick(arrayOf(loopback))
        assertEquals("127.0.0.1", picked.hostAddress)
        assertTrue(Ipv4.describe(arrayOf(loopback)).contains("v4="))
    }
}
