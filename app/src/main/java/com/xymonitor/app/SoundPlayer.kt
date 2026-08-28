package com.xymonitor.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper

class SoundPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    fun playNewItem(uriString: String) {
        if (uriString.isNotBlank()) {
            try {
                playUri(Uri.parse(uriString))
                return
            } catch (_: Exception) {
                release()
            }
        }
        playRaw(R.raw.new_item)
    }

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

    fun release() {
        player?.release()
        player = null
    }

    private fun playRaw(resId: Int) {
        val afd = context.resources.openRawResourceFd(resId) ?: return
        try {
            startPlayer { player ->
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
        } finally {
            afd.close()
        }
    }

    private fun playUri(uri: Uri) {
        startPlayer { player ->
            player.setDataSource(context, uri)
        }
    }

    private fun startPlayer(bind: (MediaPlayer) -> Unit) {
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            bind(this)
            setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
            prepare()
            start()
        }
    }
}
