package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.*
import ru.sber.cb.aichallenge_one.database.MessageRepository
import ru.sber.cb.aichallenge_one.domain.AiProvider
import ru.sber.cb.aichallenge_one.domain.ConversationHistory
import ru.sber.cb.aichallenge_one.service.mcp.IMcpClientService

/**
 * Specialized provider handler for OpenRouter with token tracking support.
 * Extends base functionality with OpenAI-specific features like token usage monitoring and tool calling.
 *
 * @param openAIApiClient OpenRouter/OpenAI API client
 * @param summarizationService Service for conversation summarization
 * @param messageRepository Repository for persistent message storage
 * @param maxTokens Maximum tokens for responses (optional)
 * @param mcpClientServiceList MCP client for tool calling
 * @param toolAdapterService Service for converting MCP tools to OpenRouter format
 * @param toolExecutionService Service for executing tool calling workflow (optional, requires OpenRouter)
 */
class OpenRouterProviderHandler(
    private val openAIApiClient: OpenAIApiClient,
    private val summarizationService: SummarizationService,
    private val messageRepository: MessageRepository,
    private val maxTokens: Int? = null,
    private val mcpClientServiceList: List<IMcpClientService>,
    private val toolAdapterService: ToolAdapterService,
    private val toolExecutionService: ToolExecutionService? = null
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
            val adapter = OpenRouterClientAdapter(openAIApiClient, maxTokens)
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

    /**
     * Process a user message with tool calling support.
     * Automatically fetches available tools from MCP server and handles tool execution workflow.
     *
     * @param userText User's message
     * @param systemPrompt Custom system prompt
     * @param temperature Response randomness (0.0-2.0)
     * @return Result with response text, token usage, and response time
     */
    suspend fun processMessageWithTools(
        userText: String,
        systemPrompt: String,
        temperature: Double
    ): OpenRouterMessageResult {
        // Validate tool execution service is available (requires OpenRouter configuration)
        if (toolExecutionService == null) {
            logger.warn("Tool execution service not available (OpenRouter not configured), falling back to regular processing")
            return processMessageWithMetadata(userText, systemPrompt, temperature)
        }

        logger.info("Processing OpenRouter message with tools: $userText")

        try {
            // Fetch available tools from MCP server
            val mcpTools = mcpClientServiceList.map { it.listTools() }.flatten()
            val openRouterTools = toolAdapterService.convertMcpToolsToOpenRouter(mcpTools)

            logger.info("Found ${openRouterTools.size} available tools from MCP server")

            // Convert current history to OpenAIMessageWithTools format
            val messageHistoryWithTools = history.toList().map { message ->
                OpenAIMessageWithTools(
                    role = message.role,
                    content = message.content
                )
            }.toMutableList()

            // Track start time for response time measurement
            val startTime = System.currentTimeMillis()

            // Execute tool calling workflow
            val responseText = toolExecutionService.handleToolCallingWorkflow(
                messageHistory = messageHistoryWithTools,
                tools = openRouterTools,
                userMessage = userText,
                systemPrompt = systemPrompt,
                temperature = temperature
            )

            val responseTimeMs = System.currentTimeMillis() - startTime

            // Extract token usage from the last response in the workflow
            // Note: We get usage from openAIApiClient's response history
            val lastResponse = openAIApiClient.getLatestResponse()
            val usage = lastResponse?.response?.usage

            // Add user message to history
            val userMessage = OpenAIMessage(role = MessageRole.USER.value, content = userText)
            history.add(userMessage)

            // Add assistant response to history
            val assistantMessage = OpenAIMessage(role = MessageRole.ASSISTANT.value, content = responseText)
            history.add(assistantMessage)

            // Save messages to database
            try {
                messageRepository.saveMessage(providerName, MessageRole.USER.value, userText)
                messageRepository.saveMessage(providerName, MessageRole.ASSISTANT.value, responseText)
            } catch (e: Exception) {
                logger.error("Failed to save messages to database", e)
            }

            // Check if summarization is needed
            if (summarizationService.shouldSummarize(history.messageCount)) {
                performSummarization()
            }

            logger.info("Tool calling workflow completed successfully")

            return OpenRouterMessageResult(
                text = responseText,
                usage = usage,
                responseTimeMs = responseTimeMs
            )

        } catch (e: Exception) {
            logger.error("Error in tool calling workflow, falling back to regular processing", e)
            // Fallback to regular processing if tool calling fails
            return processMessageWithMetadata(userText, systemPrompt, temperature)
        }
    }
}

/**
 * Result of OpenRouter message processing with metadata.
 */
data class OpenRouterMessageResult(
    val text: String,
    val usage: OpenAIUsage?,
    val responseTimeMs: Long
)
