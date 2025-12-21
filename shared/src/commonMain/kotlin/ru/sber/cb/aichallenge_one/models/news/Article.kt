package ru.sber.cb.aichallenge_one.models.news

import kotlinx.serialization.Serializable

@Serializable
data class Article(
    val id: Int? = null, // Database ID, null for new articles
    val source: Source? = null,
    val author: String? = null,
    val title: String,
    val description: String? = null,
    val url: String? = null,
    val urlToImage: String? = null,
    val publishedAt: String? = null, // ISO-8601 format
    val content: String? = null
)
