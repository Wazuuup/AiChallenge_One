package ru.sber.cb.aichallenge_one.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class OpenRouterModel(
    val id: String,
    val name: String? = null,
    @SerialName("created")
    val created: Long? = null,
    val description: String? = null,
    @SerialName("context_length")
    val contextLength: Int? = null,
    val pricing: OpenRouterPricing? = null,
    @SerialName("top_provider")
    val topProvider: OpenRouterProvider? = null,
    val architecture: OpenRouterArchitecture? = null
)

@Serializable
data class OpenRouterPricing(
    val prompt: String? = null,
    val completion: String? = null,
    val image: String? = null,
    val request: String? = null
) {
    /**
     * Check if all pricing fields are "0" (free model)
     */
    fun isFree(): Boolean {
        return prompt == "0" && completion == "0" &&
                (image == null || image == "0") &&
                (request == null || request == "0")
    }
}

@Serializable
data class OpenRouterProvider(
    @SerialName("max_completion_tokens")
    val maxCompletionTokens: Int? = null,
    @SerialName("is_moderated")
    val isModerated: Boolean? = null
)

@Serializable
data class OpenRouterArchitecture(
    val modality: String? = null,
    val tokenizer: String? = null,
    @SerialName("instruct_type")
    val instructType: String? = null
)

@Serializable
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModel>
)

@Serializable
data class ModelStats(
    val totalModels: Int,
    val providers: Int,
    val providerBreakdown: Map<String, Int>,
    val modalityBreakdown: Map<String, Int>,
    val lastFetchTime: Long,
    val cacheAge: Long
)

@Serializable
data class ModelsListResponse(
    val models: List<OpenRouterModel>,
    val count: Int
)

@Serializable
data class ModelsSearchResponse(
    val query: String,
    val models: List<OpenRouterModel>,
    val count: Int
)

@Serializable
data class ModelsByProviderResponse(
    val providers: Map<String, List<OpenRouterModel>>,
    val providerCount: Int
)

/**
 * Service for fetching available models from OpenRouter API
 *
 * @param httpClient Ktor HTTP client instance
 * @param baseUrl Base URL of OpenRouter API (e.g., "https://openrouter.ai/api/v1")
 * @param apiKey API key for authentication
 */
class OpenRouterModelsService(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(OpenRouterModelsService::class.java)

    // Local cache of available models
    private var availableModels: List<OpenRouterModel> = emptyList()
    private var lastFetchTime: Long = 0
    private val cacheExpirationMs = 3600000L // 1 hour

    /**
     * Fetch available models from OpenRouter API
     *
     * @param forceRefresh If true, bypass cache and fetch fresh data
     * @return List of available models
     */
    suspend fun fetchAvailableModels(forceRefresh: Boolean = false): List<OpenRouterModel> {
        val currentTime = System.currentTimeMillis()

        // Return cached models if available and not expired
        if (!forceRefresh && availableModels.isNotEmpty() && (currentTime - lastFetchTime) < cacheExpirationMs) {
            logger.debug("Returning cached models (${availableModels.size} models)")
            return availableModels
        }

        return try {
            logger.info("Fetching available models from OpenRouter API")

            val response: HttpResponse = httpClient.get("$baseUrl/models") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
            }

            if (response.status.isSuccess()) {
                val modelsResponse: OpenRouterModelsResponse = response.body()

                // Filter only free models (where all pricing fields are "0")
                val allModels = modelsResponse.data
                availableModels = allModels.filter { model ->
                    model.pricing?.isFree() == true
                }
                lastFetchTime = currentTime

                logger.info("Successfully fetched ${allModels.size} models from OpenRouter, filtered to ${availableModels.size} free models")
                logger.debug("Free models: ${availableModels.map { it.id }}")

                availableModels
            } else {
                val errorBody = response.bodyAsText()
                logger.error("Failed to fetch models from OpenRouter: ${response.status} - $errorBody")
                throw Exception("Failed to fetch models: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Error fetching models from OpenRouter", e)
            // Return cached models if available, even if expired
            if (availableModels.isNotEmpty()) {
                logger.warn("Returning stale cached models due to error")
                return availableModels
            }
            throw e
        }
    }

    /**
     * Get cached models without fetching
     *
     * @return List of cached models (may be empty if never fetched)
     */
    fun getCachedModels(): List<OpenRouterModel> {
        return availableModels
    }

    /**
     * Get a specific model by ID
     *
     * @param modelId The model ID to search for
     * @return The model if found, null otherwise
     */
    suspend fun getModelById(modelId: String): OpenRouterModel? {
        if (availableModels.isEmpty()) {
            fetchAvailableModels()
        }
        return availableModels.find { it.id == modelId }
    }

    /**
     * Search models by name or description
     *
     * @param query Search query
     * @return List of matching models
     */
    suspend fun searchModels(query: String): List<OpenRouterModel> {
        if (availableModels.isEmpty()) {
            fetchAvailableModels()
        }

        val lowerQuery = query.lowercase()
        return availableModels.filter { model ->
            model.id.lowercase().contains(lowerQuery) ||
                    model.name?.lowercase()?.contains(lowerQuery) == true ||
                    model.description?.lowercase()?.contains(lowerQuery) == true
        }
    }

    /**
     * Get models grouped by provider
     *
     * @return Map of provider name to list of models
     */
    suspend fun getModelsByProvider(): Map<String, List<OpenRouterModel>> {
        if (availableModels.isEmpty()) {
            fetchAvailableModels()
        }

        return availableModels.groupBy { model ->
            model.id.substringBefore("/")
        }
    }

    /**
     * Get statistics about available models
     */
    suspend fun getModelStats(): ModelStats {
        if (availableModels.isEmpty()) {
            fetchAvailableModels()
        }

        val providers = getModelsByProvider()
        val modalityCount = availableModels
            .mapNotNull { it.architecture?.modality }
            .groupingBy { it }
            .eachCount()

        return ModelStats(
            totalModels = availableModels.size,
            providers = providers.size,
            providerBreakdown = providers.mapValues { it.value.size },
            modalityBreakdown = modalityCount,
            lastFetchTime = lastFetchTime,
            cacheAge = System.currentTimeMillis() - lastFetchTime
        )
    }

    /**
     * Clear the cached models
     */
    fun clearCache() {
        logger.info("Clearing models cache")
        availableModels = emptyList()
        lastFetchTime = 0
    }
}
