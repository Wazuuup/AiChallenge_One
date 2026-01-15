package ru.sber.cb.aichallenge_one.mcp_tickets.database

import com.typesafe.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

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
                SchemaUtils.create(TicketsTable)
                logger.info("Tickets table created/verified successfully")
            }

            // Check if we need to seed data
            val seedData = try {
                config.getBoolean("tickets.seed_data")
            } catch (e: Exception) {
                false
            }

            if (seedData) {
                seedInitialData()
            }

        } catch (e: Exception) {
            logger.error("Failed to initialize database", e)
            throw e
        }
    }

    private fun seedInitialData() {
        transaction {
            // Check if table is empty
            val count = TicketsTable.selectAll().count()
            if (count > 0) {
                logger.info("Tickets table already has data, skipping seed")
                return@transaction
            }

            logger.info("Seeding initial tickets data...")

            val seedTickets = listOf(
                SeedTicket(
                    title = "Не работает авторизация GigaChat",
                    description = "При попытке отправить сообщение получаю ошибку 401 Unauthorized. Проверил credentials - они корректные.",
                    initiator = "user@example.com",
                    priority = 4,
                    status = "open"
                ),
                SeedTicket(
                    title = "Медленная загрузка frontend",
                    description = "Compose App грузится более 10 секунд на первом запуске. После кэширования работает нормально.",
                    initiator = "dev@company.ru",
                    priority = 2,
                    status = "open"
                ),
                SeedTicket(
                    title = "Ошибка в RAG поиске",
                    description = "Поиск возвращает нерелевантные результаты по запросу 'как работает авторизация'. Возможно проблема с индексацией.",
                    initiator = "tester@qa.com",
                    priority = 3,
                    status = "closed"
                ),
                SeedTicket(
                    title = "Не отображается статистика токенов",
                    description = "После отправки сообщения через OpenRouter статистика токенов не обновляется на UI.",
                    initiator = "analyst@data.org",
                    priority = 2,
                    status = "closed"
                ),
                SeedTicket(
                    title = "MCP сервер не запускается",
                    description = "При запуске mcp:notes получаю ошибку 'Address already in use'. Порт 8082 занят другим процессом.",
                    initiator = "admin@server.local",
                    priority = 5,
                    status = "open"
                ),
                SeedTicket(
                    title = "Нужна документация по API",
                    description = "Хочу интегрировать ваш чат в свой проект. Где найти документацию по REST API?",
                    initiator = "external@partner.com",
                    priority = 1,
                    status = "open"
                ),
                SeedTicket(
                    title = "Ошибка при создании заметки",
                    description = "При создании заметки с длинным текстом (более 10000 символов) получаю ошибку 500.",
                    initiator = "writer@blog.net",
                    priority = 3,
                    status = "open"
                ),
                SeedTicket(
                    title = "Docker compose не работает на Windows",
                    description = "При запуске docker-compose up получаю ошибку с volume mounts. Использую Docker Desktop 4.25.",
                    initiator = "windows-user@local.dev",
                    priority = 3,
                    status = "closed"
                )
            )

            val now = LocalDateTime.now()
            seedTickets.forEach { ticket ->
                TicketsTable.insert {
                    it[title] = ticket.title
                    it[description] = ticket.description
                    it[initiator] = ticket.initiator
                    it[priority] = ticket.priority
                    it[status] = ticket.status
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }

            logger.info("Seeded ${seedTickets.size} tickets successfully")
        }
    }

    private data class SeedTicket(
        val title: String,
        val description: String,
        val initiator: String?,
        val priority: Int,
        val status: String
    )
}
