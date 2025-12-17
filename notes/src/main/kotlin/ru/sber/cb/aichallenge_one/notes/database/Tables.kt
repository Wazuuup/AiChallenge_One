package ru.sber.cb.aichallenge_one.notes.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object Notes : Table("notes") {
    val id = integer("id").autoIncrement()
    val text = text("text")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val dueDate = datetime("due_date").nullable()
    val priority = varchar("priority", 10).default("MEDIUM")
    val completed = bool("completed").default(false)

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, createdAt)
        index(false, completed)
        index(false, priority)
    }
}
