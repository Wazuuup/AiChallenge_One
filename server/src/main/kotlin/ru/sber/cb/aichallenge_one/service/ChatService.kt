package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.GigaChatApiClient
import ru.sber.cb.aichallenge_one.client.GigaChatMessage
import ru.sber.cb.aichallenge_one.client.MessageRole
import ru.sber.cb.aichallenge_one.models.ChatResponse
import ru.sber.cb.aichallenge_one.models.ResponseStatus

class ChatService(val gigaChatApiClient: GigaChatApiClient) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    private val messageHistory = mutableListOf<GigaChatMessage>()

    suspend fun processUserMessage(userText: String): ChatResponse {
        return try {
            logger.info("Processing user message: $userText")

            messageHistory.add(GigaChatMessage(role = MessageRole.USER.value, content = userText))

            val response = gigaChatApiClient.sendMessage(messageHistory)
            logger.info("Received response from GigaChat")

            messageHistory.add(GigaChatMessage(role = MessageRole.ASSISTANT.value, content = response))

            ChatResponse(response, ResponseStatus.SUCCESS)
        } catch (e: Exception) {
            logger.error("Error processing user message", e)
            ChatResponse("Возникла ошибка при получении ответа: ${e.message}", ResponseStatus.ERROR)
        }
    }

    fun clearHistory() {
        logger.info("Clearing message history")
        messageHistory.clear()
    }
}
