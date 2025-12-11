package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.MessageRole
import ru.sber.cb.aichallenge_one.domain.AiClient
import ru.sber.cb.aichallenge_one.domain.AiProvider
import ru.sber.cb.aichallenge_one.domain.ConversationMessage
import ru.sber.cb.aichallenge_one.domain.SummarizationConfig

/**
 * Universal service for summarizing conversation history across different AI providers.
 * Uses the Strategy pattern with AiClient interface to support multiple providers.
 *
 * @param config Configuration for summarization behavior
 */
class SummarizationService(
    private val config: SummarizationConfig = SummarizationConfig()
) {
    private val logger = LoggerFactory.getLogger(SummarizationService::class.java)

    companion object {
        // Language-specific system prompts
        private val SYSTEM_PROMPTS = mapOf(
            AiProvider.GIGACHAT to """
                Ты - ассистент для суммаризации диалогов. Твоя задача - создать краткое резюме предоставленной истории разговора.

                Требования к резюме:
                1. Сохрани все ключевые факты, имена, числа и важную информацию
                2. Объедини похожие темы в один абзац
                3. Используй краткие, но информативные формулировки
                4. Сохрани хронологический порядок обсуждения тем
                5. Не добавляй информацию, которой не было в оригинале
                6. Если был задан вопрос и дан ответ, сохрани суть обоих

                Формат ответа: напиши краткое резюме разговора в виде связного текста.
            """.trimIndent(),

            AiProvider.OPENROUTER to """
                You are an assistant specialized in conversation summarization. Your task is to create a concise summary of the provided conversation history.

                Requirements for the summary:
                1. Preserve all key facts, names, numbers, and important information
                2. Combine similar topics into a single paragraph
                3. Use brief but informative formulations
                4. Maintain chronological order of discussed topics
                5. Do not add information that was not in the original conversation
                6. If a question was asked and answered, preserve the essence of both

                Output format: Write a concise summary of the conversation as coherent text.
            """.trimIndent()
        )

        // Language-specific summary request templates
        private val REQUEST_TEMPLATES = mapOf(
            AiProvider.GIGACHAT to "Пожалуйста, создай краткое резюме следующего разговора:\n\n%s",
            AiProvider.OPENROUTER to "Please create a concise summary of the following conversation:\n\n%s"
        )

        // Language-specific summary prefixes
        private val SUMMARY_PREFIXES = mapOf(
            AiProvider.GIGACHAT to "[Резюме предыдущего разговора]: ",
            AiProvider.OPENROUTER to "[Summary of previous conversation]: "
        )
    }

    /**
     * Summarizes conversation history using the provided AI client.
     * This is a universal method that works with any AI provider implementing AiClient interface.
     *
     * @param T The message type (must implement ConversationMessage)
     * @param messageHistory The list of messages to summarize
     * @param aiClient The AI client to use for summarization
     * @return A single message containing the summary
     * @throws SummarizationException if summarization fails
     */
    suspend fun <T : ConversationMessage> summarize(
        messageHistory: List<T>,
        aiClient: AiClient<T>
    ): T {
        val provider = aiClient.getProvider()
        logger.info("Starting ${provider.displayName} summarization of ${messageHistory.size} messages")

        try {
            // Build conversation text for summarization
            val conversationText = buildConversationText(messageHistory, aiClient)

            // Get language-specific templates
            val systemPrompt = SYSTEM_PROMPTS[provider]
                ?: throw SummarizationException("No system prompt configured for provider: $provider")

            val requestTemplate = REQUEST_TEMPLATES[provider]
                ?: throw SummarizationException("No request template configured for provider: $provider")

            val summaryPrefix = SUMMARY_PREFIXES[provider] ?: config.summaryPrefix

            // Create temporary history with summarization request
            val request = String.format(requestTemplate, conversationText)
            val tempHistory = listOf(aiClient.createMessage(MessageRole.USER.value, request))

            // Get summary from AI
            val summary = aiClient.sendMessage(
                messageHistory = tempHistory,
                systemPrompt = systemPrompt,
                temperature = config.temperature
            )

            logger.info("Successfully generated ${provider.displayName} summary: ${summary.take(100)}...")

            // Return summary as a USER message to maintain conversation flow
            return aiClient.createMessage(
                role = MessageRole.USER.value,
                content = "$summaryPrefix$summary"
            )
        } catch (e: Exception) {
            logger.error("Error during ${aiClient.getProvider().displayName} summarization", e)
            throw SummarizationException(
                "Failed to summarize message history for ${aiClient.getProvider().displayName}: ${e.message}",
                e
            )
        }
    }

    /**
     * Builds a readable text representation of the conversation history.
     * Uses provider-specific role names for better localization.
     */
    private fun <T : ConversationMessage> buildConversationText(
        messageHistory: List<T>,
        aiClient: AiClient<T>
    ): String {
        return messageHistory.joinToString("\n\n") { message ->
            val roleName = aiClient.getLocalizedRoleName(message.role)
            "$roleName: ${message.content}"
        }
    }

    /**
     * Checks if summarization should be triggered based on message count.
     *
     * @param messageCount Current number of messages
     * @return true if summarization threshold is reached
     */
    fun shouldSummarize(messageCount: Int): Boolean {
        return messageCount >= config.threshold
    }
}

/**
 * Exception thrown when summarization fails.
 */
class SummarizationException(message: String, cause: Throwable? = null) : Exception(message, cause)
