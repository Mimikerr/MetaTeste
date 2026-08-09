package com.example.metateste.host

import com.example.metateste.host.automation.ClipboardPasteInjector
import com.example.metateste.host.automation.ForegroundWindowGuard
import com.example.metateste.host.session.NexusSession
import com.example.metateste.host.voice.VoskVoiceTranscriber
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import java.io.File
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NexusApplication")

/**
 * Looks for the Vosk model directory both relative to the current working directory (whatever it
 * is when `:host:run`/the packaged jar is launched from) and one level up, so the model can live
 * at the repo root regardless of which directory the process happened to start in.
 */
private fun resolveVoskModelDir(): File? {
    System.getenv("NEXUS_VOSK_MODEL_PATH")?.let { return File(it) }
    val candidates = listOf(File("vosk-model"), File("../vosk-model"))
    return candidates.firstOrNull { it.isDirectory } ?: candidates.first()
}

fun Application.nexusModule() {
    val terminalInjector = ClipboardPasteInjector(ForegroundWindowGuard())

    val modelDir = resolveVoskModelDir()
    val voiceTranscriber = modelDir?.let { VoskVoiceTranscriber.createIfModelPresent(it.path) }
    if (voiceTranscriber == null) {
        logger.warn(
            "Vosk model not found at '{}' — voice_audio commands will fail with a clear error. " +
                "Download vosk-model-small-pt-0.3 from https://alphacephei.com/vosk/models, unzip it, " +
                "and point NEXUS_VOSK_MODEL_PATH at the extracted folder (or place it at ./vosk-model, next to this repo).",
            modelDir?.absolutePath,
        )
    } else {
        logger.info("Vosk model loaded from '{}'", modelDir.absolutePath)
    }

    install(WebSockets)
    routing {
        webSocket("/nexus") {
            NexusSession(this, terminalInjector, voiceTranscriber).run()
        }
    }
}
