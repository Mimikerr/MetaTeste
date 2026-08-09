package com.example.metateste.host.automation

import java.awt.event.KeyEvent

/** Parses user-typed key names (from the command config UI) into `KeyEvent.VK_*` codes. */
object KeyCodeParser {

    private val NAMED: Map<String, Int> = buildMap {
        put("CONTROL", KeyEvent.VK_CONTROL)
        put("CTRL", KeyEvent.VK_CONTROL)
        put("ALT", KeyEvent.VK_ALT)
        put("SHIFT", KeyEvent.VK_SHIFT)
        put("WINDOWS", KeyEvent.VK_WINDOWS)
        put("WIN", KeyEvent.VK_WINDOWS)
        put("ENTER", KeyEvent.VK_ENTER)
        put("RETURN", KeyEvent.VK_ENTER)
        put("TAB", KeyEvent.VK_TAB)
        put("ESCAPE", KeyEvent.VK_ESCAPE)
        put("ESC", KeyEvent.VK_ESCAPE)
        put("SPACE", KeyEvent.VK_SPACE)
        put("DELETE", KeyEvent.VK_DELETE)
        put("DEL", KeyEvent.VK_DELETE)
        put("BACKSPACE", KeyEvent.VK_BACK_SPACE)
        put("HOME", KeyEvent.VK_HOME)
        put("END", KeyEvent.VK_END)
        put("PAGE_UP", KeyEvent.VK_PAGE_UP)
        put("PAGEUP", KeyEvent.VK_PAGE_UP)
        put("PAGE_DOWN", KeyEvent.VK_PAGE_DOWN)
        put("PAGEDOWN", KeyEvent.VK_PAGE_DOWN)
        put("INSERT", KeyEvent.VK_INSERT)
        put("UP", KeyEvent.VK_UP)
        put("DOWN", KeyEvent.VK_DOWN)
        put("LEFT", KeyEvent.VK_LEFT)
        put("RIGHT", KeyEvent.VK_RIGHT)
        for (n in 1..24) put("F$n", KeyEvent.VK_F1 + (n - 1))
    }

    /** Returns null for unrecognized key names. */
    fun parse(name: String): Int? {
        val key = name.trim().uppercase()
        NAMED[key]?.let { return it }
        if (key.length == 1) {
            val code = KeyEvent.getExtendedKeyCodeForChar(key[0].code)
            if (code != KeyEvent.VK_UNDEFINED) return code
        }
        return null
    }

    fun parseChord(names: List<String>): Result<List<Int>> = runCatching {
        names.map { name -> parse(name) ?: error("tecla desconhecida: '$name'") }
    }
}
