package com.example.metateste.nexus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metateste.nexus.haptics.HapticFeedback
import com.example.metateste.nexus.network.NexusWebSocketClient
import com.example.metateste.nexus.settings.HostAddressStore
import com.example.metateste.nexus.voice.VoiceRepository
import com.example.metateste.nexus.voice.VoiceState
import com.example.metateste.shared.CommandAck
import com.example.metateste.shared.CommandStatus
import com.example.metateste.shared.VoiceAudio
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NexusViewModel(application: Application) : AndroidViewModel(application) {

    private val hostAddressStore = HostAddressStore(application)
    private val haptics = HapticFeedback(application)

    /** Set to the messageId of the VoiceAudio currently awaiting a reply; acks for stale/unrelated ids are ignored. */
    private var pendingCommandId: String? = null

    private val client = NexusWebSocketClient(
        deviceId = android.provider.Settings.Secure.getString(
            application.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        ) ?: "quest-unknown",
        hostProvider = { hostAddressStore.host },
        portProvider = { hostAddressStore.port },
    )

    private val _uiState = MutableStateFlow(
        NexusUiState(hostAddress = hostAddressStore.host, hostPort = hostAddressStore.port),
    )
    val uiState: StateFlow<NexusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { client.run() }
        viewModelScope.launch {
            client.connectionState.collect { state -> _uiState.update { it.copy(connectionState = state) } }
        }
        viewModelScope.launch {
            client.incoming.collect { message ->
                if (message is CommandAck && message.correlatesTo == pendingCommandId) {
                    pendingCommandId = null
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            lastAckId = message.messageId,
                            lastAckStatus = message.status,
                            lastAckDetail = message.detail,
                            lastRecognizedText = message.recognizedText ?: it.lastRecognizedText,
                        )
                    }
                    if (message.status == CommandStatus.SUCCESS) haptics.success() else haptics.failure()
                }
            }
        }
        viewModelScope.launch {
            VoiceRepository.state.collect { state ->
                when (state) {
                    VoiceState.Listening -> _uiState.update { it.copy(isListening = true, voiceError = null) }
                    is VoiceState.FinalAudio -> {
                        val messageId = UUID.randomUUID().toString()
                        pendingCommandId = messageId
                        _uiState.update { it.copy(isListening = false, isProcessing = true) }
                        client.send(
                            VoiceAudio(
                                messageId = messageId,
                                timestamp = System.currentTimeMillis(),
                                audioBase64 = android.util.Base64.encodeToString(state.pcm16le, android.util.Base64.NO_WRAP),
                                sampleRateHz = state.sampleRateHz,
                            ),
                        )
                    }
                    is VoiceState.Error -> _uiState.update { it.copy(isListening = false, voiceError = state.message) }
                    VoiceState.Idle -> Unit
                }
            }
        }
    }

    fun updateHostAddress(host: String, port: Int) {
        hostAddressStore.host = host
        hostAddressStore.port = port
        _uiState.update { it.copy(hostAddress = host, hostPort = port) }
    }
}
