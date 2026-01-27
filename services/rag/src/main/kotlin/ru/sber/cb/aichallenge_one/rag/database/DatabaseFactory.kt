package ru.sber.cb.aichallenge_one.rag.database

import com.typesafe.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
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

            logger.info("Database initialized successfully (using vectorizer embeddings table)")
        } catch (e: Exception) {
            logger.error("Failed to initialize database", e)
            throw e
        }
    }
}
