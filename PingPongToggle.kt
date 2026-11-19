package com.naatdelay.dd7.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.naatdelay.dd7.ui.theme.LocalPedalColors
import com.naatdelay.dd7.ui.theme.PedalTypography
import kotlinx.coroutines.delay

@Composable
fun PingPongToggle(
    enabled: Boolean,
    delayTimeMs: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPedalColors.current
    val haptic = LocalHapticFeedback.current
    var leftActive by remember { mutableStateOf(true) }

    LaunchedEffect(enabled, delayTimeMs) {
        if (!enabled) return@LaunchedEffect
        val intervalMs = delayTimeMs.toLong().coerceIn(120L, 1200L)
        while (true) {
            delay(intervalMs)
            leftActive = !leftActive
        }
    }

    val bgColor by animateColorAsState(
        targetValue = if (enabled) colors.accentPingPong else colors.surfaceRecessed,
        label = "pingPongBg"
    )
    val contentColor = if (enabled) Color.White else colors.textSecondary

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, if (enabled) colors.accentPingPong else colors.borderSubtle, RoundedCornerShape(20.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (enabled && leftActive) Color.White else contentColor.copy(alpha = 0.35f))
        )
        Text(
            text = if (enabled) "PING-PONG STEREO: L <-> R" else "PING-PONG STEREO: OFF",
            style = PedalTypography.knobLegend,
            color = contentColor
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (enabled && !leftActive) Color.White else contentColor.copy(alpha = 0.35f))
        )
    }
}
