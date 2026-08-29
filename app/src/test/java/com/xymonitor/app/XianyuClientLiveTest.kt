package com.xymonitor.app

import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class XianyuClientLiveTest {
    @Ignore("hits real network")
    @Test
    fun fetchFirstCardFromPublicSeller() {
        val id = XianyuClient().fetchFirstCardId("1666703902")
        assertTrue("should return first card item id, got $id", id.matches(Regex("\\d+")))
    }
}
