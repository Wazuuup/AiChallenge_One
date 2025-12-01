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
                /* call.respond(HttpStatusCode.OK, ChatResponse(
                     text = "sssss",
                     status = ResponseStatus.SUCCESS
                 ))*/


                val response: ChatResponse = chatService.processUserMessage(request.text)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.application.environment.log.error("Error in send-message endpoint", e)
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }
    }
}
