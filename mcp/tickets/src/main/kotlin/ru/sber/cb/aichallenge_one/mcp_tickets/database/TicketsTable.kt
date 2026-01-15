package ru.sber.cb.aichallenge_one.mcp_tickets.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object TicketsTable : Table("tickets") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 255)
    val description = text("description")
    val initiator = varchar("initiator", 255).nullable()
    val priority = integer("priority").default(3) // 1-5
    val status = varchar("status", 20).default("open") // open, closed
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, status)
        index(false, priority)
        index(false, initiator)
        index(false, createdAt)
    }
}
