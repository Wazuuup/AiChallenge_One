package ru.sber.cb.aichallenge_one.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.sber.cb.aichallenge_one.models.*
import kotlin.js.JsName

/**
 * External declaration for browser's EventSource API.
 * This is a native browser API for Server-Sent Events (SSE).
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/EventSource">MDN EventSource Documentation</a>
 */
@JsName("EventSource")
external class EventSource(url: String) {
    val readyState: Int
    var onmessage: ((MessageEvent) -> Unit)?
    var onerror: ((Event) -> Unit)?
    fun close()
}

/**
 * External declaration for browser's MessageEvent.
 */
@JsName("MessageEvent")
external interface MessageEvent {
    val data: String?
}

/**
 * External declaration for browser's Event.
 */
@JsName("Event")
external interface Event

/**
 * External declaration for browser's encodeURIComponent function.
 */
@JsName("encodeURIComponent")
external fun encodeURIComponent(str: String): String

/**
 * Streaming chunk from Ollama API.
 *
 * @property model Model name used for generation
 * @property createdAt ISO timestamp when chunk was generated
 * @property response Partial response text (null if done=true)
 * @property done Whether generation is complete
 * @property context Token context (only present when done=true)
 * @property evalCount Number of tokens evaluated (only present when done=true)
 * @property evalDuration Time spent evaluating in nanoseconds (only present when done=true)
 */
@Serializable
data class OllamaStreamChunk(
    val model: String,
    @Serializable
    val createdAt: String? = null,
    val response: String? = null,
    val done: Boolean = false,
    val context: List<Int>? = null,
    @Serializable
    val evalCount: Int? = null,
    @Serializable
    val evalDuration: Long? = null
)

/**
 * Result of streaming Ollama message.
 *
 * @property fullText Complete accumulated text from all chunks
 * @property isComplete Whether streaming is complete
 * @property tokenCount Number of tokens in context (available when done=true)
 */
@Serializable
data class OllamaStreamResult(
    val fullText: String,
    val isComplete: Boolean,
    val tokenCount: Int? = null
)

@Serializable
data class OpenRouterModel(
    val id: String,
    val name: String? = null
)

@Serializable
data class ModelsListResponse(
    val models: List<OpenRouterModel>,
    val count: Int
)

/**
 * Frontend API client for AI Chat backend.
 *
 * Supports:
 * - Standard message sending (GigaChat, OpenRouter, Ollama)
 * - SSE streaming for Ollama provider
 * - History management
 * - Model fetching
 * - Notifications
 */
class ChatApi {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private val serverUrl = "http://localhost:${Constants.SERVER_PORT.number}"

    /**
     * Send a message to the AI chat backend.
     *
     * Supports multiple providers:
     * - "gigachat": Sber GigaChat API (default)
     * - "openrouter": OpenRouter API (requires API key)
     * - "ollama": Local Ollama instance (requires Ollama to be running)
     *
     * @param text Message text to send
     * @param systemPrompt Optional system prompt to set context
     * @param temperature Sampling temperature (0.0-2.0, default: 0.7)
     * @param provider AI provider to use ("gigachat", "openrouter", or "ollama")
     * @param model Optional model name (for OpenRouter and Ollama)
     * @param maxTokens Optional maximum tokens for completion
     * @param useRag Enable RAG (Retrieval-Augmented Generation) context
     * @param isHelpCommand Whether this is a /help command for codebase questions
     * @return ChatResponse containing the AI response
     * @throws Exception if the request fails
     */
    suspend fun sendMessage(
        text: String,
        systemPrompt: String = "",
        temperature: Double = 0.7,
        provider: String = "gigachat",
        model: String? = null,
        maxTokens: Int? = null,
        enableTools: Boolean = true,
        useRag: Boolean = false,
        isHelpCommand: Boolean = false,
        isSupportCommand: Boolean = false
    ): ChatResponse {
        return try {
            val response = client.post("$serverUrl/api/send-message") {
                contentType(ContentType.Application.Json)
                setBody(
                    SendMessageRequest(
                        text = text,
                        systemPrompt = systemPrompt,
                        temperature = temperature,
                        provider = provider,
                        model = model,
                        maxTokens = maxTokens,
                        enableTools = enableTools,
                        useRag = useRag,
                        isHelpCommand = isHelpCommand,
                        isSupportCommand = isSupportCommand
                    )
                )
            }
            response.body()
        } catch (e: Exception) {
            println("Error sending message: $e")
            throw e
        }
    }

    suspend fun clearHistory() {
        try {
            client.post("$serverUrl/api/clear-history") {
                contentType(ContentType.Application.Json)
            }
        } catch (e: Exception) {
            println("Error clearing history: $e")
            throw e
        }
    }

    suspend fun fetchHistory(provider: String): List<ChatMessage> {
        return try {
            client.get("$serverUrl/api/history?provider=$provider").body()
        } catch (e: Exception) {
            println("Error fetching history: $e")
            emptyList()
        }
    }

