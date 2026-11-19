package com.naatdelay.dd7

import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.naatdelay.dd7.audio.AudioNoisyReceiver
import com.naatdelay.dd7.ui.pedal.PedalScreen
import com.naatdelay.dd7.ui.pedal.PedalViewModel
import com.naatdelay.dd7.ui.theme.DD7PedalTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val pedalViewModel: PedalViewModel by viewModels()
    private lateinit var noisyReceiver: AudioNoisyReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        noisyReceiver = AudioNoisyReceiver {
            pedalViewModel.pausePlaybackForNoisyOutput()
        }
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        setContent {
            val uiState by pedalViewModel.uiState.collectAsState()
            DD7PedalTheme(darkStudio = uiState.darkStudioTheme) {
                PedalScreen(viewModel = pedalViewModel)
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisyReceiver) }
        super.onDestroy()
    }
}
