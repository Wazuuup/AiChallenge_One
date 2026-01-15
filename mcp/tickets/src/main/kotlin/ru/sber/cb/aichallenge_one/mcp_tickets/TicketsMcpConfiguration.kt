package ru.sber.cb.aichallenge_one.mcp_tickets

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import ru.sber.cb.aichallenge_one.mcp_tickets.repository.TicketsRepository
import ru.sber.cb.aichallenge_one.models.tickets.Ticket

/**
 * Configures the MCP (Model Context Protocol) server with tickets tools.
 */
fun Application.configureTicketsMcp() {
    val repository = TicketsRepository()

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("Tickets MCP Server is running on port $TICKETS_MCP_PORT (HTTP) and $TICKETS_MCP_SSL_PORT (HTTPS)")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "tickets-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                // ========== Tool 1: create_ticket ==========
                addTool(
                    name = "create_ticket",
                    description = "Create a new support ticket",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("title") {
                                    put("type", "string")
                                    put("description", "Title of the ticket")
                                }
                                putJsonObject("description") {
                                    put("type", "string")
                                    put("description", "Detailed description of the issue")
                                }
                                putJsonObject("initiator") {
                                    put("type", "string")
                                    put("description", "Name or email of the person who created the ticket")
                                }
                                putJsonObject("priority") {
                                    put("type", "integer")
                                    put("description", "Priority level 1-5 (1=low, 5=critical). Default: 3")
                                    put("minimum", 1)
                                    put("maximum", 5)
                                    put("default", 3)
                                }
                            }
                            putJsonArray("required") {
                                add("title")
                                add("description")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val title = arguments.arguments?.get("title")?.jsonPrimitive?.content
                    val description = arguments.arguments?.get("description")?.jsonPrimitive?.content
                    val initiator = arguments.arguments?.get("initiator")?.jsonPrimitive?.contentOrNull
                    val priority = arguments.arguments?.get("priority")?.jsonPrimitive?.intOrNull ?: 3

                    runBlocking {
                        if (title.isNullOrBlank()) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Title is required")),
                                isError = true
                            )
                        }
                        if (description.isNullOrBlank()) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Description is required")),
                                isError = true
                            )
                        }

                        val ticket = repository.create(title, description, initiator, priority)
                        if (ticket != null) {
                            CallToolResult(content = listOf(TextContent(formatTicket(ticket))))
                        } else {
                            CallToolResult(
                                content = listOf(TextContent("Error: Failed to create ticket")),
                                isError = true
                            )
                        }
                    }
                }

                // ========== Tool 2: get_ticket ==========
                addTool(
                    name = "get_ticket",
                    description = "Get a ticket by its ID",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("id") {
                                    put("type", "integer")
                                    put("description", "The ticket ID")
                                }
                            }
                            putJsonArray("required") {
                                add("id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val id = arguments.arguments?.get("id")?.jsonPrimitive?.intOrNull

                    runBlocking {
                        if (id == null) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Ticket ID is required")),
                                isError = true
                            )
                        }

                        val ticket = repository.findById(id)
                        if (ticket != null) {
                            CallToolResult(content = listOf(TextContent(formatTicket(ticket))))
                        } else {
                            CallToolResult(
                                content = listOf(TextContent("Error: Ticket with ID $id not found")),
                                isError = true
                            )
                        }
                    }
                }

                // ========== Tool 3: update_ticket ==========
                addTool(
                    name = "update_ticket",
                    description = "Update an existing ticket. Only provide fields you want to change.",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("id") {
                                    put("type", "integer")
                                    put("description", "The ticket ID to update")
                                }
                                putJsonObject("title") {
                                    put("type", "string")
                                    put("description", "New title for the ticket")
                                }
                                putJsonObject("description") {
                                    put("type", "string")
                                    put("description", "New description for the ticket")
                                }
                                putJsonObject("initiator") {
                                    put("type", "string")
                                    put("description", "New initiator name/email")
                                }
                                putJsonObject("priority") {
                                    put("type", "integer")
                                    put("description", "New priority level 1-5")
                                    put("minimum", 1)
                                    put("maximum", 5)
                                }
                                putJsonObject("status") {
                                    put("type", "string")
                                    put("description", "New status: 'open' or 'closed'")
                                    putJsonArray("enum") {
                                        add("open")
                                        add("closed")
                                    }
                                }
                            }
                            putJsonArray("required") {
                                add("id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val id = arguments.arguments?.get("id")?.jsonPrimitive?.intOrNull
                    val title = arguments.arguments?.get("title")?.jsonPrimitive?.contentOrNull
                    val description = arguments.arguments?.get("description")?.jsonPrimitive?.contentOrNull
                    val initiator = arguments.arguments?.get("initiator")?.jsonPrimitive?.contentOrNull
                    val priority = arguments.arguments?.get("priority")?.jsonPrimitive?.intOrNull
                    val status = arguments.arguments?.get("status")?.jsonPrimitive?.contentOrNull

                    runBlocking {
                        if (id == null) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Ticket ID is required")),
                                isError = true
                            )
                        }

                        val ticket = repository.update(id, title, description, initiator, priority, status)
                        if (ticket != null) {
                            CallToolResult(
                                content = listOf(
                                    TextContent(
                                        "Ticket updated successfully:\n${
                                            formatTicket(
                                                ticket
                                            )
                                        }"
                                    )
                                )
                            )
                        } else {
                            CallToolResult(
                                content = listOf(TextContent("Error: Failed to update ticket with ID $id (not found or update failed)")),
                                isError = true
                            )
                        }
                    }
                }

                // ========== Tool 4: list_tickets ==========
                addTool(
                    name = "list_tickets",
                    description = "List all tickets (returns up to 50 tickets, ordered by creation date descending)",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {}
                            put("additionalProperties", false)
                        }
                    )
                ) { _: CallToolRequest ->
                    runBlocking {
                        val tickets = repository.findAll()
                        if (tickets.isEmpty()) {
                            CallToolResult(content = listOf(TextContent("No tickets found")))
                        } else {
                            CallToolResult(content = listOf(TextContent(formatTicketList(tickets))))
                        }
                    }
                }

                // ========== Tool 5: filter_by_initiator ==========
                addTool(
                    name = "filter_by_initiator",
                    description = "Find all tickets created by a specific initiator",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("initiator") {
                                    put("type", "string")
                                    put("description", "The initiator name or email to filter by (exact match)")
                                }
                            }
                            putJsonArray("required") {
                                add("initiator")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val initiator = arguments.arguments?.get("initiator")?.jsonPrimitive?.content

                    runBlocking {
                        if (initiator.isNullOrBlank()) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Initiator is required")),
                                isError = true
                            )
                        }

                        val tickets = repository.findByInitiator(initiator)
                        if (tickets.isEmpty()) {
                            CallToolResult(content = listOf(TextContent("No tickets found for initiator: $initiator")))
                        } else {
                            CallToolResult(
                                content = listOf(
                                    TextContent(
                                        "Tickets by initiator '$initiator':\n${
                                            formatTicketList(
                                                tickets
                                            )
                                        }"
                                    )
                                )
                            )
                        }
                    }
                }

                // ========== Tool 6: filter_by_title ==========
                addTool(
                    name = "filter_by_title",
                    description = "Search tickets by title (case-insensitive partial match)",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("title") {
                                    put("type", "string")
                                    put("description", "The title text to search for")
                                }
                            }
                            putJsonArray("required") {
                                add("title")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val title = arguments.arguments?.get("title")?.jsonPrimitive?.content

                    runBlocking {
                        if (title.isNullOrBlank()) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Title search text is required")),
                                isError = true
                            )
                        }

                        val tickets = repository.findByTitleLike(title)
                        if (tickets.isEmpty()) {
                            CallToolResult(content = listOf(TextContent("No tickets found with title containing: $title")))
                        } else {
                            CallToolResult(
                                content = listOf(
                                    TextContent(
                                        "Tickets matching title '$title':\n${
                                            formatTicketList(
                                                tickets
                                            )
                                        }"
                                    )
                                )
                            )
                        }
                    }
                }

                // ========== Tool 7: filter_by_priority ==========
                addTool(
                    name = "filter_by_priority",
                    description = "Filter tickets by priority level",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("priority") {
                                    put("type", "integer")
                                    put("description", "Priority level to filter by (1-5)")
                                    put("minimum", 1)
                                    put("maximum", 5)
                                }
                                putJsonObject("operator") {
                                    put("type", "string")
                                    put(
                                        "description",
                                        "Comparison operator: '=' (exact), '>=' (at least), '<=' (at most). Default: '='"
                                    )
                                    putJsonArray("enum") {
                                        add("=")
                                        add(">=")
                                        add("<=")
                                    }
                                    put("default", "=")
                                }
                            }
                            putJsonArray("required") {
                                add("priority")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val priority = arguments.arguments?.get("priority")?.jsonPrimitive?.intOrNull
                    val operator = arguments.arguments?.get("operator")?.jsonPrimitive?.contentOrNull ?: "="

                    runBlocking {
                        if (priority == null || priority !in 1..5) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Priority must be an integer between 1 and 5")),
                                isError = true
                            )
                        }

                        val tickets = repository.findByPriority(priority, operator)
                        if (tickets.isEmpty()) {
                            CallToolResult(content = listOf(TextContent("No tickets found with priority $operator $priority")))
                        } else {
                            CallToolResult(
                                content = listOf(
                                    TextContent(
                                        "Tickets with priority $operator $priority:\n${
                                            formatTicketList(
                                                tickets
                                            )
                                        }"
                                    )
                                )
                            )
                        }
                    }
                }

                // ========== Tool 8: filter_by_status ==========
                addTool(
                    name = "filter_by_status",
                    description = "Filter tickets by status (open or closed)",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("status") {
                                    put("type", "string")
                                    put("description", "Status to filter by")
                                    putJsonArray("enum") {
                                        add("open")
                                        add("closed")
                                    }
                                }
                            }
                            putJsonArray("required") {
                                add("status")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val status = arguments.arguments?.get("status")?.jsonPrimitive?.content

                    runBlocking {
                        if (status.isNullOrBlank() || status !in listOf("open", "closed")) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Status must be 'open' or 'closed'")),
                                isError = true
                            )
                        }

                        val tickets = repository.findByStatus(status)
                        if (tickets.isEmpty()) {
                            CallToolResult(content = listOf(TextContent("No $status tickets found")))
                        } else {
                            CallToolResult(
                                content = listOf(
                                    TextContent(
                                        "${status.replaceFirstChar { it.uppercase() }} tickets:\n${
                                            formatTicketList(
                                                tickets
                                            )
                                        }"
                                    )
                                )
                            )
                        }
                    }
                }

                // ========== Tool 9: search_description ==========
                addTool(
                    name = "search_description",
                    description = "Search tickets by description content (case-insensitive full-text search)",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("query") {
                                    put("type", "string")
                                    put("description", "Text to search for in ticket descriptions")
                                }
                            }
                            putJsonArray("required") {
                                add("query")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val query = arguments.arguments?.get("query")?.jsonPrimitive?.content

                    runBlocking {
                        if (query.isNullOrBlank()) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Search query is required")),
                                isError = true
                            )
                        }

                        val tickets = repository.searchDescription(query)
                        if (tickets.isEmpty()) {
                            CallToolResult(content = listOf(TextContent("No tickets found with description containing: $query")))
                        } else {
                            CallToolResult(
                                content = listOf(
                                    TextContent(
                                        "Tickets matching description '$query':\n${
                                            formatTicketList(
                                                tickets
                                            )
                                        }"
                                    )
                                )
                            )
                        }
                    }
                }

                // ========== Tool 10: close_ticket ==========
                addTool(
                    name = "close_ticket",
                    description = "Close a ticket (shortcut for updating status to 'closed')",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("id") {
                                    put("type", "integer")
                                    put("description", "The ticket ID to close")
                                }
                            }
                            putJsonArray("required") {
                                add("id")
                            }
                            put("additionalProperties", false)
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val id = arguments.arguments?.get("id")?.jsonPrimitive?.intOrNull

                    runBlocking {
                        if (id == null) {
                            return@runBlocking CallToolResult(
                                content = listOf(TextContent("Error: Ticket ID is required")),
                                isError = true
                            )
                        }

                        val ticket = repository.close(id)
                        if (ticket != null) {
                            CallToolResult(
                                content = listOf(
                                    TextContent(
                                        "Ticket #$id closed successfully:\n${
                                            formatTicket(
                                                ticket
                                            )
                                        }"
                                    )
                                )
                            )
                        } else {
                            CallToolResult(
                                content = listOf(TextContent("Error: Failed to close ticket with ID $id (not found or already closed)")),
                                isError = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format a single ticket for display
 */
private fun formatTicket(ticket: Ticket): String {
    return buildString {
        appendLine("Ticket #${ticket.id}")
        appendLine("  Title: ${ticket.title}")
        appendLine("  Description: ${ticket.description}")
        appendLine("  Initiator: ${ticket.initiator ?: "N/A"}")
        appendLine("  Priority: ${ticket.priority} (${priorityLabel(ticket.priority)})")
        appendLine("  Status: ${ticket.status}")
        appendLine("  Created: ${ticket.createdAt}")
        appendLine("  Updated: ${ticket.updatedAt}")
    }
}

/**
 * Format a list of tickets for display
 */
private fun formatTicketList(tickets: List<Ticket>): String {
    return buildString {
        appendLine("Found ${tickets.size} ticket(s):")
        appendLine()
        tickets.forEach { ticket ->
            appendLine("--- Ticket #${ticket.id} ---")
            appendLine("  Title: ${ticket.title}")
            appendLine("  Status: ${ticket.status} | Priority: ${ticket.priority} (${priorityLabel(ticket.priority)})")
            appendLine("  Initiator: ${ticket.initiator ?: "N/A"}")
            appendLine("  Description: ${ticket.description.take(100)}${if (ticket.description.length > 100) "..." else ""}")
            appendLine()
        }
    }
}

/**
 * Get human-readable priority label
 */
private fun priorityLabel(priority: Int): String = when (priority) {
    1 -> "low"
    2 -> "below normal"
    3 -> "normal"
    4 -> "high"
    5 -> "critical"
    else -> "unknown"
}
