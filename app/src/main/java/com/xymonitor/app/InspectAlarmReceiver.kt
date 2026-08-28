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
        val plannedAt = prefs.nextInspectAt
        val late = if (plannedAt > 0L) {
            (System.currentTimeMillis() - plannedAt).coerceAtLeast(0L)
        } else {
            0L
        }
        DebugLog.i("闹钟触发 延迟=${Interval.formatSeconds(late)}s")
        MonitorService.tick(context)
    }
}
