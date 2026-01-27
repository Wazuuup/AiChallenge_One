package ru.sber.cb.aichallenge_one.rag.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.models.vectorizer.TextVectorizeRequest
import ru.sber.cb.aichallenge_one.models.vectorizer.TextVectorizeResponse

class VectorizerClient(
    private val httpClient: HttpClient,
    private val vectorizerUrl: String
) {
    private val logger = LoggerFactory.getLogger(VectorizerClient::class.java)

    suspend fun vectorize(text: String, model: String = "nomic-embed-text"): FloatArray? {
        return try {
            val response: HttpResponse = httpClient.post("$vectorizerUrl/api/vectorize") {
                contentType(ContentType.Application.Json)
                setBody(TextVectorizeRequest(text = text, model = model))
            }

            if (response.status.isSuccess()) {
                val vectorizeResponse = response.body<TextVectorizeResponse>()
                logger.info("Successfully vectorized text (dimension: ${vectorizeResponse.dimension})")
                vectorizeResponse.embedding.toFloatArray()
            } else {
                logger.error("Vectorizer API error: ${response.status} - ${response.bodyAsText()}")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to vectorize text", e)
            null
        }
    }
}
