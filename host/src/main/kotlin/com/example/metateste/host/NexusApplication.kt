package com.example.metateste.host

import com.example.metateste.host.automation.AppLauncher
import com.example.metateste.host.automation.ClipboardPasteInjector
import com.example.metateste.host.automation.ForegroundWindowGuard
import com.example.metateste.host.automation.KeyboardActuator
import com.example.metateste.host.automation.ProcessRunner
import com.example.metateste.host.chat.ChatEvent
import com.example.metateste.host.chat.ChatEventBus
import com.example.metateste.host.chat.ChatJson
import com.example.metateste.host.commands.CommandMappingStore
import com.example.metateste.host.commands.CommandsJson
import com.example.metateste.host.commands.MacroExecutor
import com.example.metateste.host.commands.commandsApi
import com.example.metateste.host.llm.AnthropicLlmProvider
import com.example.metateste.host.llm.FileReaderTool
import com.example.metateste.host.llm.LlmOrchestrator
import com.example.metateste.host.llm.LlmProvider
import com.example.metateste.host.llm.LmStudioLlmProvider
import com.example.metateste.host.llm.NullLlmProvider
import com.example.metateste.host.services.ManagedService
import com.example.metateste.host.services.servicesApi
import com.example.metateste.host.session.NexusSession
import com.example.metateste.host.tts.EdgeTtsSynthesizerService
import com.example.metateste.host.tts.TextToSpeechSynthesizer
import com.example.metateste.host.voice.VoiceTranscriber
import com.example.metateste.host.voice.WhisperTranscriptionService
import com.example.metateste.host.voice.WitAiVoiceTranscriber
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import java.io.File
import kotlinx.coroutines.launch
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
 * Prefers Wit.ai (cloud) when a token is configured; otherwise uses the local faster-whisper
 * microservice (host/whisper-service).
 */
private fun resolveVoiceTranscriber(): VoiceTranscriber {
    System.getenv("NEXUS_WITAI_TOKEN")?.let { token ->
        logger.info("Using Wit.ai for speech transcription")
        return WitAiVoiceTranscriber(token)
    }

    val whisperUrl = System.getenv("NEXUS_WHISPER_URL") ?: "http://127.0.0.1:8000/transcribe"
    logger.info("Using local faster-whisper service for speech transcription at '{}'", whisperUrl)
    return WhisperTranscriptionService(whisperUrl)
}

/**
 * Picks the LLM brain implementation via NEXUS_LLM_PROVIDER ("anthropic", the default, or
 * "lmstudio" for a fully local/offline brain with no API key). If "anthropic" is picked (or the
 * variable is unset) without NEXUS_ANTHROPIC_API_KEY configured, the brain stays disabled and
 * voice commands that don't match a macro fall back to the pre-existing raw-text injection.
 */
private fun resolveLlmProvider(): LlmProvider {
    return when (System.getenv("NEXUS_LLM_PROVIDER")?.lowercase()) {
        "lmstudio" -> {
            val url = System.getenv("NEXUS_LMSTUDIO_URL") ?: "http://127.0.0.1:1234/v1/chat/completions"
            val model = System.getenv("NEXUS_LLM_MODEL") ?: "local-model"
            logger.info("Using LM Studio ({}) as the LLM brain at '{}'", model, url)
            LmStudioLlmProvider(model = model, url = url)
        }

        else -> {
            val apiKey = System.getenv("NEXUS_ANTHROPIC_API_KEY")
            if (apiKey == null) {
                logger.warn("NEXUS_ANTHROPIC_API_KEY não configurada — o cérebro (LLM) fica desativado")
                NullLlmProvider
            } else {
                val model = System.getenv("NEXUS_LLM_MODEL") ?: "claude-sonnet-5"
                logger.info("Using Anthropic ({}) as the LLM brain", model)
                AnthropicLlmProvider(apiKey, model = model)
            }
        }
    }
}

