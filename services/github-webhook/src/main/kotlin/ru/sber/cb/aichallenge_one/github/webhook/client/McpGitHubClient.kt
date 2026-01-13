package ru.sber.cb.aichallenge_one.github.webhook.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

class McpGitHubClient(
    private val httpClient: HttpClient,
    private val mcpBaseUrl: String
) {
    private val logger = LoggerFactory.getLogger(McpGitHubClient::class.java)

    suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int): String? {
        return try {
            logger.info("Fetching PR #$prNumber diff via MCP (owner: $owner, repo: $repo)")

            val request = McpToolCallRequest(
                method = "tools/call",
                params = McpToolCallParams(
                    name = "get_pr_diff",
                    arguments = mapOf(
                        "owner" to owner,
                        "repo" to repo,
                        "pr_number" to prNumber
                    )
                )
            )

            val response: HttpResponse = httpClient.post("$mcpBaseUrl/mcp") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val mcpResponse = response.body<McpToolCallResponse>()
                val diff = mcpResponse.content.firstOrNull()?.text
                logger.info("Successfully fetched diff (${diff?.length ?: 0} chars)")
                diff
            } else {
                logger.error("MCP API error: ${response.status} - ${response.bodyAsText()}")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch PR diff via MCP", e)
            null
        }
    }

    suspend fun postComment(owner: String, repo: String, prNumber: Int, body: String): Boolean {
        return try {
            logger.info("Posting comment to PR #$prNumber via MCP")

            val request = McpToolCallRequest(
                method = "tools/call",
                params = McpToolCallParams(
                    name = "post_pr_comment",
                    arguments = mapOf(
                        "owner" to owner,
                        "repo" to repo,
                        "pr_number" to prNumber,
                        "body" to body
                    )
                )
            )

            val response: HttpResponse = httpClient.post("$mcpBaseUrl/mcp") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                logger.info("Successfully posted comment to PR #$prNumber")
                true
            } else {
                logger.error("MCP API error: ${response.status} - ${response.bodyAsText()}")
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to post comment via MCP", e)
            false
        }
    }

    @Serializable
    private data class McpToolCallRequest(
        val method: String,
        val params: McpToolCallParams
    )

    @Serializable
    private data class McpToolCallParams(
        val name: String,
        @Contextual val arguments: Map<String, Any>
    )

    @Serializable
    private data class McpToolCallResponse(
        val content: List<McpContent>
    )

    @Serializable
    private data class McpContent(
        val type: String,
        val text: String
    )
}
