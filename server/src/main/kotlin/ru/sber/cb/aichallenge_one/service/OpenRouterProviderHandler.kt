package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.MessageRole
import ru.sber.cb.aichallenge_one.client.OpenAIApiClient
import ru.sber.cb.aichallenge_one.client.OpenAIMessage
import ru.sber.cb.aichallenge_one.client.OpenAIUsage
import ru.sber.cb.aichallenge_one.domain.AiProvider
import ru.sber.cb.aichallenge_one.domain.ConversationHistory

/**
 * Specialized provider handler for OpenRouter with token tracking support.
 * Extends base functionality with OpenAI-specific features like token usage monitoring.
 */
class OpenRouterProviderHandler(
    private val openAIApiClient: OpenAIApiClient,
    private val summarizationService: SummarizationService,
    private val maxTokens: Int? = null
) {
    private val logger = LoggerFactory.getLogger(OpenRouterProviderHandler::class.java)
    private val history = ConversationHistory<OpenAIMessage>()

    /**
     * Process a user message with full metadata including token usage.
     *
     * @return Result with response text and token usage information
     */
    suspend fun processMessageWithMetadata(
        userText: String,
        systemPrompt: String,
        temperature: Double
    ): OpenRouterMessageResult {
        logger.info("Processing OpenRouter message: $userText")

        // Add user message to history
        val userMessage = OpenAIMessage(role = MessageRole.USER.value, content = userText)
        history.add(userMessage)

        // Check if summarization is needed
        if (summarizationService.shouldSummarize(history.messageCount)) {
            performSummarization()
        }

        // Send to OpenRouter with metadata capture
        val result = openAIApiClient.sendMessage(
            messageHistory = history.toList(),
            customSystemPrompt = systemPrompt,
            temperature = temperature,
            maxTokensOverride = maxTokens
        )

        logger.info("Received response from OpenRouter")

        // Add assistant response to history
        val assistantMessage = OpenAIMessage(role = MessageRole.ASSISTANT.value, content = result.text)
        history.add(assistantMessage)

        return OpenRouterMessageResult(
            text = result.text,
            usage = result.usage,
            responseTimeMs = result.responseTimeMs
        )
    }

    /**
     * Perform conversation summarization for OpenRouter.
     */
    private suspend fun performSummarization() {
        logger.info("OpenRouter message threshold reached (${history.messageCount} messages). Triggering summarization...")

        try {
            // Create temporary adapter for summarization
            val adapter = ru.sber.cb.aichallenge_one.client.OpenRouterClientAdapter(openAIApiClient, maxTokens)
            val summary = summarizationService.summarize(history.toList(), adapter)
            history.clear()
            history.add(summary)
            logger.info("Successfully summarized OpenRouter history. New history size: ${history.size()}")
        } catch (e: Exception) {
            logger.error("OpenRouter summarization failed, continuing with full history", e)
        }
    }

    /**
     * Clear conversation history.
     */
    fun clearHistory() {
        logger.info("Clearing OpenRouter message history")
        history.clear()
    }

    fun getProvider(): AiProvider = AiProvider.OPENROUTER
}

/**
 * Result of OpenRouter message processing with metadata.
 */
data class OpenRouterMessageResult(
    val text: String,
    val usage: OpenAIUsage?,
    val responseTimeMs: Long
)
