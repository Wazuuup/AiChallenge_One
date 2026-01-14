package ru.sber.cb.aichallenge_one.github.webhook.di

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import ru.sber.cb.aichallenge_one.github.webhook.client.McpGitHubClient
import ru.sber.cb.aichallenge_one.github.webhook.client.RagApiClient
import ru.sber.cb.aichallenge_one.github.webhook.client.ReviewApiClient
import ru.sber.cb.aichallenge_one.github.webhook.service.DiffAnalysisService
import ru.sber.cb.aichallenge_one.github.webhook.service.ReviewOrchestrationService

fun webhookModule(
    openRouterApiKey: String,
    openRouterBaseUrl: String,
    reviewModel: String,
    reviewTemperature: Double,
    reviewMaxTokens: Int,
    reviewTimeout: Long,
    ragApiUrl: String,
    ragSearchLimit: Int,
    mcpGitHubReviewerUrl: String,
    maxDiffLines: Int
) = module {
    // HTTP Client for API calls
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                })
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
            install(HttpTimeout) {
                requestTimeoutMillis = reviewTimeout
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    // API Clients
    single {
        ReviewApiClient(
            httpClient = get(),
            apiKey = openRouterApiKey,
            baseUrl = openRouterBaseUrl,
            defaultModel = reviewModel,
            defaultTemperature = reviewTemperature,
            defaultMaxTokens = reviewMaxTokens,
            timeoutMillis = reviewTimeout
        )
    }

    single {
        RagApiClient(
            httpClient = get(),
            ragBaseUrl = ragApiUrl,
            defaultLimit = ragSearchLimit
        )
    }

    single {
        McpGitHubClient(
            httpClient = get(),
            mcpBaseUrl = mcpGitHubReviewerUrl
        )
    }

    // Services
    single {
        DiffAnalysisService(
            maxDiffLines = maxDiffLines
        )
    }

    single {
        ReviewOrchestrationService(
            diffAnalysisService = get(),
            reviewApiClient = get(),
            ragApiClient = get(),
            mcpGitHubClient = get(),
            reviewModel = reviewModel,
            reviewTemperature = reviewTemperature,
            reviewMaxTokens = reviewMaxTokens
        )
    }
}
