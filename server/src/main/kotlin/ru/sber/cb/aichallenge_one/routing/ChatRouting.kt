package ru.sber.cb.aichallenge_one.routing

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import ru.sber.cb.aichallenge_one.models.ChatResponse
import ru.sber.cb.aichallenge_one.models.SendMessageRequest
import ru.sber.cb.aichallenge_one.service.ChatService

fun Route.chatRouting() {
    val chatService by inject<ChatService>()

    route("/api") {
        post("/send-message") {
            try {
                val request = call.receive<SendMessageRequest>()

                if (request.text.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Message text cannot be empty")
                    return@post
                }

                val response: ChatResponse = chatService.processUserMessage(request.text, request.systemPrompt)
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
    }
}
