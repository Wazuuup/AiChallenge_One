package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.*
import ru.sber.cb.aichallenge_one.models.ChatResponse
import ru.sber.cb.aichallenge_one.models.ResponseStatus
import ru.sber.cb.aichallenge_one.models.TokenUsage

class ChatService(
    val gigaChatApiClient: GigaChatApiClient,
    val openAIApiClient: OpenAIApiClient?,
    val summarizationService: SummarizationService
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    private val gigaChatHistory = mutableListOf<GigaChatMessage>()
    private val openRouterHistory = mutableListOf<OpenAIMessage>()

    // Message count tracking for summarization
    private var gigaChatMessageCount = 0
    private var openRouterMessageCount = 0

    // Token usage tracking for OpenRouter
    private var cumulativePromptTokens = 0
    private var cumulativeCompletionTokens = 0
    private var cumulativeTotalTokens = 0
    private var currentModel: String? = null

    companion object {
        private const val SUMMARIZATION_THRESHOLD = 10 // Summarize every 10 messages
    }

    suspend fun processUserMessage(
        userText: String,
        systemPrompt: String = "",
        temperature: Double = 0.7,
        provider: String = "gigachat",
        model: String? = null,
        maxTokens: Int? = null
    ): ChatResponse {
        return try {
            logger.info("Processing user message: $userText with temperature: $temperature, provider: $provider, model: $model")

            if (provider == "openrouter") {
                // Use OpenRouter (OpenAI-compatible API)
                if (openAIApiClient == null) {
                    logger.error("OpenRouter client not configured")
                    return ChatResponse(
                        "OpenRouter не настроен. Пожалуйста, настройте OpenAI API в конфигурации сервера.",
                        ResponseStatus.ERROR
                    )
                }

                // Check if model has changed - if so, reset token usage and history
                if (model != null && currentModel != model) {
                    logger.info("Model changed from $currentModel to $model. Resetting token usage and history.")
                    resetTokenUsage()
                    openRouterHistory.clear()
                    openRouterMessageCount = 0
                    currentModel = model
                }

                // Add user message to history
                openRouterHistory.add(OpenAIMessage(role = MessageRole.USER.value, content = userText))
                openRouterMessageCount++

                // Check if summarization is needed (every 10 messages sent to LLM)
                if (openRouterMessageCount >= SUMMARIZATION_THRESHOLD) {
                    logger.info("OpenRouter message threshold reached ($openRouterMessageCount messages). Triggering summarization...")
                    try {
                        val summary = summarizationService.summarizeOpenRouterHistory(openRouterHistory)
                        openRouterHistory.clear()
                        openRouterHistory.add(summary)
                        openRouterMessageCount = 0
                        logger.info("Successfully summarized OpenRouter history. New history size: ${openRouterHistory.size}")
                    } catch (e: Exception) {
                        logger.error("OpenRouter summarization failed, continuing with full history", e)
                        // Continue with full history if summarization fails
                    }
                }

                // Send to OpenRouter
                val result = openAIApiClient.sendMessage(openRouterHistory, systemPrompt, temperature, maxTokens)
                logger.info("Received response from OpenRouter")

                // Add assistant response to history
                openRouterHistory.add(OpenAIMessage(role = MessageRole.ASSISTANT.value, content = result.text))

                // Track last response token usage
                val lastResponseTokenUsage = result.usage?.let { usage ->
                    TokenUsage(
                        promptTokens = usage.promptTokens,
                        completionTokens = usage.completionTokens,
                        totalTokens = usage.totalTokens
                    )
                }

                // Accumulate token usage
                result.usage?.let { usage ->
                    cumulativePromptTokens += usage.promptTokens
                    cumulativeCompletionTokens += usage.completionTokens
                    cumulativeTotalTokens += usage.totalTokens
                    logger.debug("Cumulative token usage - Prompt: $cumulativePromptTokens, Completion: $cumulativeCompletionTokens, Total: $cumulativeTotalTokens")
                }

                val cumulativeTokenUsage = TokenUsage(
                    promptTokens = cumulativePromptTokens,
                    completionTokens = cumulativeCompletionTokens,
                    totalTokens = cumulativeTotalTokens
                )

                logger.debug("Response time: ${result.responseTimeMs}ms")

                ChatResponse(
                    text = result.text,
                    status = ResponseStatus.SUCCESS,
                    tokenUsage = cumulativeTokenUsage,
                    lastResponseTokenUsage = lastResponseTokenUsage,
                    responseTimeMs = result.responseTimeMs
                )
            } else {
                // Use GigaChat (default)
                gigaChatHistory.add(GigaChatMessage(role = MessageRole.USER.value, content = userText))
                gigaChatMessageCount++

                // Check if summarization is needed (every 10 messages sent to LLM)
                if (gigaChatMessageCount >= SUMMARIZATION_THRESHOLD) {
                    logger.info("Message threshold reached ($gigaChatMessageCount messages). Triggering summarization...")
                    try {
                        val summary = summarizationService.summarizeHistory(gigaChatHistory)
                        gigaChatHistory.clear()
                        gigaChatHistory.add(summary)
                        gigaChatMessageCount = 0
                        logger.info("Successfully summarized history. New history size: ${gigaChatHistory.size}")
                    } catch (e: Exception) {
                        logger.error("Summarization failed, continuing with full history", e)
                        // Continue with full history if summarization fails
                    }
                }

                val response = gigaChatApiClient.sendMessage(gigaChatHistory, systemPrompt, temperature)
                logger.info("Received response from GigaChat")

                gigaChatHistory.add(GigaChatMessage(role = MessageRole.ASSISTANT.value, content = response))

                ChatResponse(response, ResponseStatus.SUCCESS)
            }
        } catch (e: Exception) {
            logger.error("Error processing user message", e)
            ChatResponse("Возникла ошибка при получении ответа: ${e.message}", ResponseStatus.ERROR)
        }
    }

    fun clearHistory() {
        logger.info("Clearing message history")
        gigaChatHistory.clear()
        openRouterHistory.clear()
        gigaChatMessageCount = 0
        openRouterMessageCount = 0
        resetTokenUsage()
        currentModel = null
    }

    private fun resetTokenUsage() {
        logger.info("Resetting token usage")
        cumulativePromptTokens = 0
        cumulativeCompletionTokens = 0
        cumulativeTotalTokens = 0
    }
}
