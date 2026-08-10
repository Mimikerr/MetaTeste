package com.example.metateste.host.llm

/** A tool the LLM brain can call. All parameters are declared as strings to keep providers simple. */
data class LlmTool(
    val name: String,
    val description: String,
    val parameters: LlmToolParameters,
)

data class LlmToolParameters(
    val properties: Map<String, LlmToolProperty>,
    val required: List<String> = emptyList(),
)

data class LlmToolProperty(val type: String, val description: String)

/** What happens after a tool call executes. */
sealed interface LlmToolOutcome {
    /** Fed back to the model as the tool's result; the agentic loop continues. */
    data class Result(val text: String) : LlmToolOutcome

    /** Stops the loop immediately — no further model call is made, [text] becomes the final answer. */
    data class Halt(val text: String) : LlmToolOutcome
}

/** Invoked by [LlmProvider] implementations whenever the model calls a tool. */
fun interface LlmToolExecutor {
    suspend fun execute(toolName: String, input: Map<String, String>): LlmToolOutcome
}

/**
 * A pluggable LLM "brain": runs a full agentic tool-use loop (calling the model, executing tool
 * calls via [LlmToolExecutor], feeding results back) and returns the model's final answer text.
 */
interface LlmProvider {
    suspend fun converse(systemPrompt: String, userText: String, tools: List<LlmTool>, toolExecutor: LlmToolExecutor): Result<String>
}

/** Used when no LLM provider is configured — the brain is simply unavailable, never silently wrong. */
object NullLlmProvider : LlmProvider {
    override suspend fun converse(
        systemPrompt: String,
        userText: String,
        tools: List<LlmTool>,
        toolExecutor: LlmToolExecutor,
    ): Result<String> = Result.failure(IllegalStateException("nenhum provedor de LLM configurado (defina NEXUS_ANTHROPIC_API_KEY)"))
}
