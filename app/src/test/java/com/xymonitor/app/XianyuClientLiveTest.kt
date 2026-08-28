package com.xymonitor.app

import org.junit.Assert.assertTrue
import org.junit.Test

class XianyuClientLiveTest {
    @Test
    fun fetchFirstCardFromPublicSeller() {
        val id = XianyuClient().fetchFirstCardId("1666703902")
        assertTrue("should return first card item id, got $id", id.matches(Regex("\\d+")))
    }
}
