package com.xymonitor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DebugLog.init(context)
        val prefs = Prefs(context)
        if (!prefs.running || prefs.userId.isBlank()) return
        val reason = intent?.action.orEmpty().substringAfterLast('.')
        val nextAt = prefs.nextInspectAt
        val now = System.currentTimeMillis()
        if (nextAt > now + 2_000L) {
            val remain = nextAt - now
            DebugLog.i("恢复监控 原因=$reason 补约剩余 ${Interval.formatSeconds(remain)}s")
            InspectScheduler.scheduleAt(context, nextAt)
        } else {
            DebugLog.i("恢复监控 原因=$reason 已到期，3秒后巡检")
            InspectScheduler.schedule(context, 3_000L)
        }
    }
}
