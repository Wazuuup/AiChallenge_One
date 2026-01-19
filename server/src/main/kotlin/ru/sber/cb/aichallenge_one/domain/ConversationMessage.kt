package ru.sber.cb.aichallenge_one.domain

/**
 * Common interface for all conversation messages across different AI providers.
 * Provides a unified abstraction over GigaChatMessage and OpenAIMessage.
 */
interface ConversationMessage {
    val role: String
    val content: String
}

/**
 * Enum representing different AI providers supported by the system.
 */
enum class AiProvider(val displayName: String) {
    GIGACHAT("GigaChat"),
    OPENROUTER("OpenRouter"),
    OLLAMA("Ollama (Local)");

    companion object {
        fun fromString(value: String): AiProvider {
            return when (value.lowercase()) {
                "gigachat" -> GIGACHAT
                "openrouter" -> OPENROUTER
                "ollama" -> OLLAMA
                else -> GIGACHAT // default
            }
        }
    }
}

/**
 * Configuration for summarization behavior.
 */
data class SummarizationConfig(
    val threshold: Int = 10,
    val temperature: Double = 0.3,
    val summaryPrefix: String = "[Summary of previous conversation]"
)

/**
 * Wrapper for conversation history with metadata.
 */
data class ConversationHistory<T : ConversationMessage>(
    val messages: MutableList<T> = mutableListOf(),
    var messageCount: Int = 0
) {
    fun add(message: T) {
        messages.add(message)
        messageCount++
    }

    fun clear() {
        messages.clear()
        messageCount = 0
    }

    fun size(): Int = messages.size

    fun isEmpty(): Boolean = messages.isEmpty()

    fun toList(): List<T> = messages.toList()
}
