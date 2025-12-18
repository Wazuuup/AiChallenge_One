package ru.sber.cb.aichallenge_one.news.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(
        jdbcUrl: String,
        driverClassName: String,
        username: String,
        password: String,
        maximumPoolSize: Int = 10
    ) {
        val database = Database.connect(
            createHikariDataSource(
                jdbcUrl = jdbcUrl,
                driverClassName = driverClassName,
                username = username,
                password = password,
                maximumPoolSize = maximumPoolSize
            )
        )

        transaction(database) {
            SchemaUtils.create(ArticlesTable)
        }
    }

    private fun createHikariDataSource(
        jdbcUrl: String,
        driverClassName: String,
        username: String,
        password: String,
        maximumPoolSize: Int
    ): HikariDataSource {
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.driverClassName = driverClassName
            this.username = username
            this.password = password
            this.maximumPoolSize = maximumPoolSize
            validate()
        }
        return HikariDataSource(config)
    }
}
