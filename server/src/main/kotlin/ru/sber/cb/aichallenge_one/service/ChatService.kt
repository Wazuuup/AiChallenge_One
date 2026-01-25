package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.*
import ru.sber.cb.aichallenge_one.database.MessageRepository
import ru.sber.cb.aichallenge_one.domain.AiProvider
import ru.sber.cb.aichallenge_one.models.ChatResponse
import ru.sber.cb.aichallenge_one.models.ResponseStatus
import ru.sber.cb.aichallenge_one.models.TokenUsage
import ru.sber.cb.aichallenge_one.service.mcp.IMcpClientService

/**
 * System prompt for data analyst mode (/analyse command)
 */
private const val ANALYTICS_SYSTEM_PROMPT = """Ты - опытный аналитик данных интернет-магазина сантехники.

Твои обязанности:
1. Анализировать данные о заказах, продажах и возвратах
2. Выявлять паттерны и тенденции в поведении клиентов
3. Определять причины возвратов товаров
4. Формулировать рекомендации на основе данных
5. Отвечать на вопросы о метриках бизнеса

Доступные данные:
- История заказов (ID, дата, товары, сумма, статус)
- Информация о возвратах (причина, дата, категория товара)
- Данные о клиентах (при необходимости)

При анализе:
- Используй только предоставленные данные из RAG
- Опирайся на факты, не делай предположений
- Если данных недостаточно для ответа, укажи это явно
- Приводи конкретные цифры и проценты
- Давай практические рекомендации

Порядок работы:
1. Проанализируй контекст из базы знаний (данные уже добавлены в сообщение)
2. Найди релевантную информацию для ответа на вопрос
3. Сформулируй чёткий и структурированный ответ
4. Приведи конкретные цифры и рекомендации

Отвечай ТОЛЬКО на русском языке, чётко и по делу. Не используй эмодзи."""

/**
 * System prompt for support agent mode (/support command)
 */
private const val SUPPORT_SYSTEM_PROMPT = """Ты - специалист технической поддержки приложения AiChallenge_One.

Твои обязанности:
1. Отвечать на вопросы пользователей о приложении
2. Создавать тикеты поддержки для новых проблем
3. Находить и обновлять существующие тикеты
4. Закрывать решённые тикеты

При ответе на вопросы:
- Используй RAG для поиска информации в кодовой базе (контекст уже добавлен к сообщению)
- Используй MCP tools для работы с тикетами поддержки
- Комбинируй информацию из обоих источников для наиболее полного ответа

О приложении:
- Kotlin Multiplatform веб-приложение для AI чата (GigaChat/OpenRouter)
- Compose Multiplatform frontend, Ktor backend
- MCP серверы для различных интеграций
- RAG на базе векторного поиска (Ollama + pgvector)

Доступные MCP tools для работы с тикетами:
- create_ticket: создать новый тикет (требуется title и description)
- get_ticket: получить тикет по ID
- update_ticket: обновить тикет
- list_tickets: получить список всех тикетов
- filter_by_initiator: найти тикеты по инициатору
- filter_by_title: найти тикеты по названию
- filter_by_priority: найти тикеты по приоритету
- filter_by_status: найти тикеты по статусу (open/closed)
- search_description: поиск по описанию тикетов
- close_ticket: закрыть тикет

При создании тикета:
- Обязательно спроси имя или email инициатора
- Установи приоритет на основе срочности проблемы (1-5, где 5 - критический)
- Дай понятное название и подробное описание

Приоритеты:
- 1: низкий (вопросы, запросы на улучшение)
- 2: ниже среднего (мелкие неудобства)
- 3: средний (обычные проблемы)
- 4: высокий (серьёзные проблемы, мешающие работе)
- 5: критический (система не работает)

Отвечай чётко и по делу. Не используй эмодзи."""

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
 * - RAG (Retrieval-Augmented Generation) context enrichment
 * - Ollama local LLM support with optional summarization and tools
 *
 * @param gigaChatApiClient GigaChat API client
 * @param openAIApiClient OpenRouter/OpenAI API client (optional)
 * @param ollamaClientAdapter Ollama client adapter (optional)
 * @param summarizationService Universal summarization service
 * @param messageRepository Repository for persistent message storage
 * @param mcpClientServiceList MCP client for tool calling
 * @param toolAdapterService Tool format conversion service
 * @param toolExecutionService Tool execution workflow handler (optional, requires OpenRouter/Ollama)
 * @param ragClient RAG service client for context retrieval
 * @param ollamaEnableSummarization Enable summarization for Ollama (default: false)
 * @param ollamaEnableTools Enable MCP tools for Ollama (default: true)
 */
