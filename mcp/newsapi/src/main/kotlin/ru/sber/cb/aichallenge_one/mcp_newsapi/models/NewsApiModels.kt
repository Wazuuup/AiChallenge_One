package ru.sber.cb.aichallenge_one.mcp_newsapi.models

import kotlinx.serialization.Serializable

/**
 * Article source information
 */
@Serializable
data class Source(
    val id: String? = null,
    val name: String? = null
)

/**
 * News article from NewsAPI
 */
@Serializable
data class Article(
    val source: Source? = null,
    val author: String? = null,
    val title: String,
    val description: String? = null,
    val url: String? = null,
    val urlToImage: String? = null,
    val publishedAt: String? = null,
    val content: String? = null
)

/**
 * Response containing list of articles
 */
@Serializable
data class ArticleList(
    val status: String,
    val totalResults: Int? = null,
    val articles: List<Article>? = null,
    val code: String? = null,
    val message: String? = null
)

/**
 * Error response from NewsAPI
 */
@Serializable
data class ErrorModel(
    val message: String,
    val code: Int
)
