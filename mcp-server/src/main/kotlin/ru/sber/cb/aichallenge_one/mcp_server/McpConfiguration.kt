package ru.sber.cb.aichallenge_one.mcp_server

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

/**
 * Configures the MCP (Model Context Protocol) server with tools.
 */
fun Application.configureMcpServer() {
    val currencyService = CurrencyExchangeService()

    routing {
        // Health check endpoint
        get("/health") {
            call.respondText("MCP Server is running on port $MCP_SERVER_PORT")
        }

        // MCP endpoint
        mcp {
            Server(
                serverInfo = Implementation(
                    name = "reverse-string-mcp-server",
                    version = "1.0.0"
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools()
                    )
                )
            ).apply {
                addTool(
                    name = "reverse",
                    description = "Reverses the input string",
                    inputSchema = ToolSchema(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("text") {
                                    put("type", "string")
                                }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("text"))
                            }
                        }
                    )
                ) { arguments: CallToolRequest ->
                    val text = arguments.arguments
                        ?.get("text")
                        ?.jsonPrimitive
                        ?.content
                        ?: ""

                    CallToolResult(
                        content = listOf(TextContent(text.reversed()))
                    )
                }

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
                                add(JsonPrimitive("currency_code"))
                            }
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
            }
        }
    }
}
