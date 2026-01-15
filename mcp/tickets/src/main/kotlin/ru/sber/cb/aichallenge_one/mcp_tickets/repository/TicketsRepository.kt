package ru.sber.cb.aichallenge_one.mcp_tickets.repository

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.mcp_tickets.database.TicketsTable
import ru.sber.cb.aichallenge_one.models.tickets.Ticket
import ru.sber.cb.aichallenge_one.models.tickets.TicketStatus
import java.time.LocalDateTime
import java.time.ZoneOffset

class TicketsRepository {
    private val logger = LoggerFactory.getLogger(TicketsRepository::class.java)
    private val maxLimit = 50

    suspend fun create(
        title: String,
        description: String,
        initiator: String? = null,
        priority: Int = 3
    ): Ticket? {
        return try {
            dbQuery {
                val now = LocalDateTime.now()
                val insertStatement = TicketsTable.insert {
                    it[TicketsTable.title] = title
                    it[TicketsTable.description] = description
                    it[TicketsTable.initiator] = initiator
                    it[TicketsTable.priority] = priority.coerceIn(1, 5)
                    it[TicketsTable.status] = "open"
                    it[TicketsTable.createdAt] = now
                    it[TicketsTable.updatedAt] = now
                }

                val resultRow = TicketsTable.selectAll()
                    .where { TicketsTable.id eq insertStatement[TicketsTable.id] }
                    .single()

                rowToTicket(resultRow)
            }
        } catch (e: Exception) {
            logger.error("Failed to create ticket", e)
            null
        }
    }

    suspend fun findById(id: Int): Ticket? {
        return try {
            dbQuery {
                TicketsTable.selectAll()
                    .where { TicketsTable.id eq id }
                    .map { rowToTicket(it) }
                    .singleOrNull()
            }
        } catch (e: Exception) {
            logger.error("Failed to find ticket by id: $id", e)
            null
        }
    }

    suspend fun findAll(): List<Ticket> {
        return try {
            dbQuery {
                TicketsTable.selectAll()
                    .orderBy(TicketsTable.createdAt, SortOrder.DESC)
                    .limit(maxLimit)
                    .map { rowToTicket(it) }
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch all tickets", e)
            emptyList()
        }
    }

    suspend fun findByInitiator(initiator: String): List<Ticket> {
        return try {
            dbQuery {
                TicketsTable.selectAll()
                    .where { TicketsTable.initiator eq initiator }
                    .orderBy(TicketsTable.createdAt, SortOrder.DESC)
                    .limit(maxLimit)
                    .map { rowToTicket(it) }
            }
        } catch (e: Exception) {
            logger.error("Failed to find tickets by initiator: $initiator", e)
            emptyList()
        }
    }

    suspend fun findByTitleLike(title: String): List<Ticket> {
        return try {
            dbQuery {
                TicketsTable.selectAll()
                    .where { TicketsTable.title.lowerCase() like "%${title.lowercase()}%" }
                    .orderBy(TicketsTable.createdAt, SortOrder.DESC)
                    .limit(maxLimit)
                    .map { rowToTicket(it) }
            }
        } catch (e: Exception) {
            logger.error("Failed to find tickets by title: $title", e)
            emptyList()
        }
    }

    suspend fun findByPriority(priority: Int, operator: String = "="): List<Ticket> {
        return try {
            dbQuery {
                val condition: Op<Boolean> = when (operator) {
                    ">=" -> TicketsTable.priority greaterEq priority
                    "<=" -> TicketsTable.priority lessEq priority
                    else -> TicketsTable.priority eq priority
                }

                TicketsTable.selectAll()
                    .where { condition }
                    .orderBy(TicketsTable.priority, SortOrder.DESC)
                    .orderBy(TicketsTable.createdAt, SortOrder.DESC)
                    .limit(maxLimit)
                    .map { rowToTicket(it) }
            }
        } catch (e: Exception) {
            logger.error("Failed to find tickets by priority: $priority $operator", e)
            emptyList()
        }
    }

    suspend fun findByStatus(status: String): List<Ticket> {
        return try {
            dbQuery {
                TicketsTable.selectAll()
                    .where { TicketsTable.status eq status.lowercase() }
                    .orderBy(TicketsTable.createdAt, SortOrder.DESC)
                    .limit(maxLimit)
                    .map { rowToTicket(it) }
            }
        } catch (e: Exception) {
            logger.error("Failed to find tickets by status: $status", e)
            emptyList()
        }
    }

    suspend fun searchDescription(query: String): List<Ticket> {
        return try {
            dbQuery {
                TicketsTable.selectAll()
                    .where { TicketsTable.description.lowerCase() like "%${query.lowercase()}%" }
                    .orderBy(TicketsTable.createdAt, SortOrder.DESC)
                    .limit(maxLimit)
                    .map { rowToTicket(it) }
            }
        } catch (e: Exception) {
            logger.error("Failed to search tickets by description: $query", e)
            emptyList()
        }
    }

    suspend fun update(
        id: Int,
        title: String? = null,
        description: String? = null,
        initiator: String? = null,
        priority: Int? = null,
        status: String? = null
    ): Ticket? {
        return try {
            dbQuery {
                val updateCount = TicketsTable.update({ TicketsTable.id eq id }) {
                    if (title != null) it[TicketsTable.title] = title
                    if (description != null) it[TicketsTable.description] = description
                    if (initiator != null) it[TicketsTable.initiator] = initiator
                    if (priority != null) it[TicketsTable.priority] = priority.coerceIn(1, 5)
                    if (status != null) it[TicketsTable.status] = status.lowercase()
                    it[TicketsTable.updatedAt] = LocalDateTime.now()
                }

                if (updateCount > 0) {
                    TicketsTable.selectAll()
                        .where { TicketsTable.id eq id }
                        .map { rowToTicket(it) }
                        .singleOrNull()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to update ticket id: $id", e)
            null
        }
    }

    suspend fun close(id: Int): Ticket? {
        return update(id, status = "closed")
    }

    private fun rowToTicket(row: ResultRow): Ticket {
        return Ticket(
            id = row[TicketsTable.id],
            title = row[TicketsTable.title],
            description = row[TicketsTable.description],
            initiator = row[TicketsTable.initiator],
            priority = row[TicketsTable.priority],
            status = if (row[TicketsTable.status] == "closed") TicketStatus.CLOSED else TicketStatus.OPEN,
            createdAt = row[TicketsTable.createdAt].toInstant(ZoneOffset.UTC).toString(),
            updatedAt = row[TicketsTable.updatedAt].toInstant(ZoneOffset.UTC).toString()
        )
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
