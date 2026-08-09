package com.example.metateste.nexus.voice

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState

    /** A complete utterance, ready to send to the host for transcription. */
    class FinalAudio(val pcm16le: ByteArray, val sampleRateHz: Int) : VoiceState

    data class Error(val message: String) : VoiceState
}
