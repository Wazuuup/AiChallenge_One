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
shared (commonMain) - платформо-независимые модели

services/ - Backend REST API серверы
├── services:notes (JVM) → shared (commonMain)
├── services:news-crud (JVM) → shared (commonMain)
├── services:notes-scheduler (JVM) - Scheduler для notes summary
├── services:vectorizer (JVM) → shared (commonMain) - Векторизация текстов с Ollama
└── services:rag (JVM) → shared (commonMain) - RAG поиск по векторной БД

mcp/ - Model Context Protocol серверы
├── mcp:notes (JVM) → shared (commonMain)
├── mcp:newsapi (JVM) → shared (commonMain)
├── mcp:newscrud (JVM) → shared (commonMain)
├── mcp:notes-polling (JVM) - Docker управление для scheduler
├── mcp:rag (JVM) → shared (commonMain) - MCP для RAG поиска
└── mcp:client (JVM) - MCP клиент
```

### shared

- **Chat модели**: `ChatMessage`, `ChatResponse`, `SendMessageRequest`, `SenderType`, `ResponseStatus`
- **News модели**: `Article`, `Source`, `CreateArticleRequest`, `UpdateArticleRequest`
- **Notes модели**: `Note`, `NotePriority`, `CreateNoteRequest`, `UpdateNoteRequest`
- **RAG модели**: `SearchRequest`, `SearchResponse`
- **Vectorizer модели**: `VectorizeRequest`, `VectorizeResponse`, `TextVectorizeRequest`, `TextVectorizeResponse`
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

### services:notes

**Описание**: REST API сервер для управления заметками с PostgreSQL хранилищем.

**Порт**: 8084

**Ключевые компоненты**:

- `Application.kt` - точка входа (Ktor Netty)
- `database/Tables.kt` - Exposed table schema
- `database/DatabaseFactory.kt` - HikariCP connection pool
- `repository/NoteRepository.kt` - CRUD операции с БД
- `service/NotesService.kt` - бизнес-логика и валидация
- `routing/NotesRouting.kt` - REST endpoints
- `di/NotesModule.kt` - Koin DI

**REST API** (`/api/notes`):

- `GET /api/notes?limit={}&offset={}` - список заметок с пагинацией
- `GET /api/notes/{id}` - получить заметку по ID
- `POST /api/notes` - создать заметку
- `PUT /api/notes/{id}` - обновить заметку
- `DELETE /api/notes/{id}` - удалить заметку

### services:news-crud

**Описание**: REST API сервер для CRUD операций с новостными статьями, с PostgreSQL хранилищем.

**Порт**: 8087

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

### mcp:notes

**Описание**: MCP (Model Context Protocol) сервер для управления заметками и курсами валют ЦБ РФ.

**Порты**: 8082 (HTTP), 8443 (HTTPS)

**Ключевые компоненты**:

- `Application.kt` - HTTP/HTTPS server setup с auto-generated SSL certificates
- `McpConfiguration.kt` - MCP server с инструментами для заметок и валют
- `service/NotesApiService.kt` - HTTP client для notes API
- `service/CurrencyExchangeService.kt` - интеграция с ЦБ РФ API

**MCP Tools**:

1. Notes management (CRUD операции с заметками)
2. `get_exchange_rates` - получить курсы валют ЦБ РФ

**SSL/TLS**:

- Автоматическая генерация self-signed сертификатов
- Keystore: `mcp/notes/src/main/resources/keystore.jks`
- Поддержка environment variables: `SSL_KEY_ALIAS`, `SSL_KEYSTORE_PASSWORD`, `SSL_KEY_PASSWORD`

### mcp:newsapi

**Описание**: MCP сервер для интеграции с NewsAPI.org (внешний API новостей).

**Порты**: 8085 (HTTP), 8444 (HTTPS)

**Ключевые компоненты**:

- `Application.kt` - HTTP/HTTPS server setup
- `NewsApiConfiguration.kt` - MCP server с инструментами NewsAPI
- `service/NewsApiService.kt` - HTTP client для newsapi.org
- `models/NewsApiModels.kt` - модели данных NewsAPI

**MCP Tools**:

1. `search_news` - поиск новостей по ключевым словам
2. `get_top_headlines` - получить топ новости
3. `get_sources` - получить список источников новостей

### mcp:newscrud

**Описание**: MCP (Model Context Protocol) сервер, предоставляющий инструменты для работы с services:news-crud API.

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
- Keystore: `mcp/newscrud/src/main/resources/keystore.jks`
- Поддержка environment variables: `SSL_KEY_ALIAS`, `SSL_KEYSTORE_PASSWORD`, `SSL_KEY_PASSWORD`

### mcp:client

**Описание**: MCP клиент для тестирования MCP серверов.

**Ключевые компоненты**:

- `Application.kt` - main entry point для различных тестовых сценариев
- `ExchangeRateClient.kt` - HTTP клиент для mcp:notes
- `ExchangeRateClientSSL.kt` - HTTPS клиент с SSL/TLS

**Использование**:

```bash
.\gradlew.bat :mcp:client:run                    # HTTP тест
.\gradlew.bat :mcp:client:runExchangeRateSSL     # HTTPS тест
```

### services:notes-scheduler

**Описание**: Scheduler сервис для периодического вызова notes summary endpoint с использованием cron expressions.

**Ключевые компоненты**:

- `Application.kt` - main entry point с cron scheduler логикой
- Использует `cron-utils` для парсинга cron expressions
- HTTP клиент для вызова MCP notes endpoint

**Конфигурация** (application.conf):

```hocon
scheduler {
    mcp_server_url = "http://localhost:8082"
    mcp_server_url = ${?MCP_SERVER_URL}

    cron_expression = "*/2 * * * *"  # каждые 2 минуты
    cron_expression = ${?CRON_EXPRESSION}

    enabled = true
    enabled = ${?SCHEDULER_ENABLED}
}
```

**Docker**:

- Dockerfile в `services/notes-scheduler/Dockerfile`
- Multi-stage build
- Amazon Corretto 17 Alpine runtime
- Поддержка `host.docker.internal` для связи с хостом

**Использование**:

```bash
# Запуск напрямую
.\gradlew.bat :services:notes-scheduler:run

