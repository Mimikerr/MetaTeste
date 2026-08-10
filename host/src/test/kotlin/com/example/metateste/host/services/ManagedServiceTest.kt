package com.example.metateste.host.services

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedServiceTest {

    private val cmdExe = System.getenv("ComSpec") ?: "C:\\Windows\\System32\\cmd.exe"
    private val workingDir = File(".")

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("condição não satisfeita em ${timeoutMs}ms", condition())
    }

    @Test
    fun `starts a long-running process and reports it as running with a pid`() {
        val service = ManagedService("test", "Teste", workingDir, listOf(cmdExe, "/c", "ping", "-n", "20", "127.0.0.1"))

        val result = service.start()

        assertTrue(result.isSuccess)
        waitUntil { service.status().state == ServiceState.RUNNING }
        assertNotNull(service.status().pid)

        service.stop()
        waitUntil { service.status().state == ServiceState.STOPPED }
    }

    @Test
    fun `fails to start when the executable does not exist`() {
        val service = ManagedService("test", "Teste", workingDir, listOf("C:\\does\\not\\exist.exe"))

        val result = service.start()

        assertTrue(result.isFailure)
        assertEquals(ServiceState.FAILED, service.status().state)
    }

    @Test
    fun `moves to FAILED when the process exits on its own without being stopped`() {
        val service = ManagedService("test", "Teste", workingDir, listOf(cmdExe, "/c", "exit", "3"))

        service.start()

        waitUntil { service.status().state == ServiceState.FAILED }
        assertTrue(service.status().lastError?.contains("3") == true)
    }

    @Test
    fun `captures stdout into the recent log`() {
        val service = ManagedService("test", "Teste", workingDir, listOf(cmdExe, "/c", "echo", "hello from test"))

        service.start()

        waitUntil { service.status().recentLog.any { it.contains("hello from test") } }
    }

    @Test
    fun `calling start twice while already running is a no-op`() {
        val service = ManagedService("test", "Teste", workingDir, listOf(cmdExe, "/c", "ping", "-n", "20", "127.0.0.1"))

        service.start()
        waitUntil { service.status().state == ServiceState.RUNNING }
        val pidAfterFirstStart = service.status().pid

        val secondResult = service.start()

        assertTrue(secondResult.isSuccess)
        assertEquals(pidAfterFirstStart, service.status().pid)

        service.stop()
        waitUntil { service.status().state == ServiceState.STOPPED }
    }
}
