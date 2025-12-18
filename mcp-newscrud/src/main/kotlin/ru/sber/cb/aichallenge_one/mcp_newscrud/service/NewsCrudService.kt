package ru.sber.cb.aichallenge_one.mcp_newscrud.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.sber.cb.aichallenge_one.models.news.Article
import ru.sber.cb.aichallenge_one.models.news.CreateArticleRequest
import ru.sber.cb.aichallenge_one.models.news.UpdateArticleRequest

class NewsCrudService(
    private val baseUrl: String = "http://localhost:8081"
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
     * Get all articles with pagination
     */
    suspend fun getAllArticles(limit: Int = 100, offset: Long = 0): List<Article>? {
        return try {
            client.get("$baseUrl/api/news") {
                parameter("limit", limit)
                parameter("offset", offset)
            }.body()
        } catch (e: Exception) {
            println("Error getting all articles: ${e.message}")
            null
        }
    }

    /**
     * Get article by ID
     */
    suspend fun getArticleById(id: Int): Article? {
        return try {
            client.get("$baseUrl/api/news/$id").body()
        } catch (e: Exception) {
            println("Error getting article by ID: ${e.message}")
            null
        }
    }

    /**
     * Search articles
     */
    suspend fun searchArticles(query: String, limit: Int = 100): List<Article>? {
        return try {
            client.get("$baseUrl/api/news/search") {
                parameter("q", query)
                parameter("limit", limit)
            }.body()
        } catch (e: Exception) {
            println("Error searching articles: ${e.message}")
            null
        }
    }

    /**
     * Create new article
     */
    suspend fun createArticle(request: CreateArticleRequest): Article? {
        return try {
            client.post("$baseUrl/api/news") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        } catch (e: Exception) {
            println("Error creating article: ${e.message}")
            null
        }
    }

    /**
     * Update article
     */
    suspend fun updateArticle(id: Int, request: UpdateArticleRequest): Article? {
        return try {
            client.put("$baseUrl/api/news/$id") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        } catch (e: Exception) {
            println("Error updating article: ${e.message}")
            null
        }
    }

    /**
     * Delete article
     */
    suspend fun deleteArticle(id: Int): Boolean {
        return try {
            val response = client.delete("$baseUrl/api/news/$id")
            response.status == HttpStatusCode.NoContent
        } catch (e: Exception) {
            println("Error deleting article: ${e.message}")
            false
        }
    }
}
