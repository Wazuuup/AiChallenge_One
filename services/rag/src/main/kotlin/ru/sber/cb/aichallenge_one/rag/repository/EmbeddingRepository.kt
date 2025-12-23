package ru.sber.cb.aichallenge_one.rag.repository

import com.pgvector.PGvector
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.sql.Connection

class EmbeddingRepository {
    private val logger = LoggerFactory.getLogger(EmbeddingRepository::class.java)

    suspend fun searchSimilar(queryVector: FloatArray, limit: Int = 10): List<String> {
        return try {
            dbQuery {
                // Use <=> operator for cosine distance (matches the HNSW index with vector_cosine_ops)
                // Search in the embeddings table from vectorizer module
                val sql = """
                    SELECT chunk_text, embedding <=> ?::vector as distance
                    FROM embeddings
                    ORDER BY distance
                    LIMIT ?
                """.trimIndent()

                val jdbcConnection = this.connection.connection as Connection
                val stmt = jdbcConnection.prepareStatement(sql)
                val results = mutableListOf<String>()
                try {
                    stmt.setObject(1, PGvector(queryVector))
                    stmt.setInt(2, limit)
                    val rs = stmt.executeQuery()

                    logger.debug("Executing similarity search with vector dimension: ${queryVector.size}, limit: $limit")

                    while (rs.next()) {
                        val chunkText = rs.getString("chunk_text")
                        val distance = rs.getDouble("distance")
                        results.add(chunkText)
                        logger.debug("Found similar chunk with cosine distance: $distance")
                    }

                    logger.info("Found ${results.size} similar chunks in embeddings table")
                } finally {
                    stmt.close()
                }
                results
            }
        } catch (e: Exception) {
            logger.error("Failed to search similar chunks", e)
            emptyList()
        }
    }

    suspend fun countEmbeddings(): Long {
        return try {
            dbQuery {
                val sql = "SELECT COUNT(*) FROM embeddings"
                val jdbcConnection = this.connection.connection as Connection
                val stmt = jdbcConnection.prepareStatement(sql)
                try {
                    val rs = stmt.executeQuery()
                    if (rs.next()) rs.getLong(1) else 0L
                } finally {
                    stmt.close()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to count embeddings", e)
            0L
        }
    }

    private suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
