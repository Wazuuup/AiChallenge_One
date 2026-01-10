package ru.sber.cb.aichallenge_one.mcp_rag

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import ru.sber.cb.aichallenge_one.mcp_rag.service.RagApiService

/**
 * Configures the MCP (Model Context Protocol) server with RAG tools.
 */
fun Application.configureRagMcp() {
    val ragService = RagApiService()

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("RAG MCP Server is running on port $RAG_MCP_PORT (HTTP) and $RAG_MCP_SSL_PORT (HTTPS)")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "rag-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                // ========== Tool: search_similar_chunks ==========
                addTool(
                    name = "search_similar_chunks",
                    description = "Search for semantically similar text chunks in the RAG knowledge base using vector similarity search",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("query") {
                                    put("type", "string")
                                    put("description", "The search query text to find similar chunks")
                                }
                                putJsonObject("limit") {
                                    put("type", "integer")
                                    put(
                                        "description",
                                        "Maximum number of similar chunks to return (default: 5, max: 100)"
                                    )
                                    put("default", 5)
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
                    val limit = arguments.arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 5

                    runBlocking {
                        if (query.isNullOrBlank()) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Search query cannot be empty")),
                                isError = true
                            )
                        }

                        if (limit < 1 || limit > 100) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Limit must be between 1 and 100")),
                                isError = true
                            )
                        }

                        val searchResponse = ragService.searchSimilarChunks(query, limit)

                        if (searchResponse == null) {
                            CallToolResult(
                                content = listOf(TextContent("Error: Failed to search similar chunks in RAG service")),
                                isError = true
                            )
                        } else if (searchResponse.results.isEmpty()) {
                            CallToolResult(
                                content = listOf(TextContent("No similar chunks found for query: '$query'"))
                            )
                        } else {
                            val resultText = buildString {
                                appendLine("Found ${searchResponse.results.size} similar chunks for query: '$query'")
                                appendLine()

                                searchResponse.results.forEachIndexed { index, chunk ->
                                    appendLine("--- Chunk ${index + 1} ---")
                                    appendLine(chunk)
                                    appendLine()
                                }
                            }
                            CallToolResult(content = listOf(TextContent(resultText)))
                        }
                    }
                }
            }
        }
    }
}
