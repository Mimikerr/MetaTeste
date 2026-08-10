package com.example.metateste.host.llm

import com.example.metateste.host.chat.ChatEventBus
import com.example.metateste.host.chat.ToolCallEvent
import com.example.metateste.host.chat.ToolResultEvent
import com.example.metateste.host.commands.CommandMappingStore
import com.example.metateste.host.commands.MacroExecutor
import com.example.metateste.host.commands.toAckDetail

data class PendingCommand(val executable: String, val args: List<String>, val timeoutSeconds: Int)

sealed interface LlmOutcome {
    data class Answer(val text: String) : LlmOutcome
    data class NeedsConfirmation(val question: String, val command: PendingCommand) : LlmOutcome
    data class Failed(val reason: String) : LlmOutcome
}

/**
 * Drives one voice-command turn through the LLM: exposes `read_file`/`run_command`/`run_macro`
 * as tools and turns the model's final answer (or an early `run_command` halt) into an
 * [LlmOutcome]. `run_command` never actually runs here — it only records a [PendingCommand] and
 * halts the loop, so the caller can gate execution on the user's voice confirmation.
 */
class LlmOrchestrator(
    private val provider: LlmProvider,
    private val fileReader: FileReaderTool,
    private val commandMappingStore: CommandMappingStore,
    private val macroExecutor: MacroExecutor,
) {
    suspend fun handle(userText: String): LlmOutcome {
        var pendingCommand: PendingCommand? = null

        val toolExecutor = LlmToolExecutor { toolName, input ->
            ChatEventBus.publish(ToolCallEvent(System.currentTimeMillis(), toolName, input))
            val outcome = when (toolName) {
                "read_file" -> readFile(input)
                "run_command" -> {
                    val command = parsePendingCommand(input)
                    pendingCommand = command
                    val commandLine = (listOf(command.executable) + command.args).joinToString(" ")
                    LlmToolOutcome.Halt("Quer que eu rode \"$commandLine\"?")
                }
                "run_macro" -> runMacro(input)
                else -> LlmToolOutcome.Result("ferramenta desconhecida: $toolName")
            }
            val outputText = when (outcome) {
                is LlmToolOutcome.Result -> outcome.text
                is LlmToolOutcome.Halt -> "(aguardando confirmação do usuário)"
            }
            ChatEventBus.publish(ToolResultEvent(System.currentTimeMillis(), toolName, outputText))
            outcome
        }

        val result = provider.converse(SYSTEM_PROMPT, userText, TOOLS, toolExecutor)

        val command = pendingCommand
        if (command != null) {
            return LlmOutcome.NeedsConfirmation(result.getOrElse { DEFAULT_CONFIRMATION_QUESTION }, command)
        }
        return result.fold(
            onSuccess = { LlmOutcome.Answer(it) },
            onFailure = { LlmOutcome.Failed(it.message ?: "erro desconhecido ao consultar o cérebro") },
        )
    }

    private fun readFile(input: Map<String, String>): LlmToolOutcome {
        val path = input["path"].orEmpty()
        return fileReader.read(path).fold(
            onSuccess = { LlmToolOutcome.Result(it) },
            onFailure = { LlmToolOutcome.Result("erro ao ler arquivo: ${it.message}") },
        )
    }

    private suspend fun runMacro(input: Map<String, String>): LlmToolOutcome {
        val trigger = input["trigger"].orEmpty()
        val matched = commandMappingStore.all().firstOrNull { it.trigger.equals(trigger, ignoreCase = true) }
            ?: return LlmToolOutcome.Result("macro '$trigger' não encontrada")
        val macroResult = macroExecutor.execute(matched.steps)
        return LlmToolOutcome.Result(
            if (macroResult.isSuccess) "macro '$trigger' executada com sucesso" else "macro '$trigger' falhou: ${macroResult.toAckDetail()}",
        )
    }

    private fun parsePendingCommand(input: Map<String, String>): PendingCommand {
        val requestedExecutable = input["executable"].orEmpty()
        val requestedArgs = input["args"].orEmpty().split(Regex("\\s+")).filter { it.isNotBlank() }
        val timeoutSeconds = (input["timeout_seconds"]?.toIntOrNull() ?: 20).coerceIn(1, 60)

        // "start"/"dir"/etc. are cmd.exe builtins, not files ProcessBuilder can find on its own —
        // route those through cmd /c so e.g. "abrir o navegador" -> start <url> actually works.
        if (requestedExecutable.lowercase() in CMD_BUILTINS) {
            val comspec = System.getenv("ComSpec") ?: "cmd.exe"
            return PendingCommand(comspec, listOf("/c", requestedExecutable) + requestedArgs, timeoutSeconds)
        }
        return PendingCommand(requestedExecutable, requestedArgs, timeoutSeconds)
    }

    companion object {
        private const val DEFAULT_CONFIRMATION_QUESTION = "Posso rodar esse comando?"

        private val CMD_BUILTINS = setOf(
            "start", "dir", "echo", "cd", "type", "copy", "del", "move", "mkdir", "rmdir", "cls", "set", "title", "ver", "where",
        )

        private const val SYSTEM_PROMPT = """
Você é o assistente de voz que roda no PC do usuário, controlado por um headset Meta Quest.
Responda sempre em português do Brasil, de forma breve — sua resposta será falada em voz alta.
Use as ferramentas disponíveis quando fizer sentido: leia arquivos, rode macros já cadastradas,
ou rode comandos de terminal. A confirmação do usuário para rodar um comando é tratada fora do
seu controle — não afirme que um comando já rodou até ver o resultado dele. Se não tiver certeza
da intenção do usuário, pergunte antes de agir.
"""

        private val TOOLS = listOf(
            LlmTool(
                name = "read_file",
                description = "Lê o conteúdo de um arquivo de texto dentro da área de arquivos permitida do usuário.",
                parameters = LlmToolParameters(
                    properties = mapOf(
                        "path" to LlmToolProperty("string", "Caminho relativo à área de arquivos permitida, ex.: notas/hoje.txt"),
                    ),
                    required = listOf("path"),
                ),
            ),
            LlmTool(
                name = "run_command",
                description = "Roda um comando de terminal e devolve stdout/stderr capturados. Sempre exige confirmação do usuário antes de rodar de verdade. " +
                    "Para abrir uma URL, arquivo ou pasta no programa padrão do Windows, use executable=\"start\" com a URL/caminho em args.",
                parameters = LlmToolParameters(
                    properties = mapOf(
                        "executable" to LlmToolProperty("string", "Nome ou caminho do executável, ex.: git, npm, python, start"),
                        "args" to LlmToolProperty("string", "Argumentos separados por espaço, ex.: status --short, ou uma URL para \"start\""),
                        "timeout_seconds" to LlmToolProperty("string", "Tempo máximo em segundos, entre 1 e 60 (padrão 20)"),
                    ),
                    required = listOf("executable"),
                ),
            ),
            LlmTool(
                name = "run_macro",
                description = "Executa uma macro já cadastrada pelo usuário, pelo texto exato do seu gatilho (trigger).",
                parameters = LlmToolParameters(
                    properties = mapOf("trigger" to LlmToolProperty("string", "O trigger exato da macro cadastrada")),
                    required = listOf("trigger"),
                ),
            ),
        )
    }
}
