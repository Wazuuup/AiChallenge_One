package ru.sber.cb.aichallenge_one.notes.routing

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import ru.sber.cb.aichallenge_one.models.notes.CreateNoteRequest
import ru.sber.cb.aichallenge_one.models.notes.UpdateNoteRequest
import ru.sber.cb.aichallenge_one.notes.service.NotesService

fun Route.notesRouting() {
    val notesService by inject<NotesService>()

    route("/api/notes") {
        // CREATE: POST /api/notes
        post {
            try {
                val request = call.receive<CreateNoteRequest>()
                val response = notesService.createNote(request)

                if (response.success) {
                    call.respond(HttpStatusCode.Created, response)
                } else {
                    call.respond(HttpStatusCode.BadRequest, response)
                }
            } catch (e: Exception) {
                call.application.environment.log.error("Error creating note", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Internal server error", "success" to false)
                )
            }
        }

        // READ ALL: GET /api/notes
        get {
            try {
                val notes = notesService.getAllNotes()
                call.respond(HttpStatusCode.OK, notes)
            } catch (e: Exception) {
                call.application.environment.log.error("Error fetching notes", e)
                call.respond(HttpStatusCode.InternalServerError, emptyList<String>())
            }
        }

        // READ ONE: GET /api/notes/{id}
        get("/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Invalid note ID", "success" to false)
                    )
                    return@get
                }

                val response = notesService.getNoteById(id)
                if (response.success) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.NotFound, response)
                }
            } catch (e: Exception) {
                call.application.environment.log.error("Error fetching note", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Internal server error", "success" to false)
                )
            }
        }

        // UPDATE: PUT /api/notes/{id}
        put("/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Invalid note ID", "success" to false)
                    )
                    return@put
                }

                val request = call.receive<UpdateNoteRequest>()
                val response = notesService.updateNote(id, request)

                if (response.success) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.NotFound, response)
                }
            } catch (e: Exception) {
                call.application.environment.log.error("Error updating note", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Internal server error", "success" to false)
                )
            }
        }

        // DELETE: DELETE /api/notes/{id}
        delete("/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "Invalid note ID", "success" to false)
                    )
                    return@delete
                }

                val response = notesService.deleteNote(id)
                if (response.success) {
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    call.respond(HttpStatusCode.NotFound, response)
                }
            } catch (e: Exception) {
                call.application.environment.log.error("Error deleting note", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Internal server error", "success" to false)
                )
            }
        }
    }
}
