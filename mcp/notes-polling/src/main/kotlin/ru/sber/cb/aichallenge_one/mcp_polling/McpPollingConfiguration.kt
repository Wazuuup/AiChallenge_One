package ru.sber.cb.aichallenge_one.mcp_polling

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("McpPollingServer")
private const val CONTAINER_NAME = "notes-scheduler"
private const val IMAGE_NAME = "notes-scheduler:latest"

/**
 * Configures the MCP Polling server with Docker control tools.
 */
fun Application.configureMcpPollingServer() {
    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("MCP Notes Polling Server is running on port $MCP_POLLING_PORT (HTTP) and $MCP_POLLING_SSL_PORT (HTTPS)")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "notes-polling-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                // Tool 1: Trigger notes summary polling
                addTool(
                    name = "trigger_notes_summary_polling",
                    description = "Builds and starts the notes-scheduler service as a Docker container. The scheduler will periodically trigger /api/summary/pushToUI endpoint on the server based on cron expression.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("cron_expression") {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Optional cron expression (default: '*/2 * * * *' - every 2 minutes)"
                                    )
                                }
                                putJsonObject("server_url") {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Optional server URL (default: 'http://host.docker.internal:8080')"
                                    )
                                }
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val cronExpression = arguments.arguments
                        ?.get("cron_expression")?.jsonPrimitive?.contentOrNull
                        ?: "*/2 * * * *"
                    val serverUrl = arguments.arguments
                        ?.get("server_url")?.jsonPrimitive?.contentOrNull
                        ?: "http://host.docker.internal:8080"

                    runBlocking {
                        try {
                            logger.info("Starting notes-scheduler container...")
                            val result = buildString {
                                appendLine("Building and starting notes-scheduler...")
                                appendLine()

                                // Check if container is already running
                                val checkRunning = executeCommand(
                                    "docker", "ps", "-q", "-f", "name=$CONTAINER_NAME"
                                )

                                if (checkRunning.isNotBlank()) {
                                    appendLine("⚠ Container '$CONTAINER_NAME' is already running")
                                    appendLine("Stopping existing container...")
                                    executeCommand("docker", "stop", CONTAINER_NAME)
                                    executeCommand("docker", "rm", CONTAINER_NAME)
                                    appendLine("✓ Stopped existing container")
                                    appendLine()
                                }

                                // Build the application locally first
                                val projectRoot = findProjectRoot()
                                appendLine("Using project root: ${projectRoot.absolutePath}")
                                appendLine()

                                appendLine("Building notes-scheduler application...")
                                val gradleBuildOutput = executeCommandWithDir(
                                    projectRoot,
                                    "cmd.exe",
                                    "/c",
                                    "gradlew.bat",
                                    ":services:notes-scheduler:installDist",
                                    "--no-configuration-cache"
                                )

                                if (gradleBuildOutput.contains("BUILD FAILED", ignoreCase = true)) {
                                    appendLine("❌ Gradle build failed:")
                                    appendLine(gradleBuildOutput.takeLast(500)) // Show last 500 chars
                                    return@buildString
                                }

                                appendLine("✓ Application built successfully")
                                appendLine()

                                // Build Docker image with pre-built application
                                appendLine("Building Docker image...")
                                val buildOutput = executeCommandWithDir(
                                    projectRoot,
                                    "docker",
                                    "build",
                                    "-t", IMAGE_NAME,
                                    "-f", "services/notes-scheduler/Dockerfile",
                                    "."
                                )

                                if (buildOutput.contains("ERROR", ignoreCase = true)) {
                                    appendLine("❌ Docker build failed:")
                                    appendLine(buildOutput.takeLast(500)) // Show last 500 chars
                                    return@buildString
                                }

                                appendLine("✓ Docker image built successfully")
                                appendLine()

                                // Run container
                                appendLine("Starting container with:")
                                appendLine("  - Cron expression: $cronExpression")
                                appendLine("  - Server URL: $serverUrl")
                                appendLine()

                                val runOutput = executeCommand(
                                    "docker", "run",
                                    "-d",
                                    "--name", CONTAINER_NAME,
                                    "--add-host", "host.docker.internal:host-gateway",
                                    "-e", "CRON_EXPRESSION=$cronExpression",
                                    "-e", "SERVER_URL=$serverUrl",
                                    "-e", "SCHEDULER_ENABLED=true",
                                    IMAGE_NAME
                                )

                                if (runOutput.isNotBlank() && !runOutput.contains("Error", ignoreCase = true)) {
                                    appendLine("✓ Container started successfully")
                                    appendLine("Container ID: ${runOutput.trim()}")
                                    appendLine()
                                    appendLine("Use 'docker logs $CONTAINER_NAME' to view logs")
                                    appendLine("Use stop_notes_summary_polling tool to stop the scheduler")
                                } else {
                                    appendLine("❌ Failed to start container:")
                                    appendLine(runOutput)
                                }
                            }

                            CallToolResult(
                                content = listOf(TextContent(result))
                            )
                        } catch (e: Exception) {
                            logger.error("Error starting scheduler: ${e.message}", e)
                            CallToolResult(
                                content = listOf(TextContent("Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 2: Stop notes summary polling
                addTool(
                    name = "stop_notes_summary_polling",
                    description = "Stops and removes the notes-scheduler Docker container",
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
                            logger.info("Stopping notes-scheduler container...")
                            val result = buildString {
                                appendLine("Stopping notes-scheduler...")
                                appendLine()

                                // Check if container exists
                                val checkExists = executeCommand(
                                    "docker", "ps", "-a", "-q", "-f", "name=$CONTAINER_NAME"
                                )

                                if (checkExists.isBlank()) {
                                    appendLine("⚠ Container '$CONTAINER_NAME' is not running")
                                    return@buildString
                                }

                                // Stop container
                                executeCommand("docker", "stop", CONTAINER_NAME)
                                appendLine("✓ Container stopped")

                                // Remove container
                                executeCommand("docker", "rm", CONTAINER_NAME)
                                appendLine("✓ Container removed")
                                appendLine()
                                appendLine("Notes scheduler has been stopped successfully")
                            }

                            CallToolResult(
                                content = listOf(TextContent(result))
                            )
                        } catch (e: Exception) {
                            logger.error("Error stopping scheduler: ${e.message}", e)
                            CallToolResult(
                                content = listOf(TextContent("Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Finds the project root directory by looking for build.gradle.kts
 */
private fun findProjectRoot(): File {
    var currentDir = File(".").absoluteFile
    while (currentDir != null) {
        // Check if this directory contains build.gradle.kts (root project marker)
        if (File(currentDir, "build.gradle.kts").exists() &&
            File(currentDir, "settings.gradle.kts").exists()
        ) {
            logger.info("Found project root: ${currentDir.absolutePath}")
            return currentDir
        }
        currentDir = currentDir.parentFile
    }
    // Fallback to current directory
    logger.warn("Could not find project root, using current directory")
    return File(".").absoluteFile
}

/**
 * Executes a shell command and returns the output
 */
private fun executeCommand(vararg command: String): String {
    return executeCommandWithDir(File("."), *command)
}

/**
 * Executes a shell command with a specific working directory and returns the output
 */
private fun executeCommandWithDir(
    workingDir: File,
    vararg command: String
): String {
    return try {
        val process = ProcessBuilder(*command)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output
    } catch (e: Exception) {
        logger.error("Command execution failed: ${command.joinToString(" ")}", e)
        "Error executing command: ${e.message}"
    }
}
