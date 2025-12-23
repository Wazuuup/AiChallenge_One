package ru.sber.cb.aichallenge_one.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.models.rag.SearchRequest
import ru.sber.cb.aichallenge_one.models.rag.SearchResponse

class RagClient(
    private val httpClient: HttpClient,
    private val ragBaseUrl: String = "http://localhost:8091"
) {
    private val logger = LoggerFactory.getLogger(RagClient::class.java)

    suspend fun searchSimilar(query: String, limit: Int = 5): List<String>? {
        return try {
            logger.info("Searching RAG service for: '$query' (limit: $limit)")

            val response = httpClient.post("$ragBaseUrl/api/rag/search") {
                contentType(ContentType.Application.Json)
                setBody(SearchRequest(query, limit))
            }

            if (response.status.isSuccess()) {
                val searchResponse = response.body<SearchResponse>()
                logger.info("RAG service returned ${searchResponse.results.size} results")
                searchResponse.results
            } else {
                logger.error("RAG service returned error: ${response.status}")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to call RAG service", e)
            null
        }
    }
}
