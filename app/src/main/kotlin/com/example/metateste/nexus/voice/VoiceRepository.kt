package com.example.metateste.nexus.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bridges [VoiceCaptureService]'s capture loop to the UI layer, which has no Service binding. */
object VoiceRepository {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    fun publishListening() {
        _state.value = VoiceState.Listening
    }

    fun publishFinalAudio(pcm16le: ByteArray, sampleRateHz: Int) {
        _state.value = VoiceState.FinalAudio(pcm16le, sampleRateHz)
    }

    fun publishError(message: String) {
        _state.value = VoiceState.Error(message)
    }
}
