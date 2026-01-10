package ru.sber.cb.aichallenge_one.service.mcp.impl

import ru.sber.cb.aichallenge_one.service.mcp.IMcpClientService

class RAGMcpClientService(
    private val ragMcpServerUrl: String = "http://localhost:8092",
    private val ragMcpClientName: String = "rag-mcp-client",
    private val ragMcpClientVersion: String = "1.0.0",
) : IMcpClientService, AbstractMcpClientService(
    mcpServerUrl = ragMcpServerUrl,
    mcpClientName = ragMcpClientName,
    mcpClientVersion = ragMcpClientVersion
)