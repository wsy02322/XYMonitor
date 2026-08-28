package com.xymonitor.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object ChangeAlert {
    fun fire(context: Context, itemId: String, reason: String) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val visible = AppForeground.monitorVisible
        val whitelist = Health.batteryIgnored(app)
        val notifyOk = Health.notificationsEnabled(app)
        val fsi = AlertChannels.canUseFullScreen(app)
        AlertHaptic.start(app)
        AlertChannels.sync(app, prefs.newItemSoundUri)
        SoundPlayer().playNewItem(app, prefs.newItemSoundUri)
        val posted = postNotification(app, itemId)
        DebugLog.i(
            "提醒($reason) 震动=开始 声音=已播 通知=${if (posted) "已发" else "失败"} " +
                "前台=${if (visible) "是" else "否"} 白名单=${if (whitelist) "是" else "否"} " +
                "通知权限=${if (notifyOk) "是" else "否"} 全屏=${if (fsi) "是" else "否"} id=$itemId",
        )
        if (visible) {
            try {
                app.startActivity(
                    Intent(app, ErrorAlertActivity::class.java)
                        .putExtra(ErrorAlertActivity.EXTRA_TITLE, "第一件变化")
                        .putExtra(ErrorAlertActivity.EXTRA_MESSAGE, itemId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            } catch (e: Exception) {
                DebugLog.i("弹窗失败 ${e.message}")
            }
        }
    }

    private fun postNotification(context: Context, itemId: String): Boolean {
        return try {
            val pending = PendingIntent.getActivity(
                context,
                3,
                Intent(context, ErrorAlertActivity::class.java)
                    .putExtra(ErrorAlertActivity.EXTRA_TITLE, "第一件变化")
                    .putExtra(ErrorAlertActivity.EXTRA_MESSAGE, itemId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val ack = PendingIntent.getBroadcast(
                context,
                103,
                Intent(context, AlertAckReceiver::class.java).setAction(MonitorService.ACTION_ACK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, AlertChannels.alertChannelId(Prefs(context).newItemSoundUri))
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("第一件变化")
                .setContentText(itemId)
                .setStyle(NotificationCompat.BigTextStyle().bigText(itemId))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pending)
                .setDeleteIntent(ack)
                .apply {
                    if (AlertChannels.canUseFullScreen(context)) {
                        setFullScreenIntent(pending, true)
                    }
                }
                .build()
            context.getSystemService(NotificationManager::class.java).notify(3, notification)
            true
        } catch (e: Exception) {
            DebugLog.i("通知失败 ${e.message}")
            false
        }
    }
}

object Health {
    fun batteryIgnored(context: Context): Boolean {
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun notificationsEnabled(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun frozenHint(plannedMs: Long, actualMs: Long): Boolean {
        if (plannedMs <= 0 || actualMs <= 0) return false
        return actualMs > plannedMs + 15_000L && actualMs > plannedMs * 3 / 2
    }
}
