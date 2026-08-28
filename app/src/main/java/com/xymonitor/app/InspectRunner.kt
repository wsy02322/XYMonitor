package com.xymonitor.app

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.PowerManager

object InspectRunner {
    private val lock = Any()
    private val clients = HashMap<String, VpsClient>()
    @Volatile private var inspecting = false
    @Volatile private var generation = 0
    @Volatile private var startedAt = 0L
    @Volatile private var activeClient: VpsClient? = null

    fun isInspecting(): Boolean = inspecting

    fun elapsedMs(): Long {
        val start = startedAt
        if (start <= 0L) return 0L
        return (System.currentTimeMillis() - start).coerceAtLeast(0L)
    }

    fun run(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        DebugLog.init(app)
        val myGen: Int
        val interrupting: Boolean
        val alreadyMs: Long
        synchronized(lock) {
            if (inspecting && !force) {
                DebugLog.i("问服务器进行中，忽略")
                return
            }
            interrupting = inspecting && force
            alreadyMs = elapsedMs()
            inspecting = true
            startedAt = System.currentTimeMillis()
            myGen = ++generation
        }
        if (interrupting) {
            DebugLog.i("看门狗打断 已进行=${Interval.formatSeconds(alreadyMs)}s")
            activeClient?.abort()
        }
        InspectScheduler.scheduleWatchdog(app)
        val wake = acquireWake(app)
        val wifi = acquireWifi(app)
        try {
            inspectOnce(app, myGen)
            if (myGen != generation) {
                DebugLog.i("问服务器作废")
                return
            }
            val prefs = Prefs(app)
            if (prefs.running) {
                val delay = Inbox.nextDelayMs()
                InspectScheduler.schedule(app, delay)
            }
            MonitorService.keepAlive(app)
            app.sendBroadcast(Intent(MonitorService.ACTION_STATUS).setPackage(app.packageName))
        } finally {
            releaseWake(wake)
            releaseWifi(wifi)
            synchronized(lock) {
                if (myGen == generation) {
                    inspecting = false
                    startedAt = 0L
                    InspectScheduler.cancelWatchdog(app)
                }
            }
        }
    }

