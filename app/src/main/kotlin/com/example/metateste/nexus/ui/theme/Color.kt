package com.example.metateste.nexus.ui.theme

import androidx.compose.ui.graphics.Color

val JarvisBackground = Color(0xFF05080D)
val JarvisSurface = Color(0xFF10151F)
val JarvisSurfaceVariant = Color(0xFF1B2432)
val JarvisOutline = Color(0xFF2A3646)

val JarvisCyan = Color(0xFF29F1FF)
val JarvisCyanDim = Color(0xFF0EA5B8)
val JarvisBlue = Color(0xFF3D7BFF)

val JarvisOnBackground = Color(0xFFE4F3F7)
val JarvisOnSurfaceMuted = Color(0xFF8FA3B0)

val JarvisSuccess = Color(0xFF19E38B)
val JarvisWarning = Color(0xFFFFB300)
val JarvisError = Color(0xFFFF4D5E)

/** Semantic status colors the Material3 ColorScheme has no built-in slot for (e.g. "warning"). */
object NexusStatusColors {
    val success = JarvisSuccess
    val warning = JarvisWarning
    val error = JarvisError
}
