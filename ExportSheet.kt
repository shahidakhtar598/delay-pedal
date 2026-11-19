package com.naatdelay.dd7.ui.export

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.naatdelay.dd7.audio.ExportForegroundService
import com.naatdelay.dd7.audio.ExportServiceState
import com.naatdelay.dd7.audio.NativeAudioBridge
import com.naatdelay.dd7.ui.theme.LocalPedalColors
import com.naatdelay.dd7.ui.theme.PedalTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    defaultFileName: String,
    hasAudioLoaded: Boolean,
    isProActivated: Boolean,
    exportState: ExportServiceState,
    onStartExport: (String, Int) -> Unit,
    onCancelExport: () -> Unit,
    onOpenActivation: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalPedalColors.current
    val context = LocalContext.current
    var fileName by remember(defaultFileName) { mutableStateOf(defaultFileName) }
    var selectedFormat by remember { mutableIntStateOf(NativeAudioBridge.FORMAT_WAV_16) }

    val formats = listOf(
        NativeAudioBridge.FORMAT_MP3_192 to "MP3 192k",
        NativeAudioBridge.FORMAT_MP3_320 to "MP3 320k",
        NativeAudioBridge.FORMAT_WAV_16 to "WAV 16-bit"
    )

    ModalBottomSheet(
        onDismissRequest = {
            ExportForegroundService.resetState()
            onDismiss()
        },
        containerColor = colors.surfaceCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Export Studio Audio", style = PedalTypography.headingSheet, color = colors.textPrimary)

            if (!isProActivated) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.statusWarning.copy(alpha = 0.14f))
                        .clickable(onClick = onOpenActivation)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Trial Mode: exports first 30 sec. Tap to unlock full Naat export.",
                        style = PedalTypography.captionMeta,
                        color = colors.statusWarning
                    )
                }
            }

            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("Output Filename") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Segmented Format Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceRecessed)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                formats.forEach { (code, label) ->
                    val active = selectedFormat == code
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) colors.surfaceCard else Color.Transparent)
                            .clickable { selectedFormat = code },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = PedalTypography.captionMeta,
                            color = if (active) colors.accentCobalt else colors.textSecondary
                        )
                    }
                }
            }

            when (exportState) {
                is ExportServiceState.Running -> {
                    LinearProgressIndicator(
                        progress = { exportState.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.accentCobalt
                    )
                    Text(
                        "Rendering offline + echo tail... ${exportState.progressPercent}%",
                        style = PedalTypography.monoReadout,
                        color = colors.textSecondary
                    )
                    OutlinedButton(onClick = onCancelExport, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel Export", color = colors.ledActiveRed)
                    }
                }

                is ExportServiceState.Completed -> {
                    Text(
                        "Saved to Music/DD7_Vocal_Exports/${exportState.fileName} (echo tail +${exportState.tailMs} ms)",
                        style = PedalTypography.bodyPrimary,
                        color = colors.ledTempoGreen
                    )
                    Button(
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = if (exportState.fileName.endsWith(".wav")) "audio/wav" else "audio/mpeg"
                                putExtra(Intent.EXTRA_STREAM, exportState.outputUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Share Naat Audio"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentCobalt),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Share to WhatsApp / Social")
                    }
                }

                is ExportServiceState.Failed -> {
                    Text(exportState.errorMessage, style = PedalTypography.bodyPrimary, color = colors.ledActiveRed)
                    Button(
                        onClick = { onStartExport(fileName.ifBlank { "Naat_DD7X" }, selectedFormat) },
                        enabled = hasAudioLoaded,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentCobalt),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Retry Export")
                    }
                }

                ExportServiceState.Idle -> {
                    Button(
                        onClick = { onStartExport(fileName.ifBlank { "Naat_DD7X" }, selectedFormat) },
                        enabled = hasAudioLoaded,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentCobalt),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(if (hasAudioLoaded) "Export with Auto Echo Tail" else "Import an Audio File First")
                    }
                }
            }
        }
    }
}
