package ru.sber.cb.aichallenge_one.models

import kotlinx.serialization.Serializable

@Serializable
data class TranscribeRequest(
    val audioData: String,  // base64 encoded WebM
    val format: String = "webm",
    val language: String = "auto"
)

@Serializable
data class TranscribeResponse(
    val text: String,
    val status: String,  // SUCCESS | ERROR
    val tokenUsage: TokenUsage? = null,
    val duration: Double,  // seconds
    val cost: Double,
    val error: String? = null
)