class ChatService(
    gigaChatApiClient: GigaChatApiClient,
    openAIApiClient: OpenAIApiClient?,
    ollamaClientAdapter: OllamaClientAdapter?,
    summarizationService: SummarizationService,
    messageRepository: MessageRepository,
    mcpClientServiceList: List<IMcpClientService>,
    toolAdapterService: ToolAdapterService,
    toolExecutionService: ToolExecutionService? = null,
    private val ragClient: RagClient,
    ollamaEnableSummarization: Boolean? = null,
    ollamaEnableTools: Boolean? = null
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    // Provider handlers
    private val gigaChatHandler: ProviderHandler<GigaChatMessage>
    private val openRouterHandler: OpenRouterProviderHandler?
    private val ollamaHandler: OllamaProviderHandler?

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

        // Initialize Ollama handler if available with optional summarization and tool calling support
        ollamaHandler = ollamaClientAdapter?.let { adapter ->
            OllamaProviderHandler(
                ollamaClientAdapter = adapter,
                summarizationService = summarizationService,
                messageRepository = messageRepository,
                enableSummarization = ollamaEnableSummarization ?: false,
                mcpClientServiceList = mcpClientServiceList,
                toolAdapterService = toolAdapterService,
                toolExecutionService = toolExecutionService,
                enableTools = ollamaEnableTools ?: true
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
     * @param useRag Enable RAG context retrieval (default: false)
     * @param isHelpCommand Is this a /help command for codebase questions (default: false)
     * @param isSupportCommand Is this a /support command for support agent mode (default: false)
     * @param isAnalyseCommand Is this a /analyse command for data analyst mode (default: false)
     * @return ChatResponse with AI reply and metadata
     */
    suspend fun processUserMessage(
        userText: String,
        systemPrompt: String = "",
        temperature: Double = 0.7,
        provider: String = "gigachat",
        model: String? = null,
        maxTokens: Int? = null,
        enableTools: Boolean = true,
        useRag: Boolean = false,
        isHelpCommand: Boolean = false,
        isSupportCommand: Boolean = false,
        isAnalyseCommand: Boolean = false
    ): ChatResponse {
        return try {
            val aiProvider = AiProvider.fromString(provider)

            // Handle /help command: force enable RAG and add codebase-specific system prompt
            // Handle /support command: force enable RAG and tools, add support-specific system prompt
            // Handle /analyse command: force enable RAG, add data analyst system prompt
            val effectiveUseRag = useRag || isHelpCommand || isSupportCommand || isAnalyseCommand
            val effectiveEnableTools = enableTools || isSupportCommand
            val effectiveSystemPrompt = when {
                isSupportCommand -> SUPPORT_SYSTEM_PROMPT
                isAnalyseCommand -> ANALYTICS_SYSTEM_PROMPT
                isHelpCommand -> """You are an expert software development assistant specializing in codebase analysis.
Your task is to answer questions about the codebase using the provided context from the knowledge base.

Key guidelines:
1. Base your answers primarily on the context provided from the knowledge base
2. If the context contains relevant information, cite it and explain clearly
3. If the context doesn't fully answer the question, mention what's available and what's missing
4. Provide code examples when relevant
5. Be specific about file paths, class names, and function names when they appear in the context
6. If you're uncertain, say so - don't make assumptions beyond the provided context

Answer the user's question below using the context from the knowledge base."""

                else -> systemPrompt
            }

            logger.info("Processing message [provider=${aiProvider.displayName}, temperature=$temperature, model=$model, enableTools=$effectiveEnableTools, useRag=$effectiveUseRag, isHelpCommand=$isHelpCommand, isSupportCommand=$isSupportCommand, isAnalyseCommand=$isAnalyseCommand]")

            // RAG Context Augmentation - Add context to USER prompt, not system prompt
            val enrichedUserText = if (effectiveUseRag) {
                val ragResults = ragClient.searchSimilar(userText, limit = 5)
                if (ragResults != null && ragResults.isNotEmpty()) {
                    val ragContext = buildString {
                        append("=== Relevant Context from Knowledge Base ===\n")
                        ragResults.forEachIndexed { index, chunk ->
                            append("${index + 1}. $chunk\n")
                        }
                        append("=== End of Context ===\n\n")
                        append("User Question: $userText")
                    }
                    logger.info("RAG context added to user prompt: ${ragResults.size} chunks")
                    ragContext
                } else {
                    logger.warn("RAG enabled but no results found or RAG service unavailable")
                    userText
                }
            } else {
                userText
            }

            when (aiProvider) {
                AiProvider.GIGACHAT -> processGigaChatMessage(
                    userText = enrichedUserText,
                    systemPrompt = effectiveSystemPrompt,
                    temperature = temperature
                )
                AiProvider.OPENROUTER -> processOpenRouterMessage(
                    userText = enrichedUserText,
                    systemPrompt = effectiveSystemPrompt,
                    temperature = temperature,
                    model = model,
                    maxTokens = maxTokens,
                    enableTools = effectiveEnableTools
                )
                AiProvider.OLLAMA -> processOllamaMessage(
                    userText = enrichedUserText,
                    systemPrompt = effectiveSystemPrompt,
                    temperature = temperature,
                    model = model,
                    enableTools = effectiveEnableTools
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
            openRouterHandler.processMessageWithTools(userText, systemPrompt, temperature, maxTokens, model)
        } else {
            logger.info("Processing without tool calling")
            openRouterHandler.processMessageWithMetadata(userText, systemPrompt, temperature, maxTokens, model)
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
     * Process message using Ollama with token tracking and optional tool calling.
     */
    private suspend fun processOllamaMessage(
        userText: String,
        systemPrompt: String,
        temperature: Double,
        model: String?,
        enableTools: Boolean
    ): ChatResponse {
        // Validate Ollama is configured
        if (ollamaHandler == null) {
            logger.error("Ollama client not configured")
            return ChatResponse(
                "Ollama не настроен. Пожалуйста, установите Ollama и скачайте модель gemma3:1b.",
                ResponseStatus.ERROR
            )
        }

        // Route to appropriate processing method based on enableTools flag
        val result = if (enableTools) {
            logger.info("Processing Ollama message with tool calling enabled")
            ollamaHandler.processMessageWithTools(userText, systemPrompt, temperature, model)
        } else {
            logger.info("Processing Ollama message without tool calling")
            ollamaHandler.processMessageWithMetadata(userText, systemPrompt, temperature, model)
        }

        // Track last response tokens (Ollama may not always return usage data)
        val lastResponseTokenUsage = result.usage?.let { usage ->
            TokenUsage(
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
                totalTokens = usage.totalTokens
            )
        }

        logger.debug("Response time: ${result.responseTimeMs}ms, model: ${result.model}")

        return ChatResponse(
            text = result.text,
            status = ResponseStatus.SUCCESS,
            tokenUsage = lastResponseTokenUsage, // Ollama doesn't accumulate tokens like OpenRouter
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
        ollamaHandler?.clearHistory()
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
        ollamaHandler?.loadHistory()
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
