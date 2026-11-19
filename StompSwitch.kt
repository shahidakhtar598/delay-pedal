package com.naatdelay.dd7.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naatdelay.dd7.ui.theme.LocalPedalColors
import com.naatdelay.dd7.ui.theme.PedalTypography

/**
 * Tactile rubberized stompbox footswitch pad:
 * - Single tap: toggles Delay Effect ON/OFF (with smooth trails)
 * - Long-press: enters Tap Tempo mode (CHECK LED turns emerald green; taps set D.TIME)
 */
@Composable
fun StompSwitch(
    isBypassed: Boolean,
    isTapTempoMode: Boolean,
    onTap: () -> Unit,
    onLongPressTapTempo: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 104.dp
) {
    val colors = LocalPedalColors.current
    val haptic = LocalHapticFeedback.current

    val ledColor = when {
        isTapTempoMode -> colors.ledTempoGreen
        !isBypassed -> colors.ledActiveRed
        else -> Color(0xFF454B54)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.stompPadBg)
            .border(1.dp, Color(0xFF2F3540), RoundedCornerShape(16.dp))
            .pointerInput(isTapTempoMode) {
                detectTapGestures(
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTap()
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPressTapTempo()
                    }
                )
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ledColor)
                )
                Text(
                    text = when {
                        isTapTempoMode -> "TAP TEMPO ACTIVE (TAP TO SET)"
                        !isBypassed -> "EFFECT ON · TRAILS ACTIVE"
                        else -> "BYPASSED (DRY VOCAL)"
                    },
                    style = PedalTypography.knobLegend,
                    color = Color(0xFFF4F6F9)
                )
            }
            Text(
                text = if (isTapTempoMode) "Hold again to exit Tap Tempo" else "Tap to Bypass · Hold for Tap Tempo",
                style = PedalTypography.captionMeta,
                color = Color(0xFF9AA4B2)
            )
        }
    }
}
