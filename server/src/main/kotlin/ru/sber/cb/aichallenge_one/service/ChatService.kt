package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.GigaChatApiClient
import ru.sber.cb.aichallenge_one.client.GigaChatClientAdapter
import ru.sber.cb.aichallenge_one.client.GigaChatMessage
import ru.sber.cb.aichallenge_one.client.OpenAIApiClient
import ru.sber.cb.aichallenge_one.database.MessageRepository
import ru.sber.cb.aichallenge_one.domain.AiProvider
import ru.sber.cb.aichallenge_one.models.ChatResponse
import ru.sber.cb.aichallenge_one.models.ResponseStatus
import ru.sber.cb.aichallenge_one.models.TokenUsage
import ru.sber.cb.aichallenge_one.service.mcp.IMcpClientService

/**
 * Refactored ChatService using Strategy pattern with ProviderHandlers.
 * Cleanly separates provider-specific logic while maintaining shared functionality.
 *
 * Key improvements:
 * - Eliminated code duplication through ProviderHandler abstraction
 * - Clean separation of concerns (routing, history, summarization)
 * - Specialized handlers for provider-specific features (e.g., token tracking for OpenRouter)
 * - Easy to extend with new providers
 * - Persistent storage for conversation history
 * - MCP tool calling support for OpenRouter (when configured)
 *
 * @param gigaChatApiClient GigaChat API client
 * @param openAIApiClient OpenRouter/OpenAI API client (optional)
 * @param summarizationService Universal summarization service
 * @param messageRepository Repository for persistent message storage
 * @param mcpClientServiceList MCP client for tool calling
 * @param toolAdapterService Tool format conversion service
 * @param toolExecutionService Tool execution workflow handler (optional, requires OpenRouter)
 */
