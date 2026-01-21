# AiChallenge_One - Project Guide

Kotlin Multiplatform AI Chat приложение с поддержкой облачных (GigaChat, OpenRouter) и локальных (Ollama) LLM
провайдеров.

## Технологический стек

- **Kotlin**: 2.2.21
- **Compose Multiplatform**: 1.9.1
- **Ktor**: 3.3.3 (Server: Netty, Client: CIO)
- **Koin DI**: 4.1.0
- **Kotlinx Serialization**: 1.8.0
- **PostgreSQL**: 16-alpine
- **MCP SDK**: 0.8.1 (Model Context Protocol)
- **Платформы**: JVM (server), JS/WasmJS (frontend)

## Текущая архитектура

```
┌─────────────────────────────────────────────────────┐
│              Frontend (Compose Web)                  │
│                  Port: 8080                          │
└────────────────────┬────────────────────────────────┘
                     │ HTTP/REST
┌────────────────────▼────────────────────────────────┐
│           Main Server (Ktor)                         │
│    AI Chat + Tool Calling + History                 │
│              Port: 8080                              │
└──┬─────────┬─────────┬──────────────┬───────────────┘
   │         │         │              │
   │         │         │              │
┌──▼───┐ ┌──▼───┐ ┌──▼───────┐ ┌─────▼────┐
│Giga  │ │Open │ │  Ollama   │ │Postgres  │
│Chat  │ │Rout │ │ (Local)   │ │  :5432   │
│API   │ │er   │ │ :11434    │ │          │
└──────┘ └─────┘ └───────────┘ └──────────┘
```

## Активные модули

### shared

Общие модели данных для frontend и backend.

- **Chat**: `ChatMessage`, `ChatResponse`, `SendMessageRequest`, `TokenUsage`
- **News**: `Article`, `Source`, `CreateArticleRequest`, `UpdateArticleRequest`
- **Notes**: `Note`, `NotePriority`, `CreateNoteRequest`, `UpdateNoteRequest`
- **RAG**: `SearchRequest`, `SearchResponse`
- **Vectorizer**: `TextVectorizeRequest`, `TextVectorizeResponse`
- **Tickets**: `Ticket`, `TicketStatus`, `CreateTicketRequest`, `UpdateTicketRequest`
- Константы: `SERVER_PORT = 8080`

### server

Основной AI Chat сервер (порт 8080).

**Ключевые компоненты**:
- `Application.kt` - точка входа
- `routing/ChatRouting.kt` - REST endpoints
- `service/ChatService.kt` - бизнес-логика
- `service/ProviderHandler.kt` - универсальный обработчик AI провайдеров
- `service/SummarizationService.kt` - автосуммаризация истории
- `service/ToolExecutionService.kt` - MCP tool calling
- `client/GigaChatApiClient.kt` - GigaChat API клиент
- `client/OpenAIApiClient.kt` - OpenRouter/OpenAI API клиент
- `client/OllamaApiClient.kt` - Ollama API клиент
- `client/*Adapter.kt` - Adapter pattern для унификации
- `domain/` - AiClient интерфейс, ConversationMessage, AiProvider
- `di/AppModule.kt` - Koin DI
- `database/` - PostgreSQL схема
- `repository/MessageRepository.kt` - персистентность истории

**AI Провайдеры**:
- **GigaChat** - Сбер API (требует OAuth)
- **OpenRouter** - Мульти-провайдер (100+ моделей, function calling)
- **Ollama** - Локальный LLM (privacy-first, offline)

### composeApp

Web Frontend на Compose Multiplatform.

- `main.kt` - web entry point
- `App.kt` - root composable
- `ui/ChatScreen.kt` - UI компоненты
- `viewmodel/ChatViewModel.kt` - MVVM state management
- `api/ChatApi.kt` - HTTP client

## AI Провайдеры

### GigaChat (Сбер)

**Преимущества**:

- Бесплатный API
- Поддержка русского языка
- Высокое качество ответов

