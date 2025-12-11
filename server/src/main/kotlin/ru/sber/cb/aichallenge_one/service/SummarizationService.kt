package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.*

/**
 * Service responsible for summarizing chat message history.
 * When the message history grows too large, this service uses AI models
 * (GigaChat or OpenRouter) to create a concise summary that preserves important context.
 */
class SummarizationService(
    private val gigaChatApiClient: GigaChatApiClient,
    private val openAIApiClient: OpenAIApiClient?
) {
    private val logger = LoggerFactory.getLogger(SummarizationService::class.java)

    companion object {
        private const val SUMMARIZATION_SYSTEM_PROMPT = """
            Ты - ассистент для суммаризации диалогов. Твоя задача - создать краткое резюме предоставленной истории разговора.

            Требования к резюме:
            1. Сохрани все ключевые факты, имена, числа и важную информацию
            2. Объедини похожие темы в один абзац
            3. Используй краткие, но информативные формулировки
            4. Сохрани хронологический порядок обсуждения тем
            5. Не добавляй информацию, которой не было в оригинале
            6. Если был задан вопрос и дан ответ, сохрани суть обоих

            Формат ответа: напиши краткое резюме разговора в виде связного текста.
        """

        private const val OPENROUTER_SUMMARIZATION_SYSTEM_PROMPT = """
            You are an assistant specialized in conversation summarization. Your task is to create a concise summary of the provided conversation history.

            Requirements for the summary:
            1. Preserve all key facts, names, numbers, and important information
            2. Combine similar topics into a single paragraph
            3. Use brief but informative formulations
            4. Maintain chronological order of discussed topics
            5. Do not add information that was not in the original conversation
            6. If a question was asked and answered, preserve the essence of both

            Output format: Write a concise summary of the conversation as coherent text.
        """
    }

    /**
     * Summarizes the provided message history into a condensed version.
     *
     * @param messageHistory The list of messages to summarize
     * @return A single GigaChatMessage containing the summary with USER role
     */
    suspend fun summarizeHistory(messageHistory: List<GigaChatMessage>): GigaChatMessage {
        logger.info("Starting summarization of ${messageHistory.size} messages")

        try {
            // Convert message history to a readable format for summarization
            val conversationText = buildConversationText(messageHistory)

            // Create a temporary history with just the conversation text
            val tempHistory = listOf(
                GigaChatMessage(
                    role = MessageRole.USER.value,
                    content = "Пожалуйста, создай краткое резюме следующего разговора:\n\n$conversationText"
                )
            )

            // Get summary from GigaChat
            val summary = gigaChatApiClient.sendMessage(
                messageHistory = tempHistory,
                customSystemPrompt = SUMMARIZATION_SYSTEM_PROMPT,
                temperature = 0.3 // Lower temperature for more consistent summarization
            )

            logger.info("Successfully generated summary: ${summary.take(100)}...")

            // Return the summary as a USER message to maintain conversation flow
            return GigaChatMessage(
                role = MessageRole.USER.value,
                content = "[Резюме предыдущего разговора]: $summary"
            )
        } catch (e: Exception) {
            logger.error("Error during summarization", e)
            throw SummarizationException("Failed to summarize message history: ${e.message}", e)
        }
    }

    /**
     * Builds a readable text representation of the conversation history.
     */
    private fun buildConversationText(messageHistory: List<GigaChatMessage>): String {
        return messageHistory.joinToString("\n\n") { message ->
            val roleName = when (message.role) {
                MessageRole.USER.value -> "Пользователь"
                MessageRole.ASSISTANT.value -> "Ассистент"
                MessageRole.SYSTEM.value -> "Система"
                else -> message.role
            }
            "$roleName: ${message.content}"
        }
    }

    /**
     * Summarizes the provided OpenRouter message history into a condensed version.
     *
     * @param messageHistory The list of OpenAI messages to summarize
     * @return A single OpenAIMessage containing the summary with USER role
     * @throws SummarizationException if OpenAI client is not configured or summarization fails
     */
    suspend fun summarizeOpenRouterHistory(messageHistory: List<OpenAIMessage>): OpenAIMessage {
        if (openAIApiClient == null) {
            logger.error("OpenAI API client is not configured, cannot summarize OpenRouter history")
            throw SummarizationException("OpenRouter client is not configured")
        }

        logger.info("Starting OpenRouter summarization of ${messageHistory.size} messages")

        try {
            // Convert message history to a readable format for summarization
            val conversationText = buildOpenRouterConversationText(messageHistory)

            // Create a temporary history with just the conversation text
            val tempHistory = listOf(
                OpenAIMessage(
                    role = MessageRole.USER.value,
                    content = "Please create a concise summary of the following conversation:\n\n$conversationText"
                )
            )

            // Get summary from OpenRouter/OpenAI
            val result = openAIApiClient.sendMessage(
                messageHistory = tempHistory,
                customSystemPrompt = OPENROUTER_SUMMARIZATION_SYSTEM_PROMPT,
                temperature = 0.3 // Lower temperature for more consistent summarization
            )

            logger.info("Successfully generated OpenRouter summary: ${result.text.take(100)}...")

            // Return the summary as a USER message to maintain conversation flow
            return OpenAIMessage(
                role = MessageRole.USER.value,
                content = "[Summary of previous conversation]: ${result.text}"
            )
        } catch (e: Exception) {
            logger.error("Error during OpenRouter summarization", e)
            throw SummarizationException("Failed to summarize OpenRouter message history: ${e.message}", e)
        }
    }

    /**
     * Builds a readable text representation of the OpenRouter conversation history.
     */
    private fun buildOpenRouterConversationText(messageHistory: List<OpenAIMessage>): String {
        return messageHistory.joinToString("\n\n") { message ->
            val roleName = when (message.role) {
                MessageRole.USER.value -> "User"
                MessageRole.ASSISTANT.value -> "Assistant"
                MessageRole.SYSTEM.value -> "System"
                else -> message.role
            }
            "$roleName: ${message.content}"
        }
    }
}

/**
 * Exception thrown when summarization fails
 */
class SummarizationException(message: String, cause: Throwable? = null) : Exception(message, cause)
