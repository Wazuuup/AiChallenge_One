# Tool Calling Quick Start

## Что было добавлено

✅ **MCP Client** в server модуль
✅ **OpenRouter Tool Calling** интеграция
✅ **Автоматическая конвертация** MCP tools → OpenRouter format
✅ **Автоматическое выполнение** tool calls через MCP
✅ **Новые API endpoints** для работы с tools

## Архитектура

```
┌─────────────────────────────────────────────────────────────┐
│                        Server Module                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────┐      ┌──────────────────┐              │
│  │   ChatService  │─────▶│ToolExecution     │              │
│  │                │      │Service           │              │
│  └────────────────┘      └────────┬─────────┘              │
│                                    │                         │
│  ┌────────────────┐      ┌────────▼─────────┐              │
│  │  MCP Client    │◀─────│ToolAdapter       │              │
│  │  Service       │      │Service           │              │
│  └────────┬───────┘      └──────────────────┘              │
│           │                                                  │
│           │ HTTP                                            │
│           ▼                                                  │
├───────────────────────────────────────────────────────────┬─┤
            │                                                │
            │                                                │
┌───────────▼──────────┐              ┌────────────────────▼┐
│   MCP Server         │              │   OpenRouter API    │
│   (localhost:8082)   │              │                     │
│                      │              │                     │
│ ┌──────────────────┐ │              │  - gpt-4           │
│ │ get_exchange_rate│ │              │  - claude-3.5      │
│ │ (MCP Tool)       │ │              │  - gemini-2.0      │
│ └──────────────────┘ │              │                     │
└──────────────────────┘              └─────────────────────┘
```

## Быстрый старт

### Шаг 1: Запустите MCP Server

```bash
.\gradlew.bat :mcp-server:run
```

MCP Server запустится на `http://localhost:8082`

### Шаг 2: Запустите Main Server

В другом терминале:

```bash
.\gradlew.bat :server:run
```

### Шаг 3: Подключитесь к MCP

```bash
curl -X POST http://localhost:8080/api/tools/connect
```

Ответ:

```json
{
  "message": "Connected to MCP server"
}
```

### Шаг 4: Получите список доступных tools

```bash
curl http://localhost:8080/api/tools/list
```

Ответ:

```json
{
  "count": 1,
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_exchange_rate",
        "description": "Get current exchange rate for a currency",
        "parameters": {
          "type": "object",
          "properties": {
            "currency_code": {
              "type": "string",
              "description": "Three-letter currency code (e.g., USD, EUR)"
            }
          },
          "required": ["currency_code"]
        }
      }
    }
  ]
}
```

### Шаг 5: Отправьте сообщение с tool calling

```bash
curl -X POST http://localhost:8080/api/tools/chat \
  -H "Content-Type: application/json" \
  -d '{
    "text": "What is the exchange rate for USD?",
    "systemPrompt": "You are a helpful assistant",
    "temperature": 0.7
  }'
```

Ответ:

```json
{
  "text": "The current exchange rate for USD is 95.50 RUB.",
  "status": "SUCCESS"
}
```

## Как это работает

1. **Пользователь отправляет вопрос**: "What is the exchange rate for USD?"

2. **Server получает tools** из MCP Server:
   ```kotlin
   val mcpTools = mcpClientService.listTools()
   ```

3. **Tools конвертируются** в OpenRouter формат:
   ```kotlin
   val openRouterTools = toolAdapterService.convertMcpToolsToOpenRouter(mcpTools)
   ```

4. **Запрос отправляется в OpenRouter** с tools:
   ```kotlin
   openAIApiClient.sendMessageWithTools(
       messages,
       tools,
       systemPrompt,
       temperature
   )
   ```

5. **OpenRouter возвращает tool call**:
   ```json
   {
     "role": "assistant",
     "tool_calls": [{
       "function": {
         "name": "get_exchange_rate",
         "arguments": "{\"currency_code\": \"USD\"}"
       }
     }]
   }
   ```

