package com.example.metateste.nexus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metateste.nexus.network.ConnectionState
import com.example.metateste.nexus.ui.theme.JarvisOnSurfaceMuted
import com.example.metateste.nexus.ui.theme.NexusStatusColors

/** Minimal top-of-HUD telemetry bar: connection dot, capture mode chip, heartbeat latency, Settings shortcut. */
@Composable
fun HudStatusBar(
    connectionState: ConnectionState,
    autoSpeakEnabled: Boolean,
    latencyMs: Long?,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (dotColor, connectionLabel) = when (connectionState) {
        ConnectionState.CONNECTED -> NexusStatusColors.success to "Conectado"
        ConnectionState.CONNECTING -> NexusStatusColors.warning to "Conectando"
        ConnectionState.RECONNECTING -> NexusStatusColors.warning to "Reconectando"
        ConnectionState.DISCONNECTED -> NexusStatusColors.error to "Offline"
    }
    val modeLabel = if (autoSpeakEnabled) "Mãos-Livres" else "Push-to-Talk"
    val pingLabel = if (latencyMs != null) "Ping: ${latencyMs}ms" else "Ping: --"

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = dotColor, shape = CircleShape),
        )
        Text(connectionLabel, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = JarvisOnSurfaceMuted)

        Spacer(modifier = Modifier.weight(1f))

        Text(modeLabel, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = JarvisOnSurfaceMuted)
        Text(pingLabel, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = JarvisOnSurfaceMuted)

        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Filled.Settings, contentDescription = "Configurações", tint = JarvisOnSurfaceMuted)
        }
    }
}
