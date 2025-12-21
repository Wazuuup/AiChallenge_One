package ru.sber.cb.aichallenge_one.service.mcp.impl

import ru.sber.cb.aichallenge_one.service.mcp.IMcpClientService

class NotesPollingMcpClientService(
    private val notesMcpServerUrl: String = "http://localhost:8089",
    private val notesMcpClientName: String = "notes-polling-mcp-client",
    private val notesMcpClientVersion: String = "1.0.0",
) : IMcpClientService, AbstractMcpClientService(
    mcpServerUrl = notesMcpServerUrl,
    mcpClientName = notesMcpClientName,
    mcpClientVersion = notesMcpClientVersion
)