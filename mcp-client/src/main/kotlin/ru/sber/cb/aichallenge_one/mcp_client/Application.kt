package ru.sber.cb.aichallenge_one.mcp_client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

const val MCP_SERVER_URL = "http://127.0.0.1:8082"

/**
 * MCP CLI Client - connects to MCP server and retrieves list of available tools
 */
fun main() = runBlocking {
    println("╔═══════════════════════════════════════════════════════════╗")
    println("║        MCP Client - Model Context Protocol CLI           ║")
    println("╚═══════════════════════════════════════════════════════════╝")
    println()

    // Create HTTP client for SSE transport
    val httpClient = HttpClient(CIO) {
        install(SSE)
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    // Create MCP client using official SDK
    val mcpClient = Client(
        clientInfo = Implementation(
            name = "mcp-cli-client",
            version = "1.0.0"
        ),
        options = ClientOptions()
    )

    try {
        println("Connecting to MCP server at $MCP_SERVER_URL...")

        // Create SSE transport to connect to server
        // Try root path as mcp() might register there by default
        val transport = SseClientTransport(
            urlString = MCP_SERVER_URL,
            client = httpClient
        )

        // Connect to server
        mcpClient.connect(transport)

        println("✓ Successfully connected to MCP server")
        println()

        // Request list of available tools
        val toolsResponse = mcpClient.listTools()
        val tools = toolsResponse.tools

        println("═══════════════════════════════════════════════════════════")
        println("Available tools:")
        println("═══════════════════════════════════════════════════════════")
        println()

        if (tools.isEmpty()) {
            println("⚠ No tools found")
        } else {
            tools.forEachIndexed { index, tool ->
                println("${index + 1}. ${tool.name}")
                println("   Description: ${tool.description}")
                println("   Input schema:")
                // Format JSON Schema for readability
                val json = Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }
                val schemaString = json.encodeToString(
                    ToolSchema.serializer(),
                    tool.inputSchema
                )
                // Pretty-print output with indentation
                schemaString.lines().forEach { line ->
                    println("   $line")
                }
                println()
            }
        }

        println("═══════════════════════════════════════════════════════════")
        println("Total tools: ${tools.size}")
        println("═══════════════════════════════════════════════════════════")

    } catch (e: Exception) {
        println("✗ Error connecting to MCP server:")
        println("  ${e.message}")
        e.printStackTrace()
        println()
        println("Make sure MCP server is running at $MCP_SERVER_URL")
        println("Start the server with: .\\gradlew.bat :mcp-server:run")
    } finally {
        // Close connections
        try {
            mcpClient.close()
        } catch (e: Exception) {
            // Ignore errors during close
        }
        try {
            httpClient.close()
        } catch (e: Exception) {
            // Ignore errors during close
        }
    }
}
