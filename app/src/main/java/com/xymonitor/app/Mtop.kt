package com.xymonitor.app

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object Mtop {
    const val APP_KEY = "34839810"
    const val API = "mtop.idle.web.xyh.item.list"
    const val VERSION = "1.0"
    const val HOST = "https://h5api.m.goofish.com"

    fun sign(t: String, token: String, data: String): String {
        return md5("$token&$t&$APP_KEY&$data")
    }

    fun requestData(userId: String): String {
        return """{"needGroupInfo":true,"pageNumber":1,"userId":"$userId","pageSize":20}"""
    }

    fun md5(text: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun retText(root: JSONObject): String {
        val ret = root.opt("ret") ?: return ""
        return if (ret is JSONArray) {
            (0 until ret.length()).joinToString(" | ") { ret.optString(it) }
        } else {
            ret.toString()
        }
    }

    fun isSuccess(root: JSONObject): Boolean {
        val text = retText(root)
        return text.contains("SUCCESS")
    }

    fun isTokenError(root: JSONObject): Boolean {
        val text = retText(root)
        return text.contains("FAIL_SYS_TOKEN_EMPTY") ||
            text.contains("FAIL_SYS_TOKEN_EXOIRED") ||
            text.contains("FAIL_SYS_TOKEN_EXPIRED") ||
            text.contains("令牌过期") ||
            text.contains("令牌为空")
    }

    fun parseItemIds(root: JSONObject): List<String> {
        val data = root.optJSONObject("data") ?: return emptyList()
        val seen = LinkedHashSet<String>()
        val cards = data.optJSONArray("cardList")
        if (cards != null) {
            for (i in 0 until cards.length()) {
                addItem(seen, cards.optJSONObject(i))
            }
        }
        return seen.toList()
    }

    fun parseFirstCardId(root: JSONObject): String? {
        return parseItemIds(root).firstOrNull()
    }

    fun parseFirstCardId(json: String): String? = parseFirstCardId(JSONObject(json))

    fun parseItemIds(json: String): List<String> = parseItemIds(JSONObject(json))

    private fun addItem(seen: MutableSet<String>, node: JSONObject?) {
        if (node == null || node.length() == 0) return
        val payload = node.optJSONObject("cardData") ?: node
        val id = itemId(payload)
        if (!id.isNullOrBlank()) seen.add(id)
    }

    private fun itemId(obj: JSONObject): String? {
        val id = obj.opt("id")
        if (id != null && id != JSONObject.NULL) {
            val text = id.toString().trim()
            if (text.isNotEmpty() && text != "null") return text
        }
        val fromDetail = obj.optJSONObject("detailParams")?.opt("itemId")
        if (fromDetail != null && fromDetail != JSONObject.NULL) {
            val text = fromDetail.toString().trim()
            if (text.isNotEmpty()) return text
        }
        val itemId = obj.opt("itemId")
        if (itemId != null && itemId != JSONObject.NULL) {
            val text = itemId.toString().trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }
}
