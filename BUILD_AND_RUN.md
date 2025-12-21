# Build and Run - Tool Calling

## ✅ Все ошибки исправлены

### Что было исправлено

1. **Gradle Cache Error** - Удалена несуществующая зависимость `ktor-client-sse`
2. **Type Bounds Error** - Исправлено использование `get<T?>()` → `getOrNull<T>()`
3. **ChatService DI** - Убраны ненужные параметры из конструктора
4. **Routing Registration** - Добавлен `configureToolCallingRouting()`
5. **Auto-connect MCP** - Автоматическое подключение к MCP при старте

## 🚀 Запуск

### Вариант 1: Автоматический build

```bash
build-clean.bat
```

### Вариант 2: Вручную

```bash
# 1. Очистить кеш
rmdir /s /q .gradle\configuration-cache

# 2. Build
.\gradlew.bat clean build --no-configuration-cache

# 3. Если успешно - запуск
.\gradlew.bat :mcp-server:run     # Terminal 1
.\gradlew.bat :server:run          # Terminal 2
```

## 📋 Проверка сборки

После сборки вы должны увидеть:

```
BUILD SUCCESSFUL in XXs
```

Если видите ошибки - запустите с подробными логами:

```bash
.\gradlew.bat :server:build --no-configuration-cache --stacktrace > build.log 2>&1
```

Затем проверьте `build.log`.

## 🎯 После успешной сборки

### 1. Запустите MCP Server

```bash
.\gradlew.bat :mcp-server:run
```

Вы должны увидеть:

```
Starting MCP Server...
HTTP:  http://localhost:8082
HTTPS: https://localhost:8443
```

### 2. Запустите Main Server (в другом терминале)

```bash
.\gradlew.bat :server:run
```

Вы должны увидеть:

```
Application started
Connecting to MCP server on startup...
✓ MCP server connected successfully
```

Если MCP server не запущен, вы увидите warning:

```
Failed to connect to MCP server on startup
You can manually connect using POST /api/tools/connect
```

Это нормально! Просто подключитесь вручную позже.

## 🧪 Тестирование

### Test 1: Health Check

```bash
curl http://localhost:8080
```

Ответ: `GigaChat Chat Server is running`

### Test 2: List Tools

```bash
curl http://localhost:8080/api/tools/list
```

Ожидаемый ответ:

```json
{
  "count": 1,
  "tools": [
    ...
  ]
}
```

### Test 3: Tool Calling Chat

```bash
curl -X POST http://localhost:8080/api/tools/chat ^
  -H "Content-Type: application/json" ^
  -d "{\"text\": \"What is the exchange rate for USD?\"}"
```

Ожидаемый ответ:

```json
{
  "text": "The current exchange rate for USD is 95.50 RUB.",
  "status": "SUCCESS"
}
```

## ⚙️ Конфигурация

### Обязательно: OpenRouter API Key

```bash
# Windows CMD
set OPENAI_API_KEY=sk-or-v1-xxxxx
set OPENAI_BASE_URL=https://openrouter.ai/api/v1
set OPENAI_MODEL=openai/gpt-4-turbo

# Или в application-dev.conf:
openai {
    baseUrl = "https://openrouter.ai/api/v1"
    apiKey = "your_key"
    model = "openai/gpt-4-turbo"
}
```

### Опционально: Изменить MCP Server URL

В `server/.../di/AppModule.kt`:

```kotlin
single {
    McpClientService(mcpServerUrl = "http://localhost:8082")
}
```

## 📁 Созданные файлы

### Основные компоненты:

```
server/src/main/kotlin/.../
├── service/
│   ├── McpClientService.kt          # MCP подключение
│   ├── ToolAdapterService.kt        # Конвертация tools
│   └── ToolExecutionService.kt      # Выполнение tools
├── client/
│   └── ToolCalling.kt               # Data classes
└── routing/
    └── ToolCallingRouting.kt        # API endpoints
```

### Документация:

```
├── BUILD_AND_RUN.md                 # Этот файл
├── GRADLE_CACHE_FIX.md              # Решение проблем Gradle
├── TOOL_CALLING_SETUP.md            # Полная настройка
├── TOOL_CALLING_QUICK_START.md      # Быстрый старт
├── TOOL_CALLING_IMPLEMENTATION.md   # Технические детали
└── build-clean.bat                  # Скрипт сборки
```

## 🔍 Troubleshooting

### "Could not find ktor-client-sse"

**Решение:**

```bash
rmdir /s /q .gradle\configuration-cache
.\gradlew.bat clean build --no-configuration-cache
```

### "Type argument is not within its bounds"

**Исправлено!** Используем `getOrNull<T>()` вместо `get<T?>()`

### "Failed to connect to MCP server"

**Причины:**

1. MCP server не запущен → `.\gradlew.bat :mcp-server:run`
2. Порт 8082 занят → Проверьте `netstat -ano | findstr :8082`
3. Firewall блокирует → Разрешите порт

### "Tool calling is not available"

**Причина:** OpenRouter не настроен

**Решение:**

```bash
set OPENAI_API_KEY=your_key
set OPENAI_BASE_URL=https://openrouter.ai/api/v1
```

Затем перезапустите server.

## 📊 Логи

### При успешном запуске вы увидите:

**MCP Server:**

```
Starting MCP Server...
HTTP:  http://localhost:8082
HTTPS: https://localhost:8443
```

**Main Server:**

```
Application started in 0.XXX seconds
Connecting to MCP server on startup...
✓ MCP server connected successfully
```

**Tool Calling Request:**

```
[ToolExecutionService] Executing 1 tool call(s)...
[ToolExecutionService] Executing tool: get_exchange_rate
[McpClientService] Calling tool 'get_exchange_rate'
[McpClientService] Tool returned: 95.50
[ToolExecutionService] Completed execution
```

## 🎓 Следующие шаги

1. ✅ Сборка успешна
2. ✅ MCP server запущен
3. ✅ Main server запущен
4. ✅ Tool calling работает

**Теперь можно:**

- Добавить больше tools в mcp-server
- Интегрировать в UI
- Добавить кеширование
- Добавить метрики

## 📚 Полная документация

- **TOOL_CALLING_SETUP.md** - Детальная настройка
- **TOOL_CALLING_QUICK_START.md** - Примеры использования
- **TOOL_CALLING_IMPLEMENTATION.md** - Архитектура

---

**Начните с:** `build-clean.bat`
