# Tool Calling - Настройка и запуск

## ✅ Исправлено

Ошибка `Could not find io.ktor:ktor-client-sse:3.3.3` исправлена.

**Причина:** В Ktor 3.x поддержка SSE для клиента включена в `ktor-client-core`, отдельный артефакт не нужен.

**Что было сделано:**

- ✅ Удалена зависимость `ktor-clientSse` из `libs.versions.toml`
- ✅ Удалена зависимость из `server/build.gradle.kts`
- ✅ SSE уже доступен через `ktor-client-core`

## 📋 Checklist перед запуском

### 1. Environment переменные для OpenRouter

```bash
# Windows
set OPENAI_API_KEY=your_openrouter_api_key_here
set OPENAI_BASE_URL=https://openrouter.ai/api/v1
set OPENAI_MODEL=openai/gpt-4-turbo

# Или создайте server/src/main/resources/application-dev.conf:
openai {
    baseUrl = "https://openrouter.ai/api/v1"
    apiKey = "your_key_here"
    model = "openai/gpt-4-turbo"
}
```

### 2. Пересобрать проект

```bash
.\gradlew.bat clean build
```

### 3. Запустить MCP Server

```bash
.\gradlew.bat :mcp-server:run
```

Должен запуститься на:

- HTTP: http://localhost:8082
- HTTPS: https://localhost:8443

### 4. Запустить Main Server

В другом терминале:

```bash
.\gradlew.bat :server:run
```

Должен запуститься на:

- HTTP: http://localhost:8080

## 🧪 Тестирование

### Шаг 1: Проверить MCP Server

```bash
curl http://localhost:8082
```

Должен вернуть что-то вроде:

```json
{
  "jsonrpc": "2.0",
  "error": {...}
}
```

### Шаг 2: Подключиться к MCP

```bash
curl -X POST http://localhost:8080/api/tools/connect
```

Ожидаемый ответ:

```json
{
  "message": "Connected to MCP server"
}
```

### Шаг 3: Получить список tools

```bash
curl http://localhost:8080/api/tools/list
```

Ожидаемый ответ:

```json
{
  "count": 1,
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_exchange_rate",
        "description": "...",
        "parameters": {...}
      }
    }
  ]
}
```

### Шаг 4: Тест tool calling

```bash
curl -X POST http://localhost:8080/api/tools/chat \
  -H "Content-Type: application/json" \
  -d "{\"text\": \"What is the exchange rate for USD?\", \"temperature\": 0.7}"
```

Ожидаемый ответ:

```json
{
  "text": "The current exchange rate for USD is 95.50 RUB.",
  "status": "SUCCESS"
}
```

## ⚠️ Возможные проблемы

### "Not connected to MCP server"

**Решение:**

```bash
curl -X POST http://localhost:8080/api/tools/connect
```

### "Tool calling is not available (OpenRouter not configured)"

**Причина:** OpenRouter API ключ не настроен

**Решение:**

```bash
set OPENAI_API_KEY=sk-or-v1-xxxxx
set OPENAI_BASE_URL=https://openrouter.ai/api/v1
```

Затем перезапустите server.

### "Failed to connect to MCP server"

**Причина:** MCP server не запущен

**Решение:**

```bash
.\gradlew.bat :mcp-server:run
```

### Connection refused

**Проверьте порты:**

```bash
# Windows
netstat -ano | findstr :8082
netstat -ano | findstr :8080
```

Если порты заняты, убейте процессы или измените порты в конфигурации.

## 🔧 Конфигурация

### Изменить порт MCP Server

В `mcp-server/src/main/resources/application.conf`:

```hocon
ktor {
    deployment {
        port = 8082  # Измените на нужный
    }
}
```

В `server/src/main/kotlin/.../di/AppModule.kt`:

```kotlin
single {
    McpClientService(mcpServerUrl = "http://localhost:8082")  // Укажите новый порт
}
```

### Изменить модель OpenRouter

```bash
set OPENAI_MODEL=anthropic/claude-3-5-sonnet
# или
set OPENAI_MODEL=google/gemini-2.0-flash-001
```

### Отключить tool calling

Не добавляйте routing для tool calling в `Application.kt`, используйте только стандартный `/api/send-message` endpoint.

## 📝 Логи для отладки

### Включить DEBUG логи

В `server/src/main/resources/logback.xml`:

```xml
<logger name="ru.sber.cb.aichallenge_one.service" level="DEBUG"/>
```

### Что смотреть в логах

**Успешное подключение к MCP:**

```
[McpClientService] Connecting to MCP server at http://localhost:8082...
[McpClientService] ✓ Successfully connected to MCP server
```

**Получение tools:**

```
[McpClientService] Fetching tools list from MCP server...
[McpClientService] Retrieved 1 tools from MCP server
```

**Выполнение tool:**

```
[ToolExecutionService] Executing tool: get_exchange_rate
[ToolExecutionService] Tool arguments: {"currency_code":"USD"}
[McpClientService] Calling tool 'get_exchange_rate' with arguments: {currency_code=USD}
[McpClientService] Tool 'get_exchange_rate' returned: 95.50
```

## 🚀 Production deployment

**НЕ используйте в production без:**

1. Proper SSL certificates (не self-signed)
2. Rate limiting
3. Authentication/Authorization
4. Input validation
5. Error handling
6. Monitoring и metrics
7. Proper logging (не DEBUG в production!)
8. Secrets management (не env vars!)

## 📚 Документация

- **Quick Start**: `TOOL_CALLING_QUICK_START.md`
- **Implementation Details**: `TOOL_CALLING_IMPLEMENTATION.md`
- **OpenRouter Docs**: https://openrouter.ai/docs/guides/features/tool-calling
- **MCP SDK**: https://github.com/modelcontextprotocol/kotlin-sdk

## ✨ Что дальше

После успешного запуска вы можете:

1. Добавить больше tools в MCP server
2. Интегрировать tool calling в основной ChatService
3. Добавить UI для тестирования
4. Добавить caching для tools list
5. Добавить metrics и monitoring
6. Добавить support для parallel tool calls

---

**Начните с:**

```bash
.\gradlew.bat :mcp-server:run
# В другом терминале:
.\gradlew.bat :server:run
# Затем следуйте инструкциям в разделе "Тестирование"
```
