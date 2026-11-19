package com.naatdelay.dd7.ui.pedal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naatdelay.dd7.BuildConfig
import com.naatdelay.dd7.audio.AudioImportDecoder
import com.naatdelay.dd7.audio.ExportForegroundService
import com.naatdelay.dd7.audio.NativeAudioBridge
import com.naatdelay.dd7.audio.WaveformGenerator
import com.naatdelay.dd7.data.local.entity.AudioProjectEntity
import com.naatdelay.dd7.data.local.entity.PresetEntity
import com.naatdelay.dd7.data.repository.LicenseRepository
import com.naatdelay.dd7.data.repository.PresetRepository
import com.naatdelay.dd7.data.repository.ProjectRepository
import com.naatdelay.dd7.data.repository.SavePresetResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class UiDelayMode(val ordinalCode: Int, val label: String, val maxMs: Float) {
    MS_50(0, "50ms", 50f),
    MS_200(1, "200ms", 200f),
    MS_800(2, "800ms", 800f),
    MS_3200(3, "3200ms", 3200f),
    ANALOG(4, "ANALOG", 800f),
    MODULATE(5, "MODULATE", 800f);

    companion object {
        fun fromName(name: String): UiDelayMode = entries.firstOrNull { it.name == name } ?: ANALOG
    }
}

data class PedalUiState(
    val effectLevel: Float = 0.48f,
    val feedback: Float = 0.38f,
    val delayTimeMs: Float = 420f,
    val mode: UiDelayMode = UiDelayMode.ANALOG,
    val isPingPong: Boolean = false,
    val pingPongWidth: Float = 0.85f,
    val antiBoomCut: Boolean = true,
    val isBypassed: Boolean = false,
    val isTapTempoMode: Boolean = false,
    val darkStudioTheme: Boolean = false,
    val activePresetId: String? = "preset-factory-01",

    // Loaded audio track
    val trackName: String = "Tap Import to load Naat recording",
    val cachedPcmPath: String? = null,
    val sampleRate: Int = 48000,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val waveformPeaks: List<Float> = emptyList(),
    val isPlaying: Boolean = false,
    val isLooping: Boolean = false,
    val isLimiting: Boolean = false,
    val isImporting: Boolean = false,
    val statusBanner: String? = null
)