# Docker build и run
docker build -t notes-scheduler:latest -f services/notes-scheduler/Dockerfile .
docker run -d --name notes-scheduler \
  --add-host host.docker.internal:host-gateway \
  -e CRON_EXPRESSION="*/2 * * * *" \
  -e MCP_SERVER_URL="http://host.docker.internal:8082" \
  notes-scheduler:latest
```

### mcp:notes-polling

**Описание**: MCP сервер для управления notes-scheduler контейнером через Docker.

**Порты**: 8088 (HTTP), 8447 (HTTPS)

**Ключевые компоненты**:

- `Application.kt` - HTTP/HTTPS server setup
- `McpPollingConfiguration.kt` - MCP server с Docker управлением
- Автоматическая генерация SSL сертификатов

**MCP Tools**:

1. `trigger_notes_summary_polling` - билдит и запускает notes-scheduler контейнер
    - Параметры: `cron_expression` (optional), `mcp_server_url` (optional)
    - Автоматический build Docker image
    - Запуск контейнера с заданными параметрами

2. `stop_notes_summary_polling` - останавливает и удаляет notes-scheduler контейнер
    - Без параметров
    - Graceful shutdown

**SSL/TLS**:

- Автоматическая генерация self-signed сертификатов
- Keystore: `mcp/notes-polling/src/main/resources/keystore.jks`
- Environment variables: `SSL_KEY_ALIAS`, `SSL_KEYSTORE_PASSWORD`, `SSL_KEY_PASSWORD`

**Использование**:

```bash
.\gradlew.bat :mcp:notes-polling:run
```

### services:vectorizer

**Описание**: REST API сервер для векторизации текстов с использованием Ollama embeddings и хранения в PostgreSQL с
pgvector.

**Порт**: 8090

**Ключевые компоненты**:

- `Application.kt` - точка входа (Ktor Netty)
- `database/DatabaseFactory.kt` - инициализация БД с pgvector extension
- `database/Embeddings.kt` - Exposed table schema для векторов
- `repository/EmbeddingRepository.kt` - CRUD операции с embeddings (raw SQL для pgvector)
- `service/VectorizerService.kt` - основной сервис векторизации
- `service/OllamaEmbeddingClient.kt` - HTTP client для Ollama API
- `service/ChunkingService.kt` - разбиение текста на чанки с учетом токенов
- `service/FileProcessingService.kt` - обработка файлов (.txt, .md)
- `routing/VectorizerRouting.kt` - REST endpoints
- `di/VectorizerModule.kt` - Koin DI

**REST API**:

- `POST /api/vectorize/folder` - векторизация всех файлов в папке
- `POST /api/vectorize` - векторизация одного текста (возвращает embedding)
- `GET /api/embeddings/count` - количество векторов в БД

**Chunking Strategy**:

- Target chunk size: 500 tokens
- Max chunk size: 600 tokens
- Min chunk size: 200 tokens
- Overlap: 75 tokens
- Использует jtokkit (CL100K_BASE encoding)

**База данных**:

- PostgreSQL с расширением pgvector
- Таблица `embeddings`: vector(768) для nomic-embed-text
- HNSW index для быстрого поиска: `vector_cosine_ops`
- Unique constraint: `(file_path, chunk_index)`

**Конфигурация** (application.conf):

```hocon
database {
  url = "jdbc:postgresql://localhost:5433/vectordb"
  driver = "org.postgresql.Driver"
  user = "vectoruser"
  password = "vectorpass"
  maxPoolSize = 10
}

