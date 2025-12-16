package ru.sber.cb.aichallenge_one.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import ru.sber.cb.aichallenge_one.domain.ConversationMessage

/**
 * OpenRouter Tool Definition (OpenAI-compatible format)
 */
@Serializable
data class OpenRouterTool(
    val type: String = "function",
    val function: OpenRouterFunction
)

@Serializable
data class OpenRouterFunction(
    val name: String,
    val description: String,
    val parameters: JsonElement  // JSON Schema for parameters
)

/**
 * Tool call in assistant's response
 */
@Serializable
data class OpenRouterToolCall(
    val id: String,
    val type: String = "function",
    val function: OpenRouterFunctionCall
)

@Serializable
data class OpenRouterFunctionCall(
    val name: String,
    val arguments: String  // JSON string of arguments
)

/**
 * Extended OpenAI message that supports tool calls
 */
@Serializable
data class OpenAIMessageWithTools(
    override val role: String,
    override val content: String,
    val tool_calls: List<OpenRouterToolCall>? = null,
    val tool_call_id: String? = null  // For tool result messages
) : ConversationMessage

/**
 * OpenAI request with tools support
 */
@Serializable
data class OpenAIRequestWithTools(
    val model: String,
    val messages: List<OpenAIMessageWithTools>,
    val temperature: Double = 0.7,
    val top_p: Double? = null,
    val max_tokens: Int? = null,
    val stream: Boolean = false,
    val tools: List<OpenRouterTool>? = null,
    val tool_choice: String? = null  // "auto", "none", or specific tool
)

/**
 * OpenAI choice with tools support
 */
@Serializable
data class OpenAIChoiceWithTools(
    val index: Int,
    val message: OpenAIMessageWithTools,
    val finish_reason: String
)

/**
 * OpenAI response with tools support
 */
@Serializable
data class OpenAIResponseWithTools(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIChoiceWithTools>,
    val usage: OpenAIUsage? = null
)