@HiltViewModel
class PedalViewModel @Inject constructor(
    private val nativeBridge: NativeAudioBridge,
    private val decoder: AudioImportDecoder,
    private val presetRepo: PresetRepository,
    private val projectRepo: ProjectRepository,
    val licenseRepo: LicenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PedalUiState())
    val uiState: StateFlow<PedalUiState> = _uiState.asStateFlow()

    val presets: StateFlow<List<PresetEntity>> = presetRepo.presets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val licenseStatus = licenseRepo.status
    val exportState = ExportForegroundService.state

    private var lastTapTimestampMs: Long = 0L

    init {
        nativeBridge.nativeInit()
        pushAllParamsToNative()
        viewModelScope.launch { licenseRepo.verifyOnStartup() }

        // 30fps playhead & limiter telemetry poll
        viewModelScope.launch {
            while (true) {
                delay(33L)
                val pos = nativeBridge.getPlaybackPositionMs()
                val lim = nativeBridge.isLimiterActive()
                _uiState.update { st ->
                    if (st.positionMs != pos || st.isLimiting != lim) {
                        st.copy(positionMs = pos, isLimiting = lim)
                    } else st
                }
            }
        }
    }

    fun importAudio(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, statusBanner = null) }
            val result = decoder.decodeToStereoFloatPcm(uri)
            result.onSuccess { decoded ->
                val loaded = nativeBridge.loadDecodedPcm(decoded.cachedPcmPath, decoded.sampleRate)
                if (loaded) {
                    pushAllParamsToNative()
                    projectRepo.saveProject(
                        AudioProjectEntity(
                            id = UUID.randomUUID().toString(),
                            displayName = decoded.displayName,
                            sourceUri = uri.toString(),
                            cachedPcmPath = decoded.cachedPcmPath,
                            durationMs = decoded.durationMs,
                            sampleRate = decoded.sampleRate,
                            waveformPeaksJson = WaveformGenerator.toJson(decoded.waveformPeaks),
                            lastPresetId = _uiState.value.activePresetId,
                            lastOpenedAt = System.currentTimeMillis()
                        )
                    )
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            trackName = decoded.displayName,
                            cachedPcmPath = decoded.cachedPcmPath,
                            sampleRate = decoded.sampleRate,
                            durationMs = decoded.durationMs,
                            positionMs = 0L,
                            waveformPeaks = decoded.waveformPeaks,
                            isPlaying = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isImporting = false, statusBanner = "Could not initialize audio playback engine.")
                    }
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        statusBanner = "We couldn't read this audio file. Please choose a valid MP3, WAV, M4A, or WhatsApp audio recording."
                    )
                }
            }
        }
    }

    fun setEffectLevel(v: Float) {
        val c = v.coerceIn(0f, 1f)
        _uiState.update { it.copy(effectLevel = c, activePresetId = null) }
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_E_LEVEL, c)
    }

    fun setFeedback(v: Float) {
        val c = v.coerceIn(0f, 0.95f)
        _uiState.update { it.copy(feedback = c, activePresetId = null) }
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_F_BACK, c)
    }

    fun setDelayTimeNormalized(norm: Float) {
        val maxMs = _uiState.value.mode.maxMs
        val ms = (1f + norm.coerceIn(0f, 1f) * (maxMs - 1f))
        setDelayTimeMs(ms)
    }

    fun setDelayTimeMs(ms: Float) {
        val clamped = ms.coerceIn(1f, _uiState.value.mode.maxMs)
        _uiState.update { it.copy(delayTimeMs = clamped, activePresetId = null) }
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_D_TIME_MS, clamped)
    }

    fun setMode(mode: UiDelayMode) {
        val clampedMs = _uiState.value.delayTimeMs.coerceIn(1f, mode.maxMs)
        _uiState.update { it.copy(mode = mode, delayTimeMs = clampedMs, activePresetId = null) }
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_MODE, mode.ordinalCode.toFloat())
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_D_TIME_MS, clampedMs)
    }

    fun togglePingPong() {
        val next = !_uiState.value.isPingPong
        _uiState.update { it.copy(isPingPong = next, activePresetId = null) }
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_PING_PONG, if (next) 1f else 0f)
    }

    fun toggleTheme() {
        _uiState.update { it.copy(darkStudioTheme = !it.darkStudioTheme) }
    }

    fun onStompTap() {
        val st = _uiState.value
        if (st.isTapTempoMode) {
            val now = System.currentTimeMillis()
            if (lastTapTimestampMs > 0L) {
                val delta = (now - lastTapTimestampMs).toFloat()
                if (delta in 40f..3200f) {
                    setDelayTimeMs(delta)
                }
            }
            lastTapTimestampMs = now
        } else {
            val nextBypass = !st.isBypassed
            _uiState.update { it.copy(isBypassed = nextBypass) }
            nativeBridge.setKnobParam(NativeAudioBridge.PARAM_BYPASS, if (nextBypass) 1f else 0f)
        }
    }

    fun onStompLongPress() {
        lastTapTimestampMs = 0L
        _uiState.update { it.copy(isTapTempoMode = !it.isTapTempoMode) }
    }

    fun togglePlayback() {
        val st = _uiState.value
        if (st.cachedPcmPath == null) return
        val nextPlay = !st.isPlaying
        _uiState.update { it.copy(isPlaying = nextPlay) }
        nativeBridge.setPlaybackState(nextPlay, -1L)
    }

    fun pausePlaybackForNoisyOutput() {
        if (_uiState.value.isPlaying) {
            _uiState.update { it.copy(isPlaying = false, statusBanner = "Headphones disconnected — playback paused.") }
            nativeBridge.setPlaybackState(false, -1L)
        }
    }

    fun seekTo(ms: Long) {
        _uiState.update { it.copy(positionMs = ms) }
        nativeBridge.setPlaybackState(_uiState.value.isPlaying, ms)
    }

    fun toggleLoop() {
        val next = !_uiState.value.isLooping
        _uiState.update { it.copy(isLooping = next) }
        nativeBridge.setLoopRange(next, 0L, _uiState.value.durationMs)
    }

    fun applyPreset(preset: PresetEntity) {
        val mode = UiDelayMode.fromName(preset.delayMode)
        _uiState.update {
            it.copy(
                effectLevel = preset.effectLevel,
                feedback = preset.feedback.coerceIn(0f, 0.95f),
                delayTimeMs = preset.delayTimeMs.coerceIn(1f, mode.maxMs),
                mode = mode,
                isPingPong = preset.isPingPong,
                pingPongWidth = preset.pingPongWidth,
                antiBoomCut = preset.antiBoomCut,
                activePresetId = preset.id
            )
        }
        pushAllParamsToNative()
    }

    suspend fun saveCustomPreset(name: String, allowOverwrite: Boolean): SavePresetResult {
        val st = _uiState.value
        return presetRepo.saveCustomPreset(
            rawName = name,
            delayMode = st.mode.name,
            effectLevel = st.effectLevel,
            feedback = st.feedback,
            delayTimeMs = st.delayTimeMs,
            isPingPong = st.isPingPong,
            pingPongWidth = st.pingPongWidth,
            antiBoomCut = st.antiBoomCut,
            isProActivated = licenseStatus.value.isProActivated,
            allowOverwrite = allowOverwrite
        )
    }

    fun deleteCustomPreset(id: String) {
        viewModelScope.launch { presetRepo.deleteCustomPreset(id) }
    }

    fun startExport(context: Context, fileName: String, formatCode: Int) {
        val st = _uiState.value
        val pcm = st.cachedPcmPath ?: return
        val maxSec = if (licenseStatus.value.isProActivated) -1 else BuildConfig.FREE_TRIAL_EXPORT_LIMIT_SEC

        val intent = Intent(context, ExportForegroundService::class.java).apply {
            action = ExportForegroundService.ACTION_START_EXPORT
            putExtra(ExportForegroundService.EXTRA_INPUT_PCM_PATH, pcm)
            putExtra(ExportForegroundService.EXTRA_FILE_NAME, fileName)
            putExtra(ExportForegroundService.EXTRA_SAMPLE_RATE, st.sampleRate)
            putExtra(ExportForegroundService.EXTRA_FORMAT_CODE, formatCode)
            putExtra(ExportForegroundService.EXTRA_E_LEVEL, st.effectLevel)
            putExtra(ExportForegroundService.EXTRA_F_BACK, st.feedback)
            putExtra(ExportForegroundService.EXTRA_D_TIME_MS, st.delayTimeMs)
            putExtra(ExportForegroundService.EXTRA_MODE, st.mode.ordinalCode)
            putExtra(ExportForegroundService.EXTRA_PING_PONG, st.isPingPong)
            putExtra(ExportForegroundService.EXTRA_PING_PONG_WIDTH, st.pingPongWidth)
            putExtra(ExportForegroundService.EXTRA_ANTI_BOOM, st.antiBoomCut)
            putExtra(ExportForegroundService.EXTRA_BYPASSED, st.isBypassed)
            putExtra(ExportForegroundService.EXTRA_MAX_DURATION_SEC, maxSec)
        }
        context.startForegroundService(intent)
    }

    fun cancelExport(context: Context) {
        val intent = Intent(context, ExportForegroundService::class.java).apply {
            action = ExportForegroundService.ACTION_CANCEL_EXPORT
        }
        context.startService(intent)
    }

    private fun pushAllParamsToNative() {
        val st = _uiState.value
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_E_LEVEL, st.effectLevel)
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_F_BACK, st.feedback)
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_D_TIME_MS, st.delayTimeMs)
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_MODE, st.mode.ordinalCode.toFloat())
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_PING_PONG, if (st.isPingPong) 1f else 0f)
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_BYPASS, if (st.isBypassed) 1f else 0f)
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_PING_PONG_WIDTH, st.pingPongWidth)
        nativeBridge.setKnobParam(NativeAudioBridge.PARAM_ANTI_BOOM_CUT, if (st.antiBoomCut) 1f else 0f)
    }

    override fun onCleared() {
        nativeBridge.nativeRelease()
        super.onCleared()
    }
}
