package ru.sber.cb.aichallenge_one.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.service.McpClientService
import ru.sber.cb.aichallenge_one.service.ToolAdapterService

/**
 * Routing for MCP tool management endpoints.
 *
 * Note: For chat with tool calling, use POST /api/send-message with:
 *   - provider: "openrouter"
 *   - enableTools: true
 */
fun Application.configureToolCallingRouting() {
    val logger = LoggerFactory.getLogger("ToolCallingRouting")
    val mcpClientService by inject<McpClientService>()
    val toolAdapterService by inject<ToolAdapterService>()

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
