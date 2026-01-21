package ru.sber.cb.aichallenge_one.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.domain.OllamaMessage as DomainOllamaMessage

// Type alias for backward compatibility within the file
typealias OllamaMessage = DomainOllamaMessage

/**
 * Enum representing message roles in Ollama API format.
 */
enum class OllamaMessageRole(val value: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant")
}

/**
 * Request body for Ollama /api/chat endpoint (OpenAI-compatible format).
 *
 * @property model Model name to use (e.g., "gemma3:1b")
 * @property messages List of conversation messages
 * @property stream Whether to stream the response (default: false)
 * @property options Optional model parameters
 */
@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

/**
 * Model options for Ollama API requests.
 *
 * @property temperature Sampling temperature (0.0-2.0, default: 0.7)
 * @property num_predict Maximum tokens to generate (default: 2048)
 * @property top_p Top-p sampling parameter (default: 0.9)
 */
@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    @SerialName("num_predict")
    val numPredict: Int? = null,
    @SerialName("top_p")
    val topP: Double? = null
)

/**
 * Usage statistics from Ollama chat response.
 * Note: Ollama may not always return usage data in OpenAI-compatible mode.
 *
 * @property prompt_tokens Number of tokens in prompt
 * @property completion_tokens Number of tokens in completion
 * @property total_tokens Total tokens used
 */
@Serializable
data class OllamaUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

/**
 * Complete response from Ollama /api/chat endpoint.
 *
 * Ollama uses a different format than OpenAI. The response structure is:
 * ```json
 * {
 *   "model": "gemma3:1b",
 *   "created_at": "2024-07-12T12:34:56.789012345Z",
 *   "message": {
 *     "role": "assistant",
 *     "content": "Response text"
 *   },
 *   "done": true,
 *   "prompt_eval_count": 10,
 *   "eval_count": 20
 * }
 * ```
 *
 * @property model Model name used for generation
 * @property created_at ISO timestamp when response was created
 * @property message The response message with role and content
 * @property done Whether generation is complete
 * @property prompt_eval_count Number of tokens in prompt
 * @property eval_count Number of tokens in completion
 */
@Serializable
data class OllamaChatResponse(
    val model: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    val message: OllamaMessage,
    val done: Boolean = true,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null
)

/**
 * Streaming chunk from Ollama /api/chat endpoint with streaming enabled.
 *
 * Format:
 * ```json
 * {
 *   "model": "gemma3:1b",
 *   "created_at": "2024-07-12T12:34:56.789012345Z",
 *   "message": {
 *     "role": "assistant",
 *     "content": "partial response text"
 *   },
 *   "done": false
 * }
 * ```
 *
 * @property model Model name used for generation
 * @property created_at ISO timestamp when chunk was generated
 * @property message Response message with role and content
 * @property done Whether generation is complete
 */
@Serializable
data class OllamaStreamChunk(
    val model: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val message: OllamaMessage? = null,
    val done: Boolean = false
)

/**
 * Result of sending a message to Ollama, including response and metadata.
 *
 * @property text The response text from the AI model
 * @property usage Token usage information (null if not provided by Ollama)
 * @property responseTimeMs Response time in milliseconds
 */
data class OllamaMessageResult(
    val text: String,
    val usage: OllamaUsage?,
    val responseTimeMs: Long
)

/**
 * HTTP client for Ollama API communication.
 *
 * Supports both OpenAI-compatible chat completions and native Ollama streaming.
 * Uses Ollama's OpenAI-compatible /api/chat endpoint for standard requests
 * and /api/generate endpoint for streaming responses.
 *
 * @property httpClient Ktor HTTP client instance
 * @property baseUrl Base URL of Ollama server (default: "http://localhost:11434")
 * @property model Default model to use (e.g., "gemma3:1b")
 * @property timeout Request timeout in milliseconds (default: 120000 = 2 minutes)
 *
 * @see <a href="https://github.com/ollama/ollama/blob/main/docs/api.md">Ollama API Documentation</a>
 */
class OllamaApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val model: String,
    private val timeout: Long
) {
    private val logger = LoggerFactory.getLogger(OllamaApiClient::class.java)

    // Tool response history for tracking token usage in tool calling workflows
    private val toolResponseHistory = mutableListOf<TimedOllamaResponse>()

    /**
     * Send a chat completion request to Ollama.
     *
     * Uses OpenAI-compatible /api/chat endpoint for maximum compatibility
     * with existing code patterns.
     *
     * @param messageHistory List of previous messages in the conversation
     * @param systemPrompt System prompt to set context (optional, defaults to empty)
     * @param temperature Sampling temperature (0.0-2.0, default: 0.7)
     * @param maxTokens Maximum tokens to generate (optional, default: 2048)
     * @param modelOverride Override model for this request (optional, uses default model if null)
     * @return OllamaMessageResult containing response text, usage stats, and timing
     * @throws Exception if Ollama is not running or request fails
     */
    suspend fun sendMessage(
        messageHistory: List<OllamaMessage>,
        systemPrompt: String = "",
        temperature: Double = 0.7,
        maxTokens: Int? = null,
        modelOverride: String? = null
    ): OllamaMessageResult {
        try {
            // Build message list with system prompt if provided
            val messages = if (systemPrompt.isNotBlank()) {
                listOf(
                    OllamaMessage(
                        role = OllamaMessageRole.SYSTEM.value,
                        content = systemPrompt
                    )
                ) + messageHistory
            } else {
                messageHistory
            }

            val effectiveModel = modelOverride ?: model

            val request = OllamaChatRequest(
                model = effectiveModel,
                messages = messages,
                stream = false,
                options = OllamaOptions(
                    temperature = temperature.coerceIn(0.0, 2.0),
                    numPredict = maxTokens,
                    topP = 0.9
                )
            )

            logger.info(
                "Sending message to Ollama: model=$effectiveModel, messages=${messages.size}, " +
                        "temperature=$temperature, maxTokens=$maxTokens"
            )

            val startTime = System.currentTimeMillis()

            val response: HttpResponse = httpClient.post("$baseUrl/api/chat") {
                headers {
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(request)
            }

            val responseTimeMs = System.currentTimeMillis() - startTime

            if (response.status.isSuccess()) {
                val chatResponse: OllamaChatResponse = response.body()

                val responseText = chatResponse.message.content
                    ?: "Empty response from Ollama"

                logger.info("Received response from Ollama: ${responseText.take(100)}...")
                logger.debug("Response time: ${responseTimeMs}ms")

                // Create usage info from Ollama response format
                val usage = if (chatResponse.promptEvalCount != null && chatResponse.evalCount != null) {
                    OllamaUsage(
                        promptTokens = chatResponse.promptEvalCount,
                        completionTokens = chatResponse.evalCount,
                        totalTokens = chatResponse.promptEvalCount + chatResponse.evalCount
                    )
                } else {
                    null
                }

                usage?.let {
                    logger.debug(
                        "Token usage - Prompt: ${it.promptTokens}, " +
                                "Completion: ${it.completionTokens}, " +
                                "Total: ${it.totalTokens}"
                    )
                }

                return OllamaMessageResult(responseText, usage, responseTimeMs)
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Ollama API error: ${response.status} - $errorBody")

                // Map common errors to user-friendly messages
                val errorMessage = mapErrorToUserMessage(response.status.value, errorBody)
                throw Exception(errorMessage)
            }
        } catch (e: Exception) {
            logger.error("Error communicating with Ollama API", e)

            // Re-throw with user-friendly message if not already mapped
            if (e.message?.startsWith("Ollama") == true) {
                throw e
            }

            // Map connection errors
            val userMessage = when {
                e.message?.contains("Connection refused") == true ||
                        e.message?.contains("Failed to connect") == true -> {
                    "Ollama is not running. Please start Ollama by running 'ollama serve' in your terminal."
                }

                e.message?.contains("timeout") == true -> {
                    "Generation timed out after ${timeout}ms. Try reducing conversation history or use a smaller model."
                }

                else -> {
                    "Ollama error: ${e.message}"
                }
            }

            throw Exception(userMessage)
        }
    }

    /**
     * Stream a generation request to Ollama.
     *
     * Uses native Ollama /api/generate endpoint with streaming enabled.
     * Writes SSE-formatted chunks to the provided writer.
     *
     * @param messages Conversation history as list of OllamaMessage
     * @param output Writer to output SSE chunks to
     * @param temperature Sampling temperature (0.0-2.0, default: 0.7)
     * @param maxTokens Maximum tokens to generate (optional, default: 2048)
     * @throws Exception if streaming fails or connection issues occur
     */
    suspend fun streamGenerate(
        messages: List<OllamaMessage>,
        output: java.io.Writer,
        temperature: Double = 0.7,
        maxTokens: Int? = null
    ) {
        try {
            // Convert messages to Ollama format
            val ollamaMessages = messages.map { msg ->
                mapOf(
                    "role" to msg.role,
                    "content" to msg.content
                )
            }

            // Ollama chat request with streaming enabled
            val streamRequest = mapOf(
                "model" to model,
                "messages" to ollamaMessages,
                "stream" to true,
                "options" to mapOf(
                    "temperature" to temperature.coerceIn(0.0, 2.0),
                    "num_predict" to (maxTokens ?: 2048)
                )
            )

            logger.info(
                "Starting streaming chat: model=$model, " +
                        "messages=${ollamaMessages.size}, temperature=$temperature"
            )

            val response: HttpResponse = httpClient.post("$baseUrl/api/chat") {
                headers {
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(streamRequest)
            }

            if (response.status.isSuccess()) {
                // Read the entire response as text and process SSE chunks
                val responseText = response.bodyAsText()
                val lines = responseText.lines()

                var fullResponse = ""

                for (line in lines) {
                    if (line.startsWith("data: ")) {
                        try {
                            val json = line.removePrefix("data: ")
                            if (json.isNotBlank()) {
                                // Parse Ollama streaming chunk
                                val chunk = Json.decodeFromString<OllamaStreamChunk>(json)

                                // Extract response content from message
                                val responseContent = chunk.message?.content ?: ""

                                // Build SSE format for frontend
                                val sseData = mapOf(
                                    "response" to responseContent,
                                    "done" to chunk.done
                                )

                                val sseJson = Json.encodeToString(sseData)
                                output.write("data: $sseJson\n\n")
                                output.flush()

                                fullResponse += responseContent

                                if (responseContent.isNotEmpty()) {
                                    logger.debug("Stream chunk: ${responseContent.take(50)}...")
                                }

                                // Check if generation is complete
                                if (chunk.done) {
                                    logger.info("Streaming completed. Total length: ${fullResponse.length}")
                                }
                            }
                        } catch (e: Exception) {
                            logger.warn("Failed to parse stream chunk", e)
                        }
                    }
                }
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Ollama streaming error: ${response.status} - $errorBody")

                val errorMessage = mapErrorToUserMessage(response.status.value, errorBody)
                throw Exception(errorMessage)
            }
        } catch (e: Exception) {
            logger.error("Error during Ollama streaming", e)

            // Map connection errors
            val userMessage = when {
                e.message?.contains("Connection refused") == true ||
                        e.message?.contains("Failed to connect") == true -> {
                    "Ollama is not running. Please start Ollama by running 'ollama serve' in your terminal."
                }

                e.message?.contains("timeout") == true -> {
                    "Streaming timed out after ${timeout}ms. Try reducing conversation history or use a smaller model."
                }

                else -> {
                    "Ollama streaming error: ${e.message}"
                }
            }

            throw Exception(userMessage)
        }
    }

    /**
     * Map HTTP status codes and error bodies to user-friendly error messages.
     *
     * @param statusCode HTTP status code
     * @param errorBody Response error body
     * @return User-friendly error message
     */
    private fun mapErrorToUserMessage(statusCode: Int, errorBody: String): String {
        return when (statusCode) {
            404 -> {
                if (errorBody.contains("model") || errorBody.contains("not found")) {
                    "Model '$model' not found in Ollama. Please download it: ollama pull $model"
                } else {
                    "Ollama endpoint not found (404). Check your Ollama installation."
                }
            }

            500 -> {
                if (errorBody.contains("memory", ignoreCase = true) ||
                    errorBody.contains("allocate", ignoreCase = true)
                ) {
                    "Ollama ran out of memory. Try a smaller model or close other applications."
                } else {
                    "Ollama server error (500). Check Ollama logs for details."
                }
            }

            503 -> {
                "Ollama service unavailable. The model may be loading. Please try again."
            }

            else -> {
                "Ollama API error ($statusCode): $errorBody"
            }
        }
    }

    /**
     * Get the current default model name.
     *
     * @return Model name (e.g., "gemma3:1b")
     */
    fun getModel(): String = model

    /**
     * Get the number of tool responses in history.
     *
     * @return Number of tool responses stored
     */
    fun getToolResponseCount(): Int = toolResponseHistory.size

    /**
     * Get a tool response at a specific index.
     *
     * @param index Index of the response
     * @return TimedOllamaResponse or null if index out of bounds
     */
    fun getToolResponseAt(index: Int): TimedOllamaResponse? =
        toolResponseHistory.getOrNull(index)

    /**
     * Get the most recent tool response.
     *
     * @return Latest TimedOllamaResponse or null if no responses
     */
    fun getLatestToolResponse(): TimedOllamaResponse? =
        toolResponseHistory.lastOrNull()

    /**
     * Fetch list of locally installed Ollama models.
     * Uses Ollama's /api/tags endpoint to get available models.
     *
     * @return List of OllamaModelInfo containing model names and metadata
     * @throws Exception if Ollama is not running or request fails
     */
    suspend fun fetchLocalModels(): List<OllamaModelInfo> {
        try {
            logger.debug("Fetching local Ollama models from $baseUrl")

            val response: OllamaModelsResponse = httpClient.get("$baseUrl/api/tags").body()

            logger.info("Found ${response.models.size} local Ollama models")
            response.models.forEach { model ->
                logger.debug("Available model: ${model.name} (${model.details.family})")
            }

            return response.models
        } catch (e: Exception) {
            logger.error("Failed to fetch local Ollama models", e)
            throw Exception("Failed to connect to Ollama at $baseUrl. Make sure Ollama is running.", e)
        }
    }

    companion object {
        /**
         * Default buffer size for reading streaming responses (8KB).
         */
        private const val DEFAULT_HTTP_BUFFER_SIZE = 8192
    }
}

/**
 * Data class to hold Ollama response with timing information.
 * Used for tracking tool calling workflows.
 *
 * @property response The Ollama chat response
 * @property timestamp Unix timestamp when response was received
 */
data class TimedOllamaResponse(
    val response: OllamaChatResponse,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Response from Ollama /api/tags endpoint containing list of available models.
 *
 * @property models List of available Ollama models
 */
@Serializable
data class OllamaModelsResponse(
    val models: List<OllamaModelInfo>
)

/**
 * Information about an Ollama model.
 *
 * @property name Model name (e.g., "gemma3:1b")
 * @property model Same as name (for compatibility)
 * @property modifiedAt ISO timestamp when model was last modified
 * @property size Model size in bytes
 * @property digest Model digest/hash
 * @property details Detailed model information
 */
@Serializable
data class OllamaModelInfo(
    val name: String,
    val model: String,
    @SerialName("modified_at")
    val modifiedAt: String,
    val size: Long,
    val digest: String,
    val details: OllamaModelDetails
)

/**
 * Detailed information about an Ollama model.
 *
 * @property parentModel Parent model name
 * @property format Model format (e.g., "gguf")
 * @property family Model family (e.g., "gemma3", "bert")
 * @property families List of model families
 * @property parameterSize Model parameter size description
 * @property quantizationLevel Quantization level (e.g., "Q4_K_M", "F16")
 */
@Serializable
data class OllamaModelDetails(
    @SerialName("parent_model")
    val parentModel: String,
    val format: String,
    val family: String,
    val families: List<String>,
    @SerialName("parameter_size")
    val parameterSize: String,
    @SerialName("quantization_level")
    val quantizationLevel: String
)
