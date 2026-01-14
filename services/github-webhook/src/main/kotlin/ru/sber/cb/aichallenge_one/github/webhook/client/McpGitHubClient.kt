package ru.sber.cb.aichallenge_one.github.webhook.client

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.github.webhook.client.mcp.GithubMcpClientService

class McpGitHubClient(
    private val githubMcpClientService: GithubMcpClientService
) {
    private val logger = LoggerFactory.getLogger(McpGitHubClient::class.java)

    suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int): String? {
        return try {
            githubMcpClientService.connect()
            logger.info("Fetching PR #$prNumber diff via MCP (owner: $owner, repo: $repo)")

            val result = githubMcpClientService.callTool(
                name = "get_pr_diff",
                arguments = mapOf(
                    "owner" to owner,
                    "repo" to repo,
                    "pr_number" to prNumber
                )
            )

            logger.info("Successfully fetched diff (${result.length} chars)")
            result
        } catch (e: Exception) {
            logger.error("Failed to fetch PR diff via MCP", e)
            null
        }
    }

    suspend fun postComment(owner: String, repo: String, prNumber: Int, body: String): Boolean {
        return try {
            githubMcpClientService.connect()
            logger.info("Posting comment to PR #$prNumber via MCP")

            githubMcpClientService.callTool(
                name = "post_pr_comment",
                arguments = mapOf(
                    "owner" to owner,
                    "repo" to repo,
                    "pr_number" to prNumber,
                    "body" to body
                )
            )

            logger.info("Successfully posted comment to PR #$prNumber")
            true
        } catch (e: Exception) {
            logger.error("Failed to post comment via MCP", e)
            false
        }
    }
}
