package com.example.metateste.nexus.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metateste.nexus.haptics.HapticFeedback
import com.example.metateste.nexus.network.ConnectionState
import com.example.metateste.nexus.network.NexusWebSocketClient
import com.example.metateste.nexus.settings.AssistantSettingsStore
import com.example.metateste.nexus.settings.HostAddressStore
import com.example.metateste.nexus.settings.VoiceModeStore
import com.example.metateste.nexus.tts.TtsPlayer
import com.example.metateste.nexus.voice.VoiceCaptureService
import com.example.metateste.nexus.voice.VoiceRepository
import com.example.metateste.nexus.voice.VoiceState
import com.example.metateste.shared.AssistantReply
import com.example.metateste.shared.ClientSettings
import com.example.metateste.shared.CommandAck
import com.example.metateste.shared.CommandStatus
import com.example.metateste.shared.TtsAudio
import com.example.metateste.shared.VoiceAudio
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NexusViewModel(application: Application) : AndroidViewModel(application) {

    private val hostAddressStore = HostAddressStore(application)
    private val voiceModeStore = VoiceModeStore(application)
    private val assistantSettingsStore = AssistantSettingsStore(application)
    private val haptics = HapticFeedback(application)
    private val ttsPlayer = TtsPlayer()

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
        NexusUiState(
            hostAddress = hostAddressStore.host,
            hostPort = hostAddressStore.port,
            autoSpeakEnabled = voiceModeStore.autoSpeakEnabled,
            requireCommandConfirmation = assistantSettingsStore.requireCommandConfirmation,
        ),
    )
    val uiState: StateFlow<NexusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { client.run() }
        viewModelScope.launch {
            client.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state == ConnectionState.CONNECTED) sendClientSettings()
            }
        }
        viewModelScope.launch {
            client.latencyMs.collect { ms -> _uiState.update { it.copy(latencyMs = ms) } }
        }
        viewModelScope.launch {
            client.incoming.collect { message ->
                when (message) {
                    is CommandAck -> handleCommandAck(message)
                    is AssistantReply -> handleAssistantReply(message)
                    is TtsAudio -> playTtsAudio(message)
                    else -> Unit
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
                        _uiState.update { it.copy(isListening = false, isProcessing = true, awaitingConfirmation = false) }
                        client.send(
                            VoiceAudio(
                                messageId = messageId,
                                timestamp = System.currentTimeMillis(),
                                audioBase64 = android.util.Base64.encodeToString(state.pcm16le, android.util.Base64.NO_WRAP),
                                sampleRateHz = state.sampleRateHz,
                            ),
                        )
                    }
                    is VoiceState.Error -> {
                        appendLog(HudLogEntry.SystemNotice(id = UUID.randomUUID().toString(), timestamp = System.currentTimeMillis(), text = state.message))
                        _uiState.update { it.copy(isListening = false, voiceError = state.message) }
                    }
                    VoiceState.Idle -> Unit
                }
            }
        }
    }

    private fun handleCommandAck(message: CommandAck) {
        if (message.correlatesTo != pendingCommandId) return
        pendingCommandId = null
        message.recognizedText?.let {
            appendLog(HudLogEntry.UserUtterance(id = UUID.randomUUID().toString(), timestamp = System.currentTimeMillis(), text = it))
        }
        appendLog(
            HudLogEntry.AssistantResponse(
                id = message.messageId,
                timestamp = System.currentTimeMillis(),
                text = message.detail ?: if (message.status == CommandStatus.SUCCESS) "Comando executado" else "Falha ao executar",
                status = message.status,
            ),
        )
        _uiState.update {
            it.copy(
                isProcessing = false,
                awaitingConfirmation = false,
                lastAckId = message.messageId,
                lastAckStatus = message.status,
                lastAckDetail = message.detail,
                lastRecognizedText = message.recognizedText ?: it.lastRecognizedText,
            )
        }
        if (message.status == CommandStatus.SUCCESS) haptics.success() else haptics.failure()
    }

    /**
     * Not gated on [pendingCommandId]: an answer with no follow-up command never gets a CommandAck,
     * so this is what clears the "thinking" spinner for that case (awaitingConfirmation keeps it
     * up while a yes/no is pending; a CommandAck resolves the flow once the user replies).
     */
    private fun handleAssistantReply(message: AssistantReply) {
        appendLog(
            HudLogEntry.AssistantResponse(
                id = message.messageId,
                timestamp = System.currentTimeMillis(),
                text = message.text,
                status = null,
            ),
        )
        _uiState.update {
            it.copy(
                assistantReplyText = message.text,
                awaitingConfirmation = message.awaitingConfirmation,
                isProcessing = message.awaitingConfirmation,
            )
        }
    }

    private fun appendLog(entry: HudLogEntry) {
        _uiState.update { it.copy(hudLog = (it.hudLog + entry).takeLast(MAX_LOG_ENTRIES)) }
    }

    private suspend fun playTtsAudio(message: TtsAudio) {
        val bytes = runCatching { android.util.Base64.decode(message.audioBase64, android.util.Base64.NO_WRAP) }.getOrNull() ?: return
        _uiState.update { it.copy(isSpeaking = true) }
        withContext(Dispatchers.IO) { ttsPlayer.play(bytes, message.sampleRateHz) }
        _uiState.update { it.copy(isSpeaking = false) }
    }

    private suspend fun sendClientSettings() {
        client.send(
            ClientSettings(
                messageId = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                requireCommandConfirmation = assistantSettingsStore.requireCommandConfirmation,
            ),
        )
    }

    fun updateHostAddress(host: String, port: Int) {
        hostAddressStore.host = host
        hostAddressStore.port = port
        _uiState.update { it.copy(hostAddress = host, hostPort = port) }
    }

    fun setAutoSpeakEnabled(enabled: Boolean) {
        voiceModeStore.autoSpeakEnabled = enabled
        _uiState.update { it.copy(autoSpeakEnabled = enabled) }
        val app = getApplication<Application>()
        app.startService(
            Intent(app, VoiceCaptureService::class.java)
                .setAction(VoiceCaptureService.ACTION_SET_AUTO_MODE)
                .putExtra(VoiceCaptureService.EXTRA_AUTO_MODE, enabled),
        )
    }

    fun setRequireCommandConfirmation(enabled: Boolean) {
        assistantSettingsStore.requireCommandConfirmation = enabled
        _uiState.update { it.copy(requireCommandConfirmation = enabled) }
        viewModelScope.launch { sendClientSettings() }
    }

    fun dismissAck() {
        _uiState.update { it.copy(lastAckId = null, lastAckStatus = null, lastAckDetail = null) }
    }

    private companion object {
        const val MAX_LOG_ENTRIES = 100
    }
}
