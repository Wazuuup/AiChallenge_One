package ru.sber.cb.aichallenge_one.client

import ru.sber.cb.aichallenge_one.domain.AiClient
import ru.sber.cb.aichallenge_one.domain.AiProvider

/**
 * Adapter that wraps GigaChatApiClient to implement the common AiClient interface.
 * This allows GigaChat to be used interchangeably with other AI providers.
 */
class GigaChatClientAdapter(
    private val gigaChatApiClient: GigaChatApiClient
) : AiClient<GigaChatMessage> {

    override suspend fun sendMessage(
        messageHistory: List<GigaChatMessage>,
        systemPrompt: String,
        temperature: Double
    ): String {
        return gigaChatApiClient.sendMessage(messageHistory, systemPrompt, temperature)
    }

    override fun createMessage(role: String, content: String): GigaChatMessage {
        return GigaChatMessage(role, content)
    }

    override fun getProvider(): AiProvider = AiProvider.GIGACHAT

    override fun getLocalizedRoleName(role: String): String {
        return when (role) {
            MessageRole.USER.value -> "Пользователь"
            MessageRole.ASSISTANT.value -> "Ассистент"
            MessageRole.SYSTEM.value -> "Система"
            else -> role
        }
    }
}
