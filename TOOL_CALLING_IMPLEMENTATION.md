# Tool Calling Implementation Guide

## Overview

MCP client has been added to the server module with full OpenRouter tool calling integration.

## Components Created

### 1. MCP Client Service (`McpClientService.kt`)

- Connects to local mcp-server at `http://localhost:8082`
- Methods:
    - `connect()` - Connect to MCP server
    - `listTools()` - Get available tools
    - `callTool(name, arguments)` - Execute a tool
    - `disconnect()` - Close connection

### 2. Tool Calling Data Classes (`ToolCalling.kt`)

- `OpenRouterTool` - Tool definition in OpenRouter format
- `OpenRouterToolCall` - Tool call from assistant
- `OpenAIMessageWithTools` - Message with tool call support
- `OpenAIRequestWithTools` - Request with tools
- `OpenAIResponseWithTools` - Response with tool calls

### 3. Tool Adapter Service (`ToolAdapterService.kt`)

- `convertMcpToolToOpenRouter()` - Convert MCP tool to OpenRouter format
- `convertMcpToolsToOpenRouter()` - Bulk conversion
- `parseToolArguments()` - Parse JSON arguments to Map

### 4. Tool Execution Service (`ToolExecutionService.kt`)

- `executeToolCalls()` - Execute tool calls from response
- `handleToolCallingWorkflow()` - Complete workflow with iterations

### 5. Updated OpenAI Client

- New method: `sendMessageWithTools()` - Send message with tools to OpenRouter

## Integration with ChatService

The ChatService now accepts three new parameters:

```kotlin
class ChatService(
    // ... existing parameters
    mcpClientService: McpClientService,
    toolAdapterService: ToolAdapterService,
    toolExecutionService: ToolExecutionService?
)
```

## Usage Flow

### 1. Initialize MCP Connection

```kotlin
// In Application.kt startup
val mcpClientService = get<McpClientService>()
mcpClientService.connect()
```

### 2. Using Tool Calling

#### Example: Chat endpoint with tools

```kotlin
suspend fun processMessageWithTools(
    userMessage: String,
    systemPrompt: String = "",
    temperature: Double = 0.7
): String {
    // 1. Get tools from MCP server
    val mcpTools = mcpClientService.listTools()
    val openRouterTools = toolAdapterService.convertMcpToolsToOpenRouter(mcpTools)

    // 2. Create message history
    val messageHistory = mutableListOf<OpenAIMessageWithTools>()

    // 3. Handle tool calling workflow
    val response = toolExecutionService.handleToolCallingWorkflow(
        messageHistory = messageHistory,
        tools = openRouterTools,
        userMessage = userMessage,
        systemPrompt = systemPrompt,
        temperature = temperature
    )

    return response
}
```

## API Request Format

### With Tools (OpenRouter)

```json
{
  "model": "openai/gpt-4",
  "messages": [
    {
      "role": "user",
      "content": "What's the exchange rate for USD?"
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_exchange_rate",
        "description": "Get exchange rate for a currency",
        "parameters": {
          "type": "object",
          "properties": {
            "currency_code": {
              "type": "string",
              "description": "Three-letter currency code"
            }
          },
          "required": ["currency_code"]
        }
      }
    }
  ]
}
```

## Response Flow

### 1. Assistant Responds with Tool Call

```json
{
  "role": "assistant",
  "content": null,
  "tool_calls": [
    {
      "id": "call_abc123",
      "type": "function",
      "function": {
        "name": "get_exchange_rate",
        "arguments": "{\"currency_code\": \"USD\"}"
      }
    }
  ]
}
```

### 2. Execute Tool via MCP

```kotlin
val result = mcpClientService.callTool(
    name = "get_exchange_rate",
    arguments = mapOf("currency_code" to "USD")
)
```

### 3. Send Tool Result Back

```json
{
  "role": "tool",
  "tool_call_id": "call_abc123",
  "content": "The exchange rate for USD is 95.50 RUB"
}
```

### 4. Assistant Final Response

```json
{
  "role": "assistant",
  "content": "The current exchange rate for USD is 95.50 RUB."
}
```

## Configuration

### DI Setup (AppModule.kt)

