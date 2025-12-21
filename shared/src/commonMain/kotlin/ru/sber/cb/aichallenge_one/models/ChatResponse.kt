package ru.sber.cb.aichallenge_one.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val text: String,
    val status: ResponseStatus,
    val tokenUsage: TokenUsage? = null,
    val lastResponseTokenUsage: TokenUsage? = null,
    val responseTimeMs: Long? = null
)
