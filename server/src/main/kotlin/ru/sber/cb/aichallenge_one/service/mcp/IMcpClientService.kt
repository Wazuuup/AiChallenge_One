package ru.sber.cb.aichallenge_one.service.mcp

import io.modelcontextprotocol.kotlin.sdk.types.Tool

interface IMcpClientService {
    suspend fun connect()

    suspend fun listTools(): List<Tool>

    suspend fun callTool(name: String, arguments: Map<String, Any?>): String

    suspend fun disconnect()
}