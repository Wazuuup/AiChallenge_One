package ru.sber.cb.aichallenge_one.github.webhook.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.models.rag.SearchRequest
import ru.sber.cb.aichallenge_one.models.rag.SearchResponse

class RagApiClient(
    private val httpClient: HttpClient,
    private val ragBaseUrl: String,
    private val defaultLimit: Int
) {
    private val logger = LoggerFactory.getLogger(RagApiClient::class.java)

    suspend fun search(keywords: List<String>, limit: Int = defaultLimit): List<String> {
        if (keywords.isEmpty()) {
            logger.warn("No keywords provided for RAG search")
            return emptyList()
        }

        return try {
            val query = keywords.joinToString(" ")
            logger.info("RAG search with ${keywords.size} keywords: ${keywords.take(5)}")

            val response: HttpResponse = httpClient.post("$ragBaseUrl/api/rag/search") {
                contentType(ContentType.Application.Json)
                setBody(SearchRequest(query = query, limit = limit))
            }

            if (response.status.isSuccess()) {
                val searchResponse = response.body<SearchResponse>()
                logger.info("RAG search returned ${searchResponse.results.size} chunks")
                searchResponse.results
            } else {
                logger.warn("RAG API returned ${response.status}, using degraded mode")
                emptyList()
            }
        } catch (e: Exception) {
            logger.warn("RAG search failed (degraded mode): ${e.message}")
            emptyList() // Degraded mode - continue without RAG context
        }
    }
}
