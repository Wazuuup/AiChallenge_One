package ru.sber.cb.aichallenge_one.models.vectorizer

import kotlinx.serialization.Serializable

@Serializable
data class TextVectorizeResponse(
    val embedding: List<Float>,
    val dimension: Int,
    val model: String
)