ollama {
  baseUrl = "http://localhost:11434"
  baseUrl = ${?OLLAMA_BASE_URL}
}
```

### services:rag

**Описание**: REST API сервер для RAG (Retrieval-Augmented Generation) с векторным поиском по базе знаний.

**Порт**: 8091

**Ключевые компоненты**:

- `Application.kt` - точка входа (Ktor Netty)
- `database/DatabaseFactory.kt` - подключение к БД с embeddings
- `repository/EmbeddingRepository.kt` - поиск похожих векторов (cosine distance)
- `service/RagService.kt` - бизнес-логика RAG
- `service/VectorizerClient.kt` - HTTP client для vectorizer API
- `routing/RagRouting.kt` - REST endpoints
- `di/RagModule.kt` - Koin DI

**REST API**:

- `POST /api/rag/search` - поиск похожих чанков по запросу
    - Body: `{"query": "search text", "limit": 5}`
    - Response: `{"results": ["chunk1", "chunk2", ...]}`

**Алгоритм работы**:

1. Получает текстовый запрос от клиента
2. Векторизует запрос через services:vectorizer
3. Выполняет cosine similarity search в БД (HNSW index)
4. Фильтрует результаты по threshold (distance < 0.5)
5. Возвращает топ-N похожих чанков

**База данных**:

- Использует ту же БД что и vectorizer (shared embeddings table)
- Поиск через оператор `<=>` (cosine distance)
- HNSW index обеспечивает быстрый поиск

**Конфигурация** (application.conf):

```hocon
database {
  url = "jdbc:postgresql://localhost:5433/vectordb"
  driver = "org.postgresql.Driver"
  user = "vectoruser"
  password = "vectorpass"
  maxPoolSize = 10
}

