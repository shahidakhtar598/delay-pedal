package com.naatdelay.dd7.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

/**
 * Automatically pauses playback when headphones or Bluetooth speakers disconnect
 * (ACTION_AUDIO_BECOMING_NOISY) so audio never blasts unexpectedly from phone speakers.
 */
class AudioNoisyReceiver(
    private val onBecomingNoisy: () -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            onBecomingNoisy()
        }
    }
}
