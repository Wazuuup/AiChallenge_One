package ru.sber.cb.aichallenge_one.github.webhook.routing

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.serialization.json.JsonObject
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.github.webhook.model.WebhookPayload
import ru.sber.cb.aichallenge_one.github.webhook.service.ReviewOrchestrationService

private val logger = LoggerFactory.getLogger("WebhookRouting")

fun Route.webhookRouting() {
    val reviewOrchestrationService by inject<ReviewOrchestrationService>()

    post("/webhook") {
        try {
            // Parse webhook payload
            val payload = call.receive<WebhookPayload>()

            logger.info("Received webhook: action=${payload.action}, PR #${payload.pullRequest.number}, repo=${payload.repository.fullName}")

            // Filter: only process "opened" events
            if (payload.action != "opened") {
                logger.info("Ignoring webhook action: ${payload.action}")
                call.respond(HttpStatusCode.OK, mapOf("message" to "Event ignored (not PR opened)"))
                return@post
            }

            // Extract PR details
            val prNumber = payload.pullRequest.number
            val owner = payload.repository.owner.login
            val repo = payload.repository.name

            logger.info("Processing PR opened event: PR #$prNumber in $owner/$repo")

            // Respond immediately with 200 OK
            call.respond(
                HttpStatusCode.OK, JsonObject(
                    mapOf(
                        "message" to "Webhook received",
                        "pr_number" to prNumber,
                        "repository" to payload.repository.fullName
                    ).toJson()
                )
            )

            // Trigger async review processing (non-blocking)
            reviewOrchestrationService.processReviewAsync(owner, repo, prNumber)

        } catch (e: Exception) {
            logger.error("Failed to process webhook", e)
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Invalid webhook payload: ${e.message}")
            )
        }
    }
}
