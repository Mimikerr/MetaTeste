package com.example.metateste.host.automation

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs an executable and captures its stdout/stderr, unlike [AppLauncher.launch] which is
 * fire-and-forget. Always applies a timeout and a cap on captured output, regardless of caller
 * settings — these guard rails are not optional.
 */
class ProcessRunner {

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean,
    )

    /**
     * [args] are passed straight to [ProcessBuilder] with no shell involved, so `&&`/`|`/`;` are
     * inert. Never throws — an executable that can't even be started (typo, missing file, a
     * cmd.exe builtin like `start` with no real file behind it, permissions, ...) comes back as a
     * normal failed [Result] instead of an exception, since this is driven by an LLM's guesses and
     * must not be able to take down the caller (e.g. the whole voice session) just by naming a bad
     * executable.
     */
    fun run(
        executable: String,
        args: List<String> = emptyList(),
        workingDir: File? = null,
        timeout: Duration = 20.seconds,
        maxOutputBytes: Int = 8_000,
    ): Result {
        val process = try {
            ProcessBuilder(listOf(executable) + args)
                .apply { if (workingDir != null) directory(workingDir) }
                .start()
        } catch (e: IOException) {
            return Result(exitCode = -1, stdout = "", stderr = "erro ao iniciar processo: ${e.message}", timedOut = false)
        }

        val stdoutReader = StreamReaderThread(process.inputStream, maxOutputBytes).apply { start() }
        val stderrReader = StreamReaderThread(process.errorStream, maxOutputBytes).apply { start() }

        val finished = process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        val timedOut = !finished
        if (timedOut) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
        }
        stdoutReader.join(2_000)
        stderrReader.join(2_000)

        return Result(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdoutReader.output(),
            stderr = stderrReader.output(),
            timedOut = timedOut,
        )
    }

    /** Drains [stream] on its own thread (stdout/stderr must be read concurrently to avoid deadlocking a full pipe). */
    private class StreamReaderThread(private val stream: InputStream, private val maxBytes: Int) : Thread() {
        private val buffer = ByteArrayOutputStream(maxBytes.coerceIn(1, 4096))

        @Volatile
        private var truncated = false

        override fun run() {
            val chunk = ByteArray(4096)
            while (true) {
                val read = try {
                    stream.read(chunk)
                } catch (_: IOException) {
                    -1
                }
                if (read == -1) break
                synchronized(buffer) {
                    val remaining = maxBytes - buffer.size()
                    when {
                        remaining <= 0 -> truncated = true
                        read > remaining -> {
                            buffer.write(chunk, 0, remaining)
                            truncated = true
                        }
                        else -> buffer.write(chunk, 0, read)
                    }
                }
            }
        }

        fun output(): String = synchronized(buffer) {
            val text = buffer.toString(Charsets.UTF_8)
            if (truncated) "$text...[truncado]" else text
        }
    }
}
