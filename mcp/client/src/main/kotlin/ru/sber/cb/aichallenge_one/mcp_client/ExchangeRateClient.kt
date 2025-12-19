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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * MCP Client that calls the get_exchange_rate tool with USD currency code
 */
fun main() = runBlocking {
    println("╔═══════════════════════════════════════════════════════════╗")
    println("║        Currency Exchange Rate Client (USD → RUB)         ║")
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
            name = "exchange-rate-client",
            version = "1.0.0"
        ),
        options = ClientOptions()
    )

    try {
        println("Connecting to MCP server at $MCP_SERVER_URL...")

        // Create SSE transport to connect to server
        val transport = SseClientTransport(
            urlString = MCP_SERVER_URL,
            client = httpClient
        )

        // Connect to server
        mcpClient.connect(transport)

        println("✓ Successfully connected to MCP server")
        println()

        // Call get_exchange_rate tool with USD currency code
        println("Calling get_exchange_rate tool with currency_code: USD")
        println("═══════════════════════════════════════════════════════════")

        val result = mcpClient.callTool(
            name = "get_exchange_rate",
            arguments = mapOf("currency_code" to "USD")
        )

        println()
        if (result.isError == true) {
            println("✗ Error calling tool:")
            result.content.forEach { content ->
                println("  ${content}")
            }
        } else {
            println("✓ Tool call successful!")
            println()
            println("Result:")
            println("───────────────────────────────────────────────────────────")
            result.content.forEach { content ->
                println(content)
            }
            println("───────────────────────────────────────────────────────────")
        }

    } catch (e: Exception) {
        println("✗ Error calling MCP tool:")
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
