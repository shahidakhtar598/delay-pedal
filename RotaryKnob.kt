package com.naatdelay.dd7.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naatdelay.dd7.ui.theme.LocalPedalColors
import com.naatdelay.dd7.ui.theme.PedalTypography
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tactile brushed-metal rotary stompbox knob:
 * - 270 deg sweep (-135 deg to +135 deg)
 * - Vertical drag physics (200dp drag = full 0%..100% sweep)
 * - Double-tap resets to Naat-safe default
 * - Floating dark live readout pill appears 12dp above knob while dragging
 */
@Composable
fun RotaryKnob(
    label: String,
    value: Float, // 0f..1f
    defaultValue: Float,
    readoutText: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    knobSize: Dp = 64.dp,
    discreteSteps: Int? = null
) {
    val colors = LocalPedalColors.current
    val haptic = LocalHapticFeedback.current
    val dragRangePx = with(LocalDensity.current) { 200.dp.toPx() }

    var isDragging by remember { mutableStateOf(false) }
    var lastDetent by remember { mutableStateOf(-1) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Floating live readout pill while dragging
            if (isDragging) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-34).dp)
                        .background(Color(0xFF111418), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = readoutText,
                        style = PedalTypography.monoReadout,
                        color = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(knobSize + 8.dp) // 72dp touch hitbox
                    .pointerInput(defaultValue) {
                        detectTapGestures(
                            onDoubleTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onValueChange(defaultValue)
                            }
                        )
                    }
                    .pointerInput(value, discreteSteps) {
                        var currentVal = value
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                currentVal = value
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onVerticalDrag = { _, dragAmount ->
                                currentVal = (currentVal - dragAmount / dragRangePx).coerceIn(0f, 1f)
                                if (discreteSteps != null && discreteSteps > 1) {
                                    val idx = (currentVal * (discreteSteps - 1)).toInt()
                                        .coerceIn(0, discreteSteps - 1)
                                    if (idx != lastDetent) {
                                        lastDetent = idx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onValueChange(idx.toFloat() / (discreteSteps - 1).toFloat())
                                } else {
                                    onValueChange(currentVal)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(knobSize)) {
                    val strokeW = 3.dp.toPx()
                    val pad = strokeW + 2.dp.toPx()
                    val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
                    val topLeft = Offset(pad, pad)

                    // Outer recessed track arc (270 deg starting at 135 deg)
                    drawArc(
                        color = colors.surfaceRecessed,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    // Active Cobalt progress arc
                    drawArc(
                        color = colors.accentCobalt,
                        startAngle = 135f,
                        sweepAngle = 270f * value.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    // Brushed metallic dial body
                    val radius = (size.minDimension / 2f) - pad - 3.dp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF2D333D), Color(0xFF15181E))
                        ),
                        radius = radius,
                        center = center
                    )

                    // Crisp white pointer notch
                    val angleDeg = 135f + 270f * value.coerceIn(0f, 1f)
                    val rad = angleDeg * (PI / 180.0)
                    val innerR = radius * 0.28f
                    val outerR = radius * 0.85f
                    drawLine(
                        color = Color.White,
                        start = Offset(
                            center.x + (innerR * cos(rad)).toFloat(),
                            center.y + (innerR * sin(rad)).toFloat()
                        ),
                        end = Offset(
                            center.x + (outerR * cos(rad)).toFloat(),
                            center.y + (outerR * sin(rad)).toFloat()
                        ),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = PedalTypography.knobLegend,
            color = colors.textPrimary
        )
        Text(
            text = readoutText,
            style = PedalTypography.captionMeta,
            color = colors.textSecondary
        )
    }
}
