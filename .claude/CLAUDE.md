# AiChallenge_One - Claude Code Guide

Kotlin Multiplatform веб-приложение для взаимодействия с GigaChat и OpenRouter AI APIs.

## Технологический стек

- **Kotlin**: 2.2.21
- **Compose Multiplatform**: 1.9.1
- **Ktor**: 3.3.3 (Server: Netty, Client: CIO)
- **Koin DI**: 4.1.0
- **Kotlinx Serialization**: 1.8.0
- **Платформы**: JVM (server), JS/WasmJS (frontend)

## Структура модулей

```
composeApp (JS/WasmJS) → shared (commonMain)
server (JVM) → shared (commonMain)
news-crud (JVM) → shared (commonMain)
mcp-newscrud (JVM) → shared (commonMain)
mcp-newsapi (JVM) → shared (commonMain)
notes (JVM) → shared (commonMain)
shared (commonMain) - платформо-независимые модели
```

### shared

- **Chat модели**: `ChatMessage`, `ChatResponse`, `SendMessageRequest`, `SenderType`, `ResponseStatus`
- **News модели**: `Article`, `Source`, `CreateArticleRequest`, `UpdateArticleRequest`
- **Notes модели**: `Note`, `NotePriority`, `CreateNoteRequest`, `UpdateNoteRequest`
- Константы: `SERVER_PORT = 8080`
- Source sets: `commonMain`, `jsMain`, `jvmMain`, `wasmJsMain`

### server

**Ключевые компоненты**:

- `Application.kt` - точка входа
- `routing/ChatRouting.kt` - REST endpoints
- `service/ChatService.kt` - бизнес-логика (Strategy pattern)
- `service/ProviderHandler.kt` - универсальный обработчик AI провайдеров
- `service/SummarizationService.kt` - автосуммаризация истории
- `client/GigaChatApiClient.kt` + `OpenAIApiClient.kt` - API клиенты
- `client/*Adapter.kt` - Adapter pattern для унификации
- `domain/` - AiClient интерфейс, ConversationMessage, AiProvider
- `di/AppModule.kt` - Koin DI

### composeApp

- `main.kt` - web entry point
- `App.kt` - root composable
- `ui/ChatScreen.kt` - UI компоненты
- `viewmodel/ChatViewModel.kt` - MVVM state management (StateFlow)
- `api/ChatApi.kt` - HTTP client

### news-crud

**Описание**: REST API сервер для CRUD операций с новостными статьями, с PostgreSQL хранилищем.

**Порт**: 8081

**Ключевые компоненты**:

- `Application.kt` - точка входа (Ktor Netty)
- `database/ArticlesTable.kt` - Exposed table schema
- `database/DatabaseFactory.kt` - HikariCP connection pool
- `repository/NewsRepository.kt` - CRUD операции с БД (coroutines + newSuspendedTransaction)
- `service/NewsService.kt` - бизнес-логика и валидация
- `routing/NewsRouting.kt` - REST endpoints
- `di/AppModule.kt` - Koin DI

**REST API** (`/api/news`):

- `GET /api/news?limit={}&offset={}` - список статей с пагинацией
- `GET /api/news/search?q={query}` - поиск по title/description/content
- `GET /api/news/{id}` - получить статью по ID
- `POST /api/news` - создать статью
- `PUT /api/news/{id}` - обновить статью
- `DELETE /api/news/{id}` - удалить статью

**База данных**:

- PostgreSQL (Exposed ORM + HikariCP)
- Таблица `articles` с полями: id, source_id, source_name, author, title, description, url, urlToImage, publishedAt,
  content, createdAt, updatedAt
- Автоматическое создание схемы при старте

**Конфигурация** (application.conf):

```hocon
database {
  url = "jdbc:postgresql://localhost:5432/newsdb"
  url = ${?DATABASE_URL}
  driver = "org.postgresql.Driver"
  user = "postgres"
  user = ${?DATABASE_USER}
  password = "postgres"
  password = ${?DATABASE_PASSWORD}
  maxPoolSize = 10
}
```

### mcp-newscrud

**Описание**: MCP (Model Context Protocol) сервер, предоставляющий инструменты для работы с news-crud API.

