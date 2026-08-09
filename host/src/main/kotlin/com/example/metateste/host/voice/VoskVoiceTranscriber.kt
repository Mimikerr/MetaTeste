package com.example.metateste.host.voice

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import org.vosk.Model
import org.vosk.Recognizer

class VoskVoiceTranscriber private constructor(modelPath: String) : VoiceTranscriber {

    private val logger = LoggerFactory.getLogger(VoskVoiceTranscriber::class.java)
    private val model = Model(modelPath)

    override fun transcribe(pcm16le: ByteArray, sampleRateHz: Int): Result<String> = runCatching {
        Recognizer(model, sampleRateHz.toFloat()).use { recognizer ->
            recognizer.acceptWaveForm(pcm16le, pcm16le.size)
            val text = Json.parseToJsonElement(recognizer.finalResult)
                .jsonObject["text"]
                ?.jsonPrimitive
                ?.content
                ?.trim()
                .orEmpty()
            repairMisdecodedUtf8IfNeeded(text)
        }
    }.onFailure { logger.warn("transcription failed: {}", it.message) }

    /**
     * Vosk's JNI layer decodes its native (UTF-8) output using the JVM's platform default
     * charset, which on this Windows JVM is `Cp1252`/`sun.jnu.encoding` — not settable via
     * `-D` flags at runtime — so accented pt-BR text comes back mangled (e.g. "não" → "nÃ£o").
     * Re-encoding as Cp1252 and re-decoding as strict UTF-8 undoes exactly that single
     * mis-decode. Only applied when the result parses as valid UTF-8, so correctly-decoded
     * text (e.g. if this ever runs on a JVM/OS where the bug doesn't occur) passes through
     * untouched instead of being corrupted by a needless round-trip.
     */
    private fun repairMisdecodedUtf8IfNeeded(text: String): String {
        if (text.isEmpty()) return text
        val bytes = runCatching { text.toByteArray(CP1252) }.getOrNull() ?: return text
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }.getOrDefault(text)
    }

    companion object {
        private val CP1252 = Charset.forName("windows-1252")

        /**
         * Returns null instead of throwing when the model directory is missing, so the host can
         * still serve WebSocket/terminal-injection traffic even if voice hasn't been set up yet.
         */
        fun createIfModelPresent(modelPath: String): VoskVoiceTranscriber? {
            if (!File(modelPath).isDirectory) return null
            return VoskVoiceTranscriber(modelPath)
        }
    }
}