```kotlin
// MCP Client Service
single {
    McpClientService(mcpServerUrl = "http://localhost:8082")
}

// Tool Adapter Service
single { ToolAdapterService() }

// Tool Execution Service
single {
    val openAIClient = get<OpenAIApiClient?>()
    if (openAIClient != null) {
        ToolExecutionService(
            mcpClientService = get(),
            toolAdapterService = get(),
            openAIApiClient = openAIClient
        )
    } else null
}
```

## Startup Sequence

1. **Application Start**
   ```kotlin
   // In Application.kt
   val mcpClientService = koinApplication.koin.get<McpClientService>()
   mcpClientService.connect()
   ```

2. **On First Request**
    - Tools are fetched from MCP server
    - Converted to OpenRouter format
    - Sent with message to OpenRouter
    - Tool calls executed via MCP
    - Results sent back
    - Final response returned to user

## Error Handling

- MCP connection errors: Logged and thrown
- Tool execution errors: Returned as tool result with error message
- Max iterations (5): Prevents infinite loops
- Graceful degradation: If no tools available, works as normal chat

## Testing

### 1. Start MCP Server

```bash
.\gradlew.bat :mcp-server:run
```

### 2. Start Main Server

```bash
.\gradlew.bat :server:run
```

### 3. Test Request

```bash
curl -X POST http://localhost:8080/api/send-message \
  -H "Content-Type: application/json" \
  -d '{
    "text": "What is the exchange rate for USD?",
    "systemPrompt": "",
    "temperature": 0.7
  }'
```

## Supported Models

OpenRouter models with tool calling support:

- `openai/gpt-4`
- `openai/gpt-4-turbo`
- `openai/gpt-3.5-turbo`
- `anthropic/claude-3-5-sonnet`
- `google/gemini-2.0-flash-001`

Check supported models: https://openrouter.ai/models?supported_parameters=tools

## Dependencies Added

```kotlin
// In gradle/libs.versions.toml
ktor-clientSse = { module = "io.ktor:ktor-client-sse", version.ref = "ktor" }
mcp-sdk = { module = "io.modelcontextprotocol:kotlin-sdk", version.ref = "mcp-sdk" }

// In server/build.gradle.kts
implementation(libs.ktor.clientSse)
implementation(libs.mcp.sdk)
```

## Files Modified/Created

### Created:

- `server/.../service/McpClientService.kt`
- `server/.../service/ToolAdapterService.kt`
- `server/.../service/ToolExecutionService.kt`
- `server/.../client/ToolCalling.kt`

### Modified:

- `server/build.gradle.kts` - Added MCP SDK and SSE client
- `gradle/libs.versions.toml` - Added ktor-clientSse
- `server/.../client/OpenAIApiClient.kt` - Added `sendMessageWithTools()`
- `server/.../di/AppModule.kt` - Added new services to DI

## Next Steps

1. Update ChatService to use tool calling for OpenRouter requests
2. Add endpoint parameter to enable/disable tool calling
3. Add logging for tool execution
4. Add metrics for tool usage
5. Add caching for tool list
6. Add configuration for MCP server URL
7. Add health check for MCP connection

## Example Integration in ChatService

```kotlin
suspend fun processUserMessage(
    userText: String,
    systemPrompt: String = "",
    temperature: Double = 0.7,
    aiProvider: AiProvider = AiProvider.GIGACHAT,
    useTools: Boolean = true  // New parameter
): ChatResponse {
    return when (aiProvider) {
        AiProvider.OPENROUTER -> {
            if (useTools && toolExecutionService != null) {
                // Use tool calling workflow
                val mcpTools = mcpClientService.listTools()
                val openRouterTools = toolAdapterService.convertMcpToolsToOpenRouter(mcpTools)

                val messageHistory = mutableListOf<OpenAIMessageWithTools>()
                val responseText = toolExecutionService.handleToolCallingWorkflow(
                    messageHistory, openRouterTools, userText, systemPrompt, temperature
                )

                ChatResponse(text = responseText, status = ResponseStatus.SUCCESS)
            } else {
                // Standard chat without tools
                openRouterHandler?.processMessage(userText, systemPrompt, temperature)
                    ?: ChatResponse("OpenRouter not configured", ResponseStatus.ERROR)
            }
        }
        AiProvider.GIGACHAT -> {
            gigaChatHandler.processMessage(userText, systemPrompt, temperature)
        }
    }
}
```
