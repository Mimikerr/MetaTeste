package com.example.metateste.nexus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metateste.nexus.ui.components.AckOverlay
import com.example.metateste.nexus.ui.components.HudLogPanel
import com.example.metateste.nexus.ui.components.HudStatusBar
import com.example.metateste.nexus.ui.components.OrbState
import com.example.metateste.nexus.ui.components.VoiceOrb
import com.example.metateste.nexus.ui.theme.JarvisBackground
import com.example.metateste.nexus.ui.theme.JarvisOnSurfaceMuted

/** The Nexus HUD: live status bar, reactive voice orb, and conversation log — the app's home screen. */
@Composable
fun NexusHudScreen(
    viewModel: NexusViewModel,
    onNavigateToSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val orbState = when {
        uiState.isProcessing -> OrbState.PROCESSING
        uiState.isListening -> OrbState.ACTIVE
        else -> OrbState.IDLE
    }

    // Horizon OS composites 2D panels as opaque regardless of window/Compose alpha (confirmed on
    // a real Quest 3 — see plan notes), so this stays a solid fill rather than a misleading alpha.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HudStatusBar(
                connectionState = uiState.connectionState,
                autoSpeakEnabled = uiState.autoSpeakEnabled,
                latencyMs = uiState.latencyMs,
                onSettingsClick = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth(),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    VoiceOrb(state = orbState, modifier = Modifier.size(200.dp))
                    if (uiState.isSpeaking) {
                        Text(
                            "🔊 Falando…",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = JarvisOnSurfaceMuted,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }

            HudLogPanel(
                entries = uiState.hudLog,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
            )
        }

        AckOverlay(
            ackId = uiState.lastAckId,
            status = uiState.lastAckStatus,
            detail = uiState.lastAckDetail,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),
            onTimeout = viewModel::dismissAck,
        )
    }
}
