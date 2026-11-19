package com.naatdelay.dd7.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naatdelay.dd7.ui.theme.LocalPedalColors
import com.naatdelay.dd7.ui.theme.PedalTypography

@Composable
fun WaveformScrubber(
    trackName: String,
    peaks: List<Float>,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isLooping: Boolean,
    isLimiting: Boolean,
    onTogglePlay: () -> Unit,
    onToggleLoop: () -> Unit,
    onSeekMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPedalColors.current
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = trackName,
                style = PedalTypography.captionMeta,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${formatTimeMs(positionMs)} / ${formatTimeMs(durationMs)}",
                style = PedalTypography.monoReadout,
                color = if (isLimiting) colors.statusWarning else colors.textSecondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.accentCobalt)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .pointerInput(durationMs) {
                        detectTapGestures { offset ->
                            if (durationMs > 0L && size.width > 0) {
                                val pct = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onSeekMs((durationMs * pct).toLong())
                            }
                        }
                    }
            ) {
                val barList = if (peaks.isNotEmpty()) peaks else List(60) { 0.12f }
                val step = size.width / barList.size.toFloat()
                val midY = size.height / 2f
                val strokeW = (step * 0.65f).coerceAtLeast(2f)

                barList.forEachIndexed { idx, amp ->
                    val x = idx * step + step / 2f
                    val halfH = (amp.coerceIn(0.08f, 1f) * midY * 0.9f)
                    val played = (idx.toFloat() / barList.size.toFloat()) <= progress
                    drawLine(
                        color = if (played) colors.accentCobalt else colors.borderSubtle,
                        start = Offset(x, midY - halfH),
                        end = Offset(x, midY + halfH),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }

            IconButton(
                onClick = onToggleLoop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isLooping) colors.accentCobalt.copy(alpha = 0.14f) else colors.surfaceRecessed)
            ) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Loop",
                    tint = if (isLooping) colors.accentCobalt else colors.textSecondary
                )
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02d:%02d".format(min, sec)
}
