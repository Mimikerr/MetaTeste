package com.example.metateste.host.automation

import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import org.slf4j.LoggerFactory

/**
 * Injects text via the system clipboard + Ctrl+V instead of synthesizing individual key
 * events: java.awt.Robot's VK_* constants can't represent accented characters (common in
 * Portuguese), so a literal key-by-key approach would mangle anything beyond ASCII.
 *
 * Windows blocks background processes from stealing foreground focus, so this deliberately
 * does not try to focus a window itself — it types into whatever the user already has
 * focused, the same model traditional dictation tools use.
 */
class ClipboardPasteInjector(
    private val guard: ForegroundWindowGuard? = null,
    private val keyboard: KeyboardActuator = KeyboardActuator(),
) : TerminalInjector {

    private val logger = LoggerFactory.getLogger(ClipboardPasteInjector::class.java)
    private val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    override fun inject(text: String): Result<Unit> = runCatching {
        guard?.let {
            val focused = it.foregroundProcessName()
            check(it.looksLikeTerminal(focused)) { "janela em foco não parece um terminal: $focused" }
        }

        val previousClipboard = readClipboardTextOrNull()
        try {
            clipboard.setContents(StringSelection(text), null)
            keyboard.pressChord(KeyEvent.VK_CONTROL, KeyEvent.VK_V)
            Thread.sleep(80)
            keyboard.pressKey(KeyEvent.VK_ENTER)
        } finally {
            Thread.sleep(50)
            previousClipboard?.let { clipboard.setContents(StringSelection(it), null) }
        }
    }.onFailure { logger.warn("failed to inject text: {}", it.message) }

    private fun readClipboardTextOrNull(): String? =
        runCatching { clipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull()
}