**Порты**: 8086 (HTTP), 8445 (HTTPS)

**Ключевые компоненты**:

- `Application.kt` - HTTP/HTTPS server setup с auto-generated SSL certificates
- `NewsCrudMcpConfiguration.kt` - MCP server с 6 инструментами
- `service/NewsCrudService.kt` - HTTP client для news-crud API

**MCP Tools**:

1. `get_all_articles` - получить все статьи (pagination: limit/offset)
2. `get_article_by_id` - получить статью по ID
3. `search_articles` - поиск статей по ключевым словам
4. `create_article` - создать новую статью (required: title)
5. `update_article` - обновить статью (required: id)
6. `delete_article` - удалить статью (required: id)

**SSL/TLS**:

- Автоматическая генерация self-signed сертификатов
- Keystore: `mcp-newscrud/src/main/resources/keystore.jks`
- Поддержка environment variables: `SSL_KEY_ALIAS`, `SSL_KEYSTORE_PASSWORD`, `SSL_KEY_PASSWORD`

**MCP Server Info**:

- Name: `newscrud-mcp-server`
- Version: `1.0.0`
- Capabilities: Tools (Server-Sent Events via Ktor SSE)

## Команды сборки (Windows)

```bash
# Запуск основного сервера (AI Chat)
.\gradlew.bat :server:run              # production
.\gradlew.bat :server:runDev           # dev config

# Запуск news-crud API (требует PostgreSQL на localhost:5432)
.\gradlew.bat :news-crud:run           # запуск на порту 8081

# Запуск MCP серверов
.\gradlew.bat :mcp-newscrud:run        # MCP для news-crud (HTTP: 8086, HTTPS: 8445)
.\gradlew.bat :mcp-newsapi:run         # MCP для NewsAPI.org (HTTP: 8085, HTTPS: 8444)

# Запуск frontend
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun  # Wasm (рекомендуется)
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun      # JS

# Сборка и тесты
.\gradlew.bat build                    # сборка всех модулей
.\gradlew.bat :news-crud:build         # сборка только news-crud
.\gradlew.bat :mcp-newscrud:build      # сборка только mcp-newscrud
.\gradlew.bat test                     # все тесты
.\gradlew.bat :server:test             # тесты server
.\gradlew.bat :news-crud:test          # тесты news-crud
```

### Запуск полного стека (News CRUD + MCP)

```bash
# Terminal 1: PostgreSQL (Docker)
docker run -d --name newsdb -p 5432:5432 -e POSTGRES_DB=newsdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:15

# Terminal 2: News CRUD API
.\gradlew.bat :news-crud:run

# Terminal 3: MCP Server для News CRUD
.\gradlew.bat :mcp-newscrud:run
```

## API спецификация

**Base URL**: `http://localhost:8080`

### POST /api/send-message

```json
// Request
{
  "text": "string (required)",
  "systemPrompt": "string (optional, default: '')",
  "temperature": "double (optional, default: 0.7, range: 0.0-2.0)"
}

// Response
{
  "text": "AI response",
  "status": "SUCCESS"
  |
  "ERROR"
}
```

### POST /api/clear-history

Очищает историю разговора на сервере.

### GET /

Health check - возвращает "GigaChat Chat Server is running"

## Архитектура

### Паттерны проектирования

**1. MVVM (Frontend)**

- Model: Shared data classes
- View: Composable functions
- ViewModel: ChatViewModel (StateFlow)

**2. Strategy Pattern (Backend)**

- `AiClient<T>` - интерфейс для AI провайдеров
- `ProviderHandler<T>` - универсальная обработка сообщений
- `GigaChatClientAdapter`, `OpenRouterClientAdapter` - адаптеры

**3. Domain-Driven Design**

- `ConversationMessage` - базовый интерфейс сообщений
- `AiProvider` enum (GIGACHAT, OPENROUTER)
- `ConversationHistory<T>` - история с метаданными

**4. Dependency Injection**

- Koin DI в `AppModule.kt`
- Singleton scope для HttpClient, API clients, services

### Рефакторинг 2025

**Проблемы старой архитектуры**:

- Дублирование ~100 строк между GigaChat/OpenRouter обработчиками
- Зависимость от конкретных типов

