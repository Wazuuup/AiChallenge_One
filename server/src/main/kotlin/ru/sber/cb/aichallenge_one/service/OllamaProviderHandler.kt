package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.OllamaClientAdapter
import ru.sber.cb.aichallenge_one.client.OllamaMessage
import ru.sber.cb.aichallenge_one.client.OllamaMessageRole
import ru.sber.cb.aichallenge_one.client.OllamaUsage
import ru.sber.cb.aichallenge_one.database.MessageRepository
import ru.sber.cb.aichallenge_one.domain.AiProvider
import ru.sber.cb.aichallenge_one.domain.ConversationHistory
import ru.sber.cb.aichallenge_one.service.mcp.IMcpClientService

/**
 * Specialized provider handler for Ollama with configurable summarization and tool calling support.
 * Extends base functionality with Ollama-specific features like local model processing,
 * optional summarization (disabled by default for local models), and MCP tools support.
 *
 * Ollama uses an OpenAI-compatible API, allowing reuse of OpenAI client infrastructure
 * while providing Ollama-specific configurations and behaviors.
 *
 * Key differences from OpenRouter:
 * - Summarization is optional (disabled by default since local models have no token limits)
 * - No API key required (local execution)
 * - Longer timeout defaults (local models can be slower)
 * - Supports function calling via OpenAI-compatible API
 *
 * @param ollamaClientAdapter Ollama client adapter (wraps OpenAI-compatible API)
 * @param summarizationService Service for conversation summarization (optional)
 * @param messageRepository Repository for persistent message storage
 * @param enableSummarization Whether to enable automatic summarization (default: false)
 * @param mcpClientServiceList List of MCP clients for tool calling (optional)
 * @param toolAdapterService Service for converting MCP tools to OpenAI format
 * @param toolExecutionService Service for executing tool calling workflow (optional)
 * @param enableTools Whether to enable MCP tools support (default: true)
 */
