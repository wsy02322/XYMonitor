package com.xymonitor.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

object Ipv4 {
    fun pick(addresses: Array<InetAddress>): Inet4Address {
        return addresses.filterIsInstance<Inet4Address>().firstOrNull()
            ?: throw UnknownHostException("无IPv4地址")
    }

    fun describe(addresses: Array<InetAddress>): String {
        if (addresses.isEmpty()) return "空"
        return addresses.joinToString(",") {
            if (it is Inet4Address) "v4=${it.hostAddress}" else "v6=${it.hostAddress}"
        }
    }
}

object NetworkWait {
    const val MAX_WAIT_MS = 2_500L
    const val POLL_MS = 250L

    fun awaitValidated(context: Context): String {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "无Connectivity"
        val start = SystemClock.elapsedRealtime()
        var last = "未知"
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - start
            last = snapshot(cm)
            if (last == "已验证") {
                DebugLog.i("网络就绪 状态=$last 等待=${Interval.formatSeconds(elapsed)}s")
                return last
            }
            if (elapsed >= MAX_WAIT_MS) {
                DebugLog.i("网络未就绪 状态=$last 已等=${Interval.formatSeconds(elapsed)}s")
                return last
            }
            try {
                Thread.sleep(POLL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                DebugLog.i("网络等待中断 状态=$last")
                return last
            }
        }
    }

    private fun snapshot(cm: ConnectivityManager): String {
        val net = cm.activeNetwork ?: return "无网络"
        val caps = cm.getNetworkCapabilities(net) ?: return "无能力"
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return when {
            validated -> "已验证"
            internet -> "有网未验证"
            else -> "无INTERNET"
        }
    }
}
