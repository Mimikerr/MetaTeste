package com.example.metateste.host.session

import com.example.metateste.host.automation.ProcessRunner
import com.example.metateste.host.automation.TerminalInjector
import com.example.metateste.host.chat.AssistantMessageEvent
import com.example.metateste.host.chat.ChatEventBus
import com.example.metateste.host.chat.CommandResultEvent
import com.example.metateste.host.chat.SystemNoteEvent
import com.example.metateste.host.chat.UserMessageEvent
import com.example.metateste.host.commands.CommandMappingStore
import com.example.metateste.host.commands.MacroExecutor
import com.example.metateste.host.commands.toAckDetail
import com.example.metateste.host.llm.LlmOrchestrator
import com.example.metateste.host.llm.LlmOutcome
import com.example.metateste.host.llm.PendingCommand
import com.example.metateste.host.tts.TextToSpeechSynthesizer
import com.example.metateste.host.voice.VoiceTranscriber
import com.example.metateste.shared.AssistantReply
import com.example.metateste.shared.ClientSettings
import com.example.metateste.shared.CommandAck
import com.example.metateste.shared.CommandStatus
import com.example.metateste.shared.Heartbeat
import com.example.metateste.shared.HeartbeatAck
import com.example.metateste.shared.Hello
import com.example.metateste.shared.HelloAck
import com.example.metateste.shared.NexusMessage
import com.example.metateste.shared.TtsAudio
import com.example.metateste.shared.VoiceAudio
import com.example.metateste.shared.VoiceCommand
import com.example.metateste.shared.decodeNexusMessage
import com.example.metateste.shared.encode
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.Base64
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Handles a single Quest<->host WebSocket connection.
 * MVP assumes one client connected at a time.
 */
