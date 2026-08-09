package com.example.metateste.host

import com.example.metateste.host.automation.AppLauncher
import com.example.metateste.host.automation.ClipboardPasteInjector
import com.example.metateste.host.automation.ForegroundWindowGuard
import com.example.metateste.host.automation.KeyboardActuator
import com.example.metateste.host.commands.CommandMappingStore
import com.example.metateste.host.commands.CommandsJson
import com.example.metateste.host.commands.MacroExecutor
import com.example.metateste.host.commands.commandsApi
import com.example.metateste.host.session.NexusSession
import com.example.metateste.host.voice.VoiceTranscriber
import com.example.metateste.host.voice.VoskVoiceTranscriber
import com.example.metateste.host.voice.WitAiVoiceTranscriber
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import java.io.File
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NexusApplication")

/**
 * Looks for a resource both relative to the current working directory (whatever it is when
 * `:host:run`/the packaged jar is launched from) and one level up, so data files can live at the
 * repo root regardless of which directory the process happened to start in.
 */
private fun resolvePath(envVar: String, fileName: String): File {
    System.getenv(envVar)?.let { return File(it) }
    val candidates = listOf(File(fileName), File("../$fileName"))
    return candidates.firstOrNull { it.exists() } ?: candidates[1]
}

/**
 * Prefers Wit.ai (free, noticeably better pt-BR accuracy than the small Vosk model) when a token
 * is configured; falls back to the local Vosk model so voice still works fully offline otherwise.
 */
private fun resolveVoiceTranscriber(): VoiceTranscriber? {
    System.getenv("NEXUS_WITAI_TOKEN")?.let { token ->
        logger.info("Using Wit.ai for speech transcription")
        return WitAiVoiceTranscriber(token)
    }

    val modelDir = resolvePath("NEXUS_VOSK_MODEL_PATH", "vosk-model")
    val voskTranscriber = if (modelDir.isDirectory) VoskVoiceTranscriber.createIfModelPresent(modelDir.path) else null
    if (voskTranscriber == null) {
        logger.warn(
            "No voice transcriber configured — voice_audio commands will fail with a clear error. " +
                "Either set NEXUS_WITAI_TOKEN (free, get one at https://wit.ai), or download " +
                "vosk-model-small-pt-0.3 from https://alphacephei.com/vosk/models, unzip it, and " +
                "point NEXUS_VOSK_MODEL_PATH at the extracted folder (or place it at ./vosk-model, " +
                "next to this repo). Checked '{}'.",
            modelDir.absolutePath,
        )
    } else {
        logger.info("Vosk model loaded from '{}'", modelDir.absolutePath)
    }
    return voskTranscriber
}

fun Application.nexusModule() {
    val keyboardActuator = KeyboardActuator()
    val terminalInjector = ClipboardPasteInjector(ForegroundWindowGuard(), keyboardActuator)
    val appLauncher = AppLauncher()
    val macroExecutor = MacroExecutor.ofReal(appLauncher, terminalInjector, keyboardActuator)
    val commandMappingStore = CommandMappingStore(resolvePath("NEXUS_COMMANDS_PATH", "voice-commands.json"))

    val voiceTranscriber = resolveVoiceTranscriber()

    val webuiDir = File(System.getenv("NEXUS_WEBUI_PATH") ?: "webui/dist").apply { mkdirs() }

    install(WebSockets)
    install(ContentNegotiation) { json(CommandsJson) }

    routing {
        webSocket("/nexus") {
            NexusSession(this, terminalInjector, voiceTranscriber, commandMappingStore, macroExecutor).run()
        }
        commandsApi(commandMappingStore)
        staticFiles("/", webuiDir)
    }
}
