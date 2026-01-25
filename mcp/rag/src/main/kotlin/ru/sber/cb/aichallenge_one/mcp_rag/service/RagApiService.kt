package ru.sber.cb.aichallenge_one.mcp_rag.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.sber.cb.aichallenge_one.models.rag.SearchRequest
import ru.sber.cb.aichallenge_one.models.rag.SearchResponse

/**
 * HTTP client service for interacting with the RAG API.
 */
class RagApiService(
    private val baseUrl: String = "http://localhost:8091"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
    }

    /**
     * Search for similar text chunks using vector similarity search.
     *
     * @param query The search query text
     * @param limit Maximum number of results to return (default: 5)
     * @return SearchResponse with similar chunks, or null if request fails
     */
    suspend fun searchSimilarChunks(query: String, limit: Int = 5): SearchResponse? {
        return try {
            val request = SearchRequest(
                query = query,
                limit = limit
            )

            client.post("$baseUrl/api/rag/search") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        } catch (e: Exception) {
            println("Error searching similar chunks: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