class NexusSession(
    private val session: DefaultWebSocketServerSession,
    private val terminalInjector: TerminalInjector,
    private val voiceTranscriber: VoiceTranscriber,
    private val commandMappingStore: CommandMappingStore,
    private val macroExecutor: MacroExecutor,
    private val llmOrchestrator: LlmOrchestrator,
    private val processRunner: ProcessRunner,
    private val ttsSynthesizer: TextToSpeechSynthesizer,
) {

    private val logger = LoggerFactory.getLogger(NexusSession::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Defaults to requiring confirmation, so a session that hasn't heard from the app yet is never unsafe. */
    private var clientSettings = ClientSettings(messageId = "", timestamp = 0L)
    private var pendingConfirmation: PendingConfirmation? = null
    private var confirmationTimeoutJob: Job? = null

    private data class PendingConfirmation(val command: PendingCommand, val originalMessageId: String)

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
            confirmationTimeoutJob?.cancel()
            scope.cancel()
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

            is ClientSettings -> clientSettings = message

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

            is VoiceAudio -> handleVoiceAudio(message)

            is Heartbeat -> reply(
                HeartbeatAck(
                    messageId = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    correlatesTo = message.messageId,
                ),
            )

            is HelloAck, is CommandAck, is HeartbeatAck, is AssistantReply, is TtsAudio -> Unit
        }
    }

    private suspend fun reply(message: NexusMessage) {
        logger.info("replying {}", message)
        session.outgoing.send(Frame.Text(message.encode()))
    }

    private suspend fun handleVoiceAudio(message: VoiceAudio) {
        val audioBytes = withContext(Dispatchers.IO) {
            runCatching { Base64.getDecoder().decode(message.audioBase64) }
        }.getOrElse {
            reply(failureAck(message.messageId, "áudio recebido em formato inválido: ${it.message}"))
            return
        }

        val transcription = withContext(Dispatchers.IO) { voiceTranscriber.transcribe(audioBytes, message.sampleRateHz) }
        val text = transcription.getOrNull()
        if (transcription.isFailure || text.isNullOrBlank()) {
            reply(failureAck(message.messageId, transcription.exceptionOrNull()?.message ?: "não foi possível reconhecer nenhuma fala no áudio"))
            return
        }
        ChatEventBus.publish(UserMessageEvent(now(), text))

        val confirmation = pendingConfirmation
        if (confirmation != null) {
            handleConfirmationReply(message.messageId, text, confirmation)
            return
        }

        val matched = commandMappingStore.findMatch(text)
        if (matched != null) {
            val macroResult = macroExecutor.execute(matched.steps)
            val status = if (macroResult.isSuccess) CommandStatus.SUCCESS else CommandStatus.FAILURE
            ChatEventBus.publish(CommandResultEvent(now(), macroResult.isSuccess, macroResult.toAckDetail() ?: "macro '${matched.trigger}' executada"))
            reply(
                CommandAck(
                    messageId = newId(),
                    timestamp = now(),
                    correlatesTo = message.messageId,
                    status = status,
                    detail = macroResult.toAckDetail(),
                    recognizedText = text,
                ),
            )
            return
        }

        when (val outcome = llmOrchestrator.handle(text)) {
            is LlmOutcome.Answer -> replyWithSpeech(message.messageId, outcome.text)

            is LlmOutcome.NeedsConfirmation -> beginConfirmation(message.messageId, outcome)

            is LlmOutcome.Failed -> {
                // O cérebro está indisponível (sem chave configurada, rede fora do ar, etc.) — cai de volta
                // para o comportamento anterior à existência do cérebro: digita o texto cru.
                ChatEventBus.publish(SystemNoteEvent(now(), "cérebro indisponível (${outcome.reason}) — digitando o texto cru"))
                val result = withContext(Dispatchers.IO) { terminalInjector.inject(text) }
                reply(
                    CommandAck(
                        messageId = newId(),
                        timestamp = now(),
                        correlatesTo = message.messageId,
                        status = if (result.isSuccess) CommandStatus.SUCCESS else CommandStatus.FAILURE,
                        detail = result.exceptionOrNull()?.message,
                        recognizedText = text,
                    ),
                )
            }
        }
    }

    private suspend fun beginConfirmation(originalMessageId: String, outcome: LlmOutcome.NeedsConfirmation) {
        if (!clientSettings.requireCommandConfirmation) {
            val result = runCommand(outcome.command)
            ChatEventBus.publish(CommandResultEvent(now(), !result.timedOut && result.exitCode == 0, result.stdout.ifBlank { result.stderr }))
            reply(commandAckFromProcessResult(originalMessageId, result))
            return
        }

        val confirmation = PendingConfirmation(outcome.command, originalMessageId)
        pendingConfirmation = confirmation
        replyWithSpeech(originalMessageId, outcome.question, awaitingConfirmation = true)

        confirmationTimeoutJob?.cancel()
        confirmationTimeoutJob = scope.launch {
            delay(CONFIRMATION_TIMEOUT_MS)
            if (pendingConfirmation === confirmation) {
                pendingConfirmation = null
                replyWithSpeech(originalMessageId, "Tempo esgotado, comando cancelado.")
            }
        }
    }

    /**
     * [replyMessageId] is the "sim"/"não" VoiceAudio's own messageId, not the original question's —
     * the app tracks "the last VoiceAudio it sent" as the id it expects a terminal reply for, so
     * acks here must correlate to this new message, not to [PendingConfirmation.originalMessageId].
     */
    private suspend fun handleConfirmationReply(replyMessageId: String, text: String, confirmation: PendingConfirmation) {
        confirmationTimeoutJob?.cancel()
        pendingConfirmation = null

        when (ConfirmationClassifier.classify(text)) {
            ConfirmationClassifier.Confirmation.AFFIRMATIVE -> {
                val result = runCommand(confirmation.command)
                ChatEventBus.publish(CommandResultEvent(now(), !result.timedOut && result.exitCode == 0, result.stdout.ifBlank { result.stderr }))
                reply(commandAckFromProcessResult(replyMessageId, result, recognizedText = text))
            }

            ConfirmationClassifier.Confirmation.NEGATIVE, ConfirmationClassifier.Confirmation.UNCLEAR -> {
                replyWithSpeech(replyMessageId, "Ok, cancelado.")
                reply(
                    CommandAck(
                        messageId = newId(),
                        timestamp = now(),
                        correlatesTo = replyMessageId,
                        status = CommandStatus.FAILURE,
                        detail = "cancelado pelo usuário",
                        recognizedText = text,
                    ),
                )
            }
        }
    }

    /** Sends the assistant's text reply, then best-effort attaches spoken audio for it — a synthesis
     * failure (e.g. tts-service not running) never blocks the text reply that already went out. */
    private suspend fun replyWithSpeech(correlatesTo: String, text: String, awaitingConfirmation: Boolean = false) {
        ChatEventBus.publish(AssistantMessageEvent(now(), text, awaitingConfirmation))
        reply(
            AssistantReply(
                messageId = newId(),
                timestamp = now(),
                correlatesTo = correlatesTo,
                text = text,
                awaitingConfirmation = awaitingConfirmation,
            ),
        )

        withContext(Dispatchers.IO) { ttsSynthesizer.synthesize(text, TTS_SAMPLE_RATE_HZ) }
            .onSuccess { pcm ->
                reply(
                    TtsAudio(
                        messageId = newId(),
                        timestamp = now(),
                        correlatesTo = correlatesTo,
                        audioBase64 = Base64.getEncoder().encodeToString(pcm),
                        sampleRateHz = TTS_SAMPLE_RATE_HZ,
                    ),
                )
            }
            .onFailure { logger.info("tts-service indisponível, respondendo só em texto: {}", it.message) }
    }

    private suspend fun runCommand(command: PendingCommand): ProcessRunner.Result = withContext(Dispatchers.IO) {
        processRunner.run(command.executable, command.args, timeout = command.timeoutSeconds.seconds)
    }

    private fun commandAckFromProcessResult(correlatesTo: String, result: ProcessRunner.Result, recognizedText: String? = null): CommandAck {
        val status = if (!result.timedOut && result.exitCode == 0) CommandStatus.SUCCESS else CommandStatus.FAILURE
        val detail = buildString {
            if (result.timedOut) append("tempo esgotado. ")
            append("exit code ${result.exitCode}")
            if (result.stdout.isNotBlank()) append("; stdout: ${result.stdout}")
            if (result.stderr.isNotBlank()) append("; stderr: ${result.stderr}")
        }
        return CommandAck(messageId = newId(), timestamp = now(), correlatesTo = correlatesTo, status = status, detail = detail, recognizedText = recognizedText)
    }

    private fun failureAck(correlatesTo: String, detail: String) =
        CommandAck(messageId = newId(), timestamp = now(), correlatesTo = correlatesTo, status = CommandStatus.FAILURE, detail = detail)

    private fun newId() = UUID.randomUUID().toString()

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val CONFIRMATION_TIMEOUT_MS = 30_000L
        private const val TTS_SAMPLE_RATE_HZ = 24000
    }
}
