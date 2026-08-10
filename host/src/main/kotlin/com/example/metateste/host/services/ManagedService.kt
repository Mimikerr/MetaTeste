package com.example.metateste.host.services

import java.io.File
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
enum class ServiceState { STOPPED, STARTING, RUNNING, STOPPING, FAILED }

@Serializable
data class ServiceStatus(
    val id: String,
    val name: String,
    val state: ServiceState,
    val pid: Long? = null,
    val lastError: String? = null,
    val recentLog: List<String> = emptyList(),
)

/**
 * Launches and supervises one external process — in practice, one of the local Python
 * microservices (whisper-service/tts-service) — so they can be started/stopped from the webui
 * instead of a separate terminal. Captures stdout+stderr into a bounded ring buffer for
 * visibility into failures. Reflects OS process liveness only — it does not health-check the
 * HTTP server inside the process, which may still be warming up after [status] reports RUNNING.
 */
class ManagedService(
    val id: String,
    val name: String,
    private val workingDir: File,
    private val command: List<String>,
) {
    private val logger = LoggerFactory.getLogger("ManagedService.$id")
    private val lock = Any()
    private val logBuffer = ConcurrentLinkedDeque<String>()

    private var process: Process? = null
    private var state: ServiceState = ServiceState.STOPPED
    private var lastError: String? = null

    fun status(): ServiceStatus = synchronized(lock) {
        ServiceStatus(id, name, state, process?.takeIf { it.isAlive }?.pid(), lastError, logBuffer.toList())
    }

    fun start(): Result<Unit> = synchronized(lock) {
        if (state == ServiceState.RUNNING || state == ServiceState.STARTING) return Result.success(Unit)

        val executable = File(command.first())
        if (!executable.isFile) {
            val message = "executável não encontrado: ${executable.absolutePath} (rodou o setup do venv? veja o README do serviço)"
            state = ServiceState.FAILED
            lastError = message
            return Result.failure(IllegalStateException(message))
        }

        return runCatching {
            logBuffer.clear()
            lastError = null
            val proc = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
            process = proc
            state = ServiceState.RUNNING
            OutputPumpThread(proc, logBuffer).start()
            ExitWatcherThread(proc, this).start()
        }.onFailure {
            state = ServiceState.FAILED
            lastError = it.message
            logger.warn("falha ao iniciar {}: {}", name, it.message)
        }
    }

    fun stop() {
        val proc = synchronized(lock) {
            val current = process ?: return
            state = ServiceState.STOPPING
            current
        }
        proc.destroy()
        Thread {
            if (!proc.waitFor(5, TimeUnit.SECONDS)) proc.destroyForcibly()
        }.start()
    }

    /** Invoked by [ExitWatcherThread] once the OS process has actually exited, from a background thread. */
    private fun onProcessExited(exitedProcess: Process, exitCode: Int) {
        synchronized(lock) {
            if (process !== exitedProcess) return // superseded by a newer start()
            process = null
            if (state == ServiceState.STOPPING) {
                state = ServiceState.STOPPED
            } else {
                state = ServiceState.FAILED
                lastError = "processo terminou sozinho (exit code $exitCode)"
                logger.warn("{} terminou inesperadamente (exit code {})", name, exitCode)
            }
        }
    }

    private class OutputPumpThread(private val process: Process, private val logBuffer: ConcurrentLinkedDeque<String>) : Thread() {
        override fun run() {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    logBuffer.addLast(line)
                    while (logBuffer.size > MAX_LOG_LINES) logBuffer.pollFirst()
                }
            }
        }
    }

    private class ExitWatcherThread(private val process: Process, private val owner: ManagedService) : Thread() {
        override fun run() {
            val exitCode = process.waitFor()
            owner.onProcessExited(process, exitCode)
        }
    }

    companion object {
        private const val MAX_LOG_LINES = 200
    }
}
