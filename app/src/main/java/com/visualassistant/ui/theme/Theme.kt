package com.visualassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// High-contrast color scheme for visual accessibility
private val HighContrastColors = darkColorScheme(
    primary = Color(0xFFFFD700),        // Bright gold - main action color
    onPrimary = Color(0xFF000000),      // Black text on gold
    secondary = Color(0xFF00FF00),      // Bright green - status/success
    onSecondary = Color(0xFF000000),
    error = Color(0xFFFF4444),          // Bright red - errors/hazards
    onError = Color(0xFFFFFFFF),
    background = Color(0xFF000000),     // Pure black background
    onBackground = Color(0xFFFFFFFF),   // Pure white text
    surface = Color(0xFF1A1A1A),        // Very dark gray surface
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFE0E0E0)
)

@Composable
fun VisualAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HighContrastColors,
        content = content
    )
}
