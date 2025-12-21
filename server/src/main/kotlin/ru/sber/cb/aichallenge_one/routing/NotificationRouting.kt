package ru.sber.cb.aichallenge_one.routing

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.OpenAIMessageWithTools
import ru.sber.cb.aichallenge_one.client.OpenRouterFunction
import ru.sber.cb.aichallenge_one.client.OpenRouterTool
import ru.sber.cb.aichallenge_one.models.MarkReadResponse
import ru.sber.cb.aichallenge_one.models.NotificationsResponse
import ru.sber.cb.aichallenge_one.models.SummaryGenerationResponse
import ru.sber.cb.aichallenge_one.repository.NotificationRepository
import ru.sber.cb.aichallenge_one.service.ToolExecutionService

fun Route.notificationRouting() {
    val notificationRepository by inject<NotificationRepository>()
    val toolExecutionService by inject<ToolExecutionService>()
    val logger = LoggerFactory.getLogger("NotificationRouting")

    route("/api/notifications") {

        // GET /api/notifications - Fetch unread notifications
        get {
            try {
                val unreadNotifications = notificationRepository.getUnreadNotifications()

                val response = NotificationsResponse(
                    notifications = unreadNotifications,
                    count = unreadNotifications.size
                )

                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                call.application.environment.log.error("Error fetching notifications", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    NotificationsResponse(notifications = emptyList(), count = 0)
                )
            }
        }

        // POST /api/notifications/{id}/read - Mark notification as read
        post("/{id}/read") {
            try {
                val notificationId = call.parameters["id"]

                if (notificationId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        MarkReadResponse(success = false, message = "Notification ID is required")
                    )
                    return@post
                }

                val success = notificationRepository.markAsRead(notificationId)

                if (success) {
                    call.respond(
                        HttpStatusCode.OK,
                        MarkReadResponse(success = true, message = "Notification marked as read")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        MarkReadResponse(success = false, message = "Notification not found")
                    )
                }

            } catch (e: Exception) {
                call.application.environment.log.error("Error marking notification as read", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    MarkReadResponse(success = false, message = "Internal server error")
                )
            }
        }

        // GET /api/notifications/stats - Get notification statistics
        get("/stats") {
            try {
                val stats = notificationRepository.getStats()
                call.respond(HttpStatusCode.OK, stats)
            } catch (e: Exception) {
                call.application.environment.log.error("Error fetching notification stats", e)
                call.respond(HttpStatusCode.InternalServerError, emptyMap<String, Int>())
            }
        }

        // POST /api/notifications/summary/pushToUI - Generate notes summary once
        post("/summary/pushToUI") {
            try {
                if (toolExecutionService == null) {
                    logger.error("ToolExecutionService not available")
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        SummaryGenerationResponse(
                            success = false,
                            message = "ToolExecutionService not configured. Ensure OpenRouter API is configured in application.conf"
                        )
                    )
                    return@post
                }

                logger.info("Starting manual notes summarization...")

                // Build get_all_notes tool
                val getAllNotesTool = OpenRouterTool(
                    type = "function",
                    function = OpenRouterFunction(
                        name = "get_all_notes",
                        description = "Retrieves all notes from the notes database",
                        parameters = buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {}
                            put("additionalProperties", false)
                        }
                    )
                )

                // Create conversation history
                val messageHistory = mutableListOf<OpenAIMessageWithTools>()

                // Call tool execution service
                val summaryText = toolExecutionService.handleToolCallingWorkflow(
                    messageHistory = messageHistory,
                    tools = listOf(getAllNotesTool),
                    userMessage = "Предоставь краткое summary по заметкам",
                    systemPrompt = "Ты личный помощник, планировщик задач",
                    temperature = 0.7,
                    maxIterations = 3
                )

                // Create notification with summary
                val notification = notificationRepository.addNotification(summaryText)

                logger.info("Notes summary generated successfully")
                logger.debug("Summary: ${summaryText.take(100)}...")
                logger.debug("Notification ID: ${notification.id}")

                call.respond(
                    HttpStatusCode.OK,
                    SummaryGenerationResponse(
                        success = true,
                        message = "Summary generated successfully",
                        notificationId = notification.id
                    )
                )

            } catch (e: Exception) {
                logger.error("Failed to generate notes summary: ${e.message}", e)

                // Create error notification
                try {
                    notificationRepository.addNotification(
                        "Не удалось создать summary заметок: ${e.message?.take(50) ?: "Unknown error"}"
                    )
                } catch (notifError: Exception) {
                    logger.error("Failed to create error notification", notifError)
                }

                call.respond(
                    HttpStatusCode.InternalServerError,
                    SummaryGenerationResponse(
                        success = false,
                        message = "Failed to generate summary: ${e.message ?: "Unknown error"}"
                    )
                )
            }
        }
    }
}