vectorizer {
  url = "http://localhost:8090"
  url = ${?VECTORIZER_URL}
}
```

### mcp:rag

**Описание**: MCP (Model Context Protocol) сервер для предоставления RAG функциональности через MCP tools.

**Порты**: 8092 (HTTP), 8448 (HTTPS)

**Ключевые компоненты**:

- `Application.kt` - HTTP/HTTPS server setup с auto-generated SSL certificates
- `RagMcpConfiguration.kt` - MCP server с RAG инструментами
- `service/RagApiService.kt` - HTTP client для services:rag API

**MCP Tools**:

1. `search_similar_chunks` - поиск похожих текстовых чанков
    - Параметры:
        - `query` (string, required): поисковый запрос
        - `limit` (integer, optional, default: 5, max: 100): количество результатов
    - Описание: "Search for semantically similar text chunks in the RAG knowledge base using vector similarity search"

**SSL/TLS**:

- Автоматическая генерация self-signed сертификатов
- Keystore: `mcp/rag/src/main/resources/keystore.jks`
- Поддержка environment variables: `SSL_KEY_ALIAS`, `SSL_KEYSTORE_PASSWORD`, `SSL_KEY_PASSWORD`

**Конфигурация** (application.conf):

```hocon
ktor {
  deployment {
    port = 8092
    ssl_port = 8448
  }
}

rag {
  api_url = "http://localhost:8091"
  api_url = ${?RAG_API_URL}
}
```

**Использование**:

```bash
.\gradlew.bat :mcp:rag:run
```

## Распределение портов

| Модуль                     | HTTP Port | HTTPS Port | Описание                                     |
|----------------------------|-----------|------------|----------------------------------------------|
| `server`                   | 8080      | -          | AI Chat Server (GigaChat/OpenRouter)         |
| `services:notes`           | 8084      | -          | REST API для заметок                         |
| `services:news-crud`       | 8087      | -          | REST API для новостей                        |
| `services:vectorizer`      | 8090      | -          | REST API для векторизации текстов (Ollama)   |
| `services:rag`             | 8091      | -          | REST API для RAG поиска                      |
| `services:notes-scheduler` | -         | -          | Scheduler для notes summary (без сервера)    |
| `mcp:notes`                | 8082      | 8443       | MCP Server (заметки + валюты ЦБ РФ)          |
| `mcp:newsapi`              | 8085      | 8444       | MCP Server (NewsAPI.org)                     |
| `mcp:newscrud`             | 8086      | 8445       | MCP Server (News CRUD proxy)                 |
| `mcp:notes-polling`        | 8088      | 8447       | MCP Server (Docker управление для scheduler) |
| `mcp:rag`                  | 8092      | 8448       | MCP Server (RAG поиск)                       |
| `mcp:client`               | -         | -          | MCP Client (тестовый, не сервер)             |

## Команды сборки (Windows)

**Примечание**: Все утилитарные .bat скрипты находятся в папке `scripts/`. Для запуска используйте:

```bash
scripts\run-server.bat                 # Запуск AI Chat Server с dev конфигурацией
scripts\deploy-mcp-server.bat          # Деплой MCP сервера на удаленный сервер
scripts\regenerate-keystore.bat        # Регенерация SSL сертификатов
# ... и другие скрипты в scripts/
```

### Gradle команды

```bash
# Запуск основного сервера (AI Chat)
.\gradlew.bat :server:run              # production (порт 8080)
.\gradlew.bat :server:runDev           # dev config

# Запуск Services (Backend REST APIs)
.\gradlew.bat :services:notes:run       # Notes API (порт 8084)
.\gradlew.bat :services:notes:runDev    # Notes API dev config
.\gradlew.bat :services:news-crud:run   # News CRUD API (порт 8087)
.\gradlew.bat :services:vectorizer:run  # Vectorizer API (порт 8090)
.\gradlew.bat :services:rag:run         # RAG API (порт 8091)

