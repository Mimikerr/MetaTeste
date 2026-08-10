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
 * Exercises the OpenAI-compatible wire format (tool_calls with stringified JSON arguments,
 * role:"tool" results) against a mocked HTTP transport — no real LM Studio instance needed.
 */
class LmStudioLlmProviderTest {

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
        val response = """{"choices":[{"message":{"role":"assistant","content":"São 14h32 em Brasília."},"finish_reason":"stop"}]}"""
        val provider = LmStudioLlmProvider(client = mockClient(response))

        val result = provider.converse("system", "que horas são", emptyList(), LlmToolExecutor { _, _ -> error("não deveria chamar nenhuma ferramenta") })

        assertEquals("São 14h32 em Brasília.", result.getOrThrow())
    }

    @Test
    fun `parses stringified tool_calls arguments, then folds the tool result into the final answer`() = runTest {
        val toolCallResponse = """
            {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
              {"id":"call_1","type":"function","function":{"name":"read_file","arguments":"{\"path\":\"notes.txt\"}"}}
            ]},"finish_reason":"tool_calls"}]}
        """.trimIndent()
        val finalResponse = """{"choices":[{"message":{"role":"assistant","content":"o arquivo diz: comprar leite"},"finish_reason":"stop"}]}"""
        val provider = LmStudioLlmProvider(client = mockClient(toolCallResponse, finalResponse))
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
        val toolCallResponse = """
            {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
              {"id":"call_1","type":"function","function":{"name":"run_command","arguments":"{\"executable\":\"git\",\"args\":\"status\"}"}}
            ]},"finish_reason":"tool_calls"}]}
        """.trimIndent()
        var callCount = 0
        val provider = LmStudioLlmProvider(client = mockClient(toolCallResponse, onCall = { callCount++ }))

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
        val engine = MockEngine { respond(content = "server error", status = HttpStatusCode.InternalServerError) }
        val client = HttpClient(engine) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 5_000 }
        }
        val provider = LmStudioLlmProvider(client = client)

        val result = provider.converse("system", "oi", emptyList(), LlmToolExecutor { _, _ -> error("não deveria chamar") })

        assertTrue(result.isFailure)
    }
}
