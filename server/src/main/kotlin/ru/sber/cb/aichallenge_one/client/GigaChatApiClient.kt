package ru.sber.cb.aichallenge_one.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.*

enum class MessageRole(val value: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    FUNCTION("function");
}
@Serializable
data class GigaChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class GigaChatRequest(
    val model: String,
    val messages: List<GigaChatMessage>,
    val temperature: Double = 0.7,
    val top_p: Double = 0.9,
    val max_tokens: Int = 1024
)

@Serializable
data class GigaChatChoice(
    val message: GigaChatMessage,
    val index: Int,
    val finish_reason: String
)

@Serializable
data class GigaChatResponse(
    val choices: List<GigaChatChoice>,
    val created: Long,
    val model: String,
    val object_type: String? = null
)

@Serializable
data class OAuthTokenResponse(
    val access_token: String,
    val expires_at: Long
)

class GigaChatApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val authUrl: String,
    private val clientId: String,
    private val clientSecret: String,
    private val scope: String
) {
    private val logger = LoggerFactory.getLogger(GigaChatApiClient::class.java)
    private var accessToken: String? = null
    private var tokenExpiresAt: Long = 0

    private suspend fun getAccessToken(): String {
        val currentTime = System.currentTimeMillis()

        if (accessToken != null && currentTime < tokenExpiresAt) {
            return accessToken!!
        }

        logger.info("Obtaining new access token from GigaChat")

        val response: HttpResponse = httpClient.post(authUrl) {
            headers {
                append(HttpHeaders.Authorization, "Basic $clientSecret")
                append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                append("RqUID", UUID.randomUUID().toString())
            }
            setBody("scope=$scope")
        }

        if (response.status.isSuccess()) {
            val tokenResponse: OAuthTokenResponse = response.body()
            accessToken = tokenResponse.access_token
            tokenExpiresAt = tokenResponse.expires_at
            logger.info("Successfully obtained access token")
            return accessToken!!
        } else {
            val errorBody = response.bodyAsText()
            logger.error("Failed to obtain access token: ${response.status} - $errorBody")
            throw Exception("Failed to obtain access token: ${response.status}")
        }
    }

    suspend fun sendMessage(messageHistory: List<GigaChatMessage>): String {
        try {
            val token = getAccessToken()

            val systemPrompt = GigaChatMessage(
                role = MessageRole.SYSTEM.value,
                content = "Ты помощник, чья задача заключается в последовательном сборе информации от пользователя перед тем, как давать конечный ответ. " +
                        "Если пользователь ставит задачу, задавай уточняющие вопросы исключительно по одному. " +
                        "Важно: не задавай сразу несколько вопросов в одном сообщении, каждый новый вопрос отправляй отдельно и дожидайся ответа пользователя. " +
                        "Только после полного сбора необходимой информации суммируй её и дай подробный и четкий ответ на первоначальный запрос пользователя."
            )

            val request = GigaChatRequest(
                model = "GigaChat",
                messages = listOf(systemPrompt) + messageHistory
            )

            logger.info("Sending message to GigaChat: {}", request)

            val response: HttpResponse = httpClient.post("$baseUrl/chat/completions") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val chatResponse: GigaChatResponse = response.body()
                return chatResponse.choices.firstOrNull()?.message?.content.also {
                    logger.info("Получен ответ от Gigachat: {}", it)
                }
                    ?: "Получен пустой ответ от GigaChat"
            } else {
                val errorBody = response.bodyAsText()
                logger.error("GigaChat API error: ${response.status} - $errorBody")
                throw Exception("GigaChat API error: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Error communicating with GigaChat API", e)
            throw e
        }
    }
}
