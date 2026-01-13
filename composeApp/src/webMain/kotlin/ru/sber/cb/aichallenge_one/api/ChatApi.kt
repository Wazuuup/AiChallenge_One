package ru.sber.cb.aichallenge_one.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.sber.cb.aichallenge_one.models.*

@Serializable
data class OpenRouterModel(
    val id: String,
    val name: String? = null
)

@Serializable
data class ModelsListResponse(
    val models: List<OpenRouterModel>,
    val count: Int
)

class ChatApi {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private val serverUrl = "http://localhost:${Constants.SERVER_PORT.number}"

    suspend fun sendMessage(
        text: String,
        systemPrompt: String = "",
        temperature: Double = 0.7,
        provider: String = "gigachat",
        model: String? = null,
        maxTokens: Int? = null,
        useRag: Boolean = false,
        isHelpCommand: Boolean = false
    ): ChatResponse {
        return try {
            val response = client.post("$serverUrl/api/send-message") {
                contentType(ContentType.Application.Json)
                setBody(
                    SendMessageRequest(
                        text,
                        systemPrompt,
                        temperature,
                        provider,
                        model,
                        maxTokens,
                        useRag = useRag,
                        isHelpCommand = isHelpCommand
                    )
                )
            }
            response.body()
        } catch (e: Exception) {
            println("Error sending message: $e")
            throw e
        }
    }

    suspend fun clearHistory() {
        try {
            client.post("$serverUrl/api/clear-history") {
                contentType(ContentType.Application.Json)
            }
        } catch (e: Exception) {
            println("Error clearing history: $e")
            throw e
        }
    }

    suspend fun fetchHistory(provider: String): List<ChatMessage> {
        return try {
            client.get("$serverUrl/api/history?provider=$provider").body()
        } catch (e: Exception) {
            println("Error fetching history: $e")
            emptyList()
        }
    }

    suspend fun fetchAvailableModels(): List<ModelInfo> {
        return try {
            val response: ModelsListResponse = client.get("$serverUrl/api/models").body()

            response.models.map { model ->
                ModelInfo(
                    id = model.id,
                    name = model.name ?: model.id
                )
            }
        } catch (e: Exception) {
            println("Error fetching models: $e")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchNotifications(): NotificationsResponse {
        return try {
            client.get("$serverUrl/api/notifications").body()
        } catch (e: Exception) {
            println("Error fetching notifications: $e")
            NotificationsResponse(notifications = emptyList(), count = 0)
        }
    }

    suspend fun markNotificationAsRead(notificationId: String): MarkReadResponse {
        return try {
            client.post("$serverUrl/api/notifications/$notificationId/read").body()
        } catch (e: Exception) {
            println("Error marking notification as read: $e")
            MarkReadResponse(success = false, message = "Network error")
        }
    }
}
