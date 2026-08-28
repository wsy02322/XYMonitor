package com.xymonitor.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MtopTest {
    @Test
    fun signMatchesClassicH5Md5() {
        val token = "0566e35c2205a2d9bf06eb19a0db7e95"
        val t = "1787912345678"
        val data = """{"needGroupInfo":true,"pageNumber":1,"userId":"1666703902","pageSize":20}"""
        val expected = Mtop.md5("$token&$t&${Mtop.APP_KEY}&$data")
        assertEquals(expected, Mtop.sign(t, token, data))
        assertEquals(32, expected.length)
    }

    @Test
    fun parseFirstPageIdsFromCardListAndTopItem() {
        val json = """
            {
              "api":"mtop.idle.web.xyh.item.list",
              "ret":["SUCCESS::调用成功"],
              "data":{
                "topItem":{"cardData":{"id":"999","title":"top"}},
                "cardList":[
                  {"cardData":{"id":1031066924442,"title":"a","detailParams":{"itemId":"1031066924442"}}},
                  {"cardData":{"id":"1029988109330","detailParams":{"itemId":"1029988109330"}}},
                  {"cardData":{"detailParams":{"itemId":"888"}}}
                ]
              }
            }
        """.trimIndent()
        val ids = Mtop.parseItemIds(json)
        assertEquals(listOf("1031066924442", "1029988109330", "888"), ids)
        assertEquals("1031066924442", Mtop.parseFirstCardId(json))
    }

    @Test
    fun emptyTopItemIsIgnored() {
        val json = """{"ret":["SUCCESS::调用成功"],"data":{"topItem":{},"cardList":[{"cardData":{"id":"1"}}]}}"""
        assertEquals(listOf("1"), Mtop.parseItemIds(json))
    }

    @Test
    fun tokenAndSuccessFlags() {
        val empty = JSONObject("""{"ret":["FAIL_SYS_TOKEN_EMPTY::令牌为空"],"data":{}}""")
        val ok = JSONObject("""{"ret":["SUCCESS::调用成功"],"data":{}}""")
        val fail = JSONObject("""{"ret":["FAIL_SYS_ILLEGAL_ACCESS::非法请求"],"data":{}}""")
        assertTrue(Mtop.isTokenError(empty))
        assertFalse(Mtop.isSuccess(empty))
        assertTrue(Mtop.isSuccess(ok))
        assertFalse(Mtop.isTokenError(ok))
        assertFalse(Mtop.isSuccess(fail))
        assertFalse(Mtop.isTokenError(fail))
    }

    @Test
    fun compactRequestData() {
        assertEquals(
            """{"needGroupInfo":true,"pageNumber":1,"userId":"1666703902","pageSize":20}""",
            Mtop.requestData("1666703902"),
        )
    }
}
