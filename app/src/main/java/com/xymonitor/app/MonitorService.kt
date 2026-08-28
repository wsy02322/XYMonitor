package com.xymonitor.app

import android.app.Notification
import android.app.NotificationChannel
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
    private val client = XianyuClient()
    private val sounds by lazy { SoundPlayer(this) }
    private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile private var userId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.running = false
            prefs.lastStatus = "已停止"
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
        prefs.resetKnownIfUserChanged(nextUserId)
        prefs.running = true
        startInForeground()
        startLoop(nextUserId)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        worker = null
        sounds.release()
        super.onDestroy()
    }

    private fun startLoop(nextUserId: String) {
        if (worker?.isAlive == true && userId == nextUserId) return
        running = false
        worker?.interrupt()
        userId = nextUserId
        running = true
        worker = Thread({
            while (running) {
                inspectOnce(nextUserId)
                if (!running) break
                try {
                    Thread.sleep(INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "xy-monitor").also { it.start() }
    }

    private fun inspectOnce(currentUserId: String) {
        val outcome = try {
            val current = client.fetchFirstPageIds(currentUserId)
            val result = Inspector.compare(prefs.knownIds, current)
            if (result.baseline) {
                prefs.replaceKnown(current)
            } else if (result.newIds.isNotEmpty()) {
                prefs.addKnown(result.newIds)
            }
            result
        } catch (_: InterruptedException) {
            return
        } catch (e: Exception) {
            Inspector.fail(e.message ?: e.javaClass.simpleName)
        }

        prefs.lastCheckAt = System.currentTimeMillis()
        if (outcome.ok) {
            prefs.lastError = ""
            prefs.lastStatus = when {
                outcome.baseline -> "已建立基线，${outcome.itemIds.size} 件"
                outcome.newIds.isNotEmpty() -> "发现 ${outcome.newIds.size} 件上新"
                else -> "无上新，当前 ${outcome.itemIds.size} 件"
            }
            if (outcome.newIds.isNotEmpty()) {
                sounds.playNewItem()
            }
        } else {
            prefs.lastError = outcome.error.orEmpty()
            prefs.lastStatus = "巡检失败"
            sounds.playFail()
        }
        notifyStatus()
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName))
    }

    private fun startInForeground() {
        ensureChannel()
        val notification = buildNotification(prefs.lastStatus.ifBlank { "启动中" })
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

    private fun notifyStatus() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(prefs.lastStatus))
    }

    private fun buildNotification(status: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = if (prefs.lastCheckAt > 0) TIME_FMT.format(Date(prefs.lastCheckAt)) else "--:--"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("运行中 · $time · $status")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notify_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun shutdown() {
        running = false
        worker?.interrupt()
        worker = null
        sounds.release()
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_STATUS = "com.xymonitor.app.STATUS"
        const val ACTION_STOP = "com.xymonitor.app.STOP"
        const val EXTRA_USER_ID = "user_id"
        const val INTERVAL_MS = 3 * 60 * 1000L
        private const val CHANNEL_ID = "monitor"
        private const val NOTIFICATION_ID = 1
        private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

        fun start(context: Context, userId: String) {
            val intent = Intent(context, MonitorService::class.java)
                .putExtra(EXTRA_USER_ID, userId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            Prefs(context).apply {
                running = false
                lastStatus = "已停止"
            }
            val intent = Intent(context, MonitorService::class.java).setAction(ACTION_STOP)
            try {
                context.startService(intent)
            } catch (_: Exception) {
                context.stopService(Intent(context, MonitorService::class.java))
            }
        }
    }
}
