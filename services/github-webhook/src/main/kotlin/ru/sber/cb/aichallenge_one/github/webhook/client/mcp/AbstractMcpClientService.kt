package ru.sber.cb.aichallenge_one.github.webhook.client.mcp

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Service for connecting to local MCP server and managing tool operations
 */
abstract class AbstractMcpClientService(
    private val mcpServerUrl: String,
    private val mcpClientName: String,
    private val mcpClientVersion: String
) : IMcpClientService {
    private val logger = LoggerFactory.getLogger(AbstractMcpClientService::class.java)
    private val mutex = Mutex()

    private var httpClient: HttpClient? = null
    private var mcpClient: Client? = null
    private var isConnected = false

    /**
     * Connect to MCP server
     */
    override suspend fun connect() = mutex.withLock {
        if (isConnected) {
            logger.debug("Already connected to MCP server")
            return
        }

        try {
            logger.info("Connecting to MCP server at $mcpServerUrl...")

            // Create HTTP client for SSE transport
            httpClient = HttpClient(CIO) {
                install(SSE)
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        prettyPrint = false
                        isLenient = true
                    })
                }
                engine {
                    endpoint {
                        connectTimeout = 10000  // 10 seconds
                        requestTimeout = 30000  // 30 seconds
                    }
                }
            }

            // Create MCP client
            mcpClient = Client(
                clientInfo = Implementation(
                    name = mcpClientName,
                    version = mcpClientVersion
                ),
                options = ClientOptions()
            )

            // Create SSE transport and connect
            val transport = SseClientTransport(
                urlString = mcpServerUrl,
                client = httpClient!!
            )

            withTimeout(45000) {  // 45 second timeout for the entire connect operation
                mcpClient!!.connect(transport)
            }
            isConnected = true

            logger.info("✓ Successfully connected to MCP server")
        } catch (e: Exception) {
            logger.error("Failed to connect to MCP server: ${e.message}", e)
            cleanup()
            throw e
        }
    }

    /**
     * Get list of available tools from MCP server
     */
    override suspend fun listTools(): List<Tool> = mutex.withLock {
        ensureConnected()

        try {
            logger.debug("Fetching tools list from MCP server...")
            val toolsResponse = mcpClient!!.listTools()
            logger.info("Retrieved ${toolsResponse.tools.size} tools from MCP server")
            return toolsResponse.tools
        } catch (e: Exception) {
            logger.error("Failed to list tools: ${e.message}", e)
            throw e
        }
    }

    /**
     * Call a tool on the MCP server
     */
    override suspend fun callTool(name: String, arguments: Map<String, Any?>): String {
        ensureConnected()

        try {
            logger.debug("Calling tool '$name' with arguments: $arguments")
            val result = mcpClient!!.callTool(name, arguments)

            if (result.isError == true) {
                val errorMsg = result.content.joinToString("\n")
                logger.error("Tool '$name' returned error: $errorMsg")
                throw Exception("Tool error: $errorMsg")
            }

            val resultContent = result.content.joinToString("\n")
            logger.debug("Tool '$name' returned: $resultContent")
            return resultContent
        } catch (e: Exception) {
            logger.error("Failed to call tool '$name': ${e.message}", e)
            throw e
        }
    }

    /**
     * Disconnect from MCP server
     */
    override suspend fun disconnect() = mutex.withLock {
        if (!isConnected) {
            return
        }

        logger.info("Disconnecting from MCP server...")
        cleanup()
        logger.info("Disconnected from MCP server")
    }

    private fun ensureConnected() {
        if (!isConnected || mcpClient == null) {
            throw IllegalStateException("Not connected to MCP server. Call connect() first.")
        }
    }

    private suspend fun cleanup() {
        try {
            mcpClient?.close()
        } catch (e: Exception) {
            logger.warn("Error closing MCP client: ${e.message}")
        }

        try {
            httpClient?.close()
        } catch (e: Exception) {
            logger.warn("Error closing HTTP client: ${e.message}")
        }

        mcpClient = null
        httpClient = null
        isConnected = false
    }
}