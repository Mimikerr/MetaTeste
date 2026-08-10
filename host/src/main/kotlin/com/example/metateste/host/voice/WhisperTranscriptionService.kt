package com.example.metateste.host.voice

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Transcreve chamando o microserviço Python local (host/whisper-service/whisper_server.py),
 * que roda faster-whisper em CPU.
 */
class WhisperTranscriptionService(
    private val url: String = System.getenv("NEXUS_WHISPER_URL") ?: "http://127.0.0.1:8000/transcribe",
) : VoiceTranscriber {

    private val logger = LoggerFactory.getLogger(WhisperTranscriptionService::class.java)
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

    override fun transcribe(pcm16le: ByteArray, sampleRateHz: Int): Result<String> = runCatching {
        runBlocking {
            val response = client.submitFormWithBinaryData(
                url = url,
                formData = formData {
                    append(
                        "file",
                        pcm16le,
                        Headers.build {
                            append(HttpHeaders.ContentType, "application/octet-stream")
                            append(HttpHeaders.ContentDisposition, "filename=\"audio.pcm\"")
                        },
                    )
                },
            )
            json.decodeFromString<WhisperResponse>(response.bodyAsText()).text.trim()
        }
    }.onFailure {
        logger.warn("whisper-service não respondeu em {} (o processo Python está rodando? veja host/whisper-service/README.md): {}", url, it.message)
    }

    @Serializable
    private data class WhisperResponse(val text: String)
}
