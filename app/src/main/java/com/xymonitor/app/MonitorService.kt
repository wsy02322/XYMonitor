package com.xymonitor.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitorService : Service() {
    private val prefs by lazy { Prefs(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLog.init(this)
        if (intent?.action == ACTION_STOP) {
            prefs.running = false
            prefs.lastStatus = "已停止"
            prefs.clearSessionTiming()
            InspectScheduler.cancel(this)
            shutdown()
            return START_NOT_STICKY
        }

        val nextUserId = intent?.getStringExtra(EXTRA_USER_ID)?.trim()
            .orEmpty()
            .ifBlank { prefs.userId }
        if (nextUserId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        prefs.userId = nextUserId
        prefs.resetFirstIdIfUserChanged(nextUserId)
        prefs.running = true
        AlertChannels.sync(this, prefs.newItemSoundUri)
        startInForeground()

        when (intent?.action) {
            ACTION_KEEPALIVE -> {}
            ACTION_START -> {
                prefs.clearSessionTiming()
                DebugLog.i(
                    "服务启动 userId=$nextUserId 前台=${yesNo(AppForeground.monitorVisible)} " +
                        "白名单=${yesNo(Health.batteryIgnored(this))} " +
                        "通知=${yesNo(Health.notificationsEnabled(this))} " +
                        "精确闹钟=${yesNo(Health.exactAlarmAllowed(this))}",
                )
                Thread({ InspectRunner.run(this, force = false) }, "xy-start").start()
            }
            else -> {
                val nextAt = prefs.nextInspectAt
                val now = System.currentTimeMillis()
                if (nextAt > 0L && !InspectPlan.overdue(now, nextAt)) {
                    val remain = InspectPlan.remainingMs(now, nextAt)
                    DebugLog.i("服务被系统拉起，补约剩余 ${Interval.formatSeconds(remain)}s")
                    InspectScheduler.scheduleAt(this, nextAt)
                } else {
                    DebugLog.i("服务被系统拉起，立即巡检")
                    Thread({ InspectRunner.run(this, force = false) }, "xy-restart").start()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (!prefs.running) {
            InspectScheduler.cancel(this)
        }
        AlertHaptic.stop(this, "服务销毁")
        super.onDestroy()
    }

    private fun startInForeground() {
        AlertChannels.sync(this, prefs.newItemSoundUri)
        val notification = buildRunningNotification(prefs.lastStatus.ifBlank { "启动中" })
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildRunningNotification(status: String): Notification {
        val open = activityPending(0, Intent(this, MainActivity::class.java))
        val time = if (prefs.lastCheckAt > 0) TIME_FMT.format(Date(prefs.lastCheckAt)) else "--:--"
        val remain = InspectPlan.remainingMs(System.currentTimeMillis(), prefs.nextInspectAt)
        val wait = if (remain > 0) {
            " · 下次 ${Interval.formatSeconds(remain)} 秒"
        } else {
            ""
        }
        return NotificationCompat.Builder(this, AlertChannels.RUNNING)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("运行中 · $time · $status$wait")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun activityPending(requestCode: Int, intent: Intent): PendingIntent {
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun shutdown() {
        DebugLog.i("服务停止")
        InspectScheduler.cancel(this)
        AlertHaptic.stop(this, "停止监控")
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_STATUS = "com.xymonitor.app.STATUS"
        const val ACTION_START = "com.xymonitor.app.START"
        const val ACTION_STOP = "com.xymonitor.app.STOP"
        const val ACTION_KEEPALIVE = "com.xymonitor.app.KEEPALIVE"
        const val ACTION_ACK = "com.xymonitor.app.ACK"
        const val EXTRA_USER_ID = "user_id"
        private const val NOTIFICATION_ID = 1
        private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

        fun start(context: Context, userId: String) {
            val intent = Intent(context, MonitorService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_USER_ID, userId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun keepAlive(context: Context) {
            val prefs = Prefs(context)
            if (!prefs.running || prefs.userId.isBlank()) return
            val intent = Intent(context, MonitorService::class.java)
                .setAction(ACTION_KEEPALIVE)
                .putExtra(EXTRA_USER_ID, prefs.userId)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            AlertHaptic.stop(context, "停止监控")
            Prefs(context).apply {
                running = false
                lastStatus = "已停止"
                clearSessionTiming()
            }
            InspectScheduler.cancel(context)
            val intent = Intent(context, MonitorService::class.java).setAction(ACTION_STOP)
            try {
                context.startService(intent)
            } catch (_: Exception) {
                context.stopService(Intent(context, MonitorService::class.java))
            }
        }

        private fun yesNo(value: Boolean): String = if (value) "是" else "否"
    }
}
