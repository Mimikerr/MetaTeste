package com.example.metateste.host.session

import com.example.metateste.host.automation.TerminalInjector
import com.example.metateste.host.voice.VoiceTranscriber
import com.example.metateste.shared.CommandAck
import com.example.metateste.shared.CommandStatus
import com.example.metateste.shared.Heartbeat
import com.example.metateste.shared.HeartbeatAck
import com.example.metateste.shared.Hello
import com.example.metateste.shared.HelloAck
import com.example.metateste.shared.NexusMessage
import com.example.metateste.shared.VoiceAudio
import com.example.metateste.shared.VoiceCommand
import com.example.metateste.shared.decodeNexusMessage
import com.example.metateste.shared.encode
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Handles a single Quest<->host WebSocket connection.
 * MVP assumes one client connected at a time.
 */
class NexusSession(
    private val session: DefaultWebSocketServerSession,
    private val terminalInjector: TerminalInjector,
    private val voiceTranscriber: VoiceTranscriber?,
) {

    private val logger = LoggerFactory.getLogger(NexusSession::class.java)

    suspend fun run() {
        logger.info("Quest connected")
        try {
            session.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val message = runCatching { frame.readText().decodeNexusMessage() }.getOrNull()
                    if (message != null) {
                        logger.info("received {}", if (message is VoiceAudio) "VoiceAudio(${message.audioBase64.length} base64 chars)" else message)
                        handle(message)
                    }
                }
            }
        } finally {
            logger.info("Quest disconnected")
        }
    }

    private suspend fun handle(message: NexusMessage) {
        when (message) {
            is Hello -> reply(
                HelloAck(
                    messageId = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    serverVersion = "0.1.0",
                ),
            )

            is VoiceCommand -> {
                val result = withContext(Dispatchers.IO) { terminalInjector.inject(message.text) }
                reply(
                    CommandAck(
                        messageId = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        correlatesTo = message.messageId,
                        status = if (result.isSuccess) CommandStatus.SUCCESS else CommandStatus.FAILURE,
                        detail = result.exceptionOrNull()?.message,
                        recognizedText = message.text,
                    ),
                )
            }

            is VoiceAudio -> handleVoiceAudio(message)?.let { reply(it) }

            is Heartbeat -> reply(
                HeartbeatAck(
                    messageId = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    correlatesTo = message.messageId,
                ),
            )

            is HelloAck, is CommandAck, is HeartbeatAck -> Unit
        }
    }

    private suspend fun reply(message: NexusMessage) {
        logger.info("replying {}", message)
        session.outgoing.send(Frame.Text(message.encode()))
    }

    /** Returns null when the utterance should be silently dropped (no ack, no overlay/haptic on the Quest). */
    private suspend fun handleVoiceAudio(message: VoiceAudio): CommandAck? {
        val transcriber = voiceTranscriber
        if (transcriber == null) {
            return CommandAck(
                messageId = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                correlatesTo = message.messageId,
                status = CommandStatus.FAILURE,
                detail = "modelo de reconhecimento de voz não configurado no host (veja NEXUS_VOSK_MODEL_PATH)",
            )
        }

        val audioBytes = withContext(Dispatchers.IO) {
            runCatching { Base64.getDecoder().decode(message.audioBase64) }
        }.getOrElse {
            return CommandAck(
                messageId = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                correlatesTo = message.messageId,
                status = CommandStatus.FAILURE,
                detail = "áudio recebido em formato inválido: ${it.message}",
            )
        }

        val transcription = withContext(Dispatchers.IO) { transcriber.transcribe(audioBytes, message.sampleRateHz) }
        val text = transcription.getOrNull()
        if (transcription.isFailure || text.isNullOrBlank()) {
            return CommandAck(
                messageId = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                correlatesTo = message.messageId,
                status = CommandStatus.FAILURE,
                detail = transcription.exceptionOrNull()?.message ?: "não foi possível reconhecer nenhuma fala no áudio",
            )
        }

        val command = extractCommandAfterWakeWord(text)
        if (command == null) {
            logger.info("ignoring utterance without wake word '{}': '{}'", WAKE_WORD, text)
            return null
        }

        val injectResult = withContext(Dispatchers.IO) { terminalInjector.inject(command) }
        return CommandAck(
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            correlatesTo = message.messageId,
            status = if (injectResult.isSuccess) CommandStatus.SUCCESS else CommandStatus.FAILURE,
            detail = injectResult.exceptionOrNull()?.message,
            recognizedText = command,
        )
    }

    /**
     * Looks for the wake word anywhere in the utterance (VAD may catch a little noise before it)
     * and returns whatever follows it, trimmed of leading punctuation. Null means "not activated".
     */
    private fun extractCommandAfterWakeWord(text: String): String? {
        val index = text.indexOf(WAKE_WORD, ignoreCase = true)
        if (index < 0) return null
        val after = text.substring(index + WAKE_WORD.length).trim(' ', ',', '.', '-', ':', ';')
        return after.ifBlank { null }
    }

    companion object {
        private const val WAKE_WORD = "computador"
    }
}
