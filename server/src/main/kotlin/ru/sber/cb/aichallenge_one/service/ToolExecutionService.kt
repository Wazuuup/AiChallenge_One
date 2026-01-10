package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.OpenAIApiClient
import ru.sber.cb.aichallenge_one.client.OpenAIMessageWithTools
import ru.sber.cb.aichallenge_one.client.OpenAIResponseWithTools
import ru.sber.cb.aichallenge_one.client.OpenRouterTool
import ru.sber.cb.aichallenge_one.service.mcp.IMcpClientService

/**
 * Service for executing tool calls and managing the tool calling workflow
 */
class ToolExecutionService(
    private val mcpClientServiceList: List<IMcpClientService>,
    private val toolAdapterService: ToolAdapterService,
    private val openAIApiClient: OpenAIApiClient
) {
    private val logger = LoggerFactory.getLogger(ToolExecutionService::class.java)

    /**
     * Execute tool calls from OpenRouter response and generate followup messages
     *
     * @param response OpenRouter response containing tool calls
     * @return List of messages to append to conversation (assistant message + tool results)
     */
    suspend fun executeToolCalls(response: OpenAIResponseWithTools): List<OpenAIMessageWithTools> {
        val messages = mutableListOf<OpenAIMessageWithTools>()

        val assistantMessage = response.choices.firstOrNull()?.message
            ?: throw Exception("No message in OpenRouter response")

        // Add assistant's message with tool calls
        messages.add(assistantMessage)

        val toolCalls = assistantMessage.tool_calls
        if (toolCalls.isNullOrEmpty()) {
            logger.debug("No tool calls to execute")
            return messages
        }

        logger.info("Executing ${toolCalls.size} tool call(s)...")

        // Execute each tool call and collect results
        for (toolCall in toolCalls) {
            try {
                val toolName = toolCall.function.name
                val argumentsJson = toolCall.function.arguments

                logger.info("Executing tool: $toolName")
                logger.debug("Tool arguments: $argumentsJson")

                // Parse arguments from JSON string to Map
                val arguments = toolAdapterService.parseToolArguments(argumentsJson)

                // Call the tool via MCP
                val result = mcpClientServiceList
                    .first { it.listTools().map { tl -> tl.name }.contains(toolName) }
                    .callTool(toolName, arguments)

                logger.info("Tool '$toolName' executed successfully")
                logger.debug("Tool result: $result")

                // Create tool result message
                val toolResultMessage = OpenAIMessageWithTools(
                    role = "tool",
                    content = result,
                    tool_call_id = toolCall.id
                )

                messages.add(toolResultMessage)

            } catch (e: Exception) {
                logger.error("Failed to execute tool '${toolCall.function.name}': ${e.message}", e)

                // Add error as tool result
                val errorMessage = OpenAIMessageWithTools(
                    role = "tool",
                    content = "Error executing tool: ${e.message}",
                    tool_call_id = toolCall.id
                )

                messages.add(errorMessage)
            }
        }

        logger.info("Completed execution of ${toolCalls.size} tool call(s)")
        return messages
    }

    /**
     * Handle complete tool calling workflow:
     * 1. Send message to OpenRouter with tools
     * 2. Execute any tool calls in the response
     * 3. Send tool results back to OpenRouter
     * 4. Repeat until no more tool calls
     *
     * @param messageHistory Current conversation history
     * @param tools Available tools in OpenRouter format
     * @param userMessage New user message
     * @param systemPrompt Custom system prompt
     * @param temperature Temperature parameter
     * @param maxTokens Maximum tokens for completion (optional)
     * @param model Model override (optional)
     * @param maxIterations Maximum number of tool calling iterations to prevent infinite loops
     * @return Final assistant response text
     */
    suspend fun handleToolCallingWorkflow(
        messageHistory: MutableList<OpenAIMessageWithTools>,
        tools: List<OpenRouterTool>,
        userMessage: String,
        systemPrompt: String = "",
        temperature: Double = 0.7,
        maxTokens: Int? = null,
        model: String? = null,
        maxIterations: Int = 5
    ): String {
        logger.info("Starting tool calling workflow with ${tools.size} available tools")

        // Add user message to history
        messageHistory.add(
            OpenAIMessageWithTools(
                role = "user",
                content = userMessage
            )
        )

        var iteration = 0
        var finalResponse: String? = null

        while (iteration < maxIterations) {
            iteration++
            logger.info("Tool calling iteration $iteration/$maxIterations")

            // Send message with tools to OpenRouter
            val response = openAIApiClient.sendMessageWithTools(
                messageHistory = messageHistory,
                tools = tools,
                customSystemPrompt = systemPrompt,
                temperature = temperature,
                maxTokensOverride = maxTokens,
                modelOverride = model
            )

            val assistantMessage = response.choices.firstOrNull()?.message
                ?: throw Exception("No message in OpenRouter response")

            val toolCalls = assistantMessage.tool_calls

            if (toolCalls.isNullOrEmpty()) {
                // No tool calls - we have final response
                finalResponse = assistantMessage.content ?: "No response content"
                messageHistory.add(assistantMessage)
                logger.info("Tool calling workflow completed after $iteration iteration(s)")
                break
            }

            // Execute tool calls and add messages to history
            val toolMessages: List<OpenAIMessageWithTools> = executeToolCalls(response)
            messageHistory.addAll(toolMessages)

            logger.debug("Added ${toolMessages.size} messages to history after tool execution")
        }

        if (finalResponse == null) {
            logger.warn("Reached max iterations ($maxIterations) without final response")
            finalResponse = "Maximum tool calling iterations reached. Please try rephrasing your question."
        }

        return finalResponse
    }
}
