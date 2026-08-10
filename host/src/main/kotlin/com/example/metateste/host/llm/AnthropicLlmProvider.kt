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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/**
 * [LlmProvider] backed by the Anthropic Messages API, called via a plain HTTP request (same
 * pattern as [com.example.metateste.host.voice.WhisperTranscriptionService]/WitAi — no SDK) so
 * the tool-use loop can be driven manually and paused on a `run_command` [LlmToolOutcome.Halt].
 */
class AnthropicLlmProvider(
    private val apiKey: String,
    private val model: String = "claude-sonnet-5",
    private val maxTurns: Int = 6,
    private val url: String = "https://api.anthropic.com/v1/messages",
    private val client: HttpClient = defaultHttpClient(),
) : LlmProvider {

    private val logger = LoggerFactory.getLogger(AnthropicLlmProvider::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun converse(
        systemPrompt: String,
        userText: String,
        tools: List<LlmTool>,
        toolExecutor: LlmToolExecutor,
    ): Result<String> = runCatching {
        val messages = mutableListOf(userMessage(userText))

        repeat(maxTurns) {
            val response = callApi(systemPrompt, tools, messages)
            val contentBlocks = (response["content"] as? JsonArray) ?: JsonArray(emptyList())
            val stopReason = response["stop_reason"]?.jsonPrimitive?.contentOrNull

            if (stopReason != "tool_use") {
                return@runCatching extractText(contentBlocks)
            }

            messages += buildJsonObject {
                put("role", "assistant")
                put("content", contentBlocks)
            }

            val toolUseBlocks = contentBlocks.mapNotNull { block ->
                block.jsonObject.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "tool_use" }
            }
            val outcomes = toolUseBlocks.map { block ->
                val toolUseId = block.getValue("id").jsonPrimitive.content
                val toolName = block.getValue("name").jsonPrimitive.content
                val input = (block["input"] as? JsonObject).orEmpty()
                    .mapValues { (_, value) -> value.jsonPrimitive.contentOrNull ?: value.toString() }
                Triple(toolUseId, toolName, toolExecutor.execute(toolName, input))
            }

            val halted = outcomes.firstNotNullOfOrNull { (_, _, outcome) -> (outcome as? LlmToolOutcome.Halt)?.text }
            if (halted != null) {
                return@runCatching halted
            }

            messages += buildJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    outcomes.forEach { (toolUseId, _, outcome) ->
                        add(
                            buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", toolUseId)
                                put("content", (outcome as LlmToolOutcome.Result).text)
                            },
                        )
                    }
                }
            }
        }

        error("o cérebro não chegou a uma resposta final em $maxTurns turnos")
    }.onFailure {
        logger.warn("Anthropic não respondeu corretamente: {}", it.message)
    }

    private suspend fun callApi(systemPrompt: String, tools: List<LlmTool>, messages: List<JsonObject>): JsonObject {
        val requestBody = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            put("system", systemPrompt)
            putJsonArray("tools") { tools.forEach { add(it.toJsonSchema()) } }
            putJsonArray("messages") { messages.forEach { add(it) } }
        }
        val responseText = client.post(url) {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonElement.serializer(), requestBody))
        }.bodyAsText()
        return json.parseToJsonElement(responseText).jsonObject
    }

    private fun extractText(contentBlocks: JsonArray): String =
        contentBlocks.joinToString("") { block ->
            val obj = block.jsonObject
            if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty() else ""
        }

    private fun userMessage(text: String) = buildJsonObject {
        put("role", "user")
        putJsonArray("content") { add(textBlock(text)) }
    }

    private fun textBlock(text: String) = buildJsonObject {
        put("type", "text")
        put("text", text)
    }

    private fun LlmTool.toJsonSchema(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        putJsonObject("input_schema") {
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

private fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = true
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        // Both requestTimeoutMillis AND socketTimeoutMillis need to be set — otherwise the
        // OkHttp engine falls back to its own default 10s read timeout, which a long tool-use
        // turn can easily exceed.
        requestTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
    }
}
