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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitorService : Service() {
    private val prefs by lazy { Prefs(this) }
    private val client = XianyuClient()
    private val sounds = SoundPlayer()
    private var worker: Thread? = null
    private var inspectLock: PowerManager.WakeLock? = null
    @Volatile private var running = false
    @Volatile private var userId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.running = false
            prefs.lastStatus = "已停止"
            prefs.nextWaitMs = 0L
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
        startLoop(nextUserId)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        worker = null
        releaseInspectLock()
        AlertHaptic.stop(this)
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
                val delay = Interval.nextDelayMs(prefs.intervalA, prefs.intervalB)
                prefs.nextWaitMs = delay
                notifyStatus()
                sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName))
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "xy-monitor").also { it.start() }
    }

    private fun inspectOnce(currentUserId: String) {
        acquireInspectLock()
        try {
            val outcome = try {
                val currentFirstId = client.fetchFirstCardId(currentUserId)
                val result = Inspector.compare(prefs.lastFirstItemId, currentFirstId)
                if (result.ok) {
                    prefs.lastFirstItemId = result.firstId
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
                    outcome.baseline -> "已记下第一件 ${outcome.firstId}"
                    outcome.changed -> "第一件变为 ${outcome.firstId}"
                    else -> "第一件未变 ${outcome.firstId}"
                }
                if (outcome.changed) {
                    alertChange(outcome.firstId)
                }
            } else {
                prefs.lastError = outcome.error.orEmpty()
                prefs.lastStatus = "巡检失败"
                alertError(outcome.error.orEmpty())
            }
            notifyStatus()
            sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName))
        } finally {
            releaseInspectLock()
        }
    }

    private fun acquireInspectLock() {
        val pm = getSystemService(PowerManager::class.java)
        val lock = inspectLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xymonitor:inspect").also {
            it.setReferenceCounted(false)
            inspectLock = it
        }
        if (!lock.isHeld) {
            try {
                lock.acquire(30_000)
            } catch (_: Exception) {
            }
        }
    }

    private fun releaseInspectLock() {
        val lock = inspectLock ?: return
        if (lock.isHeld) {
            try {
                lock.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun alertError(message: String) {
        if (prefs.errorSound) {
            sounds.playBeep()
        }
        val text = message.ifBlank { "未知错误" }
        postAlertNotification(
            id = ERROR_NOTIFICATION_ID,
            channelId = AlertChannels.ERROR,
            title = "巡检失败",
            text = text,
            requestCode = 2,
            silent = false,
        )
        try {
            startActivity(alertIntent("巡检失败", text))
        } catch (_: Exception) {
        }
    }

    private fun alertChange(itemId: String) {
        AlertHaptic.stop(this)
        AlertHaptic.start(this)
        AlertChannels.sync(this, prefs.newItemSoundUri)
        val visible = AppForeground.monitorVisible
        postAlertNotification(
            id = CHANGE_NOTIFICATION_ID,
            channelId = AlertChannels.alertChannelId(prefs.newItemSoundUri),
            title = "第一件变化",
            text = itemId,
            requestCode = 3,
            silent = visible,
        )
        if (visible) {
            sounds.playNewItem(this, prefs.newItemSoundUri)
            try {
                startActivity(alertIntent("第一件变化", itemId))
            } catch (_: Exception) {
            }
        }
    }

    private fun alertIntent(title: String, text: String): Intent {
        return Intent(this, ErrorAlertActivity::class.java)
            .putExtra(ErrorAlertActivity.EXTRA_TITLE, title)
            .putExtra(ErrorAlertActivity.EXTRA_MESSAGE, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private fun postAlertNotification(
        id: Int,
        channelId: String,
        title: String,
        text: String,
        requestCode: Int,
        silent: Boolean,
    ) {
        val pending = activityPending(requestCode, alertIntent(title, text))
        val ack = PendingIntent.getBroadcast(
            this,
            requestCode + 100,
            Intent(this, AlertAckReceiver::class.java).setAction(ACTION_ACK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setDeleteIntent(ack)
        if (silent) {
            builder.setSilent(true)
        }
        if (!silent && AlertChannels.canUseFullScreen(this)) {
            builder.setFullScreenIntent(pending, true)
        }
        getSystemService(NotificationManager::class.java).notify(id, builder.build())
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

    private fun notifyStatus() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildRunningNotification(prefs.lastStatus))
    }

    private fun buildRunningNotification(status: String): Notification {
        val open = activityPending(0, Intent(this, MainActivity::class.java))
        val time = if (prefs.lastCheckAt > 0) TIME_FMT.format(Date(prefs.lastCheckAt)) else "--:--"
        val wait = if (prefs.nextWaitMs > 0) {
            " · 下次 ${Interval.formatSeconds(prefs.nextWaitMs)} 秒"
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
        running = false
        worker?.interrupt()
        worker = null
        releaseInspectLock()
        AlertHaptic.stop(this)
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_STATUS = "com.xymonitor.app.STATUS"
        const val ACTION_STOP = "com.xymonitor.app.STOP"
        const val ACTION_ACK = "com.xymonitor.app.ACK"
        const val EXTRA_USER_ID = "user_id"
        private const val NOTIFICATION_ID = 1
        private const val ERROR_NOTIFICATION_ID = 2
        private const val CHANGE_NOTIFICATION_ID = 3
        private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

        fun start(context: Context, userId: String) {
            val intent = Intent(context, MonitorService::class.java)
                .putExtra(EXTRA_USER_ID, userId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            AlertHaptic.stop(context)
            Prefs(context).apply {
                running = false
                lastStatus = "已停止"
                nextWaitMs = 0L
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
