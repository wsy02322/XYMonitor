package com.xymonitor.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException

class XianyuClientTest {
    @Test
    fun timeoutsAreShort() {
        assertTrue(XianyuClient.CONNECT_TIMEOUT_MS == 5_000)
        assertTrue(XianyuClient.READ_TIMEOUT_MS == 5_000)
        assertTrue(XianyuClient.INSPECT_LOCK_MS >= 20_000L)
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
}
