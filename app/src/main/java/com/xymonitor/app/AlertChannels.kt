package com.xymonitor.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build

object AlertChannels {
    const val RUNNING = "monitor"
    const val ERROR = "monitor_error"
    const val ALERT_PREFIX = "monitor_alert_"

    fun alertChannelId(soundUri: String): String {
        val key = if (soundUri.isBlank()) "default" else Integer.toHexString(soundUri.hashCode())
        return ALERT_PREFIX + key
    }

    fun defaultSoundUri(context: Context): Uri {
        return Uri.parse(
            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.new_item}",
        )
    }

    fun sync(context: Context, soundUri: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureRunning(context, manager)
        ensureError(context, manager)
        val id = alertChannelId(soundUri)
        manager.notificationChannels
            .filter { it.id.startsWith(ALERT_PREFIX) && it.id != id }
            .forEach { manager.deleteNotificationChannel(it.id) }
        if (manager.getNotificationChannel(id) == null) {
            val channel = NotificationChannel(
                id,
                context.getString(R.string.notify_alert_channel),
                NotificationManager.IMPORTANCE_HIGH,
            )
            channel.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            channel.enableVibration(true)
            channel.setSound(soundUriOf(context, soundUri), notificationAudio())
            manager.createNotificationChannel(channel)
        }
    }

    fun canUseFullScreen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    private fun soundUriOf(context: Context, soundUri: String): Uri {
        return if (soundUri.isBlank()) defaultSoundUri(context) else Uri.parse(soundUri)
    }

    private fun notificationAudio(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private fun ensureRunning(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(RUNNING) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                RUNNING,
                context.getString(R.string.notify_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun ensureError(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(ERROR) != null) return
        val error = NotificationChannel(
            ERROR,
            context.getString(R.string.notify_error_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        error.enableVibration(true)
        error.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(error)
    }
}
