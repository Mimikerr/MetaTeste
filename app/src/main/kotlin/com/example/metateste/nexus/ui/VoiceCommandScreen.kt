package com.example.metateste.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metateste.nexus.ui.components.AckOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCommandScreen(viewModel: NexusViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comando de Voz") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    uiState.isListening -> Text("Ouvindo…", style = MaterialTheme.typography.bodyMedium)
                    uiState.isProcessing -> Text("Processando…", style = MaterialTheme.typography.bodyMedium)
                    else -> Text("Segure o botão Voltar do controle para falar", style = MaterialTheme.typography.bodyMedium)
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
                onTimeout = viewModel::dismissAck,
            )
        }
    }
}
