package ru.sber.cb.aichallenge_one.vectorizer.database

import com.typesafe.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun init(config: Config) {
        try {
            val hikariConfig = HikariConfig().apply {
                jdbcUrl = config.getString("database.url")
                driverClassName = config.getString("database.driver")
                username = config.getString("database.user")
                password = config.getString("database.password")
                maximumPoolSize = config.getInt("database.maxPoolSize")
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }

            val dataSource = HikariDataSource(hikariConfig)
            Database.connect(dataSource)

            logger.info("Database connection established successfully")

            transaction {
                // Enable pgvector extension
                exec("CREATE EXTENSION IF NOT EXISTS vector")
                logger.info("pgvector extension enabled")

                // Create table with custom vector column
                exec(
                    """
                    CREATE TABLE IF NOT EXISTS embeddings (
                        id SERIAL PRIMARY KEY,
                        file_path TEXT NOT NULL,
                        file_name VARCHAR(255) NOT NULL,
                        chunk_index INTEGER NOT NULL,
                        chunk_text TEXT NOT NULL,
                        token_count INTEGER NOT NULL,
                        embedding vector(768) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(file_path, chunk_index)
                    )
                """.trimIndent()
                )

                // Create indexes
                exec("CREATE INDEX IF NOT EXISTS idx_embeddings_file_path ON embeddings(file_path)")
                exec("CREATE INDEX IF NOT EXISTS idx_embeddings_file_name ON embeddings(file_name)")

                // Create HNSW index for vector similarity search
                exec("CREATE INDEX IF NOT EXISTS idx_embeddings_vector ON embeddings USING hnsw (embedding vector_cosine_ops)")

                logger.info("Embeddings table and indexes created/verified successfully")
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize database", e)
            throw e
        }
    }
}
