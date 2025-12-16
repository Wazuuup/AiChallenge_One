package ru.sber.cb.aichallenge_one.service

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.OpenRouterFunction
import ru.sber.cb.aichallenge_one.client.OpenRouterTool

/**
 * Service for converting between MCP tools and OpenRouter tool format
 */
class ToolAdapterService {
    private val logger = LoggerFactory.getLogger(ToolAdapterService::class.java)
    private val json = Json { prettyPrint = false }

    /**
     * Convert MCP Tool to OpenRouter Tool format
     */
    fun convertMcpToolToOpenRouter(mcpTool: Tool): OpenRouterTool {
        logger.debug("Converting MCP tool '${mcpTool.name}' to OpenRouter format")

        return OpenRouterTool(
            type = "function",
            function = OpenRouterFunction(
                name = mcpTool.name,
                description = mcpTool.description ?: "No description provided",
                parameters = convertMcpSchemaToJsonElement(mcpTool.inputSchema)
            )
        )
    }

    /**
     * Convert list of MCP tools to OpenRouter tools
     */
    fun convertMcpToolsToOpenRouter(mcpTools: List<Tool>): List<OpenRouterTool> {
        logger.info("Converting ${mcpTools.size} MCP tools to OpenRouter format")
        return mcpTools.map { convertMcpToolToOpenRouter(it) }
    }

    /**
     * Convert MCP ToolSchema to JsonElement
     * MCP uses JSON Schema format, which is compatible with OpenRouter
     */
    private fun convertMcpSchemaToJsonElement(schema: Any?): JsonElement {
        return when (schema) {
            is JsonElement -> schema
            else -> {
                // If schema is already a Map or other structure, convert it to JSON
                try {
                    json.parseToJsonElement(Json.encodeToString(JsonElement.serializer(), schema as JsonElement))
                } catch (e: Exception) {
                    logger.warn("Failed to convert schema, using default: ${e.message}")
                    // Default empty object schema
                    buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {})
                    }
                }
            }
        }
    }

    /**
     * Parse tool call arguments from JSON string to Map
     */
    fun parseToolArguments(argumentsJson: String): Map<String, Any?> {
        return try {
            val jsonElement = json.parseToJsonElement(argumentsJson)
            jsonElementToMap(jsonElement)
        } catch (e: Exception) {
            logger.error("Failed to parse tool arguments: ${e.message}")
            emptyMap<String, Any?>()
        } as Map<String, Any?>
    }

    /**
     * Convert JsonElement to Map recursively
     */
    private fun jsonElementToMap(element: JsonElement): Any? {
        return when (element) {
            is JsonObject -> element.mapValues { jsonElementToMap(it.value) }
            is JsonArray -> element.map { jsonElementToMap(it) }
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }

            JsonNull -> null
        }
    }
}
