package com.example.metateste.host.automation

import java.awt.Robot

/** Thin wrapper around [Robot] key synthesis, shared by [ClipboardPasteInjector] and macro key-shortcut steps. */
class KeyboardActuator {
    private val robot = Robot().apply { autoDelay = 15 }

    /** Presses all [keyCodes] in order, then releases them in reverse order (e.g. Ctrl+V). */
    fun pressChord(vararg keyCodes: Int) {
        keyCodes.forEach(robot::keyPress)
        keyCodes.reversed().forEach(robot::keyRelease)
    }

    fun pressKey(keyCode: Int) {
        robot.keyPress(keyCode)
        robot.keyRelease(keyCode)
    }
}
