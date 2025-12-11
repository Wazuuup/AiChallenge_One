package ru.sber.cb.aichallenge_one.domain

/**
 * Common interface for AI client implementations.
 * Provides a unified API for different AI providers (GigaChat, OpenRouter, etc.)
 */
interface AiClient<T : ConversationMessage> {
    /**
     * Send a message to the AI model and get a response.
     *
     * @param messageHistory List of previous messages in the conversation
     * @param systemPrompt Optional system prompt to guide the AI behavior
     * @param temperature Temperature parameter for response randomness (0.0-2.0)
     * @return Response text from the AI model
     */
    suspend fun sendMessage(
        messageHistory: List<T>,
        systemPrompt: String = "",
        temperature: Double = 0.7
    ): String

    /**
     * Create a message instance for this provider.
     *
     * @param role Message role (user, assistant, system)
     * @param content Message content
     * @return Message instance of type T
     */
    fun createMessage(role: String, content: String): T

    /**
     * Get the provider type for this client.
     */
    fun getProvider(): AiProvider

    /**
     * Get localized role name for display purposes.
     *
     * @param role The message role
     * @return Localized role name
     */
    fun getLocalizedRoleName(role: String): String
}

/**
 * Result of AI message processing with additional metadata.
 */
data class AiMessageResult(
    val text: String,
    val responseTimeMs: Long = 0,
    val metadata: Map<String, Any> = emptyMap()
)
