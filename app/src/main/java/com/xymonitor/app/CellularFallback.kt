package com.xymonitor.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object CellularFallback {
    const val REQUEST_MS = 8_000

    fun acquire(context: Context): Session? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val held = AtomicReference<Network?>(null)
        val latch = CountDownLatch(1)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                held.set(network)
                latch.countDown()
            }

            override fun onUnavailable() {
                latch.countDown()
            }
        }
        return try {
            if (Build.VERSION.SDK_INT >= 26) {
                cm.requestNetwork(request, callback, REQUEST_MS)
            } else {
                cm.requestNetwork(request, callback)
            }
            latch.await(REQUEST_MS + 500L, TimeUnit.MILLISECONDS)
            val network = held.get()
            if (network == null) {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                }
                DebugLog.i("流量网络=未获取")
                null
            } else {
                DebugLog.i("流量网络=已获取")
                Session(network) {
                    try {
                        cm.unregisterNetworkCallback(callback)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
            DebugLog.i("申请流量失败 ${e.message}")
            null
        }
    }

    class Session(
        val network: Network,
        private val release: () -> Unit,
    ) : Closeable {
        override fun close() = release()
    }
}
