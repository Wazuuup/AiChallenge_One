package ru.sber.cb.aichallenge_one.notes.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.models.notes.CreateNoteRequest
import ru.sber.cb.aichallenge_one.models.notes.Note
import ru.sber.cb.aichallenge_one.models.notes.NoteResponse
import ru.sber.cb.aichallenge_one.models.notes.UpdateNoteRequest
import ru.sber.cb.aichallenge_one.notes.repository.NoteRepository
import ru.sber.cb.aichallenge_one.notes.repository.toNote
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class NotesService(private val noteRepository: NoteRepository) {
    private val logger = LoggerFactory.getLogger(NotesService::class.java)

    suspend fun createNote(request: CreateNoteRequest): NoteResponse {
        if (request.text.isBlank()) {
            return NoteResponse(
                note = null,
                message = "Note text cannot be empty",
                success = false
            )
        }

        val dueDate = request.dueDate?.let { parseIsoDateTime(it) }

        val noteEntity = noteRepository.create(
            text = request.text,
            dueDate = dueDate,
            priority = request.priority
        )

        return if (noteEntity != null) {
            NoteResponse(
                note = noteEntity.toNote(),
                message = "Note created successfully",
                success = true
            )
        } else {
            NoteResponse(
                note = null,
                message = "Failed to create note",
                success = false
            )
        }
    }

    suspend fun getNoteById(id: Int): NoteResponse {
        val noteEntity = noteRepository.findById(id)
        return if (noteEntity != null) {
            NoteResponse(
                note = noteEntity.toNote(),
                message = null,
                success = true
            )
        } else {
            NoteResponse(
                note = null,
                message = "Note not found",
                success = false
            )
        }
    }

    suspend fun getAllNotes(): List<Note> {
        return noteRepository.findAll().map { it.toNote() }
    }

    suspend fun updateNote(id: Int, request: UpdateNoteRequest): NoteResponse {
        // Check if note exists
        val existingNote = noteRepository.findById(id)
        if (existingNote == null) {
            return NoteResponse(
                note = null,
                message = "Note not found",
                success = false
            )
        }

        // Parse dueDate if provided
        val dueDate = request.dueDate?.let { parseIsoDateTime(it) }
        val clearDueDate = request.dueDate == "" // Empty string means clear dueDate

        val success = noteRepository.update(
            id = id,
            text = request.text,
            dueDate = dueDate,
            priority = request.priority,
            completed = request.completed,
            clearDueDate = clearDueDate
        )

        return if (success) {
            val updatedNote = noteRepository.findById(id)
            NoteResponse(
                note = updatedNote?.toNote(),
                message = "Note updated successfully",
                success = true
            )
        } else {
            NoteResponse(
                note = null,
                message = "Failed to update note",
                success = false
            )
        }
    }

    suspend fun deleteNote(id: Int): NoteResponse {
        val success = noteRepository.delete(id)
        return if (success) {
            NoteResponse(
                note = null,
                message = "Note deleted successfully",
                success = true
            )
        } else {
            NoteResponse(
                note = null,
                message = "Note not found or failed to delete",
                success = false
            )
        }
    }

    private fun parseIsoDateTime(isoString: String): LocalDateTime? {
        return try {
            LocalDateTime.ofInstant(Instant.parse(isoString), ZoneOffset.UTC)
        } catch (e: Exception) {
            logger.warn("Failed to parse ISO datetime: $isoString", e)
            null
        }
    }
}
