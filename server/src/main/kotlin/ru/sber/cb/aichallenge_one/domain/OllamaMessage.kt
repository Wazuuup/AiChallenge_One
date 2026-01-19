package ru.sber.cb.aichallenge_one.domain

import kotlinx.serialization.Serializable

/**
 * Ollama-specific message implementation for local AI model conversations.
 * Compatible with Ollama API format.
 */
@Serializable
data class OllamaMessage(
    override val role: String,
    override val content: String,
    val timestamp: Long = System.currentTimeMillis()
) : ConversationMessage
