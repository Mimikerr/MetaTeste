package com.example.metateste.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class NexusMessageSerializationTest {

    private fun roundTrip(message: NexusMessage) {
        val decoded = message.encode().decodeNexusMessage()
        assertEquals(message, decoded)
    }

    @Test
    fun `hello round-trips`() {
        roundTrip(Hello(messageId = "m1", timestamp = 1L, deviceId = "quest-1", deviceName = "Quest 3", appVersion = "1.0"))
    }

    @Test
    fun `hello_ack round-trips`() {
        roundTrip(HelloAck(messageId = "m2", timestamp = 2L, serverVersion = "1.0"))
    }

    @Test
    fun `voice_command round-trips with accented text`() {
        roundTrip(
            VoiceCommand(
                messageId = "m3",
                timestamp = 3L,
                text = "abrir terminal e rodar não é possível",
            )
        )
    }

    @Test
    fun `voice_audio round-trips`() {
        roundTrip(
            VoiceAudio(
                messageId = "m3b",
                timestamp = 35L,
                audioBase64 = "AAECAwQFBgc=",
                sampleRateHz = 16000,
            )
        )
    }

    @Test
    fun `command_ack success round-trips`() {
        roundTrip(
            CommandAck(
                messageId = "m4",
                timestamp = 4L,
                correlatesTo = "m3",
                status = CommandStatus.SUCCESS,
            )
        )
    }

    @Test
    fun `command_ack success round-trips with recognized text`() {
        roundTrip(
            CommandAck(
                messageId = "m4b",
                timestamp = 45L,
                correlatesTo = "m3b",
                status = CommandStatus.SUCCESS,
                recognizedText = "abrir terminal e rodar não é possível",
            )
        )
    }

    @Test
    fun `command_ack failure round-trips with detail`() {
        roundTrip(
            CommandAck(
                messageId = "m5",
                timestamp = 5L,
                correlatesTo = "m3",
                status = CommandStatus.FAILURE,
                detail = "janela em foco não parece um terminal: chrome.exe",
            )
        )
    }

    @Test
    fun `heartbeat round-trips`() {
        roundTrip(Heartbeat(messageId = "m6", timestamp = 6L))
    }

    @Test
    fun `heartbeat_ack round-trips`() {
        roundTrip(HeartbeatAck(messageId = "m7", timestamp = 7L, correlatesTo = "m6"))
    }

    @Test
    fun `decodes polymorphically via the type discriminator`() {
        val encoded = VoiceCommand(messageId = "m8", timestamp = 8L, text = "teste").encode()
        assertEquals(true, encoded.contains("\"type\":\"voice_command\""))
    }
}
