package com.example.metateste.host.automation

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessRunnerTest {

    private val runner = ProcessRunner()

    @Test
    fun `captures stdout and exit code on success`() {
        val result = runner.run("cmd", listOf("/c", "echo", "hello"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("hello"))
        assertFalse(result.timedOut)
    }

    @Test
    fun `captures non-zero exit code`() {
        val result = runner.run("cmd", listOf("/c", "exit", "7"))

        assertEquals(7, result.exitCode)
        assertFalse(result.timedOut)
    }

    @Test
    fun `kills the process and marks timedOut when it exceeds the timeout`() {
        val result = runner.run("cmd", listOf("/c", "ping", "-n", "30", "127.0.0.1"), timeout = 300.milliseconds)

        assertTrue(result.timedOut)
        assertEquals(-1, result.exitCode)
    }

    @Test
    fun `truncates output beyond maxOutputBytes`() {
        val result = runner.run("cmd", listOf("/c", "echo", "A".repeat(5000)), maxOutputBytes = 100, timeout = 10.seconds)

        assertTrue(result.stdout.endsWith("...[truncado]"))
        assertTrue(result.stdout.length <= 100 + "...[truncado]".length)
    }

    @Test
    fun `returns a failed result instead of throwing when the executable can't even start`() {
        // "start" is a cmd.exe builtin, not a real file ProcessBuilder can find on its own —
        // this is exactly the shape of mistake an LLM-chosen executable can make.
        val result = runner.run("start", listOf("https://example.com"))

        assertEquals(-1, result.exitCode)
        assertFalse(result.timedOut)
        assertTrue(result.stderr.contains("erro ao iniciar processo"))
    }
}
