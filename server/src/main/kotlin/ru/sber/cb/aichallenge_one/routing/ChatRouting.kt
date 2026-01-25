package ru.sber.cb.aichallenge_one.routing

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.OllamaApiClient
import ru.sber.cb.aichallenge_one.database.MessageRepository
import ru.sber.cb.aichallenge_one.models.ChatMessage
import ru.sber.cb.aichallenge_one.models.ChatResponse
import ru.sber.cb.aichallenge_one.models.SendMessageRequest
import ru.sber.cb.aichallenge_one.models.SenderType
import ru.sber.cb.aichallenge_one.service.ChatService

/**
 * Response DTO for Ollama models list endpoint.
 */
@Serializable
data class OllamaModelsListResponse(
    val models: List<OllamaModelDto>,
    val count: Int,
    val error: String? = null
)

/**
 * Simple DTO for an Ollama model in the list.
 */
@Serializable
data class OllamaModelDto(
    val id: String,
    val name: String
)

fun Route.chatRouting() {
    val chatService by inject<ChatService>()
    val messageRepository by inject<MessageRepository>()
    val logger = LoggerFactory.getLogger("ChatRouting")

    route("/api") {
        post("/send-message") {
            try {
                val request = call.receive<SendMessageRequest>()

                if (request.text.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Message text cannot be empty")
                    return@post
                }

                val response: ChatResponse = chatService.processUserMessage(
                    userText = request.text,
                    systemPrompt = request.systemPrompt,
                    temperature = request.temperature,
                    provider = request.provider,
                    model = request.model,
                    maxTokens = request.maxTokens,
                    enableTools = request.enableTools,
                    useRag = request.useRag,
                    isHelpCommand = request.isHelpCommand,
                    isSupportCommand = request.isSupportCommand,
                    isAnalyseCommand = request.isAnalyseCommand
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.application.environment.log.error("Error in send-message endpoint", e)
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }

        /**
         * Streaming endpoint for Ollama messages using Server-Sent Events (SSE).
         *
         * This endpoint provides real-time streaming of Ollama's response as it's being generated.
         * The SSE format sends chunks of data with the following structure:
         * - data: {"response": "partial text", "done": false}
         * - data: {"response": "", "done": true}
         *
         * Usage:
         * POST /api/send-message-ollama-stream
         * Body: {"text": "Your message here", "temperature": 0.7, "maxTokens": 2048}
         *
         * Response: SSE stream with incremental text chunks
         */
        post("/send-message-ollama-stream") {
            try {
                val request = call.receive<SendMessageRequest>()

                if (request.text.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Message text cannot be empty")
                    return@post
                }

                logger.info("Received Ollama streaming request: ${request.text.take(100)}...")

                // Get OllamaApiClient and dependencies
                val ollamaApiClient by inject<OllamaApiClient>()
                val messageRepo by inject<MessageRepository>()

                // Build conversation history from repository
                val historyEntities = messageRepo.getHistory("ollama")
                val messages = mutableListOf<ru.sber.cb.aichallenge_one.domain.OllamaMessage>()

                // Add history
                for (entity in historyEntities.takeLast(20)) { // Limit to last 20 messages
                    messages.add(
                        ru.sber.cb.aichallenge_one.domain.OllamaMessage(
                            role = entity.role,
                            content = entity.content
                        )
                    )
                }

                // Add current user message
                messages.add(
                    ru.sber.cb.aichallenge_one.domain.OllamaMessage(
                        role = "user",
                        content = request.text
                    )
                )

                // Use respondTextWriter to send SSE stream
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    try {
                        logger.info("Starting Ollama streaming generation...")

                        // Stream the response using OllamaApiClient
                        ollamaApiClient.streamGenerate(
                            messages = messages,
                            output = this,
                            temperature = request.temperature,
                            maxTokens = request.maxTokens
                        )

                        logger.info("Ollama streaming completed successfully")
                    } catch (e: Exception) {
                        logger.error("Error during Ollama streaming", e)

                        // Send error event to client
                        val errorEvent = mapOf(
                            "error" to (e.message ?: "Unknown error"),
                            "done" to true
                        )
                        this.write("data: ${Json.encodeToString(errorEvent)}\n\n")
                        this.flush()
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in send-message-ollama-stream endpoint", e)
                call.respond(HttpStatusCode.InternalServerError, "Internal server error: ${e.message}")
            }
        }

        post("/clear-history") {
            try {
                chatService.clearHistory()
                call.respond(HttpStatusCode.OK, mapOf("message" to "History cleared"))
            } catch (e: Exception) {
                call.application.environment.log.error("Error in clear-history endpoint", e)
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }

        get("/history") {
            try {
                val provider = call.request.queryParameters["provider"] ?: "gigachat"

                if (provider != "gigachat" && provider != "openrouter" && provider != "ollama") {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "Invalid provider. Must be 'gigachat', 'openrouter', or 'ollama'"
                    )
                    return@get
                }

                val messages = messageRepository.getHistory(provider)

                val chatMessages = messages.map { entity ->
                    ChatMessage(
                        text = entity.content,
                        sender = if (entity.role == "user") SenderType.USER else SenderType.BOT
                    )
                }

                call.respond(HttpStatusCode.OK, chatMessages)
            } catch (e: Exception) {
                call.application.environment.log.error("Error fetching history for provider", e)
                call.respond(HttpStatusCode.OK, emptyList<ChatMessage>())
            }
        }

        /**
         * Get available Ollama models from local Ollama instance.
         * Returns a list of installed Ollama models that can be used for chat.
         *
         * Usage: GET /api/ollama-models
         * Response: {"models": [{"id": "gemma3:1b", "name": "gemma3:1b"}, ...], "count": 2}
         */
        get("/ollama-models") {
            try {
                val ollamaApiClient by inject<OllamaApiClient>()
                val models = ollamaApiClient.fetchLocalModels()

                val modelList = models.map { model ->
                    OllamaModelDto(
                        id = model.name,
                        name = model.name
                    )
                }

                call.respond(
                    HttpStatusCode.OK,
                    OllamaModelsListResponse(
                        models = modelList,
                        count = modelList.size
                    )
                )
            } catch (e: Exception) {
                logger.error("Error fetching Ollama models", e)
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    OllamaModelsListResponse(
                        models = emptyList(),
                        count = 0,
                        error = "Failed to fetch Ollama models. Make sure Ollama is running locally."
                    )
                )
            }
        }
    }
}
