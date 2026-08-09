package com.example.metateste.host.voice

interface VoiceTranscriber {
    /** Transcribes mono PCM16LE audio at [sampleRateHz] into text. */
    fun transcribe(pcm16le: ByteArray, sampleRateHz: Int): Result<String>
}
