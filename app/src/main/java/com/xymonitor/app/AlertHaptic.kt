package com.xymonitor.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object AlertHaptic {
    private val pattern = longArrayOf(0, 400, 800)

    fun start(context: Context) {
        val vibrator = vibrator(context.applicationContext) ?: return
        try {
            stop(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        } catch (_: Exception) {
        }
    }

    fun stop(context: Context) {
        try {
            vibrator(context.applicationContext)?.cancel()
        } catch (_: Exception) {
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
