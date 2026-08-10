package com.example.metateste.nexus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: NexusViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hostField by remember(uiState.hostAddress) { mutableStateOf(uiState.hostAddress) }
    var portField by remember(uiState.hostPort) { mutableStateOf(uiState.hostPort.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Falar automaticamente")
                    Text(
                        text = if (uiState.autoSpeakEnabled) {
                            "O microfone fica sempre ouvindo e detecta sua fala sozinho"
                        } else {
                            "Padrão: segure o botão Voltar do controle para falar"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = uiState.autoSpeakEnabled,
                    onCheckedChange = viewModel::setAutoSpeakEnabled,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Confirmar antes de rodar comandos")
                    Text(
                        text = if (uiState.requireCommandConfirmation) {
                            "O assistente pergunta antes de executar um comando de terminal"
                        } else {
                            "Desligado: comandos do assistente rodam sem perguntar. Recomendado manter ligado."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = uiState.requireCommandConfirmation,
                    onCheckedChange = viewModel::setRequireCommandConfirmation,
                )
            }
        }
    }
}
