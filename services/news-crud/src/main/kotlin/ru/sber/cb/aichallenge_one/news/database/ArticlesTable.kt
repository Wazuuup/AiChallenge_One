package ru.sber.cb.aichallenge_one.news.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object ArticlesTable : Table("articles") {
    val id = integer("id").autoIncrement()
    val sourceId = varchar("source_id", 255).nullable()
    val sourceName = varchar("source_name", 255).nullable()
    val author = varchar("author", 500).nullable()
    val title = varchar("title", 500)
    val description = text("description").nullable()
    val url = varchar("url", 1000).nullable()
    val urlToImage = varchar("url_to_image", 1000).nullable()
    val publishedAt = datetime("published_at").nullable()
    val content = text("content").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}
