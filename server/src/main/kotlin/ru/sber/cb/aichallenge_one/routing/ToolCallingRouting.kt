package ru.sber.cb.aichallenge_one.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.OpenAIMessageWithTools
import ru.sber.cb.aichallenge_one.models.ChatResponse
import ru.sber.cb.aichallenge_one.models.ResponseStatus
import ru.sber.cb.aichallenge_one.service.McpClientService
import ru.sber.cb.aichallenge_one.service.ToolAdapterService
import ru.sber.cb.aichallenge_one.service.ToolExecutionService

@Serializable
data class ToolCallingRequest(
    val text: String,
    val systemPrompt: String = "",
    val temperature: Double = 0.7
)

/**
 * Routing for tool calling endpoints
 */
fun Application.configureToolCallingRouting() {
    val logger = LoggerFactory.getLogger("ToolCallingRouting")
    val mcpClientService by inject<McpClientService>()
    val toolAdapterService by inject<ToolAdapterService>()
    val toolExecutionService: ToolExecutionService? by inject()

    routing {
        route("/api/tools") {

            // Initialize MCP connection
            post("/connect") {
                try {
                    logger.info("Connecting to MCP server...")
                    mcpClientService.connect()
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Connected to MCP server"))
                } catch (e: Exception) {
                    logger.error("Failed to connect to MCP server", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to connect: ${e.message}")
                    )
                }
            }

            // List available tools
            get("/list") {
                try {
                    logger.info("Fetching tools list...")
                    val mcpTools = mcpClientService.listTools()
                    val openRouterTools = toolAdapterService.convertMcpToolsToOpenRouter(mcpTools)

                    call.respond(
                        HttpStatusCode.OK, mapOf(
                            "count" to openRouterTools.size,
                            "tools" to openRouterTools
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to list tools", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to list tools: ${e.message}")
                    )
                }
            }

            // Send message with tool calling
            post("/chat") {
                if (toolExecutionService == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ChatResponse(
                            text = "Tool calling is not available (OpenRouter not configured)",
                            status = ResponseStatus.ERROR
                        )
                    )
                    return@post
                }

                try {
                    val request = call.receive<ToolCallingRequest>()
                    logger.info("Processing message with tool calling: ${request.text}")

                    // Get available tools
                    val mcpTools = mcpClientService.listTools()
                    val openRouterTools = toolAdapterService.convertMcpToolsToOpenRouter(mcpTools)

                    logger.info("Found ${openRouterTools.size} tools")

                    // Create message history
                    val messageHistory = mutableListOf<OpenAIMessageWithTools>()

                    // Handle tool calling workflow
                    val responseText = toolExecutionService!!.handleToolCallingWorkflow(
                        messageHistory = messageHistory,
                        tools = openRouterTools,
                        userMessage = request.text,
                        systemPrompt = request.systemPrompt,
                        temperature = request.temperature
                    )

                    call.respond(
                        HttpStatusCode.OK,
                        ChatResponse(
                            text = responseText,
                            status = ResponseStatus.SUCCESS
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Error processing tool calling request", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ChatResponse(
                            text = "Error: ${e.message}",
                            status = ResponseStatus.ERROR
                        )
                    )
                }
            }

            // Disconnect from MCP
            post("/disconnect") {
                try {
                    logger.info("Disconnecting from MCP server...")
                    mcpClientService.disconnect()
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Disconnected from MCP server"))
                } catch (e: Exception) {
                    logger.error("Failed to disconnect from MCP server", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to disconnect: ${e.message}")
                    )
                }
            }
        }
    }
}
