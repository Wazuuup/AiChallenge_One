package ru.sber.cb.aichallenge_one.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object ConversationMessages : Table("conversation_messages") {
    val id = integer("id").autoIncrement()
    val provider = varchar("provider", 20)
    val role = varchar("role", 20)
    val content = text("content")
    val isSummary = bool("is_summary").default(false)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, provider, createdAt)
    }
}
