package com.example.metateste.host.chat

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json

val ChatJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * In-memory pub/sub for [ChatEvent]s, so the webui's chat panel can watch the voice-command
 * conversation (including the LLM's tool calls) live over WebSocket. [replay] keeps recent
 * history so a webui tab opened mid-session isn't starting from a blank chat.
 */
object ChatEventBus {
    private val _events = MutableSharedFlow<ChatEvent>(replay = 200, extraBufferCapacity = 64)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    fun publish(event: ChatEvent) {
        _events.tryEmit(event)
    }
}
