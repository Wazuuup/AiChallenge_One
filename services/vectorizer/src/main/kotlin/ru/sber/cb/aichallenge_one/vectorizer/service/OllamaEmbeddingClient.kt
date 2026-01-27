package ru.sber.cb.aichallenge_one.vectorizer.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class OllamaEmbedRequest(
    val model: String,
    val input: String
)

@Serializable
data class OllamaEmbedResponse(
    @SerialName("embeddings")
    val embeddings: List<List<Float>>
)

class OllamaEmbeddingClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:11434"
) {
    private val logger = LoggerFactory.getLogger(OllamaEmbeddingClient::class.java)

    suspend fun generateEmbedding(text: String, model: String = "nomic-embed-text"): FloatArray? {
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/api/embed") {
                contentType(ContentType.Application.Json)
                setBody(
                    OllamaEmbedRequest(
                        model = model,
                        input = text
                    )
                )
            }

            if (response.status.isSuccess()) {
                val embedResponse = response.body<OllamaEmbedResponse>()
                embedResponse.embeddings.firstOrNull()?.toFloatArray()
            } else {
                logger.error("Ollama API error: ${response.status} - ${response.bodyAsText()}")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to generate embedding", e)
            null
        }
    }

    suspend fun generateBatchEmbeddings(texts: List<String>, model: String = "nomic-embed-text"): List<FloatArray?> {
        return texts.map { generateEmbedding(it, model) }
    }
}
