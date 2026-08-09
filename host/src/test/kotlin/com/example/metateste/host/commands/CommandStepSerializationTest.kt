package com.example.metateste.host.commands

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandStepSerializationTest {

    private fun roundTrip(step: CommandStep) {
        val encoded = CommandsJson.encodeToString(step)
        val decoded = CommandsJson.decodeFromString<CommandStep>(encoded)
        assertEquals(step, decoded)
    }

    @Test
    fun `launch_app step round-trips`() {
        roundTrip(LaunchAppStep(executablePath = "chrome"))
    }

    @Test
    fun `type_text step round-trips with accented text`() {
        roundTrip(TypeTextStep(text = "não é possível"))
    }

    @Test
    fun `key_shortcut step round-trips`() {
        roundTrip(KeyShortcutStep(keys = listOf("CONTROL", "C")))
    }

    @Test
    fun `delay step round-trips`() {
        roundTrip(DelayStep(milliseconds = 500))
    }

    @Test
    fun `voice command mapping with mixed steps round-trips`() {
        val mapping = VoiceCommandMapping(
            id = "m1",
            trigger = "abrir calculadora",
            steps = listOf(
                LaunchAppStep("calc.exe"),
                DelayStep(500),
                KeyShortcutStep(listOf("ALT", "F4")),
            ),
        )
        val encoded = CommandsJson.encodeToString(mapping)
        val decoded = CommandsJson.decodeFromString<VoiceCommandMapping>(encoded)
        assertEquals(mapping, decoded)
    }

    @Test
    fun `decodes polymorphically via the type discriminator`() {
        val encoded = CommandsJson.encodeToString<CommandStep>(DelayStep(250))
        assertTrue(encoded.contains("\"type\"") && encoded.contains("\"delay\""))
    }
}
