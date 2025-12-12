package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.MessageRole
import ru.sber.cb.aichallenge_one.client.OpenAIApiClient
import ru.sber.cb.aichallenge_one.client.OpenAIMessage
import ru.sber.cb.aichallenge_one.client.OpenAIUsage
import ru.sber.cb.aichallenge_one.database.MessageRepository
import ru.sber.cb.aichallenge_one.domain.AiProvider
import ru.sber.cb.aichallenge_one.domain.ConversationHistory

/**
 * Specialized provider handler for OpenRouter with token tracking support.
 * Extends base functionality with OpenAI-specific features like token usage monitoring.
 */
class OpenRouterProviderHandler(
    private val openAIApiClient: OpenAIApiClient,
    private val summarizationService: SummarizationService,
    private val messageRepository: MessageRepository,
    private val maxTokens: Int? = null
) {
    private val logger = LoggerFactory.getLogger(OpenRouterProviderHandler::class.java)
    private val history = ConversationHistory<OpenAIMessage>()
    private val providerName = "openrouter"

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

        // Save user message to database
        try {
            messageRepository.saveMessage(providerName, MessageRole.USER.value, userText)
        } catch (e: Exception) {
            logger.error("Failed to save user message to database", e)
        }

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

        // Save assistant response to database
        try {
            messageRepository.saveMessage(providerName, MessageRole.ASSISTANT.value, result.text)
        } catch (e: Exception) {
            logger.error("Failed to save assistant message to database", e)
        }

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

            // Update database with summary
            try {
                messageRepository.replaceWithSummary(providerName, summary.content, summary.role)
            } catch (e: Exception) {
                logger.error("Failed to update database with summary", e)
            }
        } catch (e: Exception) {
            logger.error("OpenRouter summarization failed, continuing with full history", e)
        }
    }

    /**
     * Clear conversation history from both memory and database.
     */
    suspend fun clearHistory() {
        logger.info("Clearing OpenRouter message history")
        history.clear()

        // Clear database
        try {
            messageRepository.clearHistory(providerName)
        } catch (e: Exception) {
            logger.error("Failed to clear history from database", e)
        }
    }

    /**
     * Load conversation history from database into memory.
     */
    suspend fun loadHistory() {
        try {
            val messages = messageRepository.getHistory(providerName)
            history.clear()

            messages.forEach { entity ->
                val message = OpenAIMessage(role = entity.role, content = entity.content)
                history.add(message)
            }

            history.messageCount = messages.size
            logger.info("Loaded ${messages.size} messages from database for OpenRouter")
        } catch (e: Exception) {
            logger.error("Failed to load history from database for OpenRouter", e)
        }
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
