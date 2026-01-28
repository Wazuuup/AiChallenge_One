package ru.sber.cb.aichallenge_one.mcp_server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import ru.sber.cb.aichallenge_one.mcp_server.service.CurrencyExchangeService
import ru.sber.cb.aichallenge_one.mcp_server.service.NotesApiService
import ru.sber.cb.aichallenge_one.models.notes.CreateNoteRequest
import ru.sber.cb.aichallenge_one.models.notes.NotePriority
import ru.sber.cb.aichallenge_one.models.notes.UpdateNoteRequest

/**
 * Configures the MCP (Model Context Protocol) server with tools.
 */
fun Application.configureMcpServer() {
    val currencyService = CurrencyExchangeService()
    val notesService = NotesApiService()

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("MCP Server is running on port $MCP_SERVER_PORT")
        }

        // Trigger notes summary to UI
        post("/trigger-summary") {
            val notes = notesService.getAllNotes()
            val summary = buildString {
                appendLine("=== Notes Summary ===")
                appendLine("Total notes: ${notes.size}")
                appendLine()
                if (notes.isEmpty()) {
                    appendLine("No notes available")
                } else {
                    val completedCount = notes.count { it.completed }
                    val pendingCount = notes.size - completedCount
                    val highPriorityCount = notes.count { it.priority == NotePriority.HIGH }

                    appendLine("Status:")
                    appendLine("  - Completed: $completedCount")
                    appendLine("  - Pending: $pendingCount")
                    appendLine("  - High Priority: $highPriorityCount")
                    appendLine()
                    appendLine("Recent notes:")
                    notes.take(5).forEach { note ->
                        appendLine("  - [${note.priority}] ${note.text.take(50)}${if (note.text.length > 50) "..." else ""}")
                    }
                }
                appendLine("====================")
            }
            call.respondText(summary, ContentType.Text.Plain)
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "test-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                addTool(
                    name = "get_exchange_rate",
                    description = "Gets current exchange rate for a foreign currency to Russian Ruble from CBR (Central Bank of Russia)",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("currency_code") {
                                    put("type", "string")
                                    put("description", "Three-letter currency code (e.g., USD, EUR, CNY)")
                                }
                            }
                            putJsonArray("required") {
                                add("currency_code")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val currencyCode = arguments.arguments
                        ?.get("currency_code")
                        ?.jsonPrimitive
                        ?.content
                        ?: ""

                    runBlocking {
                        val currencyInfo = currencyService.getExchangeRate(currencyCode)

                        if (currencyInfo != null) {
                            val resultText = buildString {
                                appendLine("Currency: ${currencyInfo.Name}")
                                appendLine("Code: ${currencyInfo.CharCode}")
                                appendLine("Nominal: ${currencyInfo.Nominal}")
                                appendLine("Current Rate: ${currencyInfo.Value} RUB")
                                appendLine("Previous Rate: ${currencyInfo.Previous} RUB")
                                appendLine(
                                    "Change: ${
                                        String.format(
                                            "%.4f",
                                            currencyInfo.Value - currencyInfo.Previous
                                        )
                                    } RUB"
                                )
                                appendLine("ID: ${currencyInfo.ID}")
                                appendLine("Numeric Code: ${currencyInfo.NumCode}")
                            }

                            CallToolResult(
                                content = listOf(TextContent(resultText))
                            )
                        } else {
                            CallToolResult(
                                content = listOf(TextContent("Currency with code '$currencyCode' not found or error fetching data from CBR API")),
                                isError = true
                            )
                        }
                    }
                }

                // ========== Notes Tools ==========

                // Tool 1: create_note
                addTool(
                    name = "create_note",
                    description = "Creates a new note with text, optional due date (ISO-8601 format), and priority (LOW, MEDIUM, HIGH)",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("text") {
                                    put("type", "string")
                                    put("description", "The note text content")
                                }
                                putJsonObject("due_date") {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Optional due date in ISO-8601 format (e.g., 2025-12-31 or 2025-12-31T23:59:59)"
                                    )
                                }
                                putJsonObject("priority") {
                                    put("type", "string")
                                    put("enum", buildJsonArray {
                                        add("LOW")
                                        add("MEDIUM")
                                        add("HIGH")
                                    })
                                    put("description", "Priority level (default: MEDIUM)")
                                }
                            }
                            putJsonArray("required") {
                                add("text")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val text = arguments.arguments
                        ?.get("text")?.jsonPrimitive?.content ?: ""
                    val dueDate = arguments.arguments
                        ?.get("due_date")?.jsonPrimitive?.contentOrNull
                    val priorityStr = arguments.arguments
                        ?.get("priority")?.jsonPrimitive?.contentOrNull

                    runBlocking {
                        // Validation
                        if (text.isBlank()) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Note text cannot be empty")),
                                isError = true
                            )
                        }

                        val priority = try {
                            if (priorityStr != null) {
                                NotePriority.valueOf(priorityStr.uppercase())
                            } else {
                                NotePriority.MEDIUM
                            }
                        } catch (e: IllegalArgumentException) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Invalid priority. Must be LOW, MEDIUM, or HIGH")),
                                isError = true
                            )
                        }

                        val request = CreateNoteRequest(
                            text = text,
                            dueDate = dueDate,
                            priority = priority
                        )

                        val response = notesService.createNote(request)

                        if (response.success && response.note != null) {
                            val note = response.note!!
                            val resultText = buildString {
                                appendLine("Note created successfully")
                                appendLine("ID: ${note.id}")
                                appendLine("Text: ${note.text}")
                                appendLine("Priority: ${note.priority}")
                                appendLine("Completed: ${if (note.completed) "Yes" else "No"}")
                                note.dueDate?.let { appendLine("Due Date: $it") }
                                appendLine("Created At: ${note.createdAt}")
                            }
                            CallToolResult(content = listOf(TextContent(resultText)))
                        } else {
                            CallToolResult(
                                content = listOf(TextContent("Failed to create note: ${response.message ?: "Unknown error"}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 2: get_all_notes
                addTool(
                    name = "get_all_notes",
                    description = "Retrieves all notes from the database",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {}
                            put("additionalProperties", false)
                        }
                    )
                ) { _: CallToolRequest ->
                    runBlocking {
                        val notes = notesService.getAllNotes()

                        if (notes.isEmpty()) {
                            CallToolResult(
                                content = listOf(TextContent("No notes found"))
                            )
                        } else {
                            val resultText = buildString {
                                appendLine("Found ${notes.size} note(s):")
                                appendLine()
                                notes.forEach { note ->
                                    appendLine("---------------------")
                                    appendLine("ID: ${note.id}")
                                    appendLine("Text: ${note.text}")
                                    appendLine("Priority: ${note.priority}")
                                    appendLine("Completed: ${if (note.completed) "Yes" else "No"}")
                                    note.dueDate?.let { appendLine("Due Date: $it") }
                                    appendLine("Created: ${note.createdAt}")
                                }
                                appendLine("---------------------")
                            }
                            CallToolResult(content = listOf(TextContent(resultText)))
                        }
                    }
                }

                // Tool 3: get_note
                addTool(
                    name = "get_note",
                    description = "Retrieves a specific note by its ID",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("note_id") {
                                    put("type", "integer")
                                    put("description", "The ID of the note to retrieve")
                                }
                            }
                            putJsonArray("required") {
                                add("note_id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val noteId = arguments.arguments
                        ?.get("note_id")?.jsonPrimitive?.intOrNull

                    runBlocking {
                        if (noteId == null || noteId <= 0) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Invalid note ID. Must be a positive integer")),
                                isError = true
                            )
                        }

                        val response = notesService.getNoteById(noteId)

                        if (response.success && response.note != null) {
                            val note = response.note!!
                            val resultText = buildString {
                                appendLine("Note Details:")
                                appendLine("ID: ${note.id}")
                                appendLine("Text: ${note.text}")
                                appendLine("Priority: ${note.priority}")
                                appendLine("Completed: ${if (note.completed) "Yes" else "No"}")
                                note.dueDate?.let { appendLine("Due Date: $it") }
                                appendLine("Created At: ${note.createdAt}")
                            }
                            CallToolResult(content = listOf(TextContent(resultText)))
                        } else {
                            CallToolResult(
                                content = listOf(TextContent("Note not found: ${response.message ?: "Note with ID $noteId does not exist"}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 4: update_note
                addTool(
                    name = "update_note",
                    description = "Updates an existing note. All fields except note_id are optional. Use clear_due_date=true to remove the due date.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("note_id") {
                                    put("type", "integer")
                                    put("description", "The ID of the note to update")
                                }
                                putJsonObject("text") {
                                    put("type", "string")
                                    put("description", "Updated note text")
                                }
                                putJsonObject("due_date") {
                                    put("type", "string")
                                    put("description", "Updated due date in ISO-8601 format")
                                }
                                putJsonObject("clear_due_date") {
                                    put("type", "boolean")
                                    put("description", "Set to true to clear/remove the due date")
                                }
                                putJsonObject("priority") {
                                    put("type", "string")
                                    put("enum", buildJsonArray {
                                        add("LOW")
                                        add("MEDIUM")
                                        add("HIGH")
                                    })
                                    put("description", "Updated priority level")
                                }
                                putJsonObject("completed") {
                                    put("type", "boolean")
                                    put("description", "Mark note as completed or not completed")
                                }
                            }
                            putJsonArray("required") {
                                add("note_id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val noteId = arguments.arguments
                        ?.get("note_id")?.jsonPrimitive?.intOrNull
                    val text = arguments.arguments
                        ?.get("text")?.jsonPrimitive?.contentOrNull
                    val dueDate = arguments.arguments
                        ?.get("due_date")?.jsonPrimitive?.contentOrNull
                    val clearDueDate = arguments.arguments
                        ?.get("clear_due_date")?.jsonPrimitive?.booleanOrNull ?: false
                    val priorityStr = arguments.arguments
                        ?.get("priority")?.jsonPrimitive?.contentOrNull
                    val completed = arguments.arguments
                        ?.get("completed")?.jsonPrimitive?.booleanOrNull

                    runBlocking {
                        if (noteId == null || noteId <= 0) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Invalid note ID. Must be a positive integer")),
                                isError = true
                            )
                        }

                        if (text != null && text.isBlank()) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Note text cannot be empty")),
                                isError = true
                            )
                        }

                        val priority = if (priorityStr != null) {
                            try {
                                NotePriority.valueOf(priorityStr.uppercase())
                            } catch (e: IllegalArgumentException) {
                                return@runBlocking CallToolResult(
                                    content = listOf(TextContent("Error: Invalid priority. Must be LOW, MEDIUM, or HIGH")),
                                    isError = true
                                )
                            }
                        } else null

                        // Handle clear_due_date: if true, explicitly set to null
                        val finalDueDate = if (clearDueDate) null else dueDate

                        val request = UpdateNoteRequest(
                            text = text,
                            dueDate = finalDueDate,
                            priority = priority,
                            completed = completed
                        )

                        val response = notesService.updateNote(noteId, request)

                        if (response.success && response.note != null) {
                            val note = response.note!!
                            val resultText = buildString {
                                appendLine("Note updated successfully")
                                appendLine("ID: ${note.id}")
                                appendLine("Text: ${note.text}")
                                appendLine("Priority: ${note.priority}")
                                appendLine("Completed: ${if (note.completed) "Yes" else "No"}")
                                note.dueDate?.let { appendLine("Due Date: $it") }
                                    ?: appendLine("Due Date: (none)")
                                appendLine("Created At: ${note.createdAt}")
                            }
                            CallToolResult(content = listOf(TextContent(resultText)))
                        } else {
                            CallToolResult(
                                content = listOf(TextContent("Failed to update note: ${response.message ?: "Note not found"}")),
                                isError = true
                            )
                        }
                    }
                }

                // Tool 5: delete_note
                addTool(
                    name = "delete_note",
                    description = "Deletes a note by its ID",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("note_id") {
                                    put("type", "integer")
                                    put("description", "The ID of the note to delete")
                                }
                            }
                            putJsonArray("required") {
                                add("note_id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val noteId = arguments.arguments
                        ?.get("note_id")?.jsonPrimitive?.intOrNull

                    runBlocking {
                        if (noteId == null || noteId <= 0) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Invalid note ID. Must be a positive integer")),
                                isError = true
                            )
                        }

                        val response = notesService.deleteNote(noteId)

                        if (response.success) {
                            CallToolResult(
                                content = listOf(TextContent("Note deleted successfully (ID: $noteId)"))
                            )
                        } else {
                            CallToolResult(
                                content = listOf(TextContent("Failed to delete note: ${response.message ?: "Note not found"}")),
                                isError = true
                            )
                        }
                    }
                }
            }
        }
    }
}
