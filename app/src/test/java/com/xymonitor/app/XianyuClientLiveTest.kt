package com.xymonitor.app

import org.junit.Assert.assertTrue
import org.junit.Test

class XianyuClientLiveTest {
    @Test
    fun fetchFirstPageFromPublicSeller() {
        val ids = XianyuClient().fetchFirstPageIds("1666703902")
        assertTrue("should return first-page item ids, got $ids", ids.size >= 1)
        assertTrue(ids.all { it.matches(Regex("\\d+")) })
    }
}
