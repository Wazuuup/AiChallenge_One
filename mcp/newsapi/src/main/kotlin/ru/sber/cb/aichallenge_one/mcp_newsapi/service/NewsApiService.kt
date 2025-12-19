package ru.sber.cb.aichallenge_one.mcp_newsapi.service

import com.typesafe.config.ConfigFactory
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.sber.cb.aichallenge_one.mcp_newsapi.models.ArticleList

/**
 * Service for fetching news from NewsAPI.org
 */
class NewsApiService {
    private val config = ConfigFactory.load()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
    }

    private val baseUrl = config.getString("newsapi.baseUrl")
    private val apiKey = try {
        config.getString("newsapi.apiKey")
    } catch (e: Exception) {
        System.getenv("NEWSAPI_API_KEY") ?: ""
    }

    /**
     * Search for news articles using /everything/ endpoint
     * @param query Theme/keywords to search for
     * @param from Date to start from (ISO-8601 format, e.g., "2025-12-01")
     * @param sortBy Sort option (relevancy, popularity, publishedAt)
     * @param language Language code (e.g., "en", "ru")
     * @param page Page number (default: 1)
     * @return ArticleList with search results or null on error
     */
    suspend fun getEverything(
        query: String,
        from: String,
        sortBy: String,
        language: String,
        page: Int? = null
    ): ArticleList? {
        return try {
            val response: ArticleList = httpClient.get("$baseUrl/everything") {
                header("X-Api-Key", apiKey)
                parameter("q", query)
                parameter("from", from)
                parameter("sortBy", sortBy)
                parameter("language", language)
                if (page != null) {
                    parameter("page", page)
                }
            }.body()
            response
        } catch (e: Exception) {
            println("Error fetching news from /everything/: ${e.message}")
            null
        }
    }

    /**
     * Get top headlines using /top-headlines/ endpoint
     * @param country Country code (e.g., "us", "ru")
     * @param category News category (business, entertainment, general, health, science, sports, technology)
     * @param query Optional search query
     * @param pageSize Number of results per page (max 100)
     * @param page Page number
     * @return ArticleList with top headlines or null on error
     */
    suspend fun getTopHeadlines(
        country: String,
        category: String? = null,
        query: String? = null,
        pageSize: Int? = null,
        page: Int? = null
    ): ArticleList? {
        return try {
            val response: ArticleList = httpClient.get("$baseUrl/top-headlines") {
                header("X-Api-Key", apiKey)
                parameter("country", country)
                if (category != null) {
                    parameter("category", category)
                }
                if (query != null) {
                    parameter("q", query)
                }
                if (pageSize != null) {
                    parameter("pageSize", pageSize)
                }
                if (page != null) {
                    parameter("page", page)
                }
            }.body()
            response
        } catch (e: Exception) {
            println("Error fetching news from /top-headlines/: ${e.message}")
            null
        }
    }

    /**
     * Clean up resources
     */
    fun close() {
        httpClient.close()
    }
}
