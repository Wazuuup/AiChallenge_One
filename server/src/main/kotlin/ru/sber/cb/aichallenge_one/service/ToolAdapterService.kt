package ru.sber.cb.aichallenge_one.service

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.OpenRouterFunction
import ru.sber.cb.aichallenge_one.client.OpenRouterTool

/**
 * Service for converting between MCP tools and OpenRouter tool format
 */
class ToolAdapterService {
    private val logger = LoggerFactory.getLogger(ToolAdapterService::class.java)
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        isLenient = true
    }

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
            is JsonElement -> {
                logger.debug("Schema is already JsonElement")
                schema
            }

            null -> {
                logger.debug("Schema is null, using default empty object schema")
                buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {})
                }
            }

            is String -> {
                logger.debug("Schema is a String, parsing as JSON")
                try {
                    json.parseToJsonElement(schema)
                } catch (e: Exception) {
                    logger.warn("Failed to parse schema string as JSON: ${e.message}")
                    buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {})
                    }
                }
            }

            is ToolSchema -> {
                logger.debug("Schema is ToolSchema, converting to JsonElement")
                try {
                    Json.parseToJsonElement(Json.encodeToString(schema.properties))
                } catch (e: Exception) {
                    logger.error("Failed to convert ToolSchema: ${e.message}", e)
                    buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {})
                    }
                }
            }
            else -> {
                // Convert schema object to JsonElement
                // ToolSchema from MCP SDK is a Map-like structure
                try {
                    logger.debug("Converting schema of type ${schema::class.qualifiedName} to JsonElement")

                    // Try multiple approaches to convert to JsonElement

                    // Approach 1: Try to use Json.encodeToJsonElement if schema is serializable
                    try {
                        // MCP SDK types are typically Map<String, Any?> under the hood
                        @Suppress("UNCHECKED_CAST")
                        val schemaMap = schema as? Map<String, Any?>
                        if (schemaMap != null) {
                            return convertMapToJsonElement(schemaMap)
                        }
                    } catch (e: ClassCastException) {
                        logger.debug("Schema is not a Map, trying JSON string conversion")
                    }

                    // Approach 2: Try toString() and parse as JSON
                    val jsonString = schema.toString()
                    try {
                        json.parseToJsonElement(jsonString)
                    } catch (parseError: Exception) {
                        logger.warn("ToolSchema.toString() produced invalid JSON: $jsonString")

                        // Approach 3: Fallback to default empty schema
                        buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {})
                            put("additionalProperties", JsonPrimitive(true))
                        }
                    }

                } catch (e: Exception) {
                    logger.error("Failed to convert schema to JsonElement: ${e.message}", e)
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
     * Convert a Map to JsonElement recursively
     */
    private fun convertMapToJsonElement(map: Map<String, Any?>): JsonElement {
        return buildJsonObject {
            map.forEach { (key, value) ->
                put(key, convertValueToJsonElement(value))
            }
        }
    }

    /**
     * Convert any value to JsonElement
     */
    private fun convertValueToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                convertMapToJsonElement(value as Map<String, Any?>)
            }

            is List<*> -> buildJsonArray {
                value.forEach { add(convertValueToJsonElement(it)) }
            }

            else -> JsonPrimitive(value.toString())
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
