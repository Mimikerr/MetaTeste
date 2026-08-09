package com.example.metateste.nexus.ui

import com.example.metateste.nexus.network.ConnectionState
import com.example.metateste.shared.CommandStatus

data class NexusUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val hostAddress: String = "",
    val hostPort: Int = 0,
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val lastRecognizedText: String? = null,
    val lastAckId: String? = null,
    val lastAckStatus: CommandStatus? = null,
    val lastAckDetail: String? = null,
    val voiceError: String? = null,
)
