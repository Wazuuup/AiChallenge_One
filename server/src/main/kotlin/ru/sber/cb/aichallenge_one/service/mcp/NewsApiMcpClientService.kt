package ru.sber.cb.aichallenge_one.service.mcp

class NewsApiMcpClientService(
    private val notesMcpServerUrl: String = "http://localhost:8085",
    private val notesMcpClientName: String = "newapi-mcp-client",
    private val notesMcpClientVersion: String = "1.0.0",
) : IMcpClientService, AbstractMcpClientService(
    mcpServerUrl = notesMcpServerUrl,
    mcpClientName = notesMcpClientName,
    mcpClientVersion = notesMcpClientVersion
)