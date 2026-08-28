package com.xymonitor.app

import android.net.Network
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicReference

data class VpsSnapshot(
    val ok: Boolean,
    val running: Boolean,
    val userId: String,
    val firstId: String,
    val kind: String,
    val changed: Boolean,
    val baseline: Boolean,
    val pendingAlert: Boolean,
    val pendingError: Boolean,
    val error: String,
    val status: String,
    val checkedAt: Long,
    val itemId: String,
) {
    companion object {
        fun parse(json: String): VpsSnapshot {
            val root = JSONObject(json)
            return VpsSnapshot(
                ok = root.optBoolean("ok", true),
                running = root.optBoolean("running", false),
                userId = root.optString("userId"),
                firstId = root.optString("firstId"),
                kind = root.optString("kind"),
                changed = root.optBoolean("changed", false),
                baseline = root.optBoolean("baseline", false),
                pendingAlert = root.optBoolean("pendingAlert", false),
                pendingError = root.optBoolean("pendingError", false),
                error = root.optString("error"),
                status = root.optString("status"),
                checkedAt = root.optLong("checkedAt", 0L),
                itemId = root.optString("itemId"),
            )
        }
    }
}

class VpsClient(
    private val baseUrl: String,
    private val token: String,
) {
    private val current = AtomicReference<HttpURLConnection?>()
    @Volatile private var via: Network? = null

    fun start(userId: String, intervalA: Int, intervalB: Int, network: Network? = null): VpsSnapshot {
        val body = JSONObject()
            .put("userId", userId)
            .put("intervalA", intervalA)
            .put("intervalB", intervalB)
            .toString()
        return request("POST", "/start", body, network)
    }

    fun pending(network: Network? = null): VpsSnapshot {
        return request("GET", "/pending", null, network)
    }

    fun ack(itemId: String = "", error: Boolean = false, network: Network? = null): VpsSnapshot {
        val body = JSONObject()
            .put("itemId", itemId)
            .put("error", error)
            .toString()
        return request("POST", "/ack", body, network)
    }

    fun stop(network: Network? = null): VpsSnapshot {
        return request("POST", "/stop", "{}", network)
    }

    fun abort() {
        try {
            current.getAndSet(null)?.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun request(method: String, path: String, body: String?, network: Network?): VpsSnapshot {
        via = network
        try {
            var last: Exception? = null
            repeat(MAX_ATTEMPTS) { index ->
                try {
                    return parseOrThrow(call(method, path, body))
                } catch (e: Exception) {
                    if (e is InterruptedException || !isRetryable(e)) throw e
                    last = e
                    val attempt = index + 1
                    if (attempt >= MAX_ATTEMPTS) return@repeat
                    DebugLog.i("问服务器重试 第${attempt}次 原因=${e.message}")
                    try {
                        Thread.sleep(CONNECT_RETRY_MS)
                    } catch (_: InterruptedException) {
                        throw e
                    }
                }
            }
            throw last ?: IllegalStateException("服务器无响应")
        } finally {
            via = null
        }
    }

    private fun parseOrThrow(text: String): VpsSnapshot {
        val snap = VpsSnapshot.parse(text)
        if (!snap.ok && snap.error.isNotBlank()) {
            throw IllegalStateException(snap.error)
        }
        return snap
    }

    private fun call(method: String, path: String, body: String?): String {
        val url = URL(join(baseUrl, path))
        val conn = open(url)
        current.set(conn)
        try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.doInput = true
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val stream: InputStream = if (conn.responseCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream ?: conn.inputStream
            }
            val text = stream.bufferedReader().use(BufferedReader::readText)
            if (conn.responseCode == 401) {
                throw IllegalStateException("密钥不对")
            }
            if (conn.responseCode !in 200..299) {
                val message = runCatching { VpsSnapshot.parse(text).error }.getOrNull()
                    ?.ifBlank { null }
                    ?: "HTTP ${conn.responseCode}"
                throw IllegalStateException(message)
            }
            if (text.isBlank()) {
                throw IllegalStateException("服务器空响应")
            }
            return text
        } finally {
            current.compareAndSet(conn, null)
            conn.disconnect()
        }
    }

    private fun open(url: URL): HttpURLConnection {
        val net = via
        return (net?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
        const val INSPECT_LOCK_MS = 50_000L
        const val MAX_ATTEMPTS = 3
        const val CONNECT_RETRY_MS = 400L

        fun join(base: String, path: String): String {
            val trimmed = base.trim().trimEnd('/')
            val suffix = path.trim().let { if (it.startsWith("/")) it else "/$it" }
            return trimmed + suffix
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

object Inbox {
    const val A = 30
    const val B = 50

    fun nextDelayMs(): Long = Interval.nextDelayMs(A, B)
}
