package com.xymonitor.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

class SoundPlayer {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    fun playNewItem(context: Context, uriString: String) {
        handler.post {
            if (uriString.isNotBlank()) {
                try {
                    val ringtone = RingtoneManager.getRingtone(context, Uri.parse(uriString))
                    if (ringtone != null) {
                        ringtone.setAudioAttributes(alarmAttrs())
                        ringtone.play()
                        return@post
                    }
                } catch (_: Exception) {
                }
            }
            playRaw(context)
        }
    }

    private fun playRaw(context: Context) {
        try {
            player?.release()
            val afd = context.resources.openRawResourceFd(R.raw.new_item) ?: return
            player = MediaPlayer().apply {
                setAudioAttributes(alarmAttrs())
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            player?.release()
            player = null
        }
    }

    private fun alarmAttrs(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
