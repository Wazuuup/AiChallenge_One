package ru.sber.cb.aichallenge_one.github.webhook.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.github.webhook.client.McpGitHubClient
import ru.sber.cb.aichallenge_one.github.webhook.client.RagApiClient
import ru.sber.cb.aichallenge_one.github.webhook.client.ReviewApiClient

class ReviewOrchestrationService(
    private val diffAnalysisService: DiffAnalysisService,
    private val reviewApiClient: ReviewApiClient,
    private val ragApiClient: RagApiClient,
    private val mcpGitHubClient: McpGitHubClient,
    private val reviewModel: String,
    private val reviewTemperature: Double,
    private val reviewMaxTokens: Int
) {
    private val logger = LoggerFactory.getLogger(ReviewOrchestrationService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Process PR review asynchronously (non-blocking)
     */
    fun processReviewAsync(owner: String, repo: String, prNumber: Int) {
        scope.launch {
            try {
                logger.info("Starting async review for PR #$prNumber in $owner/$repo")
                processReview(owner, repo, prNumber)
            } catch (e: Exception) {
                logger.error("Unexpected error in review workflow for PR #$prNumber", e)
                // Try to post error comment
                try {
                    mcpGitHubClient.postComment(
                        owner = owner,
                        repo = repo,
                        prNumber = prNumber,
                        body = "⚠️ Review failed due to unexpected error: ${e.message}"
                    )
                } catch (commentError: Exception) {
                    logger.error("Failed to post error comment", commentError)
                }
            }
        }
    }

    /**
     * Main review workflow (synchronous)
     */
    private suspend fun processReview(owner: String, repo: String, prNumber: Int) {
        logger.info("Processing review for PR #$prNumber in $owner/$repo")

        // Step 1: Fetch PR diff via MCP
        val diff = mcpGitHubClient.getPullRequestDiff(owner, repo, prNumber)
        if (diff == null) {
            logger.error("Failed to fetch diff for PR #$prNumber")
            postComment(owner, repo, prNumber, "❌ Failed to fetch PR diff. Please try again later.")
            return
        }

        // Step 2: Validate diff size
        when (val validationResult = diffAnalysisService.validateDiffSize(diff)) {
            is DiffAnalysisService.ValidationResult.TooLarge -> {
                logger.warn("PR #$prNumber is too large: ${validationResult.actualLines} lines")
                postComment(
                    owner, repo, prNumber,
                    "❌ PR too large for review (${validationResult.actualLines} lines, max: ${validationResult.maxLines} lines).\n\n" +
                            "Please split this PR into smaller chunks for effective review."
                )
                return
            }

            is DiffAnalysisService.ValidationResult.Valid -> {
                logger.info("Diff size validation passed")
            }
        }

        // Step 3: Filter binary files
        val (textDiff, binaryFiles) = diffAnalysisService.filterBinaryFiles(diff)

        // Step 4: Check if only deletions
        if (textDiff.isBlank() || diffAnalysisService.isOnlyDeletions(textDiff)) {
            logger.info("PR #$prNumber contains only deletions or binary files")
            val message = buildString {
                append("✅ No code review needed.\n\n")
                if (diffAnalysisService.isOnlyDeletions(textDiff)) {
                    append("This PR contains only deletions.\n")
                }
                if (binaryFiles.isNotEmpty()) {
                    append("Binary files excluded: ${binaryFiles.joinToString(", ")}")
                }
            }
            postComment(owner, repo, prNumber, message)
            return
        }

        // Step 5: Extract keywords for RAG search
        val keywords = diffAnalysisService.extractKeywords(textDiff)
        logger.info("Extracted ${keywords.size} keywords")

        // Step 6: RAG context search (degraded mode if unavailable)
        val ragContext = try {
            ragApiClient.search(keywords, limit = 5)
        } catch (e: Exception) {
            logger.warn("RAG search failed, continuing without context: ${e.message}")
            emptyList()
        }

        if (ragContext.isEmpty()) {
            logger.info("No RAG context available (degraded mode)")
        } else {
            logger.info("Retrieved ${ragContext.size} RAG context chunks")
        }

        // Step 7: Request LLM review
        logger.info("Requesting LLM review (model: $reviewModel)")
        val reviewResult = reviewApiClient.requestReview(
            diff = textDiff,
            ragContext = ragContext,
            model = reviewModel,
            temperature = reviewTemperature,
            maxTokens = reviewMaxTokens
        )

        // Step 8: Post review comment
        when (reviewResult) {
            is ReviewApiClient.ReviewResult.Success -> {
                val comment = formatReviewComment(
                    reviewText = reviewResult.reviewText,
                    binaryFiles = binaryFiles,
                    truncated = reviewResult.truncated,
                    tokensUsed = reviewResult.tokensUsed
                )
                val posted = postComment(owner, repo, prNumber, comment)
                if (posted) {
                    logger.info("Review completed successfully for PR #$prNumber (${reviewResult.tokensUsed} tokens)")
                }
            }

            is ReviewApiClient.ReviewResult.Error -> {
                logger.error("Review failed for PR #$prNumber: ${reviewResult.message}")
                postComment(
                    owner, repo, prNumber,
                    "⚠️ Review failed: ${reviewResult.message}\n\nPlease try again later."
                )
            }
        }
    }

    /**
     * Post comment to PR (with error handling)
     */
    private suspend fun postComment(owner: String, repo: String, prNumber: Int, body: String): Boolean {
        return try {
            mcpGitHubClient.postComment(owner, repo, prNumber, body)
        } catch (e: Exception) {
            logger.error("Failed to post comment to PR #$prNumber", e)
            false
        }
    }

    /**
     * Format review comment with prefix and metadata
     */
    private fun formatReviewComment(
        reviewText: String,
        binaryFiles: List<String>,
        truncated: Boolean,
        tokensUsed: Int
    ): String {
        return buildString {
            appendLine("🤖 AI Code Review ($reviewModel)")
            appendLine()
            appendLine(reviewText.trim())
            appendLine()
            appendLine("---")

            // Add metadata
            if (truncated) {
                appendLine("*Note: This review was partially truncated due to token limit*")
            }

            if (binaryFiles.isNotEmpty()) {
                appendLine("*Binary files excluded: ${binaryFiles.joinToString(", ")}*")
            }

            appendLine("*Tokens used: $tokensUsed*")
        }
    }
}
