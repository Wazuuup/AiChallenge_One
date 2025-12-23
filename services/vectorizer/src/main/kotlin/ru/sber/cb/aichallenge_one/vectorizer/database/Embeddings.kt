package ru.sber.cb.aichallenge_one.vectorizer.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Placeholder table definition for embeddings.
 * Note: The actual table is created using raw SQL in DatabaseFactory
 * because Exposed doesn't natively support pgvector's vector(N) type.
 */
object Embeddings : Table("embeddings") {
    val id = integer("id").autoIncrement()
    val filePath = text("file_path")
    val fileName = varchar("file_name", 255)
    val chunkIndex = integer("chunk_index")
    val chunkText = text("chunk_text")
    val tokenCount = integer("token_count")

    // Note: embedding vector(768) column is handled via raw SQL
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)
}
