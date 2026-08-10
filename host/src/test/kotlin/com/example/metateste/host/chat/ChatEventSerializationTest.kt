package com.example.metateste.host.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatEventSerializationTest {

    private fun roundTrip(event: ChatEvent) {
        val encoded = ChatJson.encodeToString(ChatEvent.serializer(), event)
        val decoded = ChatJson.decodeFromString(ChatEvent.serializer(), encoded)
        assertEquals(event, decoded)
    }

    @Test
    fun `user_message round-trips`() {
        roundTrip(UserMessageEvent(1L, "abrir o navegador"))
    }

    @Test
    fun `tool_call round-trips with input map`() {
        roundTrip(ToolCallEvent(2L, "read_file", mapOf("path" to "notas/hoje.txt")))
    }

    @Test
    fun `tool_result round-trips`() {
        roundTrip(ToolResultEvent(3L, "read_file", "comprar leite"))
    }

    @Test
    fun `assistant_message round-trips with awaitingConfirmation`() {
        roundTrip(AssistantMessageEvent(4L, "Quer que eu rode isso?", awaitingConfirmation = true))
    }

    @Test
    fun `command_result round-trips`() {
        roundTrip(CommandResultEvent(5L, success = false, detail = "exit code 1"))
    }

    @Test
    fun `system_note round-trips`() {
        roundTrip(SystemNoteEvent(6L, "cérebro indisponível"))
    }

    @Test
    fun `decodes polymorphically via the type discriminator`() {
        val encoded = ChatJson.encodeToString(ChatEvent.serializer(), UserMessageEvent(7L, "teste"))
        assertTrue(encoded.contains("\"type\":\"user_message\""))
    }
}
