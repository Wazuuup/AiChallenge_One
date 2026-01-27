package ru.sber.cb.aichallenge_one.models

import kotlinx.serialization.Serializable

@Serializable
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val sttTokens: Int = 0,
    val sttCost: Double = 0.0
)