class ChatService(
    gigaChatApiClient: GigaChatApiClient,
    openAIApiClient: OpenAIApiClient?,
    summarizationService: SummarizationService,
    messageRepository: MessageRepository,
    mcpClientServiceList: List<IMcpClientService>,
    toolAdapterService: ToolAdapterService,
    toolExecutionService: ToolExecutionService? = null
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    // Provider handlers
    private val gigaChatHandler: ProviderHandler<GigaChatMessage>
    private val openRouterHandler: OpenRouterProviderHandler?

    // OpenRouter-specific state
    private var cumulativePromptTokens = 0
    private var cumulativeCompletionTokens = 0
    private var cumulativeTotalTokens = 0
    private var currentModel: String? = null

    init {
        // Initialize GigaChat handler
        val gigaChatAdapter = GigaChatClientAdapter(gigaChatApiClient)
        gigaChatHandler = ProviderHandler(
            provider = AiProvider.GIGACHAT,
            aiClient = gigaChatAdapter,
            summarizationService = summarizationService,
            messageRepository = messageRepository,
            providerName = "gigachat"
        )

        // Initialize OpenRouter handler if available with optional tool calling support
        openRouterHandler = openAIApiClient?.let { client ->
            OpenRouterProviderHandler(
                openAIApiClient = client,
                summarizationService = summarizationService,
                messageRepository = messageRepository,
                maxTokens = null,
                mcpClientServiceList = mcpClientServiceList,
                toolAdapterService = toolAdapterService,
                toolExecutionService = toolExecutionService
            )
        }
    }

    /**
     * Process a user message and return AI response.
     * Automatically routes to the appropriate provider.
     *
     * @param userText User's message
     * @param systemPrompt Optional system prompt
     * @param temperature Response randomness (0.0-2.0)
     * @param provider Provider name ("gigachat" or "openrouter")
     * @param model Model name (OpenRouter only)
     * @param maxTokens Max response tokens (OpenRouter only)
     * @param enableTools Enable MCP tool calling for OpenRouter (default: true)
     * @return ChatResponse with AI reply and metadata
     */
    suspend fun processUserMessage(
        userText: String,
        systemPrompt: String = "",
        temperature: Double = 0.7,
        provider: String = "gigachat",
        model: String? = null,
        maxTokens: Int? = null,
        enableTools: Boolean = true
    ): ChatResponse {
        return try {
            val aiProvider = AiProvider.fromString(provider)
            logger.info("Processing message [provider=${aiProvider.displayName}, temperature=$temperature, model=$model, enableTools=$enableTools]")

            when (aiProvider) {
                AiProvider.GIGACHAT -> processGigaChatMessage(userText, systemPrompt, temperature)
                AiProvider.OPENROUTER -> processOpenRouterMessage(
                    userText,
                    systemPrompt,
                    temperature,
                    model,
                    maxTokens,
                    enableTools
                )
            }
        } catch (e: Exception) {
            logger.error("Error processing user message", e)
            ChatResponse("Возникла ошибка при получении ответа: ${e.message}", ResponseStatus.ERROR)
        }
    }

    /**
     * Process message using GigaChat.
     */
    private suspend fun processGigaChatMessage(
        userText: String,
        systemPrompt: String,
        temperature: Double
    ): ChatResponse {
        val response = gigaChatHandler.processMessage(userText, systemPrompt, temperature)
        return ChatResponse(response, ResponseStatus.SUCCESS)
    }

    /**
     * Process message using OpenRouter with token tracking and optional tool calling.
     */
    private suspend fun processOpenRouterMessage(
        userText: String,
        systemPrompt: String,
        temperature: Double,
        model: String?,
        maxTokens: Int?,
        enableTools: Boolean
    ): ChatResponse {
        // Validate OpenRouter is configured
        if (openRouterHandler == null) {
            logger.error("OpenRouter client not configured")
            return ChatResponse(
                "OpenRouter не настроен. Пожалуйста, настройте OpenAI API в конфигурации сервера.",
                ResponseStatus.ERROR
            )
        }

        // Handle model changes
        if (model != null && currentModel != model) {
            logger.info("Model changed from $currentModel to $model. Resetting state.")
            openRouterHandler.clearHistory()
            resetTokenUsage()
            currentModel = model
        }

        // Route to appropriate processing method based on enableTools flag
        val result = if (enableTools) {
            logger.info("Processing with tool calling enabled")
            openRouterHandler.processMessageWithTools(userText, systemPrompt, temperature)
        } else {
            logger.info("Processing without tool calling")
            openRouterHandler.processMessageWithMetadata(userText, systemPrompt, temperature)
        }

        // Track last response tokens
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
            logger.debug("Cumulative tokens - Prompt: $cumulativePromptTokens, Completion: $cumulativeCompletionTokens, Total: $cumulativeTotalTokens")
        }

        val cumulativeTokenUsage = TokenUsage(
            promptTokens = cumulativePromptTokens,
            completionTokens = cumulativeCompletionTokens,
            totalTokens = cumulativeTotalTokens
        )

        logger.debug("Response time: ${result.responseTimeMs}ms")

        return ChatResponse(
            text = result.text,
            status = ResponseStatus.SUCCESS,
            tokenUsage = cumulativeTokenUsage,
            lastResponseTokenUsage = lastResponseTokenUsage,
            responseTimeMs = result.responseTimeMs
        )
    }

    /**
     * Clear all conversation histories and reset state from both memory and database.
     */
    suspend fun clearHistory() {
        logger.info("Clearing all message histories")
        gigaChatHandler.clearHistory()
        openRouterHandler?.clearHistory()
        resetTokenUsage()
        currentModel = null
    }

    /**
     * Load conversation histories from database into memory for all providers.
     */
    suspend fun loadAllHistory() {
        logger.info("Loading conversation histories from database")
        gigaChatHandler.loadHistory()
        openRouterHandler?.loadHistory()
    }

    /**
     * Reset cumulative token usage counters.
     */
    private fun resetTokenUsage() {
        logger.info("Resetting token usage")
        cumulativePromptTokens = 0
        cumulativeCompletionTokens = 0
        cumulativeTotalTokens = 0
    }
}