6. **Tool выполняется через MCP**:
   ```kotlin
   val result = mcpClientService.callTool(
       "get_exchange_rate",
       mapOf("currency_code" to "USD")
   )
   // Result: "95.50"
   ```

7. **Результат отправляется обратно в OpenRouter**:
   ```json
   {
     "role": "tool",
     "content": "95.50"
   }
   ```

8. **OpenRouter формирует финальный ответ**:
   ```json
   {
     "role": "assistant",
     "content": "The current exchange rate for USD is 95.50 RUB."
   }
   ```

## API Endpoints

### POST /api/tools/connect

Подключиться к MCP server

### GET /api/tools/list

Получить список доступных tools

### POST /api/tools/chat

Отправить сообщение с tool calling

**Request:**

```json
{
  "text": "string",
  "systemPrompt": "string (optional)",
  "temperature": 0.7
}
```

**Response:**

```json
{
  "text": "string",
  "status": "SUCCESS" | "ERROR"
}
```

### POST /api/tools/disconnect

Отключиться от MCP server

## Требования

1. **OpenRouter API Key** в environment переменных:
   ```bash
   set OPENAI_API_KEY=your_key_here
   set OPENAI_BASE_URL=https://openrouter.ai/api/v1
   ```

2. **MCP Server** должен быть запущен на `localhost:8082`

3. **Supported Model** в OpenRouter:
    - `openai/gpt-4`
    - `openai/gpt-4-turbo`
    - `anthropic/claude-3-5-sonnet`
    - `google/gemini-2.0-flash-001`

## Конфигурация

### Изменить MCP Server URL

В `di/AppModule.kt`:

```kotlin
single {
    McpClientService(mcpServerUrl = "http://your-server:8082")
}
```

### Отключить tool calling

Просто не вызывайте `/api/tools/chat` endpoint, используйте стандартный `/api/send-message`

## Тестирование

### Windows PowerShell

```powershell
# Connect
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/tools/connect"

# List tools
Invoke-RestMethod -Uri "http://localhost:8080/api/tools/list"

# Chat with tools
$body = @{
    text = "What is the USD exchange rate?"
    temperature = 0.7
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/tools/chat" `
    -ContentType "application/json" `
    -Body $body
```

## Troubleshooting

### "Not connected to MCP server"

```bash
curl -X POST http://localhost:8080/api/tools/connect
```

### "Tool calling is not available"

Проверьте, что OpenRouter настроен:

```bash
echo %OPENAI_API_KEY%
echo %OPENAI_BASE_URL%
```

### "Failed to connect to MCP server"

Проверьте, что MCP server запущен:

```bash
curl http://localhost:8082
```

### Tool calls не выполняются

Проверьте логи server'а - должны быть сообщения:

```
Executing tool: get_exchange_rate
Tool 'get_exchange_rate' executed successfully
```

## Логирование

Tool calling пишет подробные логи:

```
[ToolExecutionService] Executing 1 tool call(s)...
[ToolExecutionService] Executing tool: get_exchange_rate
[ToolExecutionService] Tool arguments: {"currency_code":"USD"}
[McpClientService] Calling tool 'get_exchange_rate' with arguments: {currency_code=USD}
[McpClientService] Tool 'get_exchange_rate' returned: 95.50
[ToolExecutionService] Tool 'get_exchange_rate' executed successfully
[ToolExecutionService] Completed execution of 1 tool call(s)
```

## Следующие шаги

1. ✅ Интеграция завершена
2. 🔧 Добавить caching для tools list
3. 🔧 Добавить metrics для tool usage
4. 🔧 Добавить поддержку async tool calls
5. 🔧 Добавить UI для тестирования tool calling
6. 🔧 Добавить больше MCP tools

## Документация

- **Полная документация**: `TOOL_CALLING_IMPLEMENTATION.md`
- **OpenRouter Docs**: https://openrouter.ai/docs/guides/features/tool-calling
- **MCP SDK**: https://github.com/modelcontextprotocol/kotlin-sdk
