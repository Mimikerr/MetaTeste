package com.example.metateste.host.commands

import com.example.metateste.host.automation.AppLauncher
import com.example.metateste.host.automation.KeyCodeParser
import com.example.metateste.host.automation.KeyboardActuator
import com.example.metateste.host.automation.TerminalInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Runs a [VoiceCommandMapping]'s steps in order, stopping at the first failure.
 *
 * Depends on plain functions rather than [AppLauncher]/[KeyboardActuator] directly (both are
 * final classes with no interface) so tests can substitute fakes without touching real
 * processes/`Robot`; see the [ofReal] factory for how production wiring supplies them.
 */
class MacroExecutor(
    private val launchApp: (executablePath: String) -> Result<Unit>,
    private val injectText: (text: String) -> Result<Unit>,
    private val pressChord: (keyCodes: List<Int>) -> Result<Unit>,
) {
    data class StepResult(val index: Int, val step: CommandStep, val result: Result<Unit>)

    data class MacroResult(val stepResults: List<StepResult>, val totalSteps: Int) {
        val isSuccess: Boolean get() = stepResults.isNotEmpty() && stepResults.all { it.result.isSuccess }
    }

    suspend fun execute(steps: List<CommandStep>): MacroResult {
        val results = mutableListOf<StepResult>()
        for ((index, step) in steps.withIndex()) {
            val result = runStep(step)
            results += StepResult(index, step, result)
            if (result.isFailure) break
        }
        return MacroResult(results, totalSteps = steps.size)
    }

    private suspend fun runStep(step: CommandStep): Result<Unit> = when (step) {
        is DelayStep -> runCatching { delay(step.milliseconds.coerceIn(0, MAX_DELAY_MS)) }
        is LaunchAppStep -> withContext(Dispatchers.IO) { launchApp(step.executablePath) }
        is TypeTextStep -> withContext(Dispatchers.IO) { injectText(step.text) }
        is KeyShortcutStep -> withContext(Dispatchers.IO) {
            KeyCodeParser.parseChord(step.keys).fold(
                onSuccess = { codes -> pressChord(codes) },
                onFailure = { Result.failure(it) },
            )
        }
    }

    companion object {
        const val MAX_DELAY_MS = 10_000L

        fun ofReal(appLauncher: AppLauncher, terminalInjector: TerminalInjector, keyboard: KeyboardActuator) =
            MacroExecutor(
                launchApp = appLauncher::launch,
                injectText = terminalInjector::inject,
                pressChord = { codes -> runCatching { keyboard.pressChord(*codes.toIntArray()) } },
            )
    }
}

/** Human-readable pt-BR label used both in ack details and (mirrored manually) in the config UI. */
fun stepKindLabel(step: CommandStep): String = when (step) {
    is LaunchAppStep -> "abrir aplicativo"
    is TypeTextStep -> "digitar texto"
    is KeyShortcutStep -> "atalho de teclado"
    is DelayStep -> "espera"
}

/** Renders a [MacroExecutor.MacroResult] into a single-line [com.example.metateste.shared.CommandAck.detail]. */
fun MacroExecutor.MacroResult.toAckDetail(): String? {
    if (isSuccess) return null
    val failed = stepResults.lastOrNull() ?: return "a macro não tem nenhum passo configurado"
    val stepNumber = failed.index + 1
    val message = failed.result.exceptionOrNull()?.message ?: "erro desconhecido"
    return "passo $stepNumber/$totalSteps (${stepKindLabel(failed.step)}) falhou: $message"
}
