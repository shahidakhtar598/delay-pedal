package com.naatdelay.dd7.ui.pedal

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.naatdelay.dd7.ui.activation.ActivationSheet
import com.naatdelay.dd7.ui.components.PingPongToggle
import com.naatdelay.dd7.ui.components.RotaryKnob
import com.naatdelay.dd7.ui.components.StompSwitch
import com.naatdelay.dd7.ui.components.WaveformScrubber
import com.naatdelay.dd7.ui.export.ExportSheet
import com.naatdelay.dd7.ui.presets.PresetDrawer
import com.naatdelay.dd7.ui.theme.LocalPedalColors
import com.naatdelay.dd7.ui.theme.PedalTypography

@Composable
fun PedalScreen(viewModel: PedalViewModel) {
    val state by viewModel.uiState.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val license by viewModel.licenseStatus.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    val colors = LocalPedalColors.current
    val context = LocalContext.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val knobSize = if (screenHeightDp < 700) 56.dp else 64.dp
    val stompHeight = if (screenHeightDp < 700) 88.dp else 108.dp

    var showPresets by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showActivation by remember { mutableStateOf(false) }

    val openAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.importAudio(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgChassis)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top Utility Bar (56dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.clickable { viewModel.toggleTheme() }) {
                Text("DD-7X VOCAL", style = PedalTypography.displayBrand, color = colors.textPrimary)
                Text(
                    text = if (license.isProActivated) "PRO ACTIVATED" else "TRIAL MODE · TAP TO UNLOCK",
                    style = PedalTypography.knobLegend,
                    color = if (license.isProActivated) colors.ledTempoGreen else colors.statusWarning,
                    modifier = Modifier.clickable { showActivation = true }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopPillButton("Import", colors.surfaceRecessed, colors.textPrimary) {
                    openAudioLauncher.launch(
                        arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/aac", "audio/ogg", "audio/opus")
                    )
                }
                TopPillButton("Presets", colors.surfaceRecessed, colors.textPrimary) {
                    showPresets = true
                }
                TopPillButton("Export", colors.accentCobalt, Color.White) {
                    showExport = true
                }
            }
        }

        // Optional status / error toast banner
        state.statusBanner?.let { msg ->
            Text(
                text = msg,
                style = PedalTypography.captionMeta,
                color = colors.statusWarning,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.statusWarning.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        // 2. Waveform & Transport Card (92dp)
        WaveformScrubber(
            trackName = if (state.isImporting) "Decoding audio..." else state.trackName,
            peaks = state.waveformPeaks,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            isPlaying = state.isPlaying,
            isLooping = state.isLooping,
            isLimiting = state.isLimiting,
            onTogglePlay = viewModel::togglePlayback,
            onToggleLoop = viewModel::toggleLoop,
            onSeekMs = viewModel::seekTo
        )

        // 3. 2x2 Rotary Pedal Knob Deck + Ping-Pong Toggle
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RotaryKnob(
                    label = "E.LEVEL",
                    value = state.effectLevel,
                    defaultValue = 0.48f,
                    readoutText = "${(state.effectLevel * 100).toInt()}%",
                    onValueChange = viewModel::setEffectLevel,
                    knobSize = knobSize
                )
                RotaryKnob(
                    label = "F.BACK",
                    value = state.feedback / 0.95f,
                    defaultValue = 0.38f / 0.95f,
                    readoutText = "${(state.feedback * 100).toInt()}%",
                    onValueChange = { viewModel.setFeedback(it * 0.95f) },
                    knobSize = knobSize
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val timeNorm = ((state.delayTimeMs - 1f) / (state.mode.maxMs - 1f)).coerceIn(0f, 1f)
                RotaryKnob(
                    label = "D.TIME",
                    value = timeNorm,
                    defaultValue = (420f / state.mode.maxMs).coerceIn(0f, 1f),
                    readoutText = "${state.delayTimeMs.toInt()} ms",
                    onValueChange = viewModel::setDelayTimeNormalized,
                    knobSize = knobSize
                )
                RotaryKnob(
                    label = "MODE",
                    value = state.mode.ordinalCode.toFloat() / 5f,
                    defaultValue = UiDelayMode.ANALOG.ordinalCode.toFloat() / 5f,
                    readoutText = state.mode.label,
                    onValueChange = { norm ->
                        val idx = (norm * 5f).toInt().coerceIn(0, 5)
                        viewModel.setMode(UiDelayMode.entries[idx])
                    },
                    knobSize = knobSize,
                    discreteSteps = 6
                )
            }

            PingPongToggle(
                enabled = state.isPingPong,
                delayTimeMs = state.delayTimeMs,
                onToggle = viewModel::togglePingPong
            )
        }

        // 4. Stompbox Footswitch Zone
        StompSwitch(
            isBypassed = state.isBypassed,
            isTapTempoMode = state.isTapTempoMode,
            onTap = viewModel::onStompTap,
            onLongPressTapTempo = viewModel::onStompLongPress,
            height = stompHeight
        )
    }

    if (showPresets) {
        PresetDrawer(
            presets = presets,
            activePresetId = state.activePresetId,
            onSelect = {
                viewModel.applyPreset(it)
                showPresets = false
            },
            onDelete = viewModel::deleteCustomPreset,
            onSaveNew = { name, overwrite -> viewModel.saveCustomPreset(name, overwrite) },
            onDismiss = { showPresets = false }
        )
    }

    if (showExport) {
        ExportSheet(
            defaultFileName = state.trackName.substringBeforeLast('.').ifBlank { "Naat_DD7X" } + "_DD7X",
            hasAudioLoaded = state.cachedPcmPath != null,
            isProActivated = license.isProActivated,
            exportState = exportState,
            onStartExport = { name, fmt -> viewModel.startExport(context, name, fmt) },
            onCancelExport = { viewModel.cancelExport(context) },
            onOpenActivation = {
                showExport = false
                showActivation = true
            },
            onDismiss = { showExport = false }
        )
    }

    if (showActivation) {
        ActivationSheet(
            licenseRepo = viewModel.licenseRepo,
            licenseStatus = license,
            onDismiss = { showActivation = false }
        )
    }
}

@Composable
private fun TopPillButton(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, LocalPedalColors.current.borderSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = PedalTypography.bodyPrimary, color = fg)
    }
}
