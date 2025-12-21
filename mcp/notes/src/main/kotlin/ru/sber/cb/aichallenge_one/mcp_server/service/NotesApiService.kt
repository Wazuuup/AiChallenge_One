package ru.sber.cb.aichallenge_one.mcp_server.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.sber.cb.aichallenge_one.models.notes.CreateNoteRequest
import ru.sber.cb.aichallenge_one.models.notes.Note
import ru.sber.cb.aichallenge_one.models.notes.NoteResponse
import ru.sber.cb.aichallenge_one.models.notes.UpdateNoteRequest

/**
 * Service for interacting with the Notes module REST API
 * Notes server runs on port 8084
 */
class NotesApiService {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
    }

    private val notesApiUrl = System.getenv("NOTES_SERVICE_URL") ?: "http://localhost:8084"

    /**
     * Creates a new note
     * @return NoteResponse with success flag
     */
    suspend fun createNote(request: CreateNoteRequest): NoteResponse {
        return try {
            httpClient.post("$notesApiUrl/api/notes") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        } catch (e: Exception) {
            NoteResponse(
                note = null,
                message = "Failed to create note: ${e.message}",
                success = false
            )
        }
    }

    /**
     * Retrieves all notes
     * @return List of notes, empty list on error
     */
    suspend fun getAllNotes(): List<Note> {
        return try {
            httpClient.get("$notesApiUrl/api/notes").body()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Retrieves a specific note by ID
     * @return NoteResponse with success flag
     */
    suspend fun getNoteById(id: Int): NoteResponse {
        return try {
            httpClient.get("$notesApiUrl/api/notes/$id").body()
        } catch (e: Exception) {
            NoteResponse(
                note = null,
                message = "Failed to retrieve note: ${e.message}",
                success = false
            )
        }
    }

    /**
     * Updates an existing note
     * @return NoteResponse with success flag
     */
    suspend fun updateNote(id: Int, request: UpdateNoteRequest): NoteResponse {
        return try {
            httpClient.put("$notesApiUrl/api/notes/$id") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        } catch (e: Exception) {
            NoteResponse(
                note = null,
                message = "Failed to update note: ${e.message}",
                success = false
            )
        }
    }

    /**
     * Deletes a note by ID
     * @return NoteResponse with success flag
     */
    suspend fun deleteNote(id: Int): NoteResponse {
        return try {
            httpClient.delete("$notesApiUrl/api/notes/$id").body()
        } catch (e: Exception) {
            NoteResponse(
                note = null,
                message = "Failed to delete note: ${e.message}",
                success = false
            )
        }
    }

    /**
     * Clean up resources
     */
    fun close() {
        httpClient.close()
    }
}
