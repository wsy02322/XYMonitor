package com.xymonitor.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.SNIHostName

class XianyuClient(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val cookies = ConcurrentHashMap<String, String>()
    private val current = AtomicReference<HttpURLConnection?>()

    fun fetchFirstCardId(userId: String): String {
        var last: Exception? = null
        repeat(MAX_ATTEMPTS) { index ->
            try {
                return fetchOnce(userId)
            } catch (e: Exception) {
                if (e is InterruptedException || !isRetryable(e)) throw e
                last = e
                val attempt = index + 1
                if (attempt >= MAX_ATTEMPTS) return@repeat
                val delay = if (isDnsFailure(e)) DNS_BACKOFF_MS else CONNECT_RETRY_MS
                DebugLog.i("巡检重试 第${attempt}次 原因=${e.message} 等待=${Interval.formatSeconds(delay)}s")
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    throw e
                }
            }
        }
        throw last ?: IllegalStateException("接口调用失败")
    }

    fun abort() {
        try {
            current.getAndSet(null)?.disconnect()
        } catch (_: Exception) {
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
        val path = "/h5/${Mtop.API}/${Mtop.VERSION}/?$query"
        val conn = openHttps(path)
        current.set(conn)
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
            current.compareAndSet(conn, null)
            conn.disconnect()
        }
    }

    private fun openHttps(pathAndQuery: String): HttpsURLConnection {
        val host = HOST
        val resolved = InetAddress.getAllByName(host)
        DebugLog.i("解析 $host ${Ipv4.describe(resolved)}")
        val ipv4 = Ipv4.pick(resolved)
        val conn = URL("https://${ipv4.hostAddress}$pathAndQuery").openConnection() as HttpsURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.doInput = true
        conn.doOutput = true
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Host", host)
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Origin", "https://www.goofish.com")
        conn.setRequestProperty("Referer", "https://www.goofish.com/")
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13; XYMonitor) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36",
        )
        val cookieHeader = cookieHeader()
        if (cookieHeader.isNotBlank()) {
            conn.setRequestProperty("Cookie", cookieHeader)
        }
        conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session ->
            HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)
        }
        conn.sslSocketFactory = SniSslSocketFactory(host)
        return conn
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

    private class SniSslSocketFactory(private val hostname: String) : SSLSocketFactory() {
        private val delegate = HttpsURLConnection.getDefaultSSLSocketFactory()

        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean) =
            sni(delegate.createSocket(s, hostname, port, autoClose))

        override fun createSocket(host: String, port: Int) =
            sni(delegate.createSocket(InetAddress.getByName(host), port))

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int) =
            sni(delegate.createSocket(InetAddress.getByName(host), port, localHost, localPort))

        override fun createSocket(host: InetAddress, port: Int) =
            sni(delegate.createSocket(host, port))

        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int) =
            sni(delegate.createSocket(address, port, localAddress, localPort))

        private fun sni(socket: Socket): Socket {
            val ssl = socket as? SSLSocket ?: return socket
            try {
                val params = ssl.sslParameters
                params.serverNames = listOf(SNIHostName(hostname))
                ssl.sslParameters = params
            } catch (_: Exception) {
            }
            return ssl
        }
    }

    companion object {
        const val HOST = "h5api.m.goofish.com"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
        const val INSPECT_LOCK_MS = 35_000L
        const val MAX_ATTEMPTS = 3
        const val DNS_BACKOFF_MS = 1_500L
        const val CONNECT_RETRY_MS = 400L

        fun isDnsFailure(error: Throwable): Boolean {
            if (error is UnknownHostException) return true
            val message = error.message.orEmpty()
            return message.contains("Unable to resolve", ignoreCase = true) ||
                message.contains("No address associated", ignoreCase = true)
        }

        fun isRetryable(error: Throwable): Boolean {
            if (error is InterruptedException) return false
            if (error is ConnectException ||
                error is SocketTimeoutException ||
                error is UnknownHostException ||
                error is SocketException
            ) {
                return true
            }
            val message = error.message.orEmpty()
            return message.contains("Failed to connect", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("Unable to resolve", ignoreCase = true)
        }
    }
}
