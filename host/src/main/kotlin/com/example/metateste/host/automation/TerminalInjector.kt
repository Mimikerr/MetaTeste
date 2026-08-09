package com.example.metateste.host.automation

interface TerminalInjector {
    /** Types [text] into whatever window currently has OS focus, followed by Enter. */
    fun inject(text: String): Result<Unit>
}
