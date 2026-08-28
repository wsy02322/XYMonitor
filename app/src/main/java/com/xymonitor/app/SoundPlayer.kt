package com.xymonitor.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

class SoundPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun playNewItem() = play(R.raw.new_item)

    fun playFail() = play(R.raw.inspect_fail)

    fun release() {
        player?.release()
        player = null
    }

    private fun play(resId: Int) {
        try {
            player?.release()
            val afd = context.resources.openRawResourceFd(resId) ?: return
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
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
}
