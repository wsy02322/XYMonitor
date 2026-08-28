package com.xymonitor.app

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.PowerManager

object InspectRunner {
    private val client = XianyuClient()
    private val lock = Any()
    @Volatile private var inspecting = false
    @Volatile private var generation = 0
    @Volatile private var startedAt = 0L

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
                DebugLog.i("巡检进行中，忽略")
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
            client.abort()
        }
        InspectScheduler.scheduleWatchdog(app)
        val wake = acquireWake(app)
        val wifi = acquireWifi(app)
        try {
            inspectOnce(app, myGen)
            if (myGen != generation) {
                DebugLog.i("巡检作废")
                return
            }
            val prefs = Prefs(app)
            if (prefs.running) {
                val delay = Interval.nextDelayMs(prefs.intervalA, prefs.intervalB)
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

    private fun inspectOnce(app: Context, myGen: Int) {
        val prefs = Prefs(app)
        val started = System.currentTimeMillis()
        val previousId = prefs.lastFirstItemId
        val previousCheck = prefs.lastCheckAt
        val planned = prefs.lastPlannedWaitMs
        val outcome = try {
            NetworkWait.awaitValidated(app)
            val currentFirstId = client.fetchFirstCardId(prefs.userId)
            Inspector.compare(previousId, currentFirstId)
        } catch (_: InterruptedException) {
            DebugLog.i("巡检中断")
            return
        } catch (e: Exception) {
            Inspector.fail(e.message ?: e.javaClass.simpleName)
        }
        if (myGen != generation) {
            DebugLog.i("巡检作废")
            return
        }
        if (outcome.ok) {
            prefs.lastFirstItemId = outcome.firstId
        }
        val now = System.currentTimeMillis()
        prefs.lastCheckAt = now
        val cost = now - started
        val gap = if (previousCheck > 0) now - previousCheck else 0L
        prefs.lastActualGapMs = gap
        val frozen = Health.frozenHint(planned, gap)
        if (outcome.ok) {
            prefs.lastError = ""
            val kind = when {
                outcome.baseline -> "基线"
                outcome.changed -> "变化"
                else -> "未变"
            }
            prefs.lastStatus = when {
                outcome.baseline -> "已记下第一件 ${outcome.firstId}"
                outcome.changed -> "第一件变为 ${outcome.firstId}"
                else -> "第一件未变 ${outcome.firstId}"
            }
            DebugLog.i(
                "巡检 $kind 第一件=${outcome.firstId} 上次=${previousId.ifBlank { "-" }} " +
                    "耗时=${Interval.formatSeconds(cost)}s 距上次=${Interval.formatSeconds(gap)}s " +
                    "计划=${Interval.formatSeconds(planned)}s" +
                    if (frozen) " 可能被冻" else "",
            )
            if (outcome.changed) {
                ChangeAlert.fire(app, outcome.firstId, "上新")
            }
        } else {
            prefs.lastError = outcome.error.orEmpty()
            prefs.lastStatus = "巡检失败"
            DebugLog.i("巡检失败 ${outcome.error} 耗时=${Interval.formatSeconds(cost)}s")
            ChangeAlert.fireError(app, outcome.error.orEmpty())
        }
    }

    private fun acquireWake(app: Context): PowerManager.WakeLock? {
        return try {
            val pm = app.getSystemService(PowerManager::class.java)
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xymonitor:inspect").also {
                it.setReferenceCounted(false)
                it.acquire(XianyuClient.INSPECT_LOCK_MS)
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
