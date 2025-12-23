package ru.sber.cb.aichallenge_one.models.rag

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val results: List<String>
)
