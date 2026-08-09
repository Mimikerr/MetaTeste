package com.example.metateste.nexus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metateste.shared.CommandStatus
import kotlinx.coroutines.delay

@Composable
fun NexusScreen(viewModel: NexusViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hostField by remember(uiState.hostAddress) { mutableStateOf(uiState.hostAddress) }
    var portField by remember(uiState.hostPort) { mutableStateOf(uiState.hostPort.toString()) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Nexus Command", style = MaterialTheme.typography.headlineSmall)
            Text("Status da conexão: ${uiState.connectionState}")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hostField,
                    onValueChange = { hostField = it },
                    label = { Text("IP do PC") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = portField,
                    onValueChange = { portField = it },
                    label = { Text("Porta") },
                    modifier = Modifier.weight(1f),
                )
            }
            Button(onClick = { viewModel.updateHostAddress(hostField, portField.toIntOrNull() ?: uiState.hostPort) }) {
                Text("Salvar endereço")
            }

            when {
                uiState.isListening -> Text("Ouvindo…", style = MaterialTheme.typography.bodyMedium)
                uiState.isProcessing -> Text("Processando…", style = MaterialTheme.typography.bodyMedium)
            }
            uiState.lastRecognizedText?.let { Text("Último comando reconhecido: $it") }
            uiState.voiceError?.let { Text("Erro de voz: $it") }
        }

        AckOverlay(
            ackId = uiState.lastAckId,
            status = uiState.lastAckStatus,
            detail = uiState.lastAckDetail,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
        )
    }
}

@Composable
private fun AckOverlay(
    ackId: String?,
    status: CommandStatus?,
    detail: String?,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(ackId) {
        if (ackId != null) {
            visible = true
            delay(2500)
            visible = false
        }
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val isSuccess = status == CommandStatus.SUCCESS
        Surface(
            color = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = if (isSuccess) "Comando executado" else "Falha ao executar${detail?.let { ": $it" } ?: ""}",
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
