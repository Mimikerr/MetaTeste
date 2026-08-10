package com.example.metateste.host.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/**
 * [LlmProvider] backed by any OpenAI-compatible chat-completions server — in practice, a local
 * LM Studio instance (http://127.0.0.1:1234 by default), so the brain can run fully offline with
 * no API key/cost. Same manual tool-use loop approach as [AnthropicLlmProvider], adapted to the
 * OpenAI wire format: `tool_calls`/`role: "tool"` instead of Anthropic's content-block `tool_use`,
 * and tool arguments arrive as a JSON-encoded *string* rather than a nested object.
 */
class LmStudioLlmProvider(
    private val model: String = "local-model",
    private val maxTurns: Int = 6,
    private val url: String = "http://127.0.0.1:1234/v1/chat/completions",
    private val apiKey: String? = System.getenv("NEXUS_LMSTUDIO_API_KEY"),
    private val client: HttpClient = defaultLmStudioHttpClient(),
) : LlmProvider {

    private val logger = LoggerFactory.getLogger(LmStudioLlmProvider::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun converse(
        systemPrompt: String,
        userText: String,
        tools: List<LlmTool>,
        toolExecutor: LlmToolExecutor,
    ): Result<String> = runCatching {
        val messages = mutableListOf(
            buildJsonObject { put("role", "system"); put("content", systemPrompt) },
            buildJsonObject { put("role", "user"); put("content", userText) },
        )

        repeat(maxTurns) {
            val message = callApi(tools, messages)
            val toolCalls = message["tool_calls"] as? JsonArray

            if (toolCalls == null || toolCalls.isEmpty()) {
                return@runCatching message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }

            messages += message

            val outcomes = toolCalls.map { call ->
                val obj = call.jsonObject
                val callId = obj.getValue("id").jsonPrimitive.content
                val function = obj.getValue("function").jsonObject
                val toolName = function.getValue("name").jsonPrimitive.content
                val argumentsJson = function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                val input = runCatching { json.parseToJsonElement(argumentsJson).jsonObject }
                    .getOrDefault(JsonObject(emptyMap()))
                    .mapValues { (_, value) -> value.jsonPrimitive.contentOrNull ?: value.toString() }
                Triple(callId, toolName, toolExecutor.execute(toolName, input))
            }

            val halted = outcomes.firstNotNullOfOrNull { (_, _, outcome) -> (outcome as? LlmToolOutcome.Halt)?.text }
            if (halted != null) {
                return@runCatching halted
            }

            outcomes.forEach { (callId, _, outcome) ->
                messages += buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", callId)
                    put("content", (outcome as LlmToolOutcome.Result).text)
                }
            }
        }

        error("o cérebro (LM Studio) não chegou a uma resposta final em $maxTurns turnos")
    }.onFailure {
        logger.warn("LM Studio não respondeu corretamente em {}: {}", url, it.message)
    }

    private suspend fun callApi(tools: List<LlmTool>, messages: List<JsonObject>): JsonObject {
        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") { messages.forEach { add(it) } }
            if (tools.isNotEmpty()) {
                putJsonArray("tools") { tools.forEach { add(it.toJsonSchema()) } }
            }
        }
        val responseText = client.post(url) {
            apiKey?.let { header("Authorization", "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonElement.serializer(), requestBody))
        }.bodyAsText()

        val response = json.parseToJsonElement(responseText).jsonObject
        val choices = response["choices"]?.jsonArray
        require(!choices.isNullOrEmpty()) { "resposta do LM Studio sem 'choices': $responseText" }
        return choices.first().jsonObject.getValue("message").jsonObject
    }

    private fun LlmTool.toJsonSchema(): JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", name)
            put("description", description)
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    parameters.properties.forEach { (key, prop) ->
                        putJsonObject(key) {
                            put("type", prop.type)
                            put("description", prop.description)
                        }
                    }
                }
                putJsonArray("required") { parameters.required.forEach { add(it) } }
            }
        }
    }
}

private fun defaultLmStudioHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = true
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        // Local models can be slow to respond, especially right after being (re)loaded. Both
        // requestTimeoutMillis AND socketTimeoutMillis need to be set — otherwise the OkHttp
        // engine falls back to its own default 10s read timeout, which a 7B local model easily
        // blows past on a cold first request.
        requestTimeoutMillis = 120_000
        socketTimeoutMillis = 120_000
    }
}
