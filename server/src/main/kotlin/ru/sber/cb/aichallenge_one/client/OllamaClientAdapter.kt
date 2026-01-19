package ru.sber.cb.aichallenge_one.client

import ru.sber.cb.aichallenge_one.domain.AiClient
import ru.sber.cb.aichallenge_one.domain.AiProvider

/**
 * Adapter that wraps OllamaApiClient to implement the common AiClient interface.
 * This allows Ollama to be used interchangeably with other AI providers.
 *
 * @property ollamaApiClient The underlying Ollama API client to delegate calls to
 */
class OllamaClientAdapter(
    private val ollamaApiClient: OllamaApiClient
) : AiClient<OllamaMessage> {

    /**
     * Send a message to the Ollama model and get a response.
     *
     * @param messageHistory List of previous messages in the conversation
     * @param systemPrompt Optional system prompt to guide the AI behavior
     * @param temperature Temperature parameter for response randomness (0.0-2.0)
     * @return Response text from the Ollama model
     */
    override suspend fun sendMessage(
        messageHistory: List<OllamaMessage>,
        systemPrompt: String,
        temperature: Double
    ): String {
        val result = ollamaApiClient.sendMessage(messageHistory, systemPrompt, temperature)
        return result.text
    }

    /**
     * Create an OllamaMessage instance for this provider.
     *
     * @param role Message role (user, assistant, system)
     * @param content Message content
     * @return OllamaMessage instance
     */
    override fun createMessage(role: String, content: String): OllamaMessage {
        return OllamaMessage(role, content)
    }

    /**
     * Get the provider type for this client.
     *
     * @return AiProvider.OLLAMA
     */
    override fun getProvider(): AiProvider = AiProvider.OLLAMA

    /**
     * Get localized role name for display purposes.
     * Uses English names for Ollama roles.
     *
     * @param role The message role
     * @return Localized role name in English
     */
    override fun getLocalizedRoleName(role: String): String {
        return when (role) {
            OllamaMessageRole.USER.value -> "User"
            OllamaMessageRole.ASSISTANT.value -> "Assistant"
            OllamaMessageRole.SYSTEM.value -> "System"
            else -> role
        }
    }

    /**
     * Get the full OllamaMessageResult with metadata like token usage and response time.
     * This is useful when you need additional information beyond just the text.
     *
     * @param messageHistory List of previous messages in the conversation
     * @param systemPrompt Optional system prompt to guide the AI behavior
     * @param temperature Temperature parameter for response randomness (0.0-2.0)
     * @param modelOverride Override model for this request (optional)
     * @return OllamaMessageResult containing response text, usage stats, and timing
     */
    suspend fun sendMessageWithMetadata(
        messageHistory: List<OllamaMessage>,
        systemPrompt: String,
        temperature: Double,
        modelOverride: String? = null
    ): OllamaMessageResult {
        return ollamaApiClient.sendMessage(
            messageHistory = messageHistory,
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = null,
            modelOverride = modelOverride
        )
    }

    /**
     * Get the underlying OllamaApiClient instance.
     * Used for accessing advanced features like tool response history.
     *
     * @return The OllamaApiClient instance
     */
    fun getOllamaApiClient(): OllamaApiClient = ollamaApiClient

    /**
     * Get the current model name being used by the Ollama client.
     *
     * @return Model name (e.g., "gemma3:1b")
     */
    fun getModel(): String = ollamaApiClient.getModel()
}
