package ru.sber.cb.aichallenge_one.routing

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import ru.sber.cb.aichallenge_one.database.MessageRepository
import ru.sber.cb.aichallenge_one.models.ChatMessage
import ru.sber.cb.aichallenge_one.models.ChatResponse
import ru.sber.cb.aichallenge_one.models.SendMessageRequest
import ru.sber.cb.aichallenge_one.models.SenderType
import ru.sber.cb.aichallenge_one.service.ChatService

fun Route.chatRouting() {
    val chatService by inject<ChatService>()
    val messageRepository by inject<MessageRepository>()

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
                    useRag = request.useRag
                )
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.application.environment.log.error("Error in send-message endpoint", e)
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
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

                if (provider != "gigachat" && provider != "openrouter") {
                    call.respond(HttpStatusCode.BadRequest, "Invalid provider. Must be 'gigachat' or 'openrouter'")
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
    }
}
