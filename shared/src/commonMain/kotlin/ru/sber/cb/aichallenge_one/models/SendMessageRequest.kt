package ru.sber.cb.aichallenge_one.models

import kotlinx.serialization.Serializable

@Serializable
data class SendMessageRequest(
    val text: String,
    val systemPrompt: String = "",
    val temperature: Double = 0.7,
    val provider: String = "gigachat", // "gigachat" or "openrouter"
    val model: String? = null, // Model name for OpenRouter, null for GigaChat
    val maxTokens: Int? = null, // Maximum tokens for completion, null for default
    val enableTools: Boolean = true, // Enable MCP tool calling for OpenRouter (default: true)
    val useRag: Boolean = false, // Enable RAG (Retrieval-Augmented Generation) context (default: false)
    val isHelpCommand: Boolean = false // Is this a /help command for codebase questions (default: false)
)