    suspend fun fetchAvailableModels(provider: String = "openrouter"): List<ModelInfo> {
        return try {
            val endpoint = when (provider) {
                "ollama" -> "$serverUrl/api/ollama-models"
                else -> "$serverUrl/api/models"
            }

            val response: ModelsListResponse = client.get(endpoint).body()

            response.models.map { model ->
                ModelInfo(
                    id = model.id,
                    name = model.name ?: model.id
                )
            }
        } catch (e: Exception) {
            println("Error fetching models: $e")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchNotifications(): NotificationsResponse {
        return try {
            client.get("$serverUrl/api/notifications").body()
        } catch (e: Exception) {
            println("Error fetching notifications: $e")
            NotificationsResponse(notifications = emptyList(), count = 0)
        }
    }

    suspend fun markNotificationAsRead(notificationId: String): MarkReadResponse {
        return try {
            client.post("$serverUrl/api/notifications/$notificationId/read").body()
        } catch (e: Exception) {
            println("Error marking notification as read: $e")
            MarkReadResponse(success = false, message = "Network error")
        }
    }

    /**
     * Stream a message from Ollama using Server-Sent Events (SSE).
     *
     * This function uses the native browser EventSource API to connect to the backend's
     * streaming endpoint and receive real-time chunks of the AI response.
     *
     * **IMPORTANT**: This function requires a backend streaming endpoint to be implemented.
     * The endpoint should be at `/api/stream-ollama-message` and accept the same parameters
     * as `/api/send-message`, but return SSE-formatted OllamaStreamChunk objects.
     *
     * **Usage Example**:
     * ```kotlin
     * chatApi.streamOllamaMessage(
     *     request = SendMessageRequest(
     *         text = "Hello, Ollama!",
     *         provider = "ollama",
     *         model = "gemma3:1b",
     *         temperature = 0.7
     *     ),
     *     onChunk = { chunk ->
     *         println("Received chunk: ${chunk.response}")
     *         // Update UI with partial response
     *     },
     *     onComplete = { result ->
     *         println("Streaming complete: ${result.fullText}")
     *         // Finalize UI update
     *     },
     *     onError = { error ->
     *         println("Streaming error: $error")
     *         // Show error to user
     *     }
     * )
     * ```
     *
     * @param request The SendMessageRequest with provider set to "ollama"
     * @param onChunk Callback invoked for each streaming chunk received.
     *               Called with OllamaStreamChunk containing partial response text.
     * @param onComplete Callback invoked when streaming is complete (done=true).
     *                  Called with OllamaStreamResult containing full text and metadata.
     * @param onError Callback invoked if an error occurs during streaming.
     *
     * @see OllamaStreamChunk
     * @see OllamaStreamResult
     */
    fun streamOllamaMessage(
        request: SendMessageRequest,
        onChunk: (OllamaStreamChunk) -> Unit,
        onComplete: (OllamaStreamResult) -> Unit,
        onError: (String) -> Unit
    ) {
        // Build URL with query parameters for SSE endpoint
        val params = mutableListOf<String>()
        params.add("text=${encodeURIComponent(request.text)}")
        if (request.systemPrompt.isNotBlank()) {
            params.add("systemPrompt=${encodeURIComponent(request.systemPrompt)}")
        }
        params.add("temperature=${request.temperature}")
        params.add("provider=ollama")

        // Use local variables to avoid smart cast issues
        val model = request.model
        if (model != null) {
            params.add("model=${encodeURIComponent(model)}")
        }

        val maxTokens = request.maxTokens
        if (maxTokens != null) {
            params.add("maxTokens=$maxTokens")
        }

        params.add("enableTools=$request.enableTools")
        params.add("useRag=$request.useRag")
        params.add("isHelpCommand=$request.isHelpCommand")
        params.add("isSupportCommand=$request.isSupportCommand")

        val streamUrl = "$serverUrl/api/stream-ollama-message?${params.joinToString("&")}"

        // Use native browser EventSource for SSE
        val eventSource = EventSource(streamUrl)

        var accumulatedText = ""
        var tokenCount: Int? = null

        eventSource.onmessage = { event ->
            try {
                // Parse SSE data as JSON
                val rawData = event.data
                if (rawData != null && rawData != "[DONE]") {
                    // Use Kotlinx Serialization to parse the chunk
                    val json = Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }

                    val chunk = json.decodeFromString<OllamaStreamChunk>(rawData)

                    // Accumulate text
                    if (chunk.response != null) {
                        accumulatedText += chunk.response
                    }

                    // Extract token count if available
                    if (chunk.done && chunk.context != null) {
                        tokenCount = chunk.context.size
                    }

                    // Notify callback with chunk
                    onChunk(chunk)

                    // Check if streaming is complete
                    if (chunk.done) {
                        val result = OllamaStreamResult(
                            fullText = accumulatedText,
                            isComplete = true,
                            tokenCount = tokenCount
                        )
                        onComplete(result)
                        eventSource.close()
                    }
                }
            } catch (e: Exception) {
                println("Error parsing stream chunk: ${e.message}")
                onError("Error parsing stream response: ${e.message}")
                eventSource.close()
            }
        }

        eventSource.onerror = { error ->
            println("EventSource error: $error")
            var errorMessage = "Connection error"

            // Try to extract more detailed error info
            val readyState = eventSource.readyState as? Int
            errorMessage = when (readyState) {
                0 -> "Connection not initialized"
                1 -> "Connection open"
                2 -> "Connection closed"
                3 -> "Error occurred"
                else -> "Unknown error state: $readyState"
            }

            // Check for common Ollama errors
            if (errorMessage.contains("Error occurred") || errorMessage.contains("Connection closed")) {
                errorMessage = "Ollama streaming error: Connection lost or server not responding"
            }

            onError(errorMessage)
            eventSource.close()
        }
    }
}
