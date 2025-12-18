package ru.sber.cb.aichallenge_one.mcp_newsapi

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import ru.sber.cb.aichallenge_one.mcp_newsapi.service.NewsApiService

/**
 * Configures the MCP (Model Context Protocol) server with NewsAPI tools.
 */
fun Application.configureNewsApiMcp() {
    val newsApiService = NewsApiService()

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("NewsAPI MCP Server is running on port $NEWS_API_SERVER_PORT")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "newsapi-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                // ========== Tool 1: search_news (everything endpoint) ==========
                addTool(
                    name = "search_news",
                    description = "Search for news articles by theme/keywords with date range and language filters. Uses NewsAPI /everything/ endpoint.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("query") {
                                    put("type", "string")
                                    put("description", "Keywords or phrase to search for in article titles and bodies")
                                }
                                putJsonObject("from") {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Start date for articles in ISO-8601 format (e.g., '2025-12-01')"
                                    )
                                }
                                putJsonObject("sortBy") {
                                    put("type", "string")
                                    put("enum", buildJsonArray {
                                        add("relevancy")
                                        add("popularity")
                                        add("publishedAt")
                                    })
                                    put("description", "Sort articles by relevancy, popularity, or publishedAt")
                                }
                                putJsonObject("language") {
                                    put("type", "string")
                                    put("description", "2-letter ISO-639-1 language code (e.g., 'en', 'ru', 'es')")
                                }
                                putJsonObject("page") {
                                    put("type", "integer")
                                    put("description", "Page number for pagination (optional, default: 1)")
                                }
                            }
                            putJsonArray("required") {
                                add("query")
                                add("from")
                                add("sortBy")
                                add("language")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val query = arguments.arguments?.get("query")?.jsonPrimitive?.content ?: ""
                    val from = arguments.arguments?.get("from")?.jsonPrimitive?.content ?: ""
                    val sortBy = arguments.arguments?.get("sortBy")?.jsonPrimitive?.content ?: ""
                    val language = arguments.arguments?.get("language")?.jsonPrimitive?.content ?: ""
                    val page = arguments.arguments?.get("page")?.jsonPrimitive?.intOrNull

                    runBlocking {
                        // Validation
                        if (query.isBlank()) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Query parameter cannot be empty")),
                                isError = true
                            )
                        }

                        if (sortBy !in listOf("relevancy", "popularity", "publishedAt")) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: sortBy must be one of: relevancy, popularity, publishedAt")),
                                isError = true
                            )
                        }

                        val response = newsApiService.getEverything(
                            query = query,
                            from = from,
                            sortBy = sortBy,
                            language = language,
                            page = page
                        )

                        if (response != null && response.status == "ok") {
                            val articles = response.articles ?: emptyList()

                            if (articles.isEmpty()) {
                                CallToolResult(
                                    content = listOf(TextContent("No articles found for the given search criteria"))
                                )
                            } else {
                                val resultText = buildString {
                                    appendLine("Found ${response.totalResults} total results")
                                    appendLine("Showing ${articles.size} articles:")
                                    appendLine()

                                    articles.forEachIndexed { index, article ->
                                        appendLine("--- Article ${index + 1} ---")
                                        appendLine("Title: ${article.title}")
                                        article.author?.let { appendLine("Author: $it") }
                                        article.source?.name?.let { appendLine("Source: $it") }
                                        article.description?.let { appendLine("Description: $it") }
                                        article.url?.let { appendLine("URL: $it") }
                                        article.publishedAt?.let { appendLine("Published: $it") }
                                        appendLine()
                                    }
                                }
                                CallToolResult(content = listOf(TextContent(resultText)))
                            }
                        } else {
                            val errorMessage = response?.message ?: "Unknown error occurred"
                            val errorCode = response?.code ?: "N/A"
                            CallToolResult(
                                content = listOf(TextContent("Error fetching news: $errorMessage (Code: $errorCode)")),
                                isError = true
                            )
                        }
                    }
                }

                // ========== Tool 2: get_top_headlines ==========
                addTool(
                    name = "get_top_headlines",
                    description = "Get breaking news headlines by country and optional category. Uses NewsAPI /top-headlines/ endpoint.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("country") {
                                    put("type", "string")
                                    put("description", "2-letter ISO 3166-1 country code (e.g., 'us', 'ru', 'gb')")
                                }
                                putJsonObject("category") {
                                    put("type", "string")
                                    put("enum", buildJsonArray {
                                        add("business")
                                        add("entertainment")
                                        add("general")
                                        add("health")
                                        add("science")
                                        add("sports")
                                        add("technology")
                                    })
                                    put("description", "News category (optional)")
                                }
                                putJsonObject("q") {
                                    put("type", "string")
                                    put("description", "Keywords or phrase to search for (optional)")
                                }
                                putJsonObject("pageSize") {
                                    put("type", "integer")
                                    put("description", "Number of results to return per page (max 100, optional)")
                                }
                                putJsonObject("page") {
                                    put("type", "integer")
                                    put("description", "Page number for pagination (optional)")
                                }
                            }
                            putJsonArray("required") {
                                add("country")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val country = arguments.arguments?.get("country")?.jsonPrimitive?.content ?: ""
                    val category = arguments.arguments?.get("category")?.jsonPrimitive?.contentOrNull
                    val query = arguments.arguments?.get("q")?.jsonPrimitive?.contentOrNull
                    val pageSize = arguments.arguments?.get("pageSize")?.jsonPrimitive?.intOrNull
                    val page = arguments.arguments?.get("page")?.jsonPrimitive?.intOrNull

                    runBlocking {
                        // Validation
                        if (country.isBlank()) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Country code cannot be empty")),
                                isError = true
                            )
                        }

                        if (country.length != 2) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Country code must be a 2-letter ISO 3166-1 code")),
                                isError = true
                            )
                        }

                        val validCategories =
                            listOf("business", "entertainment", "general", "health", "science", "sports", "technology")
                        if (category != null && category !in validCategories) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Invalid category. Must be one of: ${validCategories.joinToString()}")),
                                isError = true
                            )
                        }

                        if (pageSize != null && (pageSize < 1 || pageSize > 100)) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: pageSize must be between 1 and 100")),
                                isError = true
                            )
                        }

                        val response = newsApiService.getTopHeadlines(
                            country = country,
                            category = category,
                            query = query,
                            pageSize = pageSize,
                            page = page
                        )

                        if (response != null && response.status == "ok") {
                            val articles = response.articles ?: emptyList()

                            if (articles.isEmpty()) {
                                CallToolResult(
                                    content = listOf(TextContent("No top headlines found for the given criteria"))
                                )
                            } else {
                                val resultText = buildString {
                                    appendLine("Top Headlines for ${country.uppercase()}")
                                    category?.let { appendLine("Category: ${it.uppercase()}") }
                                    appendLine("Found ${response.totalResults} total results")
                                    appendLine("Showing ${articles.size} headlines:")
                                    appendLine()

                                    articles.forEachIndexed { index, article ->
                                        appendLine("--- Headline ${index + 1} ---")
                                        appendLine("Title: ${article.title}")
                                        article.author?.let { appendLine("Author: $it") }
                                        article.source?.name?.let { appendLine("Source: $it") }
                                        article.description?.let { appendLine("Description: $it") }
                                        article.url?.let { appendLine("URL: $it") }
                                        article.publishedAt?.let { appendLine("Published: $it") }
                                        appendLine()
                                    }
                                }
                                CallToolResult(content = listOf(TextContent(resultText)))
                            }
                        } else {
                            val errorMessage = response?.message ?: "Unknown error occurred"
                            val errorCode = response?.code ?: "N/A"
                            CallToolResult(
                                content = listOf(TextContent("Error fetching top headlines: $errorMessage (Code: $errorCode)")),
                                isError = true
                            )
                        }
                    }
                }
            }
        }
    }
}
