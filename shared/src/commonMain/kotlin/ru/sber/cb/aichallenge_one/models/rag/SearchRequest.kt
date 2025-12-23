package ru.sber.cb.aichallenge_one.models.rag

import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    val query: String,
    val limit: Int = 10
)
