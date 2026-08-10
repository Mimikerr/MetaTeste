package com.example.metateste.host.tts

interface TextToSpeechSynthesizer {
    /** Synthesizes [text] into mono PCM16LE audio at [sampleRateHz]. */
    fun synthesize(text: String, sampleRateHz: Int = 24000): Result<ByteArray>
}