**Настройка**:
```bash
set GIGACHAT_CLIENT_ID=your-id
set GIGACHAT_CLIENT_SECRET=your-secret
```

### OpenRouter

**Преимущества**:

- Доступ к 100+ AI моделей
- Function calling support
- Гибкая ценовая политика

**Настройка**:
```bash
set OPENAI_API_KEY=sk-or-v1-...
set OPENAI_BASE_URL=https://openrouter.ai/api/v1
set OPENAI_MODEL=openai/gpt-3.5-turbo
```

**Важно**: Для бесплатных моделей (`:free`) требуется включить "Allow training on data"
в [настройках конфиденциальности](https://openrouter.ai/settings/privacy).

### Ollama (Local LLM)

**Преимущества**:

- **Privacy**: 100% локальная обработка
- **Cost**: Бесплатно после загрузки моделей
- **Offline**: Работает без интернета
- **MCP Tools**: Полная поддержка function calling

**Установка**:
```bash
# Windows: скачайте с https://ollama.com/download
# macOS:
brew install ollama
# Linux:
curl -fsSL https://ollama.com/install.sh | sh

# Загрузка модели
ollama pull gemma3:1b

# Запуск сервера
ollama serve
```

**Настройка** (`server/src/main/resources/application.conf`):
```hocon
ollama {
  baseUrl = "http://localhost:11434"
  model = "gemma3:1b"
  timeout = 120000  # 2 минуты
  enableSummarization = false
  enableTools = true
}
```

**Аппаратные требования**:

| Модель       | RAM (минимум) | RAM (рекомендуется) | Скорость генерации    |
|--------------|---------------|---------------------|-----------------------|
| `gemma3:1b`  | 4 GB          | 8 GB                | Быстрая (15-30 tok/s) |
| `gemma3:4b`  | 8 GB          | 16 GB               | Средняя (8-15 tok/s)  |
| `gemma3:12b` | 16 GB         | 32 GB               | Медленная (3-8 tok/s) |

## REST API

**Base URL**: `http://localhost:8080`

### POST /api/send-message

Отправить сообщение в чат.

**Request**:
```json
{
  "text": "string (required)",
  "systemPrompt": "string (optional)",
  "temperature": 0.7,
  "maxTokens": 2048,
  "aiProvider": "GIGACHAT | OPENROUTER | OLLAMA",
  "enableTools": false,
  "useRag": false,
  "model": "string (optional)"
}
```

**Response**:

```json
{
  "text": "AI response",
  "status": "SUCCESS | ERROR",
  "tokenUsage": {
    "promptTokens": 10,
    "completionTokens": 20,
    "totalTokens": 30
  }
}
```

### POST /api/clear-history

Очистить историю разговора.

### GET /

Health check.

## Команды чата

- `/help <вопрос>` - задать вопрос по кодовой базе с использованием RAG (требуется запущенный RAG сервис)

## Конфигурация

### application.conf

```hocon
ktor {
  deployment {
    port = 8080
    port = ${?PORT}
  }
}

gigachat {
    baseUrl = "https://gigachat.devices.sberbank.ru/api/v1"
    authUrl = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
    clientId = ${?GIGACHAT_CLIENT_ID}
    clientSecret = ${?GIGACHAT_CLIENT_SECRET}
    scope = "GIGACHAT_API_PERS"
}

openai {
    baseUrl = "https://openrouter.ai/api/v1"
    model = ${?OPENAI_MODEL}
  topP = ${?OPENAI_TOP_P}
}

ollama {
    baseUrl = "http://localhost:11434"
    model = "gemma3:1b"
  timeout = 120000
    enableSummarization = false
    enableTools = true
}

database {
  url = "jdbc:postgresql://localhost:5432/aichallenge"
  url = ${?DATABASE_URL}
  driver = "org.postgresql.Driver"
  user = "aichallenge"
  user = ${?DATABASE_USER}
  password = "aichallenge"
  password = ${?DATABASE_PASSWORD}
  maxPoolSize = 10
}
```

## Команды сборки и запуска

### Разработка

```bash
# Сборка проекта
.\gradlew.bat build

# Запуск сервера (с dev конфигурацией)
.\gradlew.bat :server:run

# Запуск frontend (Wasm)
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun

# Запуск frontend (JS)
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun
```

### Docker Compose

```bash
# Полный стек с базами данных
docker-compose up -d

# Остановка
docker-compose down

# Просмотр логов
docker-compose logs -f server
```

### Production Build

```bash
# Server
.\gradlew.bat :server:installDist
# Output: server/build/install/server/

# Frontend
.\gradlew.bat :composeApp:wasmJsBrowserDistribution
# Output: composeApp/build/dist/wasmJs/productionExecutable/
```

## Отключенные модули

Следующие модули закомментированы в `settings.gradle.kts` для минимального VDS деплоя:

**Services**:

- `:services:notes` - REST API для заметок (порт 8084)
- `:services:news-crud` - News CRUD API (порт 8087)
- `:services:notes-scheduler` - Scheduler для notes summary
- `:services:vectorizer` - Векторизация текстов (порт 8090)
- `:services:rag` - RAG поиск (порт 8091)
- `:services:github-webhook` - GitHub webhook (порт 8094)

**MCP Servers**:

- `:mcp:notes` - Notes + Currency (порты 8082/8443)
- `:mcp:newsapi` - NewsAPI.org (порты 8085/8444)
- `:mcp:newscrud` - News CRUD proxy (порты 8086/8445)
- `:mcp:notes-polling` - Docker управление (порты 8088/8447)
- `:mcp:rag` - RAG поиск (порты 8092/8448)
- `:mcp:git` - Git операции (порты 8093/8449)
- `:mcp:github-reviewer` - GitHub API (порты 8095/8451)
- `:mcp:tickets` - Support tickets

Для включения модулей раскомментируйте соответствующие строки в `settings.gradle.kts`.

## Архитектурные паттерны

### Strategy Pattern

Универсальная обработка разных AI провайдеров через `AiClient<T>` интерфейс.

### Adapter Pattern

Адаптеры для унификации API: `GigaChatClientAdapter`, `OpenRouterClientAdapter`, `OllamaClientAdapter`.

### MVVM (Frontend)

- Model: Shared data classes
- View: Composable UI components
- ViewModel: ChatViewModel с StateFlow

### Dependency Injection

Koin DI для управления зависимостями.

## Troubleshooting

### Ollama: Connection Refused

```
Connection refused: connect
```

**Решение**: Запустите Ollama сервер:
```bash
ollama serve
```

### Ollama: Model Not Found

```
Model 'gemma3:1b' not found
```

**Решение**: Загрузите модель:
```bash
ollama pull gemma3:1b
```

### OpenRouter: No endpoints found

```
No endpoints found matching your data policy (Free model publication)
```

**Решение**: Включите "Allow training on data" в https://openrouter.ai/settings/privacy или используйте платную модель.

### GigaChat: SSL Certificate Error

Убедитесь что `truststore.jks` находится в `server/src/main/resources/`.

## Безопасность

⚠️ **DEVELOPMENT ONLY**

Текущие ограничения:

- Открытая CORS политика
- Отсутствие аутентификации
- Отсутствие rate limiting

**Production TODO**:

- JWT аутентификация
- CORS whitelist
- Rate limiting
- HTTPS enforcement
- Environment-based secrets

## Полезные ссылки

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Ktor](https://ktor.io/)
- [GigaChat Documentation](https://developers.sber.ru/docs/ru/gigachat/)
- [OpenRouter Documentation](https://openrouter.ai/docs)
- [Ollama Official Site](https://ollama.com)
- [Gemma 3 Model Card](https://ollama.com/library/gemma3)

## Документация

Более подробная документация доступна в папке `documentation/`:

- `ARCHITECTURE.md` - Архитектура системы
- `GETTING-STARTED.md` - Быстрый старт
- `modules/` - Документация по модулям
- `deployment/` - Гайды по деплою
