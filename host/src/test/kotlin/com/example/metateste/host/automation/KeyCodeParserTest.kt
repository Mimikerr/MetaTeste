package com.example.metateste.host.automation

import java.awt.event.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyCodeParserTest {

    @Test
    fun `parses named keys case-insensitively`() {
        assertEquals(KeyEvent.VK_CONTROL, KeyCodeParser.parse("control"))
        assertEquals(KeyEvent.VK_CONTROL, KeyCodeParser.parse("CTRL"))
        assertEquals(KeyEvent.VK_ESCAPE, KeyCodeParser.parse("Esc"))
        assertEquals(KeyEvent.VK_F1, KeyCodeParser.parse("f1"))
        assertEquals(KeyEvent.VK_F1 + 11, KeyCodeParser.parse("F12"))
    }

    @Test
    fun `parses single letters and digits`() {
        assertEquals(KeyEvent.VK_C, KeyCodeParser.parse("c"))
        assertEquals(KeyEvent.VK_5, KeyCodeParser.parse("5"))
    }

    @Test
    fun `returns null for unknown key names`() {
        assertNull(KeyCodeParser.parse("nao existe"))
        assertNull(KeyCodeParser.parse(""))
    }

    @Test
    fun `parseChord succeeds when all keys are known`() {
        val result = KeyCodeParser.parseChord(listOf("ALT", "F4"))
        assertEquals(listOf(KeyEvent.VK_ALT, KeyEvent.VK_F4), result.getOrThrow())
    }

    @Test
    fun `parseChord fails when any key is unknown`() {
        val result = KeyCodeParser.parseChord(listOf("CONTROL", "BANANA"))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("BANANA") == true)
    }
}
