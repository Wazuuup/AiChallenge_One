package ru.sber.cb.aichallenge_one.github.webhook.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.github.webhook.model.ReviewMessage
import ru.sber.cb.aichallenge_one.github.webhook.model.ReviewRequest
import ru.sber.cb.aichallenge_one.github.webhook.model.ReviewResponse

class ReviewApiClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String,
    private val defaultModel: String,
    private val defaultTemperature: Double,
    private val defaultMaxTokens: Int,
    private val timeoutMillis: Long
) {
    private val logger = LoggerFactory.getLogger(ReviewApiClient::class.java)

    suspend fun requestReview(
        diff: String,
        ragContext: List<String>,
        model: String = defaultModel,
        temperature: Double = defaultTemperature,
        maxTokens: Int = defaultMaxTokens
    ): ReviewResult {
        return try {
            val prompt = formatReviewPrompt(diff, ragContext)

            val request = ReviewRequest(
                model = model,
                messages = listOf(
                    ReviewMessage(role = "system", content = prompt),
                    ReviewMessage(role = "user", content = "Please review this code.")
                ),
                temperature = temperature,
                maxTokens = maxTokens
            )

            logger.info("Requesting review from OpenRouter (model: $model, temp: $temperature, max_tokens: $maxTokens)")

            val response: HttpResponse = httpClient.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
                timeout {
                    requestTimeoutMillis = timeoutMillis
                }
            }

            if (response.status.isSuccess()) {
                val reviewResponse = response.body<ReviewResponse>()
                val reviewText = reviewResponse.choices.firstOrNull()?.message?.content
                    ?: throw Exception("Empty response from LLM")

                val tokensUsed = reviewResponse.usage?.totalTokens ?: 0
                logger.info("Review completed successfully (${reviewText.length} chars, $tokensUsed tokens)")

                ReviewResult.Success(
                    reviewText = reviewText,
                    tokensUsed = tokensUsed,
                    truncated = reviewText.length >= maxTokens * 3 // Rough estimate
                )
            } else {
                val errorBody = response.bodyAsText()
                logger.error("OpenRouter API error: ${response.status} - $errorBody")
                ReviewResult.Error("OpenRouter API error: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Failed to request review", e)
            ReviewResult.Error("Review request failed: ${e.message}")
        }
    }

    fun formatReviewPrompt(diff: String, ragContext: List<String>): String {
        return buildString {
            appendLine("# Task")
            appendLine("You are an AI code reviewer analyzing a GitHub Pull Request. Provide constructive feedback focusing on:")
            appendLine("- Code style and conventions")
            appendLine("- Architectural patterns (SOLID, separation of concerns)")
            appendLine("- Potential bugs and edge cases (null checks, exception handling, race conditions)")
            appendLine()
            appendLine("# Code Diff")
            appendLine("```")
            appendLine(diff)
            appendLine("```")
            appendLine()

            if (ragContext.isNotEmpty()) {
                appendLine("# Codebase Context (from RAG)")
                appendLine("Here are relevant code snippets from the codebase for context:")
                appendLine()
                ragContext.forEachIndexed { index, chunk ->
                    appendLine("## Context ${index + 1}")
                    appendLine("```")
                    appendLine(chunk)
                    appendLine("```")
                    appendLine()
                }
            }

            appendLine("# Guidelines")
            appendLine("- Be concise and actionable")
            appendLine("- Reference specific lines when possible (use line numbers from diff)")
            appendLine("- Suggest concrete improvements")
            appendLine("- If no issues found, state \"No issues found\"")
        }
    }

    sealed class ReviewResult {
        data class Success(
            val reviewText: String,
            val tokensUsed: Int,
            val truncated: Boolean
        ) : ReviewResult()

        data class Error(val message: String) : ReviewResult()
    }
}
