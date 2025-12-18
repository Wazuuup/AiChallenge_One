package ru.sber.cb.aichallenge_one.mcp_newscrud

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import ru.sber.cb.aichallenge_one.mcp_newscrud.service.NewsCrudService
import ru.sber.cb.aichallenge_one.models.news.CreateArticleRequest
import ru.sber.cb.aichallenge_one.models.news.Source
import ru.sber.cb.aichallenge_one.models.news.UpdateArticleRequest

/**
 * Configures the MCP (Model Context Protocol) server with News CRUD tools.
 */
fun Application.configureNewsCrudMcp() {
    val newsCrudService = NewsCrudService()

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("News CRUD MCP Server is running on port $NEWS_CRUD_MCP_PORT (HTTP) and $NEWS_CRUD_MCP_SSL_PORT (HTTPS)")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "newscrud-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                // ========== Tool 1: get_all_articles ==========
                addTool(
                    name = "get_all_articles",
                    description = "Retrieve all news articles from the database with pagination support.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("limit") {
                                    put("type", "integer")
                                    put("description", "Maximum number of articles to return (default: 100, max: 1000)")
                                }
                                putJsonObject("offset") {
                                    put("type", "integer")
                                    put("description", "Number of articles to skip for pagination (default: 0)")
                                }
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val limit = arguments.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 100
                    val offset = arguments.arguments?.get("offset")?.jsonPrimitive?.longOrNull ?: 0L

                    runBlocking {
                        val articles = newsCrudService.getAllArticles(limit, offset)

                        if (articles == null) {
                            CallToolResult(
                                content = listOf(TextContent("Error: Failed to retrieve articles from the database")),
                                isError = true
                            )
                        } else if (articles.isEmpty()) {
                            CallToolResult(
                                content = listOf(TextContent("No articles found in the database"))
                            )
                        } else {
                            val resultText = buildString {
                                appendLine("Found ${articles.size} articles:")
                                appendLine()

                                articles.forEachIndexed { index, article ->
                                    appendLine("--- Article ${index + 1} (ID: ${article.id}) ---")
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
                    }
                }

                // ========== Tool 2: get_article_by_id ==========
                addTool(
                    name = "get_article_by_id",
                    description = "Retrieve a specific news article by its ID from the database.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("id") {
                                    put("type", "integer")
                                    put("description", "The unique identifier of the article")
                                }
                            }
                            putJsonArray("required") {
                                add("id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val id = arguments.arguments?.get("id")?.jsonPrimitive?.intOrNull

                    runBlocking {
                        if (id == null || id <= 0) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Invalid article ID. ID must be a positive integer")),
                                isError = true
                            )
                        }

                        val article = newsCrudService.getArticleById(id)

                        if (article == null) {
                            CallToolResult(
                                content = listOf(TextContent("Error: Article with ID $id not found")),
                                isError = true
                            )
                        } else {
                            val resultText = buildString {
                                appendLine("Article ID: ${article.id}")
                                appendLine("Title: ${article.title}")
                                article.author?.let { appendLine("Author: $it") }
                                article.source?.let {
                                    appendLine("Source ID: ${it.id}")
                                    appendLine("Source Name: ${it.name}")
                                }
                                article.description?.let { appendLine("Description: $it") }
                                article.url?.let { appendLine("URL: $it") }
                                article.urlToImage?.let { appendLine("Image URL: $it") }
                                article.publishedAt?.let { appendLine("Published: $it") }
                                article.content?.let { appendLine("Content: $it") }
                            }
                            CallToolResult(content = listOf(TextContent(resultText)))
                        }
                    }
                }

                // ========== Tool 3: search_articles ==========
                addTool(
                    name = "search_articles",
                    description = "Search for news articles by keywords in title, description, or content.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("query") {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Search query to match against article title, description, and content"
                                    )
                                }
                                putJsonObject("limit") {
                                    put("type", "integer")
                                    put("description", "Maximum number of results to return (default: 100, max: 1000)")
                                }
                            }
                            putJsonArray("required") {
                                add("query")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val query = arguments.arguments?.get("query")?.jsonPrimitive?.content
                    val limit = arguments.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 100

                    runBlocking {
                        if (query.isNullOrBlank()) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Search query cannot be empty")),
                                isError = true
                            )
                        }

                        val articles = newsCrudService.searchArticles(query, limit)

                        if (articles == null) {
                            CallToolResult(
                                content = listOf(TextContent("Error: Failed to search articles")),
                                isError = true
                            )
                        } else if (articles.isEmpty()) {
                            CallToolResult(
                                content = listOf(TextContent("No articles found matching query: '$query'"))
                            )
                        } else {
                            val resultText = buildString {
                                appendLine("Found ${articles.size} articles matching '$query':")
                                appendLine()

                                articles.forEachIndexed { index, article ->
                                    appendLine("--- Article ${index + 1} (ID: ${article.id}) ---")
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
                    }
                }

                // ========== Tool 4: create_article ==========
                addTool(
                    name = "create_article",
                    description = "Create a new news article in the database.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("title") {
                                    put("type", "string")
                                    put("description", "Article title (required)")
                                }
                                putJsonObject("author") {
                                    put("type", "string")
                                    put("description", "Article author (optional)")
                                }
                                putJsonObject("description") {
                                    put("type", "string")
                                    put("description", "Article description (optional)")
                                }
                                putJsonObject("url") {
                                    put("type", "string")
                                    put("description", "Article URL (optional)")
                                }
                                putJsonObject("urlToImage") {
                                    put("type", "string")
                                    put("description", "Image URL (optional)")
                                }
                                putJsonObject("publishedAt") {
                                    put("type", "string")
                                    put("description", "Publication date in ISO-8601 format (optional)")
                                }
                                putJsonObject("content") {
                                    put("type", "string")
                                    put("description", "Article content (optional)")
                                }
                                putJsonObject("sourceId") {
                                    put("type", "string")
                                    put("description", "Source ID (optional)")
                                }
                                putJsonObject("sourceName") {
                                    put("type", "string")
                                    put("description", "Source name (optional)")
                                }
                            }
                            putJsonArray("required") {
                                add("title")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val title = arguments.arguments?.get("title")?.jsonPrimitive?.content
                    val author = arguments.arguments?.get("author")?.jsonPrimitive?.contentOrNull
                    val description = arguments.arguments?.get("description")?.jsonPrimitive?.contentOrNull
                    val url = arguments.arguments?.get("url")?.jsonPrimitive?.contentOrNull
                    val urlToImage = arguments.arguments?.get("urlToImage")?.jsonPrimitive?.contentOrNull
                    val publishedAt = arguments.arguments?.get("publishedAt")?.jsonPrimitive?.contentOrNull
                    val content = arguments.arguments?.get("content")?.jsonPrimitive?.contentOrNull
                    val sourceId = arguments.arguments?.get("sourceId")?.jsonPrimitive?.contentOrNull
                    val sourceName = arguments.arguments?.get("sourceName")?.jsonPrimitive?.contentOrNull

                    runBlocking {
                        if (title.isNullOrBlank()) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Title is required and cannot be blank")),
                                isError = true
                            )
                        }

                        val source = if (sourceId != null || sourceName != null) {
                            Source(id = sourceId, name = sourceName)
                        } else null

                        val request = CreateArticleRequest(
                            source = source,
                            author = author,
                            title = title,
                            description = description,
                            url = url,
                            urlToImage = urlToImage,
                            publishedAt = publishedAt,
                            content = content
                        )

                        val createdArticle = newsCrudService.createArticle(request)

                        if (createdArticle == null) {
                            CallToolResult(
                                content = listOf(TextContent("Error: Failed to create article")),
                                isError = true
                            )
                        } else {
                            val resultText = buildString {
                                appendLine("✓ Article created successfully!")
                                appendLine("Article ID: ${createdArticle.id}")
                                appendLine("Title: ${createdArticle.title}")
                                createdArticle.author?.let { appendLine("Author: $it") }
                                createdArticle.source?.name?.let { appendLine("Source: $it") }
                                createdArticle.url?.let { appendLine("URL: $it") }
                            }
                            CallToolResult(content = listOf(TextContent(resultText)))
                        }
                    }
                }

                // ========== Tool 5: update_article ==========
                addTool(
                    name = "update_article",
                    description = "Update an existing news article in the database.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("id") {
                                    put("type", "integer")
                                    put("description", "Article ID to update (required)")
                                }
                                putJsonObject("title") {
                                    put("type", "string")
                                    put("description", "Updated article title (optional)")
                                }
                                putJsonObject("author") {
                                    put("type", "string")
                                    put("description", "Updated author (optional)")
                                }
                                putJsonObject("description") {
                                    put("type", "string")
                                    put("description", "Updated description (optional)")
                                }
                                putJsonObject("url") {
                                    put("type", "string")
                                    put("description", "Updated URL (optional)")
                                }
                                putJsonObject("urlToImage") {
                                    put("type", "string")
                                    put("description", "Updated image URL (optional)")
                                }
                                putJsonObject("publishedAt") {
                                    put("type", "string")
                                    put("description", "Updated publication date (optional)")
                                }
                                putJsonObject("content") {
                                    put("type", "string")
                                    put("description", "Updated content (optional)")
                                }
                                putJsonObject("sourceId") {
                                    put("type", "string")
                                    put("description", "Updated source ID (optional)")
                                }
                                putJsonObject("sourceName") {
                                    put("type", "string")
                                    put("description", "Updated source name (optional)")
                                }
                            }
                            putJsonArray("required") {
                                add("id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val id = arguments.arguments?.get("id")?.jsonPrimitive?.intOrNull
                    val title = arguments.arguments?.get("title")?.jsonPrimitive?.contentOrNull
                    val author = arguments.arguments?.get("author")?.jsonPrimitive?.contentOrNull
                    val description = arguments.arguments?.get("description")?.jsonPrimitive?.contentOrNull
                    val url = arguments.arguments?.get("url")?.jsonPrimitive?.contentOrNull
                    val urlToImage = arguments.arguments?.get("urlToImage")?.jsonPrimitive?.contentOrNull
                    val publishedAt = arguments.arguments?.get("publishedAt")?.jsonPrimitive?.contentOrNull
                    val content = arguments.arguments?.get("content")?.jsonPrimitive?.contentOrNull
                    val sourceId = arguments.arguments?.get("sourceId")?.jsonPrimitive?.contentOrNull
                    val sourceName = arguments.arguments?.get("sourceName")?.jsonPrimitive?.contentOrNull

                    runBlocking {
                        if (id == null || id <= 0) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Invalid article ID")),
                                isError = true
                            )
                        }

                        val source = if (sourceId != null || sourceName != null) {
                            Source(id = sourceId, name = sourceName)
                        } else null

                        val request = UpdateArticleRequest(
                            source = source,
                            author = author,
                            title = title,
                            description = description,
                            url = url,
                            urlToImage = urlToImage,
                            publishedAt = publishedAt,
                            content = content
                        )

                        val updatedArticle = newsCrudService.updateArticle(id, request)

                        if (updatedArticle == null) {
                            CallToolResult(
                                content = listOf(TextContent("Error: Article with ID $id not found or failed to update")),
                                isError = true
                            )
                        } else {
                            val resultText = buildString {
                                appendLine("✓ Article updated successfully!")
                                appendLine("Article ID: ${updatedArticle.id}")
                                appendLine("Title: ${updatedArticle.title}")
                                updatedArticle.author?.let { appendLine("Author: $it") }
                                updatedArticle.source?.name?.let { appendLine("Source: $it") }
                                updatedArticle.url?.let { appendLine("URL: $it") }
                            }
                            CallToolResult(content = listOf(TextContent(resultText)))
                        }
                    }
                }

                // ========== Tool 6: delete_article ==========
                addTool(
                    name = "delete_article",
                    description = "Delete a news article from the database by its ID.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("id") {
                                    put("type", "integer")
                                    put("description", "The unique identifier of the article to delete")
                                }
                            }
                            putJsonArray("required") {
                                add("id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val id = arguments.arguments?.get("id")?.jsonPrimitive?.intOrNull

                    runBlocking {
                        if (id == null || id <= 0) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Invalid article ID. ID must be a positive integer")),
                                isError = true
                            )
                        }

                        val deleted = newsCrudService.deleteArticle(id)

                        if (deleted) {
                            CallToolResult(
                                content = listOf(TextContent("✓ Article with ID $id has been successfully deleted"))
                            )
                        } else {
                            CallToolResult(
                                content = listOf(TextContent("Error: Article with ID $id not found or failed to delete")),
                                isError = true
                            )
                        }
                    }
                }
            }
        }
    }
}
