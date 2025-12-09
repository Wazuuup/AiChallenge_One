package ru.sber.cb.aichallenge_one.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.7,
    val top_p: Double? = null,
    val max_tokens: Int? = null,
    val stream: Boolean = false
)

@Serializable
data class OpenAIChoice(
    val index: Int,
    val message: OpenAIMessage,
    @SerialName("finish_reason")
    val finishReason: String
)

@Serializable
data class OpenAIUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

@Serializable
data class OpenAIResponse(
    val id: String,
    @SerialName("object")
    val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage? = null
)

/**
 * Wrapper class to store OpenAIResponse with response time
 *
 * @param response The OpenAI API response
 * @param responseTimeMs Response time in milliseconds
 */
data class TimedOpenAIResponse(
    val response: OpenAIResponse,
    val responseTimeMs: Long
)

/**
 * OpenAI-compatible API client for calling OpenAI and compatible models
 * (e.g., OpenAI, Azure OpenAI, LocalAI, Ollama with OpenAI compatibility, etc.)
 *
 * @param httpClient Ktor HTTP client instance
 * @param baseUrl Base URL of the API (e.g., "https://api.openai.com/v1")
 * @param apiKey API key for authentication (Bearer token)
 * @param model Model name to use (e.g., "gpt-4", "gpt-3.5-turbo", "gpt-4-turbo")
 * @param maxTokens Maximum tokens in response (optional)
 * @param topP Top-p sampling parameter (optional)
 */
class OpenAIApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String = "gpt-3.5-turbo",
    private val maxTokens: Int? = null,
    private val topP: Double? = null
) {
    private val logger = LoggerFactory.getLogger(OpenAIApiClient::class.java)

    // Local variable to store copies of all OpenAIResponse objects with timing
    private val responseHistory = mutableListOf<TimedOpenAIResponse>()

    /**
     * Send a message to the OpenAI-compatible API
     *
     * @param messageHistory List of previous messages in the conversation
     * @param customSystemPrompt Custom system prompt (optional)
     * @param temperature Temperature parameter for response randomness (0.0-2.0)
     * @return Response text from the AI model
     */
    suspend fun sendMessage(
        messageHistory: List<OpenAIMessage>,
        customSystemPrompt: String = "",
        temperature: Double = 0.7
    ): String {
        try {
            val systemPromptContent = customSystemPrompt.ifBlank {
                "You are a helpful assistant."
            }

            val systemPrompt = OpenAIMessage(
                role = MessageRole.SYSTEM.value,
                content = systemPromptContent
            )

            val request = OpenAIRequest(
                model = model,
                messages = listOf(systemPrompt) + messageHistory,
                temperature = temperature.coerceIn(0.0, 2.0),
                top_p = topP,
                max_tokens = maxTokens,
                stream = false
            )

            logger.info("Sending message to OpenAI-compatible API: model=$model, messages=${messageHistory.size + 1}")

            // Measure response time
            val startTime = System.currentTimeMillis()

            val response: HttpResponse = httpClient.post("$baseUrl/chat/completions") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(request)
            }

            val responseTimeMs = System.currentTimeMillis() - startTime

            if (response.status.isSuccess()) {
                val chatResponse: OpenAIResponse = response.body()

                // Save a copy of the response with timing to history
                val timedResponse = TimedOpenAIResponse(chatResponse, responseTimeMs)
                responseHistory.add(timedResponse)
                logger.debug(
                    "Saved response to history. Response time: {}ms. Took {} promptTokens and {} completionTokens, total {}",
                    responseTimeMs,
                    timedResponse.response.usage?.promptTokens ?: 0,
                    timedResponse.response.usage?.completionTokens ?: 0,
                    timedResponse.response.usage?.totalTokens ?: 0,
                )

                val responseText = chatResponse.choices.firstOrNull()?.message?.content
                    ?: "Empty response from OpenAI-compatible API"

                logger.info("Received response from OpenAI-compatible API: ${responseText.take(100)}...")

                chatResponse.usage?.let { usage ->
                    logger.debug("Token usage - Prompt: ${usage.promptTokens}, Completion: ${usage.completionTokens}, Total: ${usage.totalTokens}")
                }

                return responseText
            } else {
                val errorBody = response.bodyAsText()
                logger.error("OpenAI-compatible API error: ${response.status} - $errorBody")
                throw Exception("OpenAI-compatible API error: ${response.status} - $errorBody")
            }
        } catch (e: Exception) {
            logger.error("Error communicating with OpenAI-compatible API", e)
            throw e
        }
    }

    /**
     * Get all saved response history with timing information
     *
     * @return Immutable list of all TimedOpenAIResponse objects
     */
    fun getResponseHistory(): List<TimedOpenAIResponse> {
        return responseHistory.toList()
    }

    /**
     * Get the most recent response with timing
     *
     * @return The last TimedOpenAIResponse or null if no responses yet
     */
    fun getLatestResponse(): TimedOpenAIResponse? {
        return responseHistory.lastOrNull()
    }

    /**
     * Get a specific response by index with timing
     *
     * @param index Index of the response (0-based)
     * @return TimedOpenAIResponse at the given index or null if index is invalid
     */
    fun getResponseAt(index: Int): TimedOpenAIResponse? {
        return responseHistory.getOrNull(index)
    }

    /**
     * Get the total number of responses saved
     *
     * @return Count of responses in history
     */
    fun getResponseCount(): Int {
        return responseHistory.size
    }

    /**
     * Clear all saved responses from history
     */
    fun clearResponseHistory() {
        logger.info("Clearing response history. Previous count: ${responseHistory.size}")
        responseHistory.clear()
    }

    /**
     * Get total token usage across all responses
     *
     * @return Map with total prompt_tokens, completion_tokens, and total_tokens
     */
    fun getTotalTokenUsage(): Map<String, Int> {
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var totalTokens = 0

        responseHistory.forEach { timedResponse ->
            timedResponse.response.usage?.let { usage ->
                totalPromptTokens += usage.promptTokens
                totalCompletionTokens += usage.completionTokens
                totalTokens += usage.totalTokens
            }
        }

        return mapOf(
            "prompt_tokens" to totalPromptTokens,
            "completion_tokens" to totalCompletionTokens,
            "total_tokens" to totalTokens
        )
    }

    /**
     * Get responses filtered by model with timing information
     *
     * @param modelName Model name to filter by
     * @return List of timed responses from the specified model
     */
    fun getResponsesByModel(modelName: String): List<TimedOpenAIResponse> {
        return responseHistory.filter { it.response.model == modelName }
    }

    /**
     * Get responses within a time range with timing information
     *
     * @param startTime Start timestamp (Unix time in seconds)
     * @param endTime End timestamp (Unix time in seconds)
     * @return List of timed responses created within the time range
     */
    fun getResponsesByTimeRange(startTime: Long, endTime: Long): List<TimedOpenAIResponse> {
        return responseHistory.filter { it.response.created in startTime..endTime }
    }

    /**
     * Get average response time in milliseconds
     *
     * @return Average response time or 0 if no responses
     */
    fun getAverageResponseTime(): Long {
        if (responseHistory.isEmpty()) return 0
        return responseHistory.sumOf { it.responseTimeMs } / responseHistory.size
    }

    /**
     * Get fastest response time in milliseconds
     *
     * @return Minimum response time or null if no responses
     */
    fun getFastestResponseTime(): Long? {
        return responseHistory.minOfOrNull { it.responseTimeMs }
    }

    /**
     * Get slowest response time in milliseconds
     *
     * @return Maximum response time or null if no responses
     */
    fun getSlowestResponseTime(): Long? {
        return responseHistory.maxOfOrNull { it.responseTimeMs }
    }

    /**
     * Get response time statistics
     *
     * @return Map with min, max, average, and median response times in milliseconds
     */
    fun getResponseTimeStats(): Map<String, Long> {
        if (responseHistory.isEmpty()) {
            return mapOf(
                "min" to 0,
                "max" to 0,
                "average" to 0,
                "median" to 0
            )
        }

        val times = responseHistory.map { it.responseTimeMs }.sorted()
        val median = if (times.size % 2 == 0) {
            (times[times.size / 2 - 1] + times[times.size / 2]) / 2
        } else {
            times[times.size / 2]
        }

        return mapOf(
            "min" to (times.minOrNull() ?: 0),
            "max" to (times.maxOrNull() ?: 0),
            "average" to getAverageResponseTime(),
            "median" to median
        )
    }

    /**
     * Convert GigaChatMessage to OpenAIMessage for compatibility
     */
    fun convertFromGigaChatMessage(message: GigaChatMessage): OpenAIMessage {
        return OpenAIMessage(role = message.role, content = message.content)
    }

    /**
     * Convert OpenAIMessage to GigaChatMessage for compatibility
     */
    fun convertToGigaChatMessage(message: OpenAIMessage): GigaChatMessage {
        return GigaChatMessage(role = message.role, content = message.content)
    }
}
