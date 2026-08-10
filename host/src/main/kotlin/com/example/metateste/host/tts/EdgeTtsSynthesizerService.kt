package com.example.metateste.host.tts

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Synthesizes speech by calling the local edge-tts microservice (host/tts-service/tts_server.py),
 * which decodes edge-tts's MP3 output down to raw PCM16LE.
 */
class EdgeTtsSynthesizerService(
    private val url: String = System.getenv("NEXUS_TTS_URL") ?: "http://127.0.0.1:8001/synthesize",
    private val voice: String = System.getenv("NEXUS_TTS_VOICE") ?: "pt-BR-FranciscaNeural",
) : TextToSpeechSynthesizer {

    private val logger = LoggerFactory.getLogger(EdgeTtsSynthesizerService::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(HttpTimeout) {
            connectTimeoutMillis = 3_000
            // socketTimeoutMillis must be set explicitly too — otherwise the OkHttp engine falls
            // back to its own default 10s read timeout regardless of requestTimeoutMillis.
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
    }

    override fun synthesize(text: String, sampleRateHz: Int): Result<ByteArray> = runCatching {
        runBlocking {
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(SynthesizeRequest.serializer(), SynthesizeRequest(text, voice, sampleRateHz)))
            }.bodyAsBytes()
        }
    }.onFailure {
        logger.warn("tts-service não respondeu em {} (o processo Python está rodando? veja host/tts-service/README.md): {}", url, it.message)
    }

    @Serializable
    private data class SynthesizeRequest(
        val text: String,
        val voice: String,
        @SerialName("sample_rate_hz") val sampleRateHz: Int,
    )
}