# Запуск MCP серверов
.\gradlew.bat :mcp:notes:run           # MCP для заметок и валют (HTTP: 8082, HTTPS: 8443)
.\gradlew.bat :mcp:newsapi:run         # MCP для NewsAPI.org (HTTP: 8085, HTTPS: 8444)
.\gradlew.bat :mcp:newscrud:run        # MCP для news-crud (HTTP: 8086, HTTPS: 8445)
.\gradlew.bat :mcp:notes-polling:run   # MCP для Docker управления scheduler (HTTP: 8088, HTTPS: 8447)
.\gradlew.bat :mcp:rag:run             # MCP для RAG поиска (HTTP: 8092, HTTPS: 8448)

# Запуск Scheduler Service
.\gradlew.bat :services:notes-scheduler:run  # Notes scheduler (без веб-сервера)

# Запуск MCP клиента
.\gradlew.bat :mcp:client:run          # HTTP тест
.\gradlew.bat :mcp:client:runExchangeRateSSL  # HTTPS тест

# Запуск frontend
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun  # Wasm (рекомендуется)
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun      # JS

# Сборка и тесты
.\gradlew.bat build                           # сборка всех модулей
.\gradlew.bat :services:notes:build           # сборка только notes
.\gradlew.bat :services:news-crud:build       # сборка только news-crud
.\gradlew.bat :services:notes-scheduler:build # сборка только notes-scheduler
.\gradlew.bat :services:vectorizer:build      # сборка только vectorizer
.\gradlew.bat :services:rag:build             # сборка только rag
.\gradlew.bat :mcp:notes:build                # сборка только mcp:notes
.\gradlew.bat :mcp:newscrud:build             # сборка только mcp:newscrud
.\gradlew.bat :mcp:newsapi:build              # сборка только mcp:newsapi
.\gradlew.bat :mcp:notes-polling:build        # сборка только mcp:notes-polling
.\gradlew.bat :mcp:rag:build                  # сборка только mcp:rag
.\gradlew.bat test                            # все тесты
.\gradlew.bat :server:test                    # тесты server
.\gradlew.bat :services:notes:test            # тесты notes
.\gradlew.bat :services:news-crud:test        # тесты news-crud
.\gradlew.bat :services:vectorizer:test       # тесты vectorizer
.\gradlew.bat :services:rag:test              # тесты rag
```

### Запуск полного стека (Notes + MCP)

```bash
# Terminal 1: PostgreSQL (Docker)
docker run -d --name notesdb -p 5432:5432 -e POSTGRES_DB=notesdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:15

# Terminal 2: Notes Service
.\gradlew.bat :services:notes:run

# Terminal 3: MCP Server для Notes
.\gradlew.bat :mcp:notes:run

# Terminal 4 (optional): MCP Client тест
.\gradlew.bat :mcp:client:run
```

### Запуск полного стека (News CRUD + MCP)

```bash
# Terminal 1: PostgreSQL (Docker)
docker run -d --name newsdb -p 5432:5432 -e POSTGRES_DB=newsdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:15

# Terminal 2: News CRUD API
.\gradlew.bat :services:news-crud:run

# Terminal 3: MCP Server для News CRUD
.\gradlew.bat :mcp:newscrud:run
```

### Запуск полного стека RAG (Vectorizer + RAG + MCP)

```bash
# Terminal 1: PostgreSQL with pgvector (Docker)
docker run -d --name vectordb -p 5433:5432 \
  -e POSTGRES_DB=vectordb \
  -e POSTGRES_USER=vectoruser \
  -e POSTGRES_PASSWORD=vectorpass \
  pgvector/pgvector:pg16

# Terminal 2: Ollama (для embeddings)
# Установите Ollama с https://ollama.ai
ollama pull nomic-embed-text

# Terminal 3: Vectorizer Service
.\gradlew.bat :services:vectorizer:run

# Terminal 4: RAG Service
.\gradlew.bat :services:rag:run

