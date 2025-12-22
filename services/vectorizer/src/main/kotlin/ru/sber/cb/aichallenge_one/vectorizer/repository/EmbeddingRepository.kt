package ru.sber.cb.aichallenge_one.vectorizer.repository

import com.pgvector.PGvector
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.sql.Connection

data class EmbeddingEntity(
    val id: Int,
    val filePath: String,
    val fileName: String,
    val chunkIndex: Int,
    val chunkText: String,
    val tokenCount: Int,
    val embedding: FloatArray,
    val createdAt: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmbeddingEntity

        if (id != other.id) return false
        if (filePath != other.filePath) return false
        if (fileName != other.fileName) return false
        if (chunkIndex != other.chunkIndex) return false
        if (chunkText != other.chunkText) return false
        if (tokenCount != other.tokenCount) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + filePath.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + chunkText.hashCode()
        result = 31 * result + tokenCount
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

class EmbeddingRepository {
    private val logger = LoggerFactory.getLogger(EmbeddingRepository::class.java)

    suspend fun insertEmbedding(
        filePath: String,
        fileName: String,
        chunkIndex: Int,
        chunkText: String,
        tokenCount: Int,
        embedding: FloatArray
    ): Boolean {
        return try {
            dbQuery {
                val sql = """
                    INSERT INTO embeddings (file_path, file_name, chunk_index, chunk_text, token_count, embedding)
                    VALUES (?, ?, ?, ?, ?, ?::vector)
                    ON CONFLICT (file_path, chunk_index) DO UPDATE
                    SET chunk_text = EXCLUDED.chunk_text,
                        token_count = EXCLUDED.token_count,
                        embedding = EXCLUDED.embedding
                """.trimIndent()

                val jdbcConnection = this.connection.connection as Connection
                val stmt = jdbcConnection.prepareStatement(sql)
                try {
                    stmt.setString(1, filePath)
                    stmt.setString(2, fileName)
                    stmt.setInt(3, chunkIndex)
                    stmt.setString(4, chunkText)
                    stmt.setInt(5, tokenCount)
                    stmt.setObject(6, PGvector(embedding))
                    stmt.executeUpdate() > 0
                } finally {
                    stmt.close()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to insert embedding for $filePath:$chunkIndex", e)
            false
        }
    }

    suspend fun deleteByFilePath(filePath: String): Int {
        return try {
            dbQuery {
                val sql = "DELETE FROM embeddings WHERE file_path = ?"
                val jdbcConnection = this.connection.connection as Connection
                val stmt = jdbcConnection.prepareStatement(sql)
                try {
                    stmt.setString(1, filePath)
                    stmt.executeUpdate()
                } finally {
                    stmt.close()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to delete embeddings for $filePath", e)
            0
        }
    }

    suspend fun findByFilePath(filePath: String): List<EmbeddingEntity> {
        return try {
            dbQuery {
                val sql = """
                    SELECT id, file_path, file_name, chunk_index, chunk_text, token_count,
                           embedding, created_at::text
                    FROM embeddings WHERE file_path = ?
                    ORDER BY chunk_index
                """.trimIndent()

                val jdbcConnection = this.connection.connection as Connection
                val stmt = jdbcConnection.prepareStatement(sql)
                val results = mutableListOf<EmbeddingEntity>()
                try {
                    stmt.setString(1, filePath)
                    val rs = stmt.executeQuery()

                    while (rs.next()) {
                        results.add(
                            EmbeddingEntity(
                                id = rs.getInt("id"),
                                filePath = rs.getString("file_path"),
                                fileName = rs.getString("file_name"),
                                chunkIndex = rs.getInt("chunk_index"),
                                chunkText = rs.getString("chunk_text"),
                                tokenCount = rs.getInt("token_count"),
                                embedding = (rs.getObject("embedding") as PGvector).toArray(),
                                createdAt = rs.getString("created_at")
                            )
                        )
                    }
                } finally {
                    stmt.close()
                }
                results
            }
        } catch (e: Exception) {
            logger.error("Failed to find embeddings for $filePath", e)
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
