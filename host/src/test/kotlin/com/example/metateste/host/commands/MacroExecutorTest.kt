package com.example.metateste.host.commands

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroExecutorTest {

    private fun executor(
        launchApp: (String) -> Result<Unit> = { Result.success(Unit) },
        injectText: (String) -> Result<Unit> = { Result.success(Unit) },
        pressChord: (List<Int>) -> Result<Unit> = { Result.success(Unit) },
    ) = MacroExecutor(launchApp, injectText, pressChord)

    @Test
    fun `runs all steps in order on success`() = runTest {
        val calls = mutableListOf<String>()
        val executor = executor(
            launchApp = { path -> calls += "launch:$path"; Result.success(Unit) },
            injectText = { text -> calls += "type:$text"; Result.success(Unit) },
            pressChord = { codes -> calls += "chord:$codes"; Result.success(Unit) },
        )

        val result = executor.execute(
            listOf(LaunchAppStep("calc.exe"), DelayStep(1), KeyShortcutStep(listOf("ALT", "F4"))),
        )

        assertTrue(result.isSuccess)
        assertEquals(3, result.stepResults.size)
        assertEquals(listOf("launch:calc.exe", "chord:[18, 115]"), calls) // VK_ALT=18, VK_F4=115
    }

    @Test
    fun `stops at the first failing step and does not run later steps`() = runTest {
        val calls = mutableListOf<String>()
        val executor = executor(
            launchApp = { calls += "launch"; Result.failure(RuntimeException("boom")) },
            injectText = { calls += "type"; Result.success(Unit) },
        )

        val result = executor.execute(listOf(LaunchAppStep("x"), TypeTextStep("never runs")))

        assertEquals(listOf("launch"), calls)
        assertEquals(1, result.stepResults.size)
        assertEquals(false, result.isSuccess)
    }

    @Test
    fun `empty step list is not a success`() = runTest {
        val result = executor().execute(emptyList())
        assertEquals(false, result.isSuccess)
    }

    @Test
    fun `key_shortcut step fails cleanly when a key name is unknown`() = runTest {
        val result = executor().execute(listOf(KeyShortcutStep(listOf("BANANA"))))
        assertEquals(false, result.isSuccess)
        assertTrue(result.stepResults.single().result.exceptionOrNull()?.message?.contains("BANANA") == true)
    }

    @Test
    fun `toAckDetail is null on success and describes the failing step otherwise`() = runTest {
        val successResult = executor().execute(listOf(DelayStep(1)))
        assertEquals(null, successResult.toAckDetail())

        val failureResult = executor(
            launchApp = { Result.success(Unit) },
            injectText = { Result.failure(RuntimeException("janela errada")) },
        ).execute(listOf(LaunchAppStep("x"), TypeTextStep("y")))
        assertEquals("passo 2/2 (digitar texto) falhou: janela errada", failureResult.toAckDetail())
    }

    @Test
    fun `delay step is clamped to the maximum`() = runTest {
        val result = executor().execute(listOf(DelayStep(MacroExecutor.MAX_DELAY_MS * 100)))
        assertTrue(result.isSuccess)
    }
}
