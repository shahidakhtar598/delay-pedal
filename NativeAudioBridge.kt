package com.naatdelay.dd7.audio

import javax.inject.Inject
import javax.inject.Singleton

data class ExportResult(
    val success: Boolean,
    val fileBytes: Long,
    val tailMs: Int,
    val errorCode: String
)

fun interface ExportProgressCallback {
    fun onProgress(percent: Int)
}

@Singleton
class NativeAudioBridge @Inject constructor() {

    companion object {
        const val PARAM_E_LEVEL = 0
        const val PARAM_F_BACK = 1
        const val PARAM_D_TIME_MS = 2
        const val PARAM_MODE = 3
        const val PARAM_PING_PONG = 4
        const val PARAM_BYPASS = 5
        const val PARAM_PING_PONG_WIDTH = 6
        const val PARAM_ANTI_BOOM_CUT = 7

        const val FORMAT_WAV_16 = 0
        const val FORMAT_MP3_192 = 1
        const val FORMAT_MP3_320 = 2

        init {
            System.loadLibrary("dd7dsp")
        }
    }

    external fun nativeInit()
    external fun nativeRelease()
    external fun loadDecodedPcm(pcmPath: String, sampleRate: Int): Boolean
    external fun setKnobParam(paramId: Int, value: Float)
    external fun setPlaybackState(playing: Boolean, seekPositionMs: Long)
    external fun setLoopRange(enabled: Boolean, startMs: Long, endMs: Long)
    external fun getPlaybackPositionMs(): Long
    external fun getOutputMeterLevel(): Float
    external fun isLimiterActive(): Boolean
    external fun cancelOfflineExport()

    external fun renderOfflineExport(
        inputPcmPath: String,
        outputFilePath: String,
        sampleRate: Int,
        formatCode: Int,
        effectLevel: Float,
        feedback: Float,
        delayTimeMs: Float,
        modeOrdinal: Int,
        pingPong: Boolean,
        pingPongWidth: Float,
        antiBoomCut: Boolean,
        bypassed: Boolean,
        maxDurationSec: Int,
        progressCallback: ExportProgressCallback?
    ): ExportResult
}