# Terminal 5: MCP Server для RAG
.\gradlew.bat :mcp:rag:run

# Индексирование документов (опционально)
curl -X POST http://localhost:8090/api/vectorize/folder \
  -H "Content-Type: application/json" \
  -d '{"folderPath": "C:\\Users\\YourUser\\Documents", "model": "nomic-embed-text"}'
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

### OpenRouter Configuration

**Environment Variables**:

```bash
# OpenRouter API ключ (обязательно для использования OpenRouter)
set OPENAI_API_KEY=sk-or-v1-...
set OPENAI_BASE_URL=https://openrouter.ai/api/v1
set OPENAI_MODEL=openai/gpt-3.5-turbo  # Optional, default модель
```

**application.conf**:

```hocon
openai {
    baseUrl = "https://openrouter.ai/api/v1"
    baseUrl = ${?OPENAI_BASE_URL}
    apiKey = ${?OPENAI_API_KEY}
    model = "openai/gpt-3.5-turbo"
    model = ${?OPENAI_MODEL}
    maxTokens = null  # Optional
    topP = null       # Optional
}
```

**Важно**:
- Для использования **бесплатных моделей** (с суффиксом `:free`), необходимо включить разрешение на обучение в [настройках конфиденциальности OpenRouter](https://openrouter.ai/settings/privacy)
- Бесплатные модели логируют все промпты и ответы для улучшения моделей - **не используйте конфиденциальные данные**!
- Для production рекомендуется использовать **платные модели** без суффикса `:free`

**Privacy Settings**:

1. Перейдите на https://openrouter.ai/settings/privacy
2. Включите "Allow training on data" для бесплатных моделей (если планируете их использовать)
3. Для платных моделей оставьте опцию выключенной
4. Можно включить Zero Data Retention (ZDR) для максимальной конфиденциальности

**Доступные модели**: https://openrouter.ai/models

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
├── scripts/                              # Утилитарные скрипты
│   ├── run-server.bat
│   ├── deploy-mcp-server.bat
│   └── ... (другие .bat скрипты)
├── shared/                               # Общие модели данных
│   └── src/commonMain/kotlin/.../models/
│       ├── chat/ (ChatMessage, ChatResponse, SendMessageRequest, etc.)
│       ├── news/ (Article, Source, CreateArticleRequest, UpdateArticleRequest)
│       └── notes/ (Note, NotePriority, CreateNoteRequest, etc.)
├── server/                               # AI Chat Server - port 8080
│   └── src/main/
│       ├── kotlin/.../
│       │   ├── Application.kt
│       │   ├── domain/ (ConversationMessage, AiClient)
│       │   ├── client/ (GigaChatApiClient, OpenAIApiClient, Adapters)
│       │   ├── service/ (ChatService, ProviderHandler, SummarizationService)
│       │   ├── routing/ChatRouting.kt
│       │   └── di/AppModule.kt
│       └── resources/ (application.conf, truststore.jks, logback.xml)
├── composeApp/                           # Web Frontend
│   └── src/webMain/
│       ├── kotlin/.../ (main.kt, App.kt, ChatScreen.kt, ChatViewModel.kt)
│       └── resources/index.html
├── services/                             # Backend REST API Services
│   ├── notes/                            # Notes API - port 8084
│   │   └── src/main/
│   │       ├── kotlin/.../notes/
│   │       │   ├── Application.kt
│   │       │   ├── database/ (Tables, DatabaseFactory)
│   │       │   ├── repository/NoteRepository.kt
│   │       │   ├── service/NotesService.kt
│   │       │   ├── routing/NotesRouting.kt
│   │       │   └── di/NotesModule.kt
│   │       └── resources/ (application.conf, logback.xml)
│   └── news-crud/                        # News CRUD API - port 8087
│       └── src/main/
│           ├── kotlin/.../news/
│           │   ├── Application.kt
│           │   ├── database/ (ArticlesTable, DatabaseFactory)
│           │   ├── repository/NewsRepository.kt
│           │   ├── service/NewsService.kt
│           │   ├── routing/NewsRouting.kt
│           │   └── di/AppModule.kt
│           └── resources/ (application.conf, logback.xml)
└── mcp/                                  # Model Context Protocol Servers
    ├── notes/                            # MCP Notes+Currency - ports 8082/8443
    │   └── src/main/
    │       ├── kotlin/.../mcp_server/
    │       │   ├── Application.kt
    │       │   ├── McpConfiguration.kt
    │       │   ├── service/ (NotesApiService, CurrencyExchangeService)
    │       │   └── model/CbrModels.kt
    │       └── resources/ (application.conf, logback.xml, keystore.jks)
    ├── newsapi/                          # MCP NewsAPI - ports 8085/8444
    │   └── src/main/
    │       ├── kotlin/.../mcp_newsapi/
    │       │   ├── Application.kt
    │       │   ├── NewsApiConfiguration.kt
    │       │   ├── service/NewsApiService.kt
    │       │   └── models/NewsApiModels.kt
    │       └── resources/ (application.conf, logback.xml, keystore.jks)
    ├── newscrud/                         # MCP NewsCRUD - ports 8086/8445
    │   └── src/main/
    │       ├── kotlin/.../mcp_newscrud/
    │       │   ├── Application.kt
    │       │   ├── NewsCrudMcpConfiguration.kt
    │       │   └── service/NewsCrudService.kt
    │       └── resources/ (application.conf, logback.xml, keystore.jks)
    └── client/                           # MCP Client (тестовый)
        └── src/main/kotlin/.../mcp_client/
            ├── Application.kt
            ├── ExchangeRateClient.kt
            └── ExchangeRateClientSSL.kt
```

## Тестирование

**Текущий статус**: Тесты не реализованы

**Зависимости настроены**:

- `kotlin-test`
- `ktor-server-test-host-jvm`
- `kotlin-test-junit`
- `junit:4.13.2`

**Запуск**: `.\gradlew.bat test` | `:server:test` | `:shared:test` | `:composeApp:test`

## Недавние исправления (2026-01-10)

### Исправление статистики токенов для OpenRouter с MCP tools

**Проблема**: При использовании OpenRouter с MCP tools (tool calling), статистика токенов на UI отображала только последний API вызов, а не суммарные данные всех вызовов в workflow.

**Решение**:
- Добавлен `toolResponseHistory` в `OpenAIApiClient` для отдельного хранения tool calling responses
- Метод `sendMessageWithTools()` теперь сохраняет все responses в историю
- `OpenRouterProviderHandler.processMessageWithTools()` суммирует токены со всех API вызовов в workflow
- Добавлены методы `getToolResponseCount()`, `getToolResponseAt()`, `getLatestToolResponse()` для доступа к истории

**Файлы**:
- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/client/OpenAIApiClient.kt`
- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/service/OpenRouterProviderHandler.kt`

### Исправление передачи maxTokens в OpenRouter API

**Проблема**: Параметр `maxTokens` с UI формы не передавался в OpenRouter API запросы, всегда использовалось значение `null`.

**Решение**:
- Добавлен параметр `maxTokensOverride` в методы `processMessageWithTools()` и `processMessageWithMetadata()` класса `OpenRouterProviderHandler`
- Добавлен параметр `maxTokens` в `ToolExecutionService.handleToolCallingWorkflow()`
- `ChatService.processOpenRouterMessage()` теперь передает `maxTokens` в handler методы

**Файлы**:
- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/service/ChatService.kt`
- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/service/OpenRouterProviderHandler.kt`
- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/service/ToolExecutionService.kt`

### Исправление передачи model в OpenRouter API

**Проблема**: Модель, выбранная на UI форме, не передавалась в OpenRouter API. Всегда использовалась модель из конфигурации (default: `gpt-3.5-turbo`).

**Решение**:
- Добавлен параметр `modelOverride` в методы `sendMessage()` и `sendMessageWithTools()` класса `OpenAIApiClient`
- Добавлен параметр `modelOverride` в handler методы `OpenRouterProviderHandler`
- Добавлен параметр `model` в `ToolExecutionService.handleToolCallingWorkflow()`
- Используется логика `effectiveModel = modelOverride ?: model` для приоритета параметра с UI

**Файлы**:
- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/client/OpenAIApiClient.kt`
- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/service/ChatService.kt`
- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/service/OpenRouterProviderHandler.kt`
- `server/src/main/kotlin/ru/sber/cb/aichallenge_one/service/ToolExecutionService.kt`

## Troubleshooting

### OpenRouter: "No endpoints found matching your data policy"

**Ошибка**:
```json
{
  "error": {
    "message": "No endpoints found matching your data policy (Free model publication)",
    "code": 404
  }
}
```

**Причина**: Бесплатные модели OpenRouter (с суффиксом `:free`) требуют явного разрешения на использование данных для обучения моделей.

**Решение**:

1. **Вариант 1 (для тестирования)**: Разрешить обучение для бесплатных моделей
   - Перейдите на https://openrouter.ai/settings/privacy
   - Включите опцию "Allow training on data" для бесплатных моделей
   - ⚠️ После этого НЕ отправляйте конфиденциальные данные через бесплатные модели!

2. **Вариант 2 (рекомендуется для production)**: Используйте платные модели
   - Выберите модель без суффикса `:free`
   - Примеры: `openai/gpt-3.5-turbo`, `openai/gpt-4`, `anthropic/claude-3-sonnet`
   - Список моделей: https://openrouter.ai/models

**Ссылки**:
- [OpenRouter Privacy Settings](https://openrouter.ai/settings/privacy)
- [OpenRouter Free Models](https://openrouter.ai/collections/free-models)
- [OpenRouter Data Collection Policy](https://openrouter.ai/docs/guides/privacy/data-collection)

### Статистика токенов не обновляется

**Проблема**: После отправки сообщения статистика токенов не отображается или показывает неправильные значения.

**Возможные причины**:
1. Модель не возвращает usage информацию (некоторые модели OpenRouter)
2. Используется tool calling, но не применены последние исправления

**Проверка**:
- Проверьте логи сервера на наличие `"Token usage - Prompt: X, Completion: Y, Total: Z"`
- Для tool calling проверьте лог `"Workflow total usage - Prompt: X, Completion: Y, Total: Z"`

**Решение**:
- Убедитесь что используется последняя версия кода (с исправлениями от 2026-01-10)
- Попробуйте другую модель если текущая не возвращает usage данные

### MCP tools не работают

**Проблема**: Tool calling не срабатывает, модель не использует доступные инструменты.

**Возможные причины**:
1. Модель не поддерживает function calling (не все модели OpenRouter поддерживают)
2. MCP сервер не запущен
3. Tools не включены в настройках (checkbox "Enable Tools")

**Проверка**:
- В UI убедитесь что включен checkbox "Enable Tools"
- Проверьте что MCP сервер запущен: `.\gradlew.bat :mcp:rag:run`
- Проверьте логи: `"Found X available tools from MCP server"`

**Решение**:
- Используйте модели с поддержкой function calling (gpt-4, gpt-3.5-turbo, claude-3)
- Запустите нужные MCP серверы перед использованием
- Для тестирования используйте RAG: включите checkbox "Use RAG" и задайте вопрос по базе знаний

## Ссылки

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Ktor](https://ktor.io/)
- [Koin](https://insert-koin.io/)
- [OpenRouter Documentation](https://openrouter.ai/docs)
- [OpenRouter Models](https://openrouter.ai/models)
- [OpenRouter Privacy Settings](https://openrouter.ai/settings/privacy)
