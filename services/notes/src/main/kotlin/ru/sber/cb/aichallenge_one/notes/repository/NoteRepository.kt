package ru.sber.cb.aichallenge_one.notes.repository

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.models.notes.Note
import ru.sber.cb.aichallenge_one.models.notes.NotePriority
import ru.sber.cb.aichallenge_one.notes.database.Notes
import java.time.LocalDateTime
import java.time.ZoneOffset

data class NoteEntity(
    val id: Int,
    val text: String,
    val createdAt: LocalDateTime,
    val dueDate: LocalDateTime?,
    val priority: String,
    val completed: Boolean
)

class NoteRepository {
    private val logger = LoggerFactory.getLogger(NoteRepository::class.java)

    suspend fun create(
        text: String,
        dueDate: LocalDateTime? = null,
        priority: NotePriority = NotePriority.MEDIUM
    ): NoteEntity? {
        return try {
            dbQuery {
                val insertStatement = Notes.insert {
                    it[Notes.text] = text
                    it[Notes.dueDate] = dueDate
                    it[Notes.priority] = priority.name
                    it[Notes.completed] = false
                }

                val resultRow = Notes.selectAll()
                    .where { Notes.id eq insertStatement[Notes.id] }
                    .single()

                rowToNoteEntity(resultRow)
            }
        } catch (e: Exception) {
            logger.error("Failed to create note", e)
            null
        }
    }

    suspend fun findById(id: Int): NoteEntity? {
        return try {
            dbQuery {
                Notes.selectAll()
                    .where { Notes.id eq id }
                    .map { rowToNoteEntity(it) }
                    .singleOrNull()
            }
        } catch (e: Exception) {
            logger.error("Failed to find note by id: $id", e)
            null
        }
    }

    suspend fun findAll(): List<NoteEntity> {
        return try {
            dbQuery {
                Notes.selectAll()
                    .orderBy(Notes.createdAt, SortOrder.DESC)
                    .map { rowToNoteEntity(it) }
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch all notes", e)
            emptyList()
        }
    }

    suspend fun update(
        id: Int,
        text: String? = null,
        dueDate: LocalDateTime? = null,
        priority: NotePriority? = null,
        completed: Boolean? = null,
        clearDueDate: Boolean = false
    ): Boolean {
        return try {
            dbQuery {
                val updateCount = Notes.update({ Notes.id eq id }) {
                    if (text != null) it[Notes.text] = text
                    if (clearDueDate) {
                        it[Notes.dueDate] = null
                    } else if (dueDate != null) {
                        it[Notes.dueDate] = dueDate
                    }
                    if (priority != null) it[Notes.priority] = priority.name
                    if (completed != null) it[Notes.completed] = completed
                }
                updateCount > 0
            }
        } catch (e: Exception) {
            logger.error("Failed to update note id: $id", e)
            false
        }
    }

    suspend fun delete(id: Int): Boolean {
        return try {
            dbQuery {
                val deleteCount = Notes.deleteWhere { Notes.id eq id }
                deleteCount > 0
            }
        } catch (e: Exception) {
            logger.error("Failed to delete note id: $id", e)
            false
        }
    }

    private fun rowToNoteEntity(row: ResultRow): NoteEntity {
        return NoteEntity(
            id = row[Notes.id],
            text = row[Notes.text],
            createdAt = row[Notes.createdAt],
            dueDate = row[Notes.dueDate],
            priority = row[Notes.priority],
            completed = row[Notes.completed]
        )
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}

// Extension function to convert NoteEntity to Note (shared model)
fun NoteEntity.toNote(): Note {
    return Note(
        id = this.id,
        text = this.text,
        createdAt = this.createdAt.toInstant(ZoneOffset.UTC).toString(),
        dueDate = this.dueDate?.toInstant(ZoneOffset.UTC)?.toString(),
        priority = NotePriority.valueOf(this.priority),
        completed = this.completed
    )
}
