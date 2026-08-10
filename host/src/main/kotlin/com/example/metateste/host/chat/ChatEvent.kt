package com.example.metateste.host.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A step in the voice-command conversation, broadcast to the webui's live chat view over WebSocket. */
@Serializable
sealed interface ChatEvent {
    val timestamp: Long
}

@Serializable
@SerialName("user_message")
data class UserMessageEvent(override val timestamp: Long, val text: String) : ChatEvent

/** The LLM brain calling one of its tools — this is the "thinking" the webui surfaces. */
@Serializable
@SerialName("tool_call")
data class ToolCallEvent(override val timestamp: Long, val tool: String, val input: Map<String, String>) : ChatEvent

@Serializable
@SerialName("tool_result")
data class ToolResultEvent(override val timestamp: Long, val tool: String, val output: String) : ChatEvent

@Serializable
@SerialName("assistant_message")
data class AssistantMessageEvent(override val timestamp: Long, val text: String, val awaitingConfirmation: Boolean = false) : ChatEvent

@Serializable
@SerialName("command_result")
data class CommandResultEvent(override val timestamp: Long, val success: Boolean, val detail: String?) : ChatEvent

/** Anything outside the direct conversation flow worth showing (brain unavailable, etc). */
@Serializable
@SerialName("system_note")
data class SystemNoteEvent(override val timestamp: Long, val text: String) : ChatEvent