/** Local edge-tts microservice (host/tts-service); if it isn't running, replies simply stay text-only. */
private fun resolveTtsSynthesizer(): TextToSpeechSynthesizer {
    val url = System.getenv("NEXUS_TTS_URL") ?: "http://127.0.0.1:8001/synthesize"
    logger.info("Using local edge-tts service for speech synthesis at '{}'", url)
    return EdgeTtsSynthesizerService(url)
}

/** Same search order as [resolvePath], but for a whole directory (a Python microservice checkout). */
private fun resolveServiceDir(dirName: String): File {
    val candidates = listOf(File(dirName), File("host/$dirName"), File("../host/$dirName"))
    return candidates.firstOrNull { it.isDirectory } ?: candidates[0]
}

private fun pythonExecutable(serviceDir: File): File = File(serviceDir, "venv/Scripts/python.exe")

/** The local Python microservices the webui can start/stop, so they don't need their own terminals. */
private fun buildManagedServices(): List<ManagedService> {
    val whisperDir = resolveServiceDir("whisper-service")
    val ttsDir = resolveServiceDir("tts-service")
    return listOf(
        ManagedService(
            id = "whisper",
            name = "STT (whisper-service)",
            workingDir = whisperDir,
            command = listOf(
                pythonExecutable(whisperDir).path,
                "-m", "uvicorn", "whisper_server:app", "--host", "127.0.0.1", "--port", "8000",
            ),
        ),
        ManagedService(
            id = "tts",
            name = "TTS (tts-service)",
            workingDir = ttsDir,
            command = listOf(
                pythonExecutable(ttsDir).path,
                "-m", "uvicorn", "tts_server:app", "--host", "127.0.0.1", "--port", "8001",
            ),
        ),
    )
}

fun Application.nexusModule() {
    val keyboardActuator = KeyboardActuator()
    val terminalInjector = ClipboardPasteInjector(ForegroundWindowGuard(), keyboardActuator)
    val appLauncher = AppLauncher()
    val macroExecutor = MacroExecutor.ofReal(appLauncher, terminalInjector, keyboardActuator)
    val commandMappingStore = CommandMappingStore(resolvePath("NEXUS_COMMANDS_PATH", "voice-commands.json"))

    val voiceTranscriber = resolveVoiceTranscriber()

    val filesRoot = (System.getenv("NEXUS_LLM_FILES_ROOT")?.let(::File) ?: File(System.getProperty("user.home"), "NexusFiles"))
        .apply { mkdirs() }
    val llmOrchestrator = LlmOrchestrator(
        provider = resolveLlmProvider(),
        fileReader = FileReaderTool(filesRoot),
        commandMappingStore = commandMappingStore,
        macroExecutor = macroExecutor,
    )
    val processRunner = ProcessRunner()
    val ttsSynthesizer = resolveTtsSynthesizer()

    val webuiDir = File(System.getenv("NEXUS_WEBUI_PATH") ?: "webui/dist").apply { mkdirs() }

    val managedServices = buildManagedServices()
    Runtime.getRuntime().addShutdownHook(Thread { managedServices.forEach { it.stop() } })

    install(WebSockets)
    install(ContentNegotiation) { json(CommandsJson) }

    routing {
        webSocket("/nexus") {
            NexusSession(
                this,
                terminalInjector,
                voiceTranscriber,
                commandMappingStore,
                macroExecutor,
                llmOrchestrator,
                processRunner,
                ttsSynthesizer,
            ).run()
        }
        webSocket("/api/chat") {
            val forwardJob = launch {
                ChatEventBus.events.collect { event -> send(Frame.Text(ChatJson.encodeToString(ChatEvent.serializer(), event))) }
            }
            try {
                for (frame in incoming) {
                    // A webui só observa — nada relevante chega por aqui, só mantemos a conexão viva.
                }
            } finally {
                forwardJob.cancel()
            }
        }
        commandsApi(commandMappingStore)
        servicesApi(managedServices)
        staticFiles("/", webuiDir)
    }
}
