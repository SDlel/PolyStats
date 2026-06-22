package com.polystats.android.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PolyStatsColors = darkColorScheme(
    primary = Color(0xFF6C4DFF),
    secondary = Color(0xFF22C55E),
    tertiary = Color(0xFF38BDF8),
    error = Color(0xFFEF4444),
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF111114),
    surfaceVariant = Color(0xFF1B1B22),
    onPrimary = Color.White,
    onSecondary = Color(0xFF03130A),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFC9CAD3)
)

@Composable
fun PolyStatsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PolyStatsColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
