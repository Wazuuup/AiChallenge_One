package ru.sber.cb.aichallenge_one.models.vectorizer

import kotlinx.serialization.Serializable

@Serializable
data class VectorizeResponse(
    val success: Boolean,
    val filesProcessed: Int,
    val chunksCreated: Int,
    val filesSkipped: List<String>,
    val errors: List<String>,
    val message: String? = null
)
