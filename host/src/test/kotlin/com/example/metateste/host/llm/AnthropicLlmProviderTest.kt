package com.example.metateste.host.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises tool_use JSON parsing against a mocked HTTP transport (no real network/API key
 * needed) — getting this parsing wrong would mean calling the wrong tool with the wrong input.
 */
class AnthropicLlmProviderTest {

    private fun mockClient(vararg responses: String, onCall: (() -> Unit)? = null): HttpClient {
        var callIndex = 0
        val engine = MockEngine {
            onCall?.invoke()
            val body = responses[callIndex.coerceAtMost(responses.lastIndex)]
            callIndex++
            respond(content = body, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return HttpClient(engine) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 5_000 }
        }
    }

    @Test
    fun `parses a plain text response into a final answer`() = runTest {
        val response = """{"content":[{"type":"text","text":"São 14h32 em Brasília."}],"stop_reason":"end_turn"}"""
        val provider = AnthropicLlmProvider(apiKey = "test-key", client = mockClient(response))

        val result = provider.converse("system", "que horas são", emptyList(), LlmToolExecutor { _, _ -> error("não deveria chamar nenhuma ferramenta") })

        assertEquals("São 14h32 em Brasília.", result.getOrThrow())
    }

    @Test
    fun `calls the right tool with parsed input, then folds the tool_result into the final answer`() = runTest {
        val toolUseResponse =
            """{"content":[{"type":"tool_use","id":"call_1","name":"read_file","input":{"path":"notes.txt"}}],"stop_reason":"tool_use"}"""
        val finalResponse = """{"content":[{"type":"text","text":"o arquivo diz: comprar leite"}],"stop_reason":"end_turn"}"""
        val provider = AnthropicLlmProvider(apiKey = "test-key", client = mockClient(toolUseResponse, finalResponse))
        val calls = mutableListOf<Pair<String, Map<String, String>>>()

        val result = provider.converse(
            "system",
            "o que tem no notes.txt",
            listOf(LlmTool("read_file", "lê arquivo", LlmToolParameters(mapOf("path" to LlmToolProperty("string", "caminho"))))),
            LlmToolExecutor { name, input ->
                calls += name to input
                LlmToolOutcome.Result("comprar leite")
            },
        )

        assertEquals(listOf("read_file" to mapOf("path" to "notes.txt")), calls)
        assertEquals("o arquivo diz: comprar leite", result.getOrThrow())
    }

    @Test
    fun `stops the loop immediately when a tool halts, without a second API call`() = runTest {
        val toolUseResponse =
            """{"content":[{"type":"tool_use","id":"call_1","name":"run_command","input":{"executable":"git","args":"status"}}],"stop_reason":"tool_use"}"""
        var callCount = 0
        val provider = AnthropicLlmProvider(apiKey = "test-key", client = mockClient(toolUseResponse, onCall = { callCount++ }))

        val result = provider.converse(
            "system",
            "roda git status",
            listOf(LlmTool("run_command", "roda comando", LlmToolParameters(emptyMap()))),
            LlmToolExecutor { _, _ -> LlmToolOutcome.Halt("Quer que eu rode isso?") },
        )

        assertEquals("Quer que eu rode isso?", result.getOrThrow())
        assertEquals(1, callCount)
    }

    @Test
    fun `surfaces HTTP failures as a Result failure instead of throwing`() = runTest {
        val engine = MockEngine { respond(content = "unauthorized", status = HttpStatusCode.Unauthorized) }
        val client = HttpClient(engine) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 5_000 }
        }
        val provider = AnthropicLlmProvider(apiKey = "bad-key", client = client)

        val result = provider.converse("system", "oi", emptyList(), LlmToolExecutor { _, _ -> error("não deveria chamar") })

        assertTrue(result.isFailure)
    }
}