    fun stopServer(context: Context) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        if (prefs.vpsUrl.isBlank() || prefs.vpsToken.isBlank()) return
        try {
            client(prefs).stop()
            DebugLog.i("已通知服务器停止")
        } catch (e: Exception) {
            DebugLog.i("通知服务器停止失败 ${e.message}")
        }
    }

    private fun inspectOnce(app: Context, myGen: Int) {
        val prefs = Prefs(app)
        val started = System.currentTimeMillis()
        val previousId = prefs.lastFirstItemId
        val previousCheck = prefs.lastCheckAt
        val planned = prefs.lastPlannedWaitMs
        val snap = try {
            pull(app, prefs)
        } catch (_: InterruptedException) {
            DebugLog.i("问服务器中断")
            return
        } catch (e: Exception) {
            prefs.lastCheckAt = System.currentTimeMillis()
            prefs.lastError = e.message ?: e.javaClass.simpleName
            prefs.lastStatus = "问服务器失败"
            prefs.lastActualGapMs = if (previousCheck > 0) prefs.lastCheckAt - previousCheck else 0L
            DebugLog.i("问服务器失败 ${prefs.lastError} 耗时=${Interval.formatSeconds(System.currentTimeMillis() - started)}s")
            ChangeAlert.fireError(app, prefs.lastError)
            return
        }
        if (myGen != generation) {
            DebugLog.i("问服务器作废")
            return
        }
        val now = System.currentTimeMillis()
        prefs.lastCheckAt = now
        val cost = now - started
        val gap = if (previousCheck > 0) now - previousCheck else 0L
        prefs.lastActualGapMs = gap
        val frozen = Health.frozenHint(planned, gap)
        prefs.lastFirstItemId = snap.firstId
        prefs.lastStatus = snap.status.ifBlank {
            when {
                snap.baseline -> "已记下第一件 ${snap.firstId}"
                snap.changed -> "第一件变为 ${snap.firstId}"
                else -> "第一件未变 ${snap.firstId}"
            }
        }
        prefs.lastError = snap.error
        val kind = when {
            snap.pendingAlert -> "待提醒"
            snap.pendingError -> "待报错"
            snap.baseline -> "基线"
            snap.changed -> "变化"
            snap.kind == "error" -> "失败"
            else -> "未变"
        }
        DebugLog.i(
            "服务器 $kind 第一件=${snap.firstId.ifBlank { "-" }} 上次=${previousId.ifBlank { "-" }} " +
                "耗时=${Interval.formatSeconds(cost)}s 距上次=${Interval.formatSeconds(gap)}s " +
                "计划=${Interval.formatSeconds(planned)}s" +
                if (frozen) " 可能被冻" else "",
        )
        if (snap.pendingAlert) {
            ChangeAlert.fire(app, snap.itemId.ifBlank { snap.firstId }, "上新")
            ackQuietly(prefs, itemId = snap.itemId.ifBlank { snap.firstId }, error = false)
        } else if (snap.pendingError) {
            ChangeAlert.fireError(app, snap.error.ifBlank { "巡检失败" })
            ackQuietly(prefs, itemId = "", error = true)
        }
    }

    private fun pull(app: Context, prefs: Prefs): VpsSnapshot {
        if (prefs.vpsUrl.isBlank()) {
            throw IllegalStateException("请填写服务器地址")
        }
        if (prefs.vpsToken.isBlank()) {
            throw IllegalStateException("请填写服务器密钥")
        }
        val status = NetworkWait.awaitValidated(app)
        if (status == NetworkWait.NONE) {
            DebugLog.i("无WiFi网络，直接走流量")
            return fetchViaCellular(app, prefs)
        }
        return try {
            callStart(prefs)
        } catch (e: Exception) {
            if (e is InterruptedException || !VpsClient.isRetryable(e)) throw e
            DebugLog.i("默认网络失败，改走流量 ${e.message}")
            fetchViaCellular(app, prefs)
        }
    }

    private fun fetchViaCellular(app: Context, prefs: Prefs): VpsSnapshot {
        val session = CellularFallback.acquire(app)
            ?: throw IllegalStateException("申请流量网络失败")
        session.use {
            return callStart(prefs, it.network)
        }
    }

    private fun callStart(prefs: Prefs, network: android.net.Network? = null): VpsSnapshot {
        val client = client(prefs)
        activeClient = client
        if (prefs.syncServer) {
            prefs.syncServer = false
            DebugLog.i("同步服务器 userId=${prefs.userId} 间隔=${prefs.intervalA}~${prefs.intervalB}s")
            return client.start(prefs.userId, prefs.intervalA, prefs.intervalB, network)
        }
        val snap = client.pending(network)
        if (!snap.running || snap.userId != prefs.userId) {
            DebugLog.i("服务器未在跑或卖家变了，重新启动")
            return client.start(prefs.userId, prefs.intervalA, prefs.intervalB, network)
        }
        return snap
    }

    private fun ackQuietly(prefs: Prefs, itemId: String, error: Boolean) {
        try {
            client(prefs).ack(itemId = itemId, error = error)
            DebugLog.i("已确认 ${if (error) "出错" else itemId}")
        } catch (e: Exception) {
            DebugLog.i("确认失败 ${e.message}")
        }
    }

    private fun client(prefs: Prefs): VpsClient {
        val key = "${prefs.vpsUrl}\n${prefs.vpsToken}"
        synchronized(lock) {
            return clients.getOrPut(key) { VpsClient(prefs.vpsUrl, prefs.vpsToken) }
        }
    }

    private fun acquireWake(app: Context): PowerManager.WakeLock? {
        return try {
            val pm = app.getSystemService(PowerManager::class.java)
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xymonitor:inspect").also {
                it.setReferenceCounted(false)
                it.acquire(VpsClient.INSPECT_LOCK_MS)
                DebugLog.i("WakeLock(inspect)=${if (it.isHeld) "持有" else "失败"}")
            }
        } catch (_: Exception) {
            DebugLog.i("WakeLock(inspect)=失败")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWifi(app: Context): WifiManager.WifiLock? {
        return try {
            val wifi = app.getSystemService(WifiManager::class.java) ?: return null
            wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "xymonitor:wifi").also {
                it.setReferenceCounted(false)
                it.acquire()
                DebugLog.i("WifiLock=${if (it.isHeld) "持有" else "失败"}")
            }
        } catch (_: Exception) {
            DebugLog.i("WifiLock=失败")
            null
        }
    }

    private fun releaseWake(lock: PowerManager.WakeLock?) {
        if (lock?.isHeld == true) {
            try {
                lock.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun releaseWifi(lock: WifiManager.WifiLock?) {
        if (lock?.isHeld == true) {
            try {
                lock.release()
            } catch (_: Exception) {
            }
        }
    }
}
