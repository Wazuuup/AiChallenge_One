package ru.sber.cb.aichallenge_one.service.mcp.impl

import ru.sber.cb.aichallenge_one.service.mcp.IMcpClientService

class TicketsMcpClientService(
    private val ticketsMcpServerUrl: String = "http://localhost:8096",
    private val ticketsMcpClientName: String = "tickets-mcp-client",
    private val ticketsMcpClientVersion: String = "1.0.0",
) : IMcpClientService, AbstractMcpClientService(
    mcpServerUrl = ticketsMcpServerUrl,
    mcpClientName = ticketsMcpClientName,
    mcpClientVersion = ticketsMcpClientVersion
)
