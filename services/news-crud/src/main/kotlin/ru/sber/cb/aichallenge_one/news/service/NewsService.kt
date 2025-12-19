package ru.sber.cb.aichallenge_one.news.service

import ru.sber.cb.aichallenge_one.models.news.Article
import ru.sber.cb.aichallenge_one.models.news.CreateArticleRequest
import ru.sber.cb.aichallenge_one.models.news.UpdateArticleRequest
import ru.sber.cb.aichallenge_one.news.repository.NewsRepository

class NewsService(private val repository: NewsRepository) {

    suspend fun createArticle(request: CreateArticleRequest): Article {
        require(request.title.isNotBlank()) { "Title cannot be blank" }

        val article = Article(
            source = request.source,
            author = request.author,
            title = request.title,
            description = request.description,
            url = request.url,
            urlToImage = request.urlToImage,
            publishedAt = request.publishedAt,
            content = request.content
        )

        return repository.createArticle(article)
    }

    suspend fun getArticleById(id: Int): Article? {
        require(id > 0) { "Article ID must be positive" }
        return repository.getArticleById(id)
    }

    suspend fun getAllArticles(limit: Int = 100, offset: Long = 0): List<Article> {
        require(limit > 0) { "Limit must be positive" }
        require(limit <= 1000) { "Limit cannot exceed 1000" }
        require(offset >= 0) { "Offset must be non-negative" }

        return repository.getAllArticles(limit, offset)
    }

    suspend fun updateArticle(id: Int, request: UpdateArticleRequest): Article? {
        require(id > 0) { "Article ID must be positive" }
        val title = request.title
        require(title == null || title.isNotBlank()) { "Title cannot be blank" }

        // Get existing article
        val existingArticle = repository.getArticleById(id) ?: return null

        // Merge update request with existing data
        val updatedArticle = existingArticle.copy(
            source = request.source ?: existingArticle.source,
            author = request.author ?: existingArticle.author,
            title = request.title ?: existingArticle.title,
            description = request.description ?: existingArticle.description,
            url = request.url ?: existingArticle.url,
            urlToImage = request.urlToImage ?: existingArticle.urlToImage,
            publishedAt = request.publishedAt ?: existingArticle.publishedAt,
            content = request.content ?: existingArticle.content
        )

        return repository.updateArticle(id, updatedArticle)
    }

    suspend fun deleteArticle(id: Int): Boolean {
        require(id > 0) { "Article ID must be positive" }
        return repository.deleteArticle(id)
    }

    suspend fun searchArticles(query: String, limit: Int = 100): List<Article> {
        require(query.isNotBlank()) { "Search query cannot be blank" }
        require(limit > 0) { "Limit must be positive" }
        require(limit <= 1000) { "Limit cannot exceed 1000" }

        return repository.searchArticles(query, limit)
    }
}
