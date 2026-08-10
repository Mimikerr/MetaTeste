package com.example.metateste.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface NexusMessage {
    val messageId: String
    val timestamp: Long
}

@Serializable
@SerialName("hello")
data class Hello(
    override val messageId: String,
    override val timestamp: Long,
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val protocolVersion: Int = 1,
) : NexusMessage

@Serializable
@SerialName("hello_ack")
data class HelloAck(
    override val messageId: String,
    override val timestamp: Long,
    val serverVersion: String,
    val protocolVersion: Int = 1,
) : NexusMessage

@Serializable
@SerialName("voice_command")
data class VoiceCommand(
    override val messageId: String,
    override val timestamp: Long,
    val text: String,
    val locale: String = "pt-BR",
) : NexusMessage

/**
 * Raw mono PCM16LE audio captured on the Quest, sent for the host to transcribe itself —
 * the Quest's on-device SpeechRecognizer isn't reachable by third-party apps on Horizon OS.
 */
@Serializable
@SerialName("voice_audio")
data class VoiceAudio(
    override val messageId: String,
    override val timestamp: Long,
    val audioBase64: String,
    val sampleRateHz: Int = 16000,
) : NexusMessage

@Serializable
enum class CommandStatus { SUCCESS, FAILURE }

@Serializable
@SerialName("command_ack")
data class CommandAck(
    override val messageId: String,
    override val timestamp: Long,
    val correlatesTo: String,
    val status: CommandStatus,
    val detail: String? = null,
    /** What the host's speech engine actually heard, when the triggering message was a [VoiceAudio]. */
    val recognizedText: String? = null,
) : NexusMessage

@Serializable
@SerialName("heartbeat")
data class Heartbeat(
    override val messageId: String,
    override val timestamp: Long,
) : NexusMessage

@Serializable
@SerialName("heartbeat_ack")
data class HeartbeatAck(
    override val messageId: String,
    override val timestamp: Long,
    val correlatesTo: String,
) : NexusMessage

/**
 * Host -> Quest conversational text: an answer from the LLM brain, a confirmation question
 * before running a command, or a cancellation notice. Distinct from [CommandAck], which only
 * carries the pass/fail result of an action.
 */
@Serializable
@SerialName("assistant_reply")
data class AssistantReply(
    override val messageId: String,
    override val timestamp: Long,
    val correlatesTo: String,
    val text: String,
    val awaitingConfirmation: Boolean = false,
) : NexusMessage

/**
 * Host -> Quest TTS audio for an [AssistantReply], mono PCM16LE, one message per full reply
 * (not chunked/streamed).
 */
@Serializable
@SerialName("tts_audio")
data class TtsAudio(
    override val messageId: String,
    override val timestamp: Long,
    val correlatesTo: String,
    val audioBase64: String,
    val sampleRateHz: Int = 24000,
) : NexusMessage

/**
 * Quest -> host client preferences, sent right after [Hello] and again whenever the user
 * changes a setting. [requireCommandConfirmation] defaults to true so a session that hasn't
 * received one yet never runs commands unconfirmed.
 */
@Serializable
@SerialName("client_settings")
data class ClientSettings(
    override val messageId: String,
    override val timestamp: Long,
    val requireCommandConfirmation: Boolean = true,
) : NexusMessage
