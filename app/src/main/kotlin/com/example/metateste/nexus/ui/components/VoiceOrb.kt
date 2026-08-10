package com.example.metateste.nexus.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metateste.nexus.ui.theme.JarvisCyan
import com.example.metateste.nexus.ui.theme.JarvisCyanDim
import com.example.metateste.nexus.ui.theme.JarvisWarning
import com.example.metateste.nexus.voice.VoiceRepository
import kotlin.math.min

enum class OrbState { IDLE, ACTIVE, PROCESSING }

/**
 * The Nexus HUD's reactive voice orb. Collects [VoiceRepository.audioEnergy] directly (not via
 * NexusUiState) — chunks arrive at ~20Hz and routing that through the shared uiState would
 * recompose more than this orb alone every time.
 */
@Composable
fun VoiceOrb(state: OrbState, modifier: Modifier = Modifier) {
    val rawEnergy by VoiceRepository.audioEnergy.collectAsStateWithLifecycle()
    val smoothedEnergy by animateFloatAsState(
        targetValue = if (state == OrbState.ACTIVE) rawEnergy else 0f,
        animationSpec = tween(durationMillis = 80, easing = LinearOutSlowInEasing),
        label = "orbEnergy",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orbInfinite")
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idlePulse",
    )
    val processingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "processingRotation",
    )

    val color = if (state == OrbState.PROCESSING) JarvisWarning else JarvisCyan
    val dimColor = if (state == OrbState.PROCESSING) JarvisWarning else JarvisCyanDim

    Canvas(modifier = modifier) {
        val baseRadius = min(size.width, size.height) / 2f * 0.55f
        val center = Offset(size.width / 2f, size.height / 2f)

        val radiusScale = when (state) {
            OrbState.IDLE -> idlePulse
            OrbState.ACTIVE -> 1f + smoothedEnergy * 0.35f
            OrbState.PROCESSING -> 1f
        }
        val glowAlpha = when (state) {
            OrbState.IDLE -> 0.2f + (idlePulse - 0.85f) / 0.2f * 0.2f
            OrbState.ACTIVE -> 0.25f + smoothedEnergy * 0.45f
            OrbState.PROCESSING -> 0.35f
        }
        val radius = baseRadius * radiusScale

        // Glow externo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = glowAlpha), color.copy(alpha = 0f)),
                center = center,
                radius = radius * 2.2f,
            ),
            radius = radius * 2.2f,
            center = center,
        )

        // Anel/borda — em PROCESSING, um arco girando em vez do anel parado (loading espacial)
        if (state == OrbState.PROCESSING) {
            drawCircle(color = dimColor.copy(alpha = 0.3f), radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
            drawArc(
                color = color,
                startAngle = processingRotation,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 4.dp.toPx()),
            )
        } else {
            drawCircle(color = color.copy(alpha = 0.7f), radius = radius, center = center, style = Stroke(width = 3.dp.toPx()))
        }

        // Núcleo central
        drawCircle(color = color, radius = radius * 0.35f, center = center)
    }
}
