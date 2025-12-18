package ru.sber.cb.aichallenge_one.service.mcp

class NotesMcpClientService(
    private val notesMcpServerUrl: String = "http://localhost:8082",
    private val notesMcpClientName: String = "server-mcp-client",
    private val notesMcpClientVersion: String = "1.0.0",
) : IMcpClientService, AbstractMcpClientService(
    mcpServerUrl = notesMcpServerUrl,
    mcpClientName = notesMcpClientName,
    mcpClientVersion = notesMcpClientVersion
)