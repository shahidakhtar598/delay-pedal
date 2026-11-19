package com.naatdelay.dd7.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class PedalColors(
    val bgChassis: Color,
    val surfaceCard: Color,
    val surfaceRecessed: Color,
    val stompPadBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentCobalt: Color,
    val accentPingPong: Color,
    val ledActiveRed: Color,
    val ledTempoGreen: Color,
    val statusWarning: Color,
    val borderSubtle: Color
)

val ClassicSilverColors = PedalColors(
    bgChassis       = Color(0xFFF2F4F8),
    surfaceCard     = Color(0xFFFFFFFF),
    surfaceRecessed = Color(0xFFE4E8F0),
    stompPadBg      = Color(0xFF1E2126),
    textPrimary     = Color(0xFF111418),
    textSecondary   = Color(0xFF5A6475),
    accentCobalt    = Color(0xFF1565C0),
    accentPingPong  = Color(0xFF0097A7),
    ledActiveRed    = Color(0xFFE53935),
    ledTempoGreen   = Color(0xFF00C853),
    statusWarning   = Color(0xFFEF6C00),
    borderSubtle    = Color(0xFFD5DCE6)
)

val DarkStudioColors = PedalColors(
    bgChassis       = Color(0xFF121418),
    surfaceCard     = Color(0xFF1C2026),
    surfaceRecessed = Color(0xFF0B0D10),
    stompPadBg      = Color(0xFF181A1F),
    textPrimary     = Color(0xFFF4F6F9),
    textSecondary   = Color(0xFF9AA4B2),
    accentCobalt    = Color(0xFF42A5F5),
    accentPingPong  = Color(0xFF00E5FF),
    ledActiveRed    = Color(0xFFFF5252),
    ledTempoGreen   = Color(0xFF00E676),
    statusWarning   = Color(0xFFFFB74D),
    borderSubtle    = Color(0xFF2C323C)
)

val LocalPedalColors = staticCompositionLocalOf { ClassicSilverColors }