**Решение**:

- Универсальный интерфейс `AiClient<T extends ConversationMessage>`
- Provider-agnostic `SummarizationService`
- Generic `ProviderHandler<T>` для любого AI провайдера

**Добавление нового провайдера**:

1. Data class реализующий `ConversationMessage`
2. Adapter реализующий `AiClient<YourMessageType>`
3. Добавить в `AiProvider` enum
4. Добавить language-specific промпт в `SummarizationService`

## Конфигурация

### application.conf (server/src/main/resources/)

```hocon
gigachat {
    baseUrl = "https://gigachat.devices.sberbank.ru/api/v1"
    authUrl = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
    clientId = ${?GIGACHAT_CLIENT_ID}
    clientSecret = ${?GIGACHAT_CLIENT_SECRET}
    scope = "GIGACHAT_API_PERS"
}
```

**Приоритет загрузки**: Environment vars → System properties → application.conf

### Credentials

```bash
# Вариант 1: Environment variables (рекомендуется)
set GIGACHAT_CLIENT_ID=your-id
set GIGACHAT_CLIENT_SECRET=your-secret

# Вариант 2: application-dev.conf (НЕ коммитить!)
```

### SSL/TLS

- `truststore.jks` (пароль: "changeit") в `server/src/main/resources/`
- Требуется для GigaChat API SSL сертификатов

## Frontend архитектура

### Компонентная структура

```
App (MaterialTheme)
└── ChatScreen (ViewModel integration)
    ├── TopAppBar (Title + Clear button)
    ├── SystemPromptInput (OutlinedTextField + Temperature slider/input)
    ├── MessageList (LazyColumn with auto-scroll)
    │   └── MessageBubble (Card, conditional styling)
    └── MessageInput (TextField + Send button with loading state)
```

### State Management

```kotlin
// ChatViewModel StateFlows
_messages: MutableStateFlow<List<ChatMessage>>
_inputText: MutableStateFlow<String>
_isLoading: MutableStateFlow<Boolean>
_systemPrompt: MutableStateFlow<String>
_temperature: MutableStateFlow<Double>
```

### HTTP Client

```kotlin
// ChatApi.kt
HttpClient(Js) { install(ContentNegotiation) { json() } }
baseUrl = "http://localhost:${SERVER_PORT}"
```

## Backend архитектура

### Инициализация (Application.kt)

```kotlin
// Config loading order
ConfigFactory.systemEnvironment()
    .withFallback(ConfigFactory.systemProperties())
    .withFallback(ConfigFactory.load())
    .resolve()

// Plugins
install(Koin) { modules(appModule(...)) }
install(ContentNegotiation) { json(...) }
install(CORS) { anyHost() } // ⚠️ Dev only
```

### ChatService

```kotlin
class ChatService(
    gigaChatApiClient: GigaChatApiClient,
    openAIApiClient: OpenAIApiClient?,
    summarizationService: SummarizationService
) {
    private val gigaChatHandler: ProviderHandler<GigaChatMessage>
    private val openRouterHandler: OpenRouterProviderHandler?

    suspend fun processUserMessage(
        userText: String,
        systemPrompt: String = "",
        temperature: Double = 0.7,
        aiProvider: AiProvider = AiProvider.GIGACHAT
    ): ChatResponse
}
```

### Summarization

- Автоматическая суммаризация при достижении threshold (default: 10 сообщений)
- Language-specific промпты (RU для GigaChat, EN для OpenRouter)
- Конфигурируемые параметры через `SummarizationConfig`

### OAuth 2.0 (GigaChat)

- Автоматическое получение и кэширование токенов
- Проверка `tokenExpiresAt` перед каждым запросом
- Bearer token в Authorization header

## Безопасность

⚠️ **DEVELOPMENT ONLY** - не готово для production

**Критичные проблемы**:

1. Открытая CORS политика (`anyHost()`)
2. Отсутствие authentication/authorization
3. Отсутствие rate limiting
4. HTTP вместо HTTPS

**Production TODO**:

- Whitelist CORS origins
- JWT authentication
- Rate limiting plugin
- Request validation (max length)
- HTTPS enforcement
- Environment-based secrets (не коммитить credentials!)

