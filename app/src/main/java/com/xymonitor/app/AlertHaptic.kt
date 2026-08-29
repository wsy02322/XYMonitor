package com.xymonitor.app

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object AlertHaptic {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var active = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var app: Context? = null

    fun start(context: Context) {
        app = context.applicationContext
        active = true
        acquireLock()
        DebugLog.i("震动=循环开始 锁=${if (wakeLock?.isHeld == true) "持有" else "失败"}")
        handler.removeCallbacks(pulse)
        handler.post(pulse)
    }

    fun stop(context: Context, reason: String = "未知") {
        val was = active
        active = false
        handler.removeCallbacks(pulse)
        try {
            vibrator(context.applicationContext)?.cancel()
        } catch (_: Exception) {
        }
        releaseLock()
        if (was) DebugLog.i("停震 来源=$reason")
    }

    fun isActive(): Boolean = active

    private val pulse = object : Runnable {
        override fun run() {
            if (!active) return
            val context = app ?: return
            vibrateOnce(context)
            handler.postDelayed(this, 1200)
        }
    }

    private fun vibrateOnce(context: Context) {
        val vibrator = vibrator(context) ?: return
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                val effect = VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build()
                vibrator.vibrate(effect, attrs)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(400)
            }
        } catch (_: Exception) {
        }
    }

    private fun acquireLock() {
        val context = app ?: return
        try {
            val pm = context.getSystemService(PowerManager::class.java)
            val lock = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xymonitor:haptic").also {
                it.setReferenceCounted(false)
                wakeLock = it
            }
            if (!lock.isHeld) lock.acquire(30 * 60 * 1000L)
        } catch (_: Exception) {
        }
    }

    private fun releaseLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            try {
                lock.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}

object AppForeground {
    @Volatile var monitorVisible: Boolean = false
}
