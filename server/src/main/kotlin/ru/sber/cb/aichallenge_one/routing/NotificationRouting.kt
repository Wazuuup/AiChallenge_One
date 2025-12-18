package ru.sber.cb.aichallenge_one.routing

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import ru.sber.cb.aichallenge_one.models.MarkReadResponse
import ru.sber.cb.aichallenge_one.models.NotificationsResponse
import ru.sber.cb.aichallenge_one.repository.NotificationRepository

fun Route.notificationRouting() {
    val notificationRepository by inject<NotificationRepository>()

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
    }
}
