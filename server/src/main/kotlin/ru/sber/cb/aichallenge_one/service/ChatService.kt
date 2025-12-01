package ru.sber.cb.aichallenge_one.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.GigaChatApiClient
import ru.sber.cb.aichallenge_one.models.ChatResponse
import ru.sber.cb.aichallenge_one.models.ResponseStatus

class ChatService(val gigaChatApiClient: GigaChatApiClient) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    suspend fun processUserMessage(userText: String): ChatResponse {
        return try {
            logger.info("Processing user message: $userText")
            val response = gigaChatApiClient.sendMessage(userText)
            logger.info("Received response from GigaChat")
            ChatResponse(response, ResponseStatus.SUCCESS)
        } catch (e: Exception) {
            logger.error("Error processing user message", e)
            ChatResponse("Возникла ошибка при получении ответа: ${e.message}", ResponseStatus.ERROR)
        }
    }
}
