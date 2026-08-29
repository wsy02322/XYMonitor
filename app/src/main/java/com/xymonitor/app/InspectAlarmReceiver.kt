package com.xymonitor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class InspectAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DebugLog.init(context)
        val prefs = Prefs(context)
        if (!prefs.running) {
            DebugLog.i("闹钟触发但已停止，忽略")
            return
        }
        val watchdog = intent?.action == InspectScheduler.ACTION_WATCHDOG
        if (watchdog) {
            DebugLog.i("看门狗触发 已进行=${Interval.formatSeconds(InspectRunner.elapsedMs())}s")
            if (!InspectRunner.isInspecting()) {
                DebugLog.i("看门狗时已结束，忽略")
                return
            }
        } else {
            val plannedAt = prefs.nextInspectAt
            val late = if (plannedAt > 0L) {
                (System.currentTimeMillis() - plannedAt).coerceAtLeast(0L)
            } else {
                0L
            }
            DebugLog.i("闹钟触发 延迟=${Interval.formatSeconds(late)}s")
        }
        val pending = goAsync()
        DebugLog.i("广播异步=开始")
        Thread({
            try {
                MonitorService.keepAlive(context)
                InspectRunner.run(context, force = watchdog)
            } catch (e: Exception) {
                DebugLog.i("巡检异常 ${e.message}")
            } finally {
                DebugLog.i("广播异步=结束")
                try {
                    pending.finish()
                } catch (_: Exception) {
                }
            }
        }, if (watchdog) "xy-watchdog" else "xy-alarm").start()
    }
}
