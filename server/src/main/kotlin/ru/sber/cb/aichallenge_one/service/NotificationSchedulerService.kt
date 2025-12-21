package ru.sber.cb.aichallenge_one.service

import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.OpenAIMessageWithTools
import ru.sber.cb.aichallenge_one.client.OpenRouterFunction
import ru.sber.cb.aichallenge_one.client.OpenRouterTool
import ru.sber.cb.aichallenge_one.repository.NotificationRepository

class NotificationSchedulerService(
    private val toolExecutionService: ToolExecutionService?,
    private val notificationRepository: NotificationRepository
) {
    private val logger = LoggerFactory.getLogger(NotificationSchedulerService::class.java)

    companion object {
        private const val INTERVAL_MS = 120_000L  // 2 minutes
        private const val SYSTEM_PROMPT = "Ты личный помощник, планировщик задач"
        private const val USER_PROMPT = "Предоставь краткое summary по заметкам"
        private const val MODEL = "gpt-oss-120b"
        private const val TEMPERATURE = 0.7
    }

    suspend fun start() {
        if (toolExecutionService == null) {
            logger.error("ToolExecutionService not available. NotificationScheduler will not start.")
            logger.error("Ensure OpenRouter API is configured in application.conf")
            return
        }

        logger.info("NotificationScheduler started. Will run every ${INTERVAL_MS / 1000} seconds")
        logger.info("Using model: $MODEL")

        // Initial delay to allow server to fully start
        delay(30_000L)

        while (true) {
            try {
                logger.debug("Running scheduled notes summarization...")

                // Build get_all_notes tool
                val getAllNotesTool = OpenRouterTool(
                    type = "function",
                    function = OpenRouterFunction(
                        name = "get_all_notes",
                        description = "Retrieves all notes from the notes database",
                        parameters = buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {}
                            put("additionalProperties", false)
                        }
                    )
                )

                // Create conversation history
                val messageHistory = mutableListOf<OpenAIMessageWithTools>()

                // Call tool execution service
                val summaryText = toolExecutionService.handleToolCallingWorkflow(
                    messageHistory = messageHistory,
                    tools = listOf(getAllNotesTool),
                    userMessage = USER_PROMPT,
                    systemPrompt = SYSTEM_PROMPT,
                    temperature = TEMPERATURE,
                    maxIterations = 3
                )

                // Create notification with summary
                val notification = notificationRepository.addNotification(summaryText)

                logger.info("Notes summary generated successfully")
                logger.debug("Summary: ${summaryText.take(100)}...")
                logger.debug("Notification ID: ${notification.id}")

            } catch (e: Exception) {
                logger.error("Failed to generate notes summary: ${e.message}", e)
                logger.error("Will retry in ${INTERVAL_MS / 1000} seconds")

                // Create error notification
                try {
                    notificationRepository.addNotification(
                        "Не удалось создать summary заметок: ${e.message?.take(50) ?: "Unknown error"}"
                    )
                } catch (notifError: Exception) {
                    logger.error("Failed to create error notification", notifError)
                }
            }

            // Wait for next iteration
            delay(INTERVAL_MS)
        }
    }
}
