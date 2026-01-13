package ru.sber.cb.aichallenge_one.github.webhook.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReviewRequest(
    val model: String,
    val messages: List<ReviewMessage>,
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int
)

@Serializable
data class ReviewMessage(
    val role: String, // "system" or "user"
    val content: String
)

@Serializable
data class ReviewResponse(
    val choices: List<ReviewChoice>,
    val usage: ReviewUsage? = null
)

@Serializable
data class ReviewChoice(
    val message: ReviewMessage
)

@Serializable
data class ReviewUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)
