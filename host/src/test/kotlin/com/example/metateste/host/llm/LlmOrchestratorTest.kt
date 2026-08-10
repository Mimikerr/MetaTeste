package com.example.metateste.host.llm

import com.example.metateste.host.commands.CommandMappingStore
import com.example.metateste.host.commands.LaunchAppStep
import com.example.metateste.host.commands.MacroExecutor
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmOrchestratorTest {

    private fun tempCommandsFile() = Files.createTempFile("voice-commands-test", ".json").toFile().apply { deleteOnExit() }
    private fun tempFilesRoot() = Files.createTempDirectory("nexus-files-test").toFile().apply { deleteOnExit() }

    private class ScriptedLlmProvider(
        private val script: suspend (systemPrompt: String, userText: String, tools: List<LlmTool>, toolExecutor: LlmToolExecutor) -> Result<String>,
    ) : LlmProvider {
        override suspend fun converse(
            systemPrompt: String,
            userText: String,
            tools: List<LlmTool>,
            toolExecutor: LlmToolExecutor,
        ): Result<String> = script(systemPrompt, userText, tools, toolExecutor)
    }

    private fun orchestrator(
        provider: LlmProvider,
        filesRoot: File = tempFilesRoot(),
        commandMappingStore: CommandMappingStore = CommandMappingStore(tempCommandsFile()),
        launchApp: (String) -> Result<Unit> = { Result.success(Unit) },
    ): LlmOrchestrator {
        val macroExecutor = MacroExecutor(
            launchApp = launchApp,
            injectText = { Result.success(Unit) },
            pressChord = { Result.success(Unit) },
        )
        return LlmOrchestrator(provider, FileReaderTool(filesRoot), commandMappingStore, macroExecutor)
    }

    @Test
    fun `plain text answer becomes Answer`() = runTest {
        val orchestrator = orchestrator(ScriptedLlmProvider { _, _, _, _ -> Result.success("São 14h32 em Brasília.") })

        val outcome = orchestrator.handle("que horas são")

        assertEquals(LlmOutcome.Answer("São 14h32 em Brasília."), outcome)
    }

    @Test
    fun `run_command halts the loop and surfaces a pending confirmation`() = runTest {
        val orchestrator = orchestrator(
            ScriptedLlmProvider { _, _, _, toolExecutor ->
                val outcome = toolExecutor.execute("run_command", mapOf("executable" to "git", "args" to "status --short"))
                Result.success((outcome as LlmToolOutcome.Halt).text)
            },
        )

        val outcome = orchestrator.handle("roda o git status pra mim") as LlmOutcome.NeedsConfirmation

        assertEquals("git", outcome.command.executable)
        assertEquals(listOf("status", "--short"), outcome.command.args)
        assertTrue(outcome.question.contains("git status --short"))
    }

    @Test
    fun `run_command with a cmd builtin like start is wrapped through cmd slash c`() = runTest {
        val orchestrator = orchestrator(
            ScriptedLlmProvider { _, _, _, toolExecutor ->
                val outcome = toolExecutor.execute("run_command", mapOf("executable" to "start", "args" to "https://example.com"))
                Result.success((outcome as LlmToolOutcome.Halt).text)
            },
        )

        val outcome = orchestrator.handle("abre o navegador") as LlmOutcome.NeedsConfirmation

        assertTrue(outcome.command.executable.lowercase().endsWith("cmd.exe"))
        assertEquals(listOf("/c", "start", "https://example.com"), outcome.command.args)
    }

    @Test
    fun `run_command never actually executes anything, only halts`() = runTest {
        var launchedAnything = false
        val orchestrator = orchestrator(
            ScriptedLlmProvider { _, _, _, toolExecutor ->
                val outcome = toolExecutor.execute("run_command", mapOf("executable" to "shutdown", "args" to "/s"))
                Result.success((outcome as LlmToolOutcome.Halt).text)
            },
            launchApp = { launchedAnything = true; Result.success(Unit) },
        )

        orchestrator.handle("desliga o pc")

        assertTrue(!launchedAnything)
    }

    @Test
    fun `read_file tool result flows back into the final answer`() = runTest {
        val filesRoot = tempFilesRoot()
        File(filesRoot, "notes.txt").writeText("comprar leite")

        val orchestrator = orchestrator(
            ScriptedLlmProvider { _, _, _, toolExecutor ->
                val outcome = toolExecutor.execute("read_file", mapOf("path" to "notes.txt")) as LlmToolOutcome.Result
                Result.success("o arquivo diz: ${outcome.text}")
            },
            filesRoot = filesRoot,
        )

        val outcome = orchestrator.handle("o que tem no notes.txt")

        assertEquals(LlmOutcome.Answer("o arquivo diz: comprar leite"), outcome)
    }

    @Test
    fun `run_macro delegates to the existing MacroExecutor and CommandMappingStore`() = runTest {
        val commandMappingStore = CommandMappingStore(tempCommandsFile())
        commandMappingStore.add("abrir navegador", listOf(LaunchAppStep("chrome")))
        val launched = mutableListOf<String>()

        val orchestrator = orchestrator(
            ScriptedLlmProvider { _, _, _, toolExecutor ->
                val outcome = toolExecutor.execute("run_macro", mapOf("trigger" to "abrir navegador")) as LlmToolOutcome.Result
                Result.success(outcome.text)
            },
            commandMappingStore = commandMappingStore,
            launchApp = { launched += it; Result.success(Unit) },
        )

        val outcome = orchestrator.handle("abre o navegador")

        assertEquals(listOf("chrome"), launched)
        assertEquals(LlmOutcome.Answer("macro 'abrir navegador' executada com sucesso"), outcome)
    }

    @Test
    fun `provider failure becomes Failed`() = runTest {
        val orchestrator = orchestrator(ScriptedLlmProvider { _, _, _, _ -> Result.failure(RuntimeException("rede fora do ar")) })

        val outcome = orchestrator.handle("qualquer coisa") as LlmOutcome.Failed

        assertEquals("rede fora do ar", outcome.reason)
    }
}
