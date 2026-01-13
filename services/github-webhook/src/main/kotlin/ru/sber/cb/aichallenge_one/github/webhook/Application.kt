package ru.sber.cb.aichallenge_one.github.webhook

import com.typesafe.config.ConfigFactory
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.core.logger.Level
import org.koin.ktor.plugin.Koin
import ru.sber.cb.aichallenge_one.github.webhook.di.webhookModule
import ru.sber.cb.aichallenge_one.github.webhook.routing.webhookRouting

const val WEBHOOK_SERVER_PORT = 8094

fun main() {
    embeddedServer(Netty, port = WEBHOOK_SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Load configuration (priority: env vars > system props > application.conf)
    val config = ConfigFactory.systemEnvironment()
        .withFallback(ConfigFactory.systemProperties())
        .withFallback(ConfigFactory.load())
        .resolve()

    log.info("GitHub Webhook Service starting on port $WEBHOOK_SERVER_PORT")

    // Load configuration values
    val openRouterApiKey = config.getString("openrouter.api_key")
    val openRouterBaseUrl = config.getString("openrouter.base_url")
    val reviewModel = config.getString("openrouter.review_model")
    val reviewTemperature = config.getDouble("openrouter.review_temperature")
    val reviewMaxTokens = config.getInt("openrouter.review_max_tokens")
    val reviewTimeout = config.getLong("openrouter.review_timeout")
    val ragApiUrl = config.getString("rag.api_url")
    val ragSearchLimit = config.getInt("rag.search_limit")
    val mcpGitHubReviewerUrl = config.getString("mcp.github_reviewer_url")
    val maxDiffLines = config.getInt("webhook.max_diff_lines")

    log.info("Configuration loaded:")
    log.info("  OpenRouter model: $reviewModel")
    log.info("  RAG API: $ragApiUrl")
    log.info("  MCP GitHub Reviewer: $mcpGitHubReviewerUrl")
    log.info("  Max diff lines: $maxDiffLines")

    // Install Koin DI
    install(Koin) {
        printLogger(Level.INFO)
        modules(
            webhookModule(
                openRouterApiKey = openRouterApiKey,
                openRouterBaseUrl = openRouterBaseUrl,
                reviewModel = reviewModel,
                reviewTemperature = reviewTemperature,
                reviewMaxTokens = reviewMaxTokens,
                reviewTimeout = reviewTimeout,
                ragApiUrl = ragApiUrl,
                ragSearchLimit = ragSearchLimit,
                mcpGitHubReviewerUrl = mcpGitHubReviewerUrl,
                maxDiffLines = maxDiffLines
            )
        )
    }

    // Install plugins
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }

    // CORS (development only!)
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        // GitHub webhook headers
        allowHeader("X-GitHub-Event")
        allowHeader("X-GitHub-Delivery")
        allowHeader("X-Hub-Signature-256")
        anyHost()  // WARNING: Restrict in production!
    }

    // Configure routing
    routing {
        get("/") {
            call.respondText("GitHub Webhook Service is running on port $WEBHOOK_SERVER_PORT")
        }

        get("/health") {
            call.respond(
                HttpStatusCode.OK, mapOf(
                    "status" to "UP",
                    "service" to "github-webhook",
                    "port" to WEBHOOK_SERVER_PORT
                )
            )
        }

        webhookRouting()
    }

    log.info("GitHub Webhook Service started successfully")
}
