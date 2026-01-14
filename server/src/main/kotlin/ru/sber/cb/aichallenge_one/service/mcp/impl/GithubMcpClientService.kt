package ru.sber.cb.aichallenge_one.service.mcp.impl

import ru.sber.cb.aichallenge_one.service.mcp.IMcpClientService

class GithubMcpClientService(
    private val mcpServerUrl: String = "http://localhost:8095",
    private val mcpClientName: String = "github-mcp-client",
    private val mcpClientVersion: String = "1.0.0",
) : IMcpClientService, AbstractMcpClientService(
    mcpServerUrl = mcpServerUrl,
    mcpClientName = mcpClientName,
    mcpClientVersion = mcpClientVersion
)