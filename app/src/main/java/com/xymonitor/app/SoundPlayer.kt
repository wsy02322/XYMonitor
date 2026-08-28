package com.xymonitor.app

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

class SoundPlayer {
    private val handler = Handler(Looper.getMainLooper())

    fun playBeep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            handler.postDelayed({
                try {
                    tone.release()
                } catch (_: Exception) {
                }
            }, 300)
        } catch (_: Exception) {
        }
    }
}
