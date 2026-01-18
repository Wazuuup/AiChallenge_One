package ru.sber.cb.aichallenge_one.mcp_vdsina

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.mcp_vdsina.service.VdsinaApiService

const val MCP_VDSINA_HTTP_PORT = 8096
const val MCP_VDSINA_HTTPS_PORT = 8452

/**
 * Configures the MCP (Model Context Protocol) server for VDSina operations.
 */
fun Application.configureMcpVdsinaServer() {
    val logger = LoggerFactory.getLogger("VdsinaMcpConfiguration")
    val vdsinaService by inject<VdsinaApiService>()

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("MCP VDSina Server is running on port $MCP_VDSINA_HTTP_PORT")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "vdsina-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                // Tool 1: list_datacenters
                addTool(
                    name = "list_datacenters",
                    description = "Get the list of available VDSina datacenters.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {}
                            put("additionalProperties", false)
                        }
                    )
                ) { _: CallToolRequest ->
                    runBlocking {
                        try {
                            val result = vdsinaService.listDatacenters()
                            logger.info("list_datacenters executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in list_datacenters", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 2: list_server_plans
                addTool(
                    name = "list_server_plans",
                    description = "Get the list of server plans for a specific group. Returns available VDS configurations with CPU, RAM, disk, and pricing.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("group_id") {
                                    put("type", "integer")
                                    put("description", "ID of the server plan group")
                                }
                            }
                            putJsonArray("required") {
                                add("group_id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val groupId = arguments.arguments
                                ?.get("group_id")
                                ?.jsonPrimitive
                                ?.intOrNull
                                ?: throw IllegalArgumentException("group_id is required")

                            val result = vdsinaService.listServerPlans(groupId)
                            logger.info("list_server_plans executed successfully for group $groupId")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in list_server_plans", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 3: list_server_groups
                addTool(
                    name = "list_server_groups",
                    description = "Get the list of server plan groups. Each group contains different server configurations.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {}
                            put("additionalProperties", false)
                        }
                    )
                ) { _: CallToolRequest ->
                    runBlocking {
                        try {
                            val result = vdsinaService.listServerGroups()
                            logger.info("list_server_groups executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in list_server_groups", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 4: list_templates
                addTool(
                    name = "list_templates",
                    description = "Get the list of available OS templates for VDS servers.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {}
                            put("additionalProperties", false)
                        }
                    )
                ) { _: CallToolRequest ->
                    runBlocking {
                        try {
                            val result = vdsinaService.listTemplates()
                            logger.info("list_templates executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in list_templates", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 5: list_ssh_keys
                addTool(
                    name = "list_ssh_keys",
                    description = "Get the list of SSH keys in your VDSina account.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {}
                            put("additionalProperties", false)
                        }
                    )
                ) { _: CallToolRequest ->
                    runBlocking {
                        try {
                            val result = vdsinaService.listSshKeys()
                            logger.info("list_ssh_keys executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in list_ssh_keys", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 6: create_ssh_key
                addTool(
                    name = "create_ssh_key",
                    description = "Create a new SSH key in your VDSina account.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("name") {
                                    put("type", "string")
                                    put("description", "Name for the SSH key")
                                }
                                putJsonObject("data") {
                                    put("type", "string")
                                    put("description", "Public SSH key data (ssh-rsa AAAA...)")
                                }
                            }
                            putJsonArray("required") {
                                add("name")
                                add("data")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val name = arguments.arguments
                                ?.get("name")
                                ?.jsonPrimitive
                                ?.contentOrNull
                                ?: throw IllegalArgumentException("name is required")

                            val data = arguments.arguments
                                ?.get("data")
                                ?.jsonPrimitive
                                ?.contentOrNull
                                ?: throw IllegalArgumentException("data is required")

                            val result = vdsinaService.createSshKey(name, data)
                            logger.info("create_ssh_key executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in create_ssh_key", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 7: list_servers
                addTool(
                    name = "list_servers",
                    description = "Get the list of all VDS servers in your account.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {}
                            put("additionalProperties", false)
                        }
                    )
                ) { _: CallToolRequest ->
                    runBlocking {
                        try {
                            val result = vdsinaService.listServers()
                            logger.info("list_servers executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in list_servers", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 8: get_server_status
                addTool(
                    name = "get_server_status",
                    description = "Get detailed information about a specific VDS server.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("server_id") {
                                    put("type", "integer")
                                    put("description", "ID of the server")
                                }
                            }
                            putJsonArray("required") {
                                add("server_id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val serverId = arguments.arguments
                                ?.get("server_id")
                                ?.jsonPrimitive
                                ?.intOrNull
                                ?: throw IllegalArgumentException("server_id is required")

                            val result = vdsinaService.getServerStatus(serverId)
                            logger.info("get_server_status executed successfully for server $serverId")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in get_server_status", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 9: create_server
                addTool(
                    name = "create_server",
                    description = "Create a new VDS server with minimum configuration (cheapest plan with at least 1GB RAM, Ubuntu 24.04).",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("name") {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Name for the server (optional, default: AiChallenge-{timestamp})"
                                    )
                                }
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val name = arguments.arguments
                                ?.get("name")
                                ?.jsonPrimitive
                                ?.contentOrNull

                            val result = vdsinaService.createServer(name)
                            logger.info("create_server executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in create_server", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 10: delete_server
                addTool(
                    name = "delete_server",
                    description = "Delete a VDS server permanently.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("server_id") {
                                    put("type", "integer")
                                    put("description", "ID of the server to delete")
                                }
                            }
                            putJsonArray("required") {
                                add("server_id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val serverId = arguments.arguments
                                ?.get("server_id")
                                ?.jsonPrimitive
                                ?.intOrNull
                                ?: throw IllegalArgumentException("server_id is required")

                            val result = vdsinaService.deleteServer(serverId)
                            logger.info("delete_server executed successfully for server $serverId")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in delete_server", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 11: deploy_app
                addTool(
                    name = "deploy_app",
                    description = "Deploy the application to the most recently created active VDS server. Runs PowerShell deployment script that installs Docker, copies files, and starts services.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {}
                            put("additionalProperties", false)
                        }
                    )
                ) { _: CallToolRequest ->
                    runBlocking {
                        try {
                            val result = vdsinaService.deployApp()
                            logger.info("deploy_app executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in deploy_app", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }


                // Tool 13: change_server_password
                addTool(
                    name = "change_server_password",
                    description = "Change the root password for a VDS server. The new password is taken from the server configuration (sudoPass in application-dev.conf).",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("server_id") {
                                    put("type", "integer")
                                    put("description", "ID of the server")
                                }
                            }
                            putJsonArray("required") {
                                add("server_id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val serverId = arguments.arguments
                                ?.get("server_id")
                                ?.jsonPrimitive
                                ?.intOrNull
                                ?: throw IllegalArgumentException("server_id is required")

                            val result = vdsinaService.changeServerPassword(serverId)
                            logger.info("change_server_password executed successfully for server $serverId")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in change_server_password", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 14: wait_for_server_active
                addTool(
                    name = "check_server_status_and_wait",
                    description = """Check if VDS server is active and ready.
                        |If server is active - returns success with IP address.
                        |If server is not active - waits 30 seconds and returns message TO RETRY THIS TOOL RIGHT AFTER THIS ATTEMPT.
                        |If server is deleted - returns abort message.
                        |Use this tool after creating a server to wait for it to become ready.""".trimMargin(),
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("server_id") {
                                    put("type", "integer")
                                    put("description", "ID of the server to check")
                                }
                            }
                            putJsonArray("required") {
                                add("server_id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val serverId = arguments.arguments
                                ?.get("server_id")
                                ?.jsonPrimitive
                                ?.intOrNull
                                ?: throw IllegalArgumentException("server_id is required")

                            val result = vdsinaService.waitForServerActive(serverId)
                            logger.info("check_server_status_and_wait: server $serverId status=${result.serverStatus}, ready=${result.ready}")

                            CallToolResult(
                                content = listOf(TextContent(text = result.message)),
                                isError = result.shouldAbort
                            )
                        } catch (e: Exception) {
                            logger.error("Error in check_server_status_and_wait", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }
            }
        }
    }
}
