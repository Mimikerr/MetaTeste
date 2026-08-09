package com.example.metateste.host.voice

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import org.slf4j.LoggerFactory

/**
 * Transcribes via Meta's Wit.ai speech API (https://api.wit.ai/speech) instead of a local model —
 * a free alternative to Vosk with noticeably better pt-BR accuracy in practice.
 */
@OptIn(ExperimentalSerializationApi::class)
class WitAiVoiceTranscriber(private val accessToken: String) : VoiceTranscriber {

    private val logger = LoggerFactory.getLogger(WitAiVoiceTranscriber::class.java)
    private val client = HttpClient(OkHttp)
    private val json = Json { ignoreUnknownKeys = true }

    override fun transcribe(pcm16le: ByteArray, sampleRateHz: Int): Result<String> = runCatching {
        runBlocking {
            val wav = encodeWav(pcm16le, sampleRateHz)
            val response = client.post("https://api.wit.ai/speech?v=$API_VERSION") {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType("audio", "wav"))
                setBody(wav)
            }
            val responseBytes: ByteArray = response.body()
            val finalText = json
                .decodeToSequence<WitSpeechEvent>(ByteArrayInputStream(responseBytes), DecodeSequenceMode.WHITESPACE_SEPARATED)
                .lastOrNull { it.type == "FINAL_TRANSCRIPTION" }
                ?.text
            finalText?.trim().orEmpty()
        }
    }.onFailure { logger.warn("Wit.ai transcription failed: {}", it.message) }

    @Serializable
    private data class WitSpeechEvent(
        val type: String? = null,
        val text: String? = null,
    )

    companion object {
        private const val API_VERSION = "20260808"
    }
}