class OllamaProviderHandler(
    private val ollamaClientAdapter: OllamaClientAdapter,
    private val summarizationService: SummarizationService,
    private val messageRepository: MessageRepository,
    private val enableSummarization: Boolean = false,
    private val mcpClientServiceList: List<IMcpClientService>? = null,
    private val toolAdapterService: ToolAdapterService? = null,
    private val toolExecutionService: ToolExecutionService? = null,
    private val enableTools: Boolean = true
) {
    private val logger = LoggerFactory.getLogger(OllamaProviderHandler::class.java)
    private val history = ConversationHistory<OllamaMessage>()
    private val providerName = "ollama"

    /**
     * Process a user message with basic functionality (no tools).
     *
     * @param userText User's message
     * @param systemPrompt Custom system prompt
     * @param temperature Response randomness (0.0-2.0)
     * @return Result with response text, token usage (if available), and response time
     */
    suspend fun processMessage(
        userText: String,
        systemPrompt: String,
        temperature: Double
    ): OllamaHandlerResult {
        logger.info("Processing Ollama message: $userText")

        // Add user message to history
        val userMessage = OllamaMessage(role = OllamaMessageRole.USER.value, content = userText)
        history.add(userMessage)

        // Save user message to database
        try {
            messageRepository.saveMessage(providerName, OllamaMessageRole.USER.value, userText)
        } catch (e: Exception) {
            logger.error("Failed to save user message to database", e)
        }

        // Check if summarization is needed (only if enabled)
        if (enableSummarization && summarizationService.shouldSummarize(history.messageCount)) {
            performSummarization()
        }

        // Send to Ollama with metadata capture
        val startTime = System.currentTimeMillis()
        val result = ollamaClientAdapter.sendMessageWithMetadata(
            messageHistory = history.toList(),
            systemPrompt = systemPrompt,
            temperature = temperature
        )
        val responseTimeMs = System.currentTimeMillis() - startTime

        logger.info("Received response from Ollama in ${responseTimeMs}ms")

        // Add assistant response to history
        val assistantMessage = OllamaMessage(role = OllamaMessageRole.ASSISTANT.value, content = result.text)
        history.add(assistantMessage)

        // Save assistant response to database
        try {
            messageRepository.saveMessage(providerName, OllamaMessageRole.ASSISTANT.value, result.text)
        } catch (e: Exception) {
            logger.error("Failed to save assistant message to database", e)
        }

        return OllamaHandlerResult(
            text = result.text,
            usage = result.usage,
            responseTimeMs = responseTimeMs,
            model = ollamaClientAdapter.getModel()
        )
    }

    /**
     * Process a user message with full metadata.
     * Supports model override for dynamic model switching.
     *
     * @param userText User's message
     * @param systemPrompt Custom system prompt
     * @param temperature Response randomness (0.0-2.0)
     * @param modelOverride Override model for this request (optional)
     * @return Result with response text, token usage, and response time
     */
    suspend fun processMessageWithMetadata(
        userText: String,
        systemPrompt: String,
        temperature: Double,
        modelOverride: String? = null
    ): OllamaHandlerResult {
        logger.info("Processing Ollama message with metadata: $userText")

        // Add user message to history
        val userMessage = OllamaMessage(role = OllamaMessageRole.USER.value, content = userText)
        history.add(userMessage)

        // Save user message to database
        try {
            messageRepository.saveMessage(providerName, OllamaMessageRole.USER.value, userText)
        } catch (e: Exception) {
            logger.error("Failed to save user message to database", e)
        }

        // Check if summarization is needed (only if enabled)
        if (enableSummarization && summarizationService.shouldSummarize(history.messageCount)) {
            performSummarization()
        }

        // Send to Ollama with metadata capture
        val startTime = System.currentTimeMillis()
        val result = ollamaClientAdapter.sendMessageWithMetadata(
            messageHistory = history.toList(),
            systemPrompt = systemPrompt,
            temperature = temperature,
            modelOverride = modelOverride
        )
        val responseTimeMs = System.currentTimeMillis() - startTime

        logger.info("Received response from Ollama in ${responseTimeMs}ms")

        // Add assistant response to history
        val assistantMessage = OllamaMessage(role = OllamaMessageRole.ASSISTANT.value, content = result.text)
        history.add(assistantMessage)

        // Save assistant response to database
        try {
            messageRepository.saveMessage(providerName, OllamaMessageRole.ASSISTANT.value, result.text)
        } catch (e: Exception) {
            logger.error("Failed to save assistant message to database", e)
        }

        return OllamaHandlerResult(
            text = result.text,
            usage = result.usage,
            responseTimeMs = responseTimeMs,
            model = modelOverride ?: ollamaClientAdapter.getModel()
        )
    }

    /**
     * Process a user message with tool calling support.
     * Automatically fetches available tools from MCP server and handles tool execution workflow.
     *
     * Ollama supports function calling through its OpenAI-compatible API, allowing
     * full integration with MCP tools for local AI processing.
     *
     * Note: Tool calling with Ollama requires the OllamaApiClient to support the OpenAI
     * tools format. This implementation provides the structure for when that support is available.
     *
     * @param userText User's message
     * @param systemPrompt Custom system prompt
     * @param temperature Response randomness (0.0-2.0)
     * @param modelOverride Override model for this request (optional, not yet implemented)
     * @return Result with response text, token usage, and response time
     */
    suspend fun processMessageWithTools(
        userText: String,
        systemPrompt: String,
        temperature: Double,
        modelOverride: String? = null
    ): OllamaHandlerResult {
        // Check if tools are enabled
        if (!enableTools) {
            logger.warn("Tools are disabled for Ollama, falling back to regular processing")
            return processMessageWithMetadata(userText, systemPrompt, temperature, modelOverride)
        }

        // Validate tool execution dependencies
        if (toolExecutionService == null || toolAdapterService == null || mcpClientServiceList == null) {
            logger.warn("Tool execution dependencies not available, falling back to regular processing")
            return processMessageWithMetadata(userText, systemPrompt, temperature, modelOverride)
        }

        logger.info("Processing Ollama message with tools: $userText")

        try {
            // Fetch available tools from MCP server
            val mcpTools = mcpClientServiceList.map { it.listTools() }.flatten()
            val openAiTools = toolAdapterService.convertMcpToolsToOpenRouter(mcpTools)

            logger.info("Found ${openAiTools.size} available tools from MCP server")
            logger.debug("Available tools: ${openAiTools.map { it.function.name }}")

            // Note: Ollama's OpenAI-compatible API support for tools is still evolving
            // The tool execution workflow is structured here but may require additional
            // implementation in OllamaApiClient when tool calling is fully supported

            // For now, fall back to regular processing with a note
            logger.info("Ollama tool calling is structured but not yet fully implemented in the API client")
            logger.info("Processing message without tools. Tool support will be available when OllamaApiClient is updated.")

            return processMessageWithMetadata(userText, systemPrompt, temperature, modelOverride)

        } catch (e: Exception) {
            logger.error("Error in tool calling setup, falling back to regular processing", e)
            // Fallback to regular processing if tool calling fails
            return processMessageWithMetadata(userText, systemPrompt, temperature, modelOverride)
        }
    }

    /**
     * Perform conversation summarization for Ollama.
     * Only executed if enableSummarization is true.
     */
    private suspend fun performSummarization() {
        logger.info("Ollama message threshold reached (${history.messageCount} messages). Triggering summarization...")

        try {
            val summary = summarizationService.summarize(history.toList(), ollamaClientAdapter)
            history.clear()
            history.add(summary)
            logger.info("Successfully summarized Ollama history. New history size: ${history.size()}")

            // Update database with summary
            try {
                messageRepository.replaceWithSummary(providerName, summary.content, summary.role)
            } catch (e: Exception) {
                logger.error("Failed to update database with summary", e)
            }
        } catch (e: Exception) {
            logger.error("Ollama summarization failed, continuing with full history", e)
        }
    }

    /**
     * Clear conversation history from both memory and database.
     */
    suspend fun clearHistory() {
        logger.info("Clearing Ollama message history")
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
                val message = OllamaMessage(role = entity.role, content = entity.content)
                history.add(message)
            }

            history.messageCount = messages.size
            logger.info("Loaded ${messages.size} messages from database for Ollama")
        } catch (e: Exception) {
            logger.error("Failed to load history from database for Ollama", e)
        }
    }

    /**
     * Get the provider type for this handler.
     */
    fun getProvider(): AiProvider = AiProvider.OLLAMA

    /**
     * Get the current history size.
     */
    fun getHistorySize(): Int = history.size()

    /**
     * Get the current message count.
     */
    fun getMessageCount(): Int = history.messageCount
}

/**
 * Result of Ollama message processing with metadata.
 * Similar to OllamaMessageResult but used at the handler level for consistency
 * with other provider handlers.
 *
 * @param text The response text from Ollama
 * @param usage Token usage information (may be null for some Ollama models)
 * @param responseTimeMs Response time in milliseconds
 * @param model The model used for generation (e.g., "gemma3:1b")
 */
data class OllamaHandlerResult(
    val text: String,
    val usage: OllamaUsage?,
    val responseTimeMs: Long,
    val model: String
)
