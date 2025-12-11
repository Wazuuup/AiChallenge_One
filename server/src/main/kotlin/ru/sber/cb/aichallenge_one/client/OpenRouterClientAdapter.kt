package ru.sber.cb.aichallenge_one.client

import ru.sber.cb.aichallenge_one.domain.AiClient
import ru.sber.cb.aichallenge_one.domain.AiProvider

/**
 * Adapter that wraps OpenAIApiClient to implement the common AiClient interface.
 * This allows OpenRouter/OpenAI to be used interchangeably with other AI providers.
 */
class OpenRouterClientAdapter(
    private val openAIApiClient: OpenAIApiClient,
    private val maxTokens: Int? = null
) : AiClient<OpenAIMessage> {

    override suspend fun sendMessage(
        messageHistory: List<OpenAIMessage>,
        systemPrompt: String,
        temperature: Double
    ): String {
        val result = openAIApiClient.sendMessage(messageHistory, systemPrompt, temperature, maxTokens)
        return result.text
    }

    override fun createMessage(role: String, content: String): OpenAIMessage {
        return OpenAIMessage(role, content)
    }

    override fun getProvider(): AiProvider = AiProvider.OPENROUTER

    override fun getLocalizedRoleName(role: String): String {
        return when (role) {
            MessageRole.USER.value -> "User"
            MessageRole.ASSISTANT.value -> "Assistant"
            MessageRole.SYSTEM.value -> "System"
            else -> role
        }
    }

    /**
     * Get the full OpenAIMessageResult with metadata like token usage.
     * This is useful when you need additional information beyond just the text.
     */
    suspend fun sendMessageWithMetadata(
        messageHistory: List<OpenAIMessage>,
        systemPrompt: String,
        temperature: Double
    ): OpenAIMessageResult {
        return openAIApiClient.sendMessage(messageHistory, systemPrompt, temperature, maxTokens)
    }
}
