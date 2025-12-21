package ru.sber.cb.aichallenge_one.routing

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import ru.sber.cb.aichallenge_one.service.ModelsByProviderResponse
import ru.sber.cb.aichallenge_one.service.ModelsListResponse
import ru.sber.cb.aichallenge_one.service.ModelsSearchResponse
import ru.sber.cb.aichallenge_one.service.OpenRouterModelsService

fun Route.modelsRouting() {
    val modelsService by inject<OpenRouterModelsService>()

    route("/api/models") {
        // Get all available models
        get {
            try {
                if (modelsService == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Models service is not configured. Please configure OpenAI API settings.")
                    )
                    return@get
                }

                val forceRefresh = call.request.queryParameters["refresh"]?.toBoolean() ?: false
                val models = modelsService.fetchAvailableModels(forceRefresh)

                call.respond(
                    HttpStatusCode.OK, ModelsListResponse(
                        models = models,
                        count = models.size
                    )
                )
            } catch (e: Exception) {
                call.application.environment.log.error("Error fetching models", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to fetch models: ${e.message}")
                )
            }
        }

        // Get model by ID
        get("/{id...}") {
            try {
                val service = modelsService
                if (service == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Models service is not configured")
                    )
                    return@get
                }

                val modelId = call.parameters.getAll("id")?.joinToString("/") ?: ""
                if (modelId.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Model ID is required"))
                    return@get
                }

                val model = service.getModelById(modelId)
                if (model != null) {
                    call.respond(HttpStatusCode.OK, model)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Model not found: $modelId"))
                }
            } catch (e: Exception) {
                call.application.environment.log.error("Error fetching model", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to fetch model: ${e.message}")
                )
            }
        }

        // Search models
        get("/search") {
            try {
                if (modelsService == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Models service is not configured")
                    )
                    return@get
                }

                val query = call.request.queryParameters["q"] ?: ""
                if (query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Search query 'q' is required"))
                    return@get
                }

                val models = modelsService.searchModels(query)
                call.respond(
                    HttpStatusCode.OK, ModelsSearchResponse(
                        query = query,
                        models = models,
                        count = models.size
                    )
                )
            } catch (e: Exception) {
                call.application.environment.log.error("Error searching models", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to search models: ${e.message}")
                )
            }
        }

        // Get models by provider
        get("/by-provider") {
            try {
                if (modelsService == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Models service is not configured")
                    )
                    return@get
                }

                val modelsByProvider = modelsService.getModelsByProvider()
                call.respond(
                    HttpStatusCode.OK, ModelsByProviderResponse(
                        providers = modelsByProvider,
                        providerCount = modelsByProvider.size
                    )
                )
            } catch (e: Exception) {
                call.application.environment.log.error("Error fetching models by provider", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to fetch models by provider: ${e.message}")
                )
            }
        }

        // Get model statistics
        get("/stats") {
            try {
                if (modelsService == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Models service is not configured")
                    )
                    return@get
                }

                val stats = modelsService.getModelStats()
                call.respond(HttpStatusCode.OK, stats)
            } catch (e: Exception) {
                call.application.environment.log.error("Error fetching model stats", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to fetch model stats: ${e.message}")
                )
            }
        }

        // Clear cache
        post("/cache/clear") {
            try {
                if (modelsService == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        mapOf("error" to "Models service is not configured")
                    )
                    return@post
                }

                modelsService.clearCache()
                call.respond(HttpStatusCode.OK, mapOf("message" to "Cache cleared successfully"))
            } catch (e: Exception) {
                call.application.environment.log.error("Error clearing cache", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to clear cache: ${e.message}")
                )
            }
        }
    }
}