## Развертывание

### Development

```bash
.\gradlew.bat build
set GIGACHAT_CLIENT_ID=...
set GIGACHAT_CLIENT_SECRET=...
.\gradlew.bat :server:run       # Terminal 1
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun  # Terminal 2
```

### Production Server

```bash
.\gradlew.bat :server:installDist
# Output: server/build/install/server/
server\build\install\server\bin\server.bat
```

### Production Frontend

```bash
.\gradlew.bat :composeApp:wasmJsBrowserDistribution
# Output: composeApp/build/dist/wasmJs/productionExecutable/
# Deploy to: AWS S3, Netlify, Vercel, GitHub Pages, Cloudflare Pages
```

### Docker

```dockerfile
FROM amazoncorretto:17-alpine
WORKDIR /app
COPY server/build/install/server/ .
ENV GIGACHAT_CLIENT_ID=${GIGACHAT_CLIENT_ID}
ENV GIGACHAT_CLIENT_SECRET=${GIGACHAT_CLIENT_SECRET}
EXPOSE 8080
CMD ["./bin/server"]
```

## Масштабирование

**Stateful компоненты**: `ChatService` хранит историю в памяти

**Решения**:

1. Sticky sessions (session affinity)
2. Redis/Database для shared state
3. Client-side история

## Структура файлов

```
.
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── shared/
│   └── src/commonMain/kotlin/.../models/
│       ├── chat/ (ChatMessage, ChatResponse, SendMessageRequest, etc.)
│       ├── news/ (Article, Source, CreateArticleRequest, UpdateArticleRequest)
│       └── notes/ (Note, NotePriority, CreateNoteRequest, etc.)
├── server/ (AI Chat Server - port 8080)
│   └── src/main/
│       ├── kotlin/.../
│       │   ├── Application.kt
│       │   ├── domain/ (ConversationMessage, AiClient)
│       │   ├── client/ (GigaChatApiClient, OpenAIApiClient, Adapters)
│       │   ├── service/ (ChatService, ProviderHandler, SummarizationService)
│       │   ├── routing/ChatRouting.kt
│       │   └── di/AppModule.kt
│       └── resources/ (application.conf, truststore.jks, logback.xml)
├── news-crud/ (News CRUD API - port 8081)
│   └── src/main/
│       ├── kotlin/.../news/
│       │   ├── Application.kt
│       │   ├── database/ (ArticlesTable, DatabaseFactory)
│       │   ├── repository/NewsRepository.kt
│       │   ├── service/NewsService.kt
│       │   ├── routing/NewsRouting.kt
│       │   └── di/AppModule.kt
│       └── resources/ (application.conf, logback.xml)
├── mcp-newscrud/ (MCP Server for News CRUD - ports 8086/8445)
│   └── src/main/
│       ├── kotlin/.../mcp_newscrud/
│       │   ├── Application.kt
│       │   ├── NewsCrudMcpConfiguration.kt
│       │   └── service/NewsCrudService.kt
│       └── resources/ (application.conf, logback.xml, keystore.jks)
├── mcp-newsapi/ (MCP Server for NewsAPI.org - ports 8085/8444)
│   └── src/main/
│       ├── kotlin/.../mcp_newsapi/
│       │   ├── Application.kt
│       │   ├── NewsApiConfiguration.kt
│       │   ├── service/NewsApiService.kt
│       │   └── models/NewsApiModels.kt
│       └── resources/ (application.conf, logback.xml, keystore.jks)
└── composeApp/ (Web Frontend)
    └── src/webMain/
        ├── kotlin/.../ (main.kt, App.kt, ChatScreen.kt, ChatViewModel.kt)
        └── resources/index.html
```

## Тестирование

**Текущий статус**: Тесты не реализованы

**Зависимости настроены**:

- `kotlin-test`
- `ktor-server-test-host-jvm`
- `kotlin-test-junit`
- `junit:4.13.2`

**Запуск**: `.\gradlew.bat test` | `:server:test` | `:shared:test` | `:composeApp:test`

## Ссылки

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Ktor](https://ktor.io/)
- [Koin](https://insert-koin.io/)
