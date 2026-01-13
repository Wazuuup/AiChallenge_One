package ru.sber.cb.aichallenge_one.mcp_git

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
import ru.sber.cb.aichallenge_one.mcp_git.service.GitService

const val MCP_GIT_HTTP_PORT = 8093
const val MCP_GIT_HTTPS_PORT = 8449

/**
 * Configures the MCP (Model Context Protocol) server for Git operations.
 */
fun Application.configureMcpGitServer(gitService: GitService) {
    val logger = LoggerFactory.getLogger("GitMcpConfiguration")

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("MCP Git Server is running on port $MCP_GIT_HTTP_PORT")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "git-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                // Tool 1: git_status
                addTool(
                    name = "git_status",
                    description = "Get the current status of the Git repository. Shows staged changes, unstaged changes, and untracked files.",
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
                            val result = gitService.getStatus()
                            logger.info("git_status executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in git_status", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 2: git_log
                addTool(
                    name = "git_log",
                    description = "Get commit history log. Shows recent commits with author, date, and message.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("max_count") {
                                    put("type", "integer")
                                    put("description", "Maximum number of commits to retrieve (default: 10)")
                                    put("default", 10)
                                }
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val maxCount = arguments.arguments
                                ?.get("max_count")
                                ?.jsonPrimitive
                                ?.intOrNull
                                ?: 10

                            val result = gitService.getLog(maxCount)
                            logger.info("git_log executed successfully with maxCount=$maxCount")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in git_log", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 3: git_diff
                addTool(
                    name = "git_diff",
                    description = "Get diff of unstaged changes in the working directory compared to HEAD.",
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
                            val result = gitService.getDiff()
                            logger.info("git_diff executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in git_diff", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 4: git_diff_staged
                addTool(
                    name = "git_diff_staged",
                    description = "Get diff of staged changes (changes in the index) compared to HEAD.",
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
                            val result = gitService.getDiffStaged()
                            logger.info("git_diff_staged executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in git_diff_staged", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 5: git_branch_list
                addTool(
                    name = "git_branch_list",
                    description = "List all branches in the repository. Shows current branch with an asterisk.",
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
                            val result = gitService.listBranches()
                            logger.info("git_branch_list executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in git_branch_list", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 6: git_branch_create
                addTool(
                    name = "git_branch_create",
                    description = "Create a new branch. Optionally checkout the new branch immediately.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("branch_name") {
                                    put("type", "string")
                                    put("description", "Name of the new branch to create")
                                }
                                putJsonObject("checkout") {
                                    put("type", "boolean")
                                    put(
                                        "description",
                                        "Whether to checkout the new branch immediately (default: false)"
                                    )
                                    put("default", false)
                                }
                            }
                            putJsonArray("required") {
                                add("branch_name")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val branchName = arguments.arguments
                                ?.get("branch_name")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("branch_name is required")

                            val checkout = arguments.arguments
                                ?.get("checkout")
                                ?.jsonPrimitive
                                ?.booleanOrNull
                                ?: false

                            val result = gitService.createBranch(branchName, checkout)
                            logger.info("git_branch_create executed successfully: $branchName (checkout=$checkout)")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in git_branch_create", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 7: git_checkout
                addTool(
                    name = "git_checkout",
                    description = "Checkout a branch or commit. Switch the working directory to the specified branch or commit hash.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("target") {
                                    put("type", "string")
                                    put("description", "Branch name or commit hash to checkout")
                                }
                            }
                            putJsonArray("required") {
                                add("target")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val target = arguments.arguments
                                ?.get("target")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("target is required")

                            val result = gitService.checkout(target)
                            logger.info("git_checkout executed successfully: $target")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in git_checkout", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 8: git_add
                addTool(
                    name = "git_add",
                    description = "Add files to the staging area. Use '.' for all files, or specify file patterns like '*.kt' or specific paths.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("file_pattern") {
                                    put("type", "string")
                                    put("description", "File pattern to add (e.g., '.', '*.kt', 'src/main/')")
                                }
                            }
                            putJsonArray("required") {
                                add("file_pattern")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val filePattern = arguments.arguments
                                ?.get("file_pattern")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("file_pattern is required")

                            val result = gitService.addFiles(filePattern)
                            logger.info("git_add executed successfully: $filePattern")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in git_add", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 9: git_commit
                addTool(
                    name = "git_commit",
                    description = "Create a commit with staged changes. Requires a commit message. Optionally specify author name and email.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("message") {
                                    put("type", "string")
                                    put("description", "Commit message")
                                }
                                putJsonObject("author") {
                                    put("type", "string")
                                    put("description", "Author name (optional, uses Git config if not provided)")
                                }
                                putJsonObject("email") {
                                    put("type", "string")
                                    put("description", "Author email (optional, uses Git config if not provided)")
                                }
                            }
                            putJsonArray("required") {
                                add("message")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val message = arguments.arguments
                                ?.get("message")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("message is required")

                            val author = arguments.arguments
                                ?.get("author")
                                ?.jsonPrimitive
                                ?.content

                            val email = arguments.arguments
                                ?.get("email")
                                ?.jsonPrimitive
                                ?.content

                            val result = gitService.commit(message, author, email)
                            logger.info("git_commit executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in git_commit", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 10: git_show_file
                addTool(
                    name = "git_show_file",
                    description = "Show the content of a file at a specific commit. Defaults to HEAD if no commit is specified.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("file_path") {
                                    put("type", "string")
                                    put("description", "Path to the file to show")
                                }
                                putJsonObject("commit_hash") {
                                    put("type", "string")
                                    put("description", "Commit hash (default: HEAD)")
                                    put("default", "HEAD")
                                }
                            }
                            putJsonArray("required") {
                                add("file_path")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    runBlocking {
                        try {
                            val filePath = arguments.arguments
                                ?.get("file_path")
                                ?.jsonPrimitive
                                ?.content
                                ?: throw IllegalArgumentException("file_path is required")

                            val commitHash = arguments.arguments
                                ?.get("commit_hash")
                                ?.jsonPrimitive
                                ?.content
                                ?: "HEAD"

                            val result = gitService.showFile(filePath, commitHash)
                            logger.info("git_show_file executed successfully: $filePath @ $commitHash")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in git_show_file", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 11: git_current_branch
                addTool(
                    name = "git_current_branch",
                    description = "Get the name of the current branch.",
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
                            val result = gitService.getCurrentBranch()
                            logger.info("git_current_branch executed successfully: $result")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in git_current_branch", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 12: git_remote_url
                addTool(
                    name = "git_remote_url",
                    description = "Get the URL of configured remote repositories.",
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
                            val result = gitService.getRemoteUrl()
                            logger.info("git_remote_url executed successfully")
                            CallToolResult(
                                content = listOf(TextContent(text = result)),
                                isError = false
                            )
                        } catch (e: Exception) {
                            logger.error("Error in git_remote_url", e)
                            CallToolResult(
                                content = listOf(TextContent(text = "Error: ${e.message}")),
                                isError = true
                            )
                        }
                    }
                }

                logger.info("MCP Git Server configured with 12 tools")
            }
        }
    }
}
