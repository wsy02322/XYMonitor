package com.xymonitor.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object InspectPlan {
    const val GRACE_MS = 15_000L

    fun nextAt(nowMs: Long, delayMs: Long): Long = nowMs + delayMs.coerceAtLeast(0L)

    fun remainingMs(nowMs: Long, nextAtMs: Long): Long {
        if (nextAtMs <= 0L) return 0L
        return (nextAtMs - nowMs).coerceAtLeast(0L)
    }

    fun overdue(nowMs: Long, nextAtMs: Long, graceMs: Long = GRACE_MS): Boolean {
        if (nextAtMs <= 0L) return false
        return nowMs > nextAtMs + graceMs
    }
}

object InspectScheduler {
    const val ACTION_TICK = "com.xymonitor.app.TICK"
    private const val REQUEST_CODE = 200
    private const val SHOW_REQUEST_CODE = 201

    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun schedule(context: Context, delayMs: Long): String {
        return scheduleAt(context, InspectPlan.nextAt(System.currentTimeMillis(), delayMs), delayMs)
    }

    fun scheduleAt(context: Context, atMs: Long, delayMs: Long? = null): String {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val at = atMs.coerceAtLeast(now + 1L)
        val wait = delayMs ?: (at - now)
        Prefs(app).apply {
            nextInspectAt = at
            nextWaitMs = wait
            if (delayMs != null) lastPlannedWaitMs = delayMs
        }
        val am = app.getSystemService(AlarmManager::class.java)
        val op = operation(app)
        val exact = canExact(app)
        val method = try {
            if (exact) {
                val show = PendingIntent.getActivity(
                    app,
                    SHOW_REQUEST_CODE,
                    Intent(app, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
                "AlarmClock"
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
                "Inexact"
            }
        } catch (e: Exception) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
                "Inexact"
            } catch (e2: Exception) {
                DebugLog.i("预约闹钟失败 ${e2.message}")
                "失败"
            }
        }
        DebugLog.i(
            "预约下次 ${Interval.formatSeconds(wait)}s 方法=$method " +
                "权限=${if (exact) "是" else "否"}",
        )
        return method
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        try {
            app.getSystemService(AlarmManager::class.java).cancel(operation(app))
        } catch (_: Exception) {
        }
        Prefs(app).apply {
            nextInspectAt = 0L
            nextWaitMs = 0L
        }
    }

    private fun operation(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, InspectAlarmReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
