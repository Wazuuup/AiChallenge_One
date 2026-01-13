package ru.sber.cb.aichallenge_one.mcp_github_reviewer

import com.typesafe.config.ConfigFactory
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
import ru.sber.cb.aichallenge_one.mcp_github_reviewer.service.GitHubService

fun Application.configureGitHubMcpServer() {
    val logger = LoggerFactory.getLogger("GitHubMcpConfiguration")

    val config = ConfigFactory.systemEnvironment()
        .withFallback(ConfigFactory.systemProperties())
        .withFallback(ConfigFactory.load())
        .resolve()

    val githubToken = if (config.hasPath("github.token")) {
        config.getString("github.token")
    } else {
        throw IllegalStateException("github.token not found in configuration. Please set GITHUB_TOKEN environment variable or configure it in application.conf")
    }


    val githubService = GitHubService(githubToken)

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("MCP GitHub Reviewer Server is running on port $GITHUB_REVIEWER_HTTP_PORT (HTTP) and $GITHUB_REVIEWER_HTTPS_PORT (HTTPS)")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "github-reviewer-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                // ========== Tool 1: get_pr_diff ==========
                addTool(
                    name = "get_pr_diff",
                    description = "Get the diff for a pull request from GitHub",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("owner") {
                                    put("type", "string")
                                    put("description", "Repository owner (e.g., 'octocat')")
                                }
                                putJsonObject("repo") {
                                    put("type", "string")
                                    put("description", "Repository name (e.g., 'Hello-World')")
                                }
                                putJsonObject("pr_number") {
                                    put("type", "integer")
                                    put("description", "Pull request number")
                                }
                            }
                            putJsonArray("required") {
                                add("owner")
                                add("repo")
                                add("pr_number")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val owner = arguments.arguments
                                ?.get("owner")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("owner is required")

                            val repo = arguments.arguments
                                ?.get("repo")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("repo is required")

                            val prNumber = arguments.arguments
                                ?.get("pr_number")
                                ?.jsonPrimitive
                                ?.intOrNull
                                ?: throw IllegalArgumentException("pr_number must be an integer")

                            logger.info("get_pr_diff: owner=$owner, repo=$repo, pr_number=$prNumber")

                            val diff = githubService.getPullRequestDiff(owner, repo, prNumber)

                            CallToolResult(
                                content = listOf(TextContent(text = diff)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in get_pr_diff", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // ========== Tool 2: post_pr_comment ==========
                addTool(
                    name = "post_pr_comment",
                    description = "Post a general comment to a pull request",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("owner") {
                                    put("type", "string")
                                    put("description", "Repository owner")
                                }
                                putJsonObject("repo") {
                                    put("type", "string")
                                    put("description", "Repository name")
                                }
                                putJsonObject("pr_number") {
                                    put("type", "integer")
                                    put("description", "Pull request number")
                                }
                                putJsonObject("body") {
                                    put("type", "string")
                                    put("description", "Comment text (markdown supported)")
                                }
                            }
                            putJsonArray("required") {
                                add("owner")
                                add("repo")
                                add("pr_number")
                                add("body")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val owner = arguments.arguments
                                ?.get("owner")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("owner is required")

                            val repo = arguments.arguments
                                ?.get("repo")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("repo is required")

                            val prNumber = arguments.arguments
                                ?.get("pr_number")
                                ?.jsonPrimitive
                                ?.intOrNull
                                ?: throw IllegalArgumentException("pr_number must be an integer")

                            val body = arguments.arguments
                                ?.get("body")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("body is required")

                            logger.info("post_pr_comment: owner=$owner, repo=$repo, pr_number=$prNumber")

                            val success = githubService.postComment(owner, repo, prNumber, body)

                            if (success) {
                                CallToolResult(
                                    content = listOf(TextContent(text = "Comment posted successfully to PR #$prNumber")),
                                    isError = false
                                )
                            } else {
                                CallToolResult(
                                    content = listOf(TextContent(text = "Failed to post comment")),
                                    isError = true
                                )
                            }
                        } catch (e: Exception) {
                            logger.error("Error in post_pr_comment", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // ========== Tool 3: get_file_content ==========
                addTool(
                    name = "get_file_content",
                    description = "Get the content of a file from the repository (max 1000 lines)",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("owner") {
                                    put("type", "string")
                                    put("description", "Repository owner")
                                }
                                putJsonObject("repo") {
                                    put("type", "string")
                                    put("description", "Repository name")
                                }
                                putJsonObject("path") {
                                    put("type", "string")
                                    put("description", "File path in repository")
                                }
                                putJsonObject("ref") {
                                    put("type", "string")
                                    put("description", "Branch/tag/commit SHA (optional, defaults to default branch)")
                                }
                            }
                            putJsonArray("required") {
                                add("owner")
                                add("repo")
                                add("path")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val owner = arguments.arguments
                                ?.get("owner")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("owner is required")

                            val repo = arguments.arguments
                                ?.get("repo")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("repo is required")

                            val path = arguments.arguments
                                ?.get("path")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("path is required")

                            val ref = arguments.arguments
                                ?.get("ref")
                                ?.jsonPrimitive
                                ?.content

                            logger.info("get_file_content: owner=$owner, repo=$repo, path=$path, ref=$ref")

                            val content = githubService.getFileContent(owner, repo, path, ref)

                            CallToolResult(
                                content = listOf(TextContent(text = content)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in get_file_content", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                logger.info("MCP GitHub Reviewer Server configured with 3 tools: get_pr_diff, post_pr_comment, get_file_content")
            }
        }
    }
}
