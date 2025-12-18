package ru.sber.cb.aichallenge_one.news.repository

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import ru.sber.cb.aichallenge_one.models.news.Article
import ru.sber.cb.aichallenge_one.models.news.Source
import ru.sber.cb.aichallenge_one.news.database.ArticlesTable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NewsRepository {

    private val dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME

    suspend fun createArticle(article: Article): Article = dbQuery {
        val now = LocalDateTime.now()
        val publishedAt = article.publishedAt?.let { LocalDateTime.parse(it, dateTimeFormatter) }

        val id = ArticlesTable.insert {
            it[sourceId] = article.source?.id
            it[sourceName] = article.source?.name
            it[author] = article.author
            it[title] = article.title
            it[description] = article.description
            it[url] = article.url
            it[urlToImage] = article.urlToImage
            it[ArticlesTable.publishedAt] = publishedAt
            it[content] = article.content
            it[createdAt] = now
            it[updatedAt] = now
        }[ArticlesTable.id]

        article.copy(id = id)
    }

    suspend fun getArticleById(id: Int): Article? = dbQuery {
        ArticlesTable.selectAll().where { ArticlesTable.id eq id }
            .map { rowToArticle(it) }
            .singleOrNull()
    }

    suspend fun getAllArticles(limit: Int = 100, offset: Long = 0): List<Article> = dbQuery {
        ArticlesTable.selectAll()
            .limit(limit, offset)
            .orderBy(ArticlesTable.createdAt, SortOrder.DESC)
            .map { rowToArticle(it) }
    }

    suspend fun updateArticle(id: Int, article: Article): Article? = dbQuery {
        val exists = ArticlesTable.selectAll().where { ArticlesTable.id eq id }.count() > 0
        if (!exists) return@dbQuery null

        val publishedAt = article.publishedAt?.let { LocalDateTime.parse(it, dateTimeFormatter) }

        ArticlesTable.update({ ArticlesTable.id eq id }) {
            article.source?.let { source ->
                it[sourceId] = source.id
                it[sourceName] = source.name
            }
            article.author?.let { author -> it[ArticlesTable.author] = author }
            it[title] = article.title
            article.description?.let { desc -> it[description] = desc }
            article.url?.let { url -> it[ArticlesTable.url] = url }
            article.urlToImage?.let { urlImg -> it[urlToImage] = urlImg }
            publishedAt?.let { pub -> it[ArticlesTable.publishedAt] = pub }
            article.content?.let { cont -> it[content] = cont }
            it[updatedAt] = LocalDateTime.now()
        }

        ArticlesTable.selectAll().where { ArticlesTable.id eq id }
            .map { rowToArticle(it) }
            .singleOrNull()
    }

    suspend fun deleteArticle(id: Int): Boolean = dbQuery {
        ArticlesTable.deleteWhere { ArticlesTable.id eq id } > 0
    }

    suspend fun searchArticles(query: String, limit: Int = 100): List<Article> = dbQuery {
        ArticlesTable.selectAll()
            .where {
                (ArticlesTable.title like "%$query%") or
                        (ArticlesTable.description like "%$query%") or
                        (ArticlesTable.content like "%$query%")
            }
            .limit(limit)
            .orderBy(ArticlesTable.createdAt, SortOrder.DESC)
            .map { rowToArticle(it) }
    }

    private fun rowToArticle(row: ResultRow): Article {
        val source = if (row[ArticlesTable.sourceId] != null || row[ArticlesTable.sourceName] != null) {
            Source(
                id = row[ArticlesTable.sourceId],
                name = row[ArticlesTable.sourceName]
            )
        } else null

        return Article(
            id = row[ArticlesTable.id],
            source = source,
            author = row[ArticlesTable.author],
            title = row[ArticlesTable.title],
            description = row[ArticlesTable.description],
            url = row[ArticlesTable.url],
            urlToImage = row[ArticlesTable.urlToImage],
            publishedAt = row[ArticlesTable.publishedAt]?.format(dateTimeFormatter),
            content = row[ArticlesTable.content]
        )
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
