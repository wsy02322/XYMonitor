package com.xymonitor.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

class XianyuClient(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val cookies = ConcurrentHashMap<String, String>()

    fun fetchFirstCardId(userId: String): String {
        return try {
            fetchOnce(userId)
        } catch (e: Exception) {
            if (e is InterruptedException || !isRetryable(e)) throw e
            DebugLog.i("巡检重试 原因=${e.message}")
            fetchOnce(userId)
        }
    }

    private fun fetchOnce(userId: String): String {
        val first = request(userId)
        val firstJson = JSONObject(first)
        val json = if (Mtop.isTokenError(firstJson)) {
            JSONObject(request(userId))
        } else {
            firstJson
        }
        if (Mtop.isTokenError(json)) {
            throw IllegalStateException("令牌无效")
        }
        if (!Mtop.isSuccess(json)) {
            throw IllegalStateException(Mtop.retText(json).ifBlank { "接口调用失败" })
        }
        return Mtop.parseFirstCardId(json) ?: throw IllegalStateException("第一页没有商品")
    }

    internal fun request(userId: String): String {
        val data = Mtop.requestData(userId)
        val t = nowMs().toString()
        val token = token()
        val sign = Mtop.sign(t, token, data)
        val query = linkedMapOf(
            "jsv" to "2.7.2",
            "appKey" to Mtop.APP_KEY,
            "t" to t,
            "sign" to sign,
            "v" to Mtop.VERSION,
            "type" to "originaljson",
            "accountSite" to "xianyu",
            "dataType" to "json",
            "timeout" to CONNECT_TIMEOUT_MS.toString(),
            "api" to Mtop.API,
            "sessionOption" to "AutoLoginOnly",
            "spm_cnt" to "a21ybx.item.0.0",
        ).entries.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v)}"
        }
        val url = URL("${Mtop.HOST}/h5/${Mtop.API}/${Mtop.VERSION}/?$query")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doInput = true
            doOutput = true
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Origin", "https://www.goofish.com")
            setRequestProperty("Referer", "https://www.goofish.com/")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; XYMonitor) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36",
            )
            val cookieHeader = cookieHeader()
            if (cookieHeader.isNotBlank()) {
                setRequestProperty("Cookie", cookieHeader)
            }
        }
        try {
            val body = "data=${enc(data)}"
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            storeCookies(conn)
            val stream: InputStream = if (conn.responseCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream ?: conn.inputStream
            }
            val text = stream.bufferedReader().use(BufferedReader::readText)
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            if (text.isBlank()) {
                throw IllegalStateException("空响应")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun token(): String {
        val raw = cookies["_m_h5_tk"].orEmpty()
        return raw.substringBefore("_")
    }

    private fun cookieHeader(): String {
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun storeCookies(conn: HttpURLConnection) {
        val fields = conn.headerFields ?: return
        val setCookies = fields.filterKeys { it.equals("Set-Cookie", ignoreCase = true) }
            .values
            .flatten()
        for (header in setCookies) {
            val pair = header.substringBefore(";").trim()
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            val name = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (name.isNotEmpty()) cookies[name] = value
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 8_000
        const val INSPECT_LOCK_MS = 45_000L

        fun isRetryable(error: Throwable): Boolean {
            if (error is InterruptedException) return false
            if (error is ConnectException || error is SocketTimeoutException || error is UnknownHostException) {
                return true
            }
            val message = error.message.orEmpty()
            return message.contains("Failed to connect", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("Unable to resolve", ignoreCase = true)
        }
    }
}
