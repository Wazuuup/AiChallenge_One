package ru.sber.cb.aichallenge_one.service.mcp.impl

import ru.sber.cb.aichallenge_one.service.mcp.IMcpClientService

class NotesMcpClientService(
    private val mcpServerUrl: String = "http://localhost:8082",
    private val mcpClientName: String = "notes-mcp-client",
    private val mcpClientVersion: String = "1.0.0",
) : IMcpClientService, AbstractMcpClientService(
    mcpServerUrl = mcpServerUrl,
    mcpClientName = mcpClientName,
    mcpClientVersion = mcpClientVersion
)