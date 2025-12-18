package ru.sber.cb.aichallenge_one.models.news

import kotlinx.serialization.Serializable

@Serializable
data class Source(
    val id: String? = null,
    val name: String? = null
)
