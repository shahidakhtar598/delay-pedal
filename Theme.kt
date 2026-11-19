package com.naatdelay.dd7.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun DD7PedalTheme(
    darkStudio: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkStudio) DarkStudioColors else ClassicSilverColors
    val m3Scheme = if (darkStudio) {
        darkColorScheme(
            primary = palette.accentCobalt,
            secondary = palette.accentPingPong,
            background = palette.bgChassis,
            surface = palette.surfaceCard,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary
        )
    } else {
        lightColorScheme(
            primary = palette.accentCobalt,
            secondary = palette.accentPingPong,
            background = palette.bgChassis,
            surface = palette.surfaceCard,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary
        )
    }

    CompositionLocalProvider(LocalPedalColors provides palette) {
        MaterialTheme(colorScheme = m3Scheme, content = content)
    }
}
