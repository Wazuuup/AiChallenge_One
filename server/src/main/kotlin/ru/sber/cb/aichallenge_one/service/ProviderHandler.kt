package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.MessageRole
import ru.sber.cb.aichallenge_one.domain.AiClient
import ru.sber.cb.aichallenge_one.domain.AiProvider
import ru.sber.cb.aichallenge_one.domain.ConversationHistory
import ru.sber.cb.aichallenge_one.domain.ConversationMessage

/**
 * Handler for provider-specific conversation logic.
 * Encapsulates history management, message processing, and summarization.
 *
 * @param T The message type for this provider
 * @param provider The AI provider type
 * @param aiClient The AI client implementation
 * @param summarizationService Service for summarizing conversation history
 */
class ProviderHandler<T : ConversationMessage>(
    val provider: AiProvider,
    private val aiClient: AiClient<T>,
    private val summarizationService: SummarizationService
) {
    private val logger = LoggerFactory.getLogger(ProviderHandler::class.java)
    private val history = ConversationHistory<T>()

    /**
     * Process a user message and get AI response.
     *
     * @param userText The user's message text
     * @param systemPrompt Optional system prompt
     * @param temperature Temperature parameter for response randomness
     * @return The AI response text
     */
    suspend fun processMessage(
        userText: String,
        systemPrompt: String,
        temperature: Double
    ): String {
        logger.info("Processing message for ${provider.displayName}: $userText")

        // Add user message to history
        val userMessage = aiClient.createMessage(MessageRole.USER.value, userText)
        history.add(userMessage)

        // Check if summarization is needed
        if (summarizationService.shouldSummarize(history.messageCount)) {
            performSummarization()
        }

        // Send to AI provider
        val response = aiClient.sendMessage(
            messageHistory = history.toList(),
            systemPrompt = systemPrompt,
            temperature = temperature
        )

        logger.info("Received response from ${provider.displayName}")

        // Add assistant response to history
        val assistantMessage = aiClient.createMessage(MessageRole.ASSISTANT.value, response)
        history.add(assistantMessage)

        return response
    }

    /**
     * Perform conversation summarization.
     */
    private suspend fun performSummarization() {
        logger.info("${provider.displayName} message threshold reached (${history.messageCount} messages). Triggering summarization...")

        try {
            val summary = summarizationService.summarize(history.toList(), aiClient)
            history.clear()
            history.add(summary)
            logger.info("Successfully summarized ${provider.displayName} history. New history size: ${history.size()}")
        } catch (e: Exception) {
            logger.error("${provider.displayName} summarization failed, continuing with full history", e)
            // Continue with full history if summarization fails
        }
    }

    /**
     * Clear conversation history.
     */
    fun clearHistory() {
        logger.info("Clearing ${provider.displayName} message history")
        history.clear()
    }

    /**
     * Get the current history size.
     */
    fun getHistorySize(): Int = history.size()

    /**
     * Get the current message count.
     */
    fun getMessageCount(): Int = history.messageCount
}
