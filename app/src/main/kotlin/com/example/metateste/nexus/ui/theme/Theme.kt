package com.example.metateste.nexus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NexusDarkColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = Color(0xFF00232A),
    primaryContainer = JarvisCyanDim,
    onPrimaryContainer = Color(0xFF001E24),
    secondary = JarvisBlue,
    onSecondary = Color.White,
    background = JarvisBackground,
    onBackground = JarvisOnBackground,
    surface = JarvisSurface,
    onSurface = JarvisOnBackground,
    surfaceVariant = JarvisSurfaceVariant,
    onSurfaceVariant = JarvisOnSurfaceMuted,
    outline = JarvisOutline,
    error = JarvisError,
    onError = Color.White,
)

/**
 * Fixed "Jarvis" dark identity — no dynamic color, no following the system theme, since the goal
 * is a consistent brand look rather than matching the user's wallpaper.
 */
@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexusDarkColorScheme,
        typography = NexusTypography,
        shapes = NexusShapes,
        content = content,
    )
}
