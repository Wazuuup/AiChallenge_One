# GitHub PR Reviewer - Technical Specification

**Дата создания**: 2026-01-13
**Версия**: 1.0
**Автор**: System Analytics Agent

## 1. Executive Summary

Автоматизированная система review Pull Request'ов с использованием LLM (gpt-oss-120b через OpenRouter) и локального RAG
для контекста кодовой базы. Система состоит из трёх компонентов:

1. **services:github-webhook** (порт 8094) - принимает webhooks от GitHub
2. **mcp:github-reviewer** (порты 8095/8451) - предоставляет MCP tools для работы с GitHub API
3. Интеграция с существующими **server** (OpenRouter client) и **services:rag** (векторный поиск)

## 2. Architecture Overview

```
GitHub Webhook (PR opened)
    ↓
services:github-webhook (8094)
    ↓ HTTP 200 OK (immediate response)
    ↓
Async processing:
    1. Extract keywords from diff (file paths + class/function names)
    2. RAG search (degraded mode if unavailable)
    3. OpenRouter API call (gpt-oss-120b, temp=0.3, max_tokens=1000)
    4. Post review comment to PR via mcp:github-reviewer tools
```

## 3. Component Specifications

### 3.1 services:github-webhook

**Порт**: 8094
**Назначение**: Webhook endpoint для приема событий GitHub PR
**Технологии**: Ktor Server (Netty), Koin DI, Kotlin Coroutines

#### 3.1.1 Package Structure

```
ru.sber.cb.aichallenge_one.github.webhook/
├── Application.kt                    # Ktor server setup
├── routing/
│   └── WebhookRouting.kt            # POST /webhook endpoint
├── service/
│   ├── WebhookService.kt            # Orchestration логика
│   ├── DiffAnalysisService.kt       # Keyword extraction, size validation
│   └── ReviewOrchestrationService.kt # Async review workflow
├── client/
│   ├── ReviewApiClient.kt           # OpenRouter API client
│   ├── RagApiClient.kt              # HTTP client для services:rag
│   └── McpGitHubClient.kt           # MCP client для github-reviewer
└── di/
    └── WebhookModule.kt             # Koin DI configuration
```

#### 3.1.2 Webhook Endpoint

**Route**: `POST /webhook`
**Request**: GitHub Webhook Payload (JSON)
**Response**: `200 OK` (immediate, без ожидания review)
**Security**: Без HMAC валидации (только для dev)

**Обрабатываемые события**:

- `pull_request.opened` - единственное обрабатываемое событие

**Фильтрация**:

- Репозиторий: без валидации (любые репозитории)
- Размер diff: максимум 1000 строк (fail если больше)
- Binary files: пропускаются (с упоминанием в комментарии)
- Deletions only: оставляется комментарий о том что review не требуется

#### 3.1.3 Review Workflow

```kotlin
// Псевдокод ReviewOrchestrationService
suspend fun processReview(prNumber: Int, repository: String, diff: String) {
    // 1. Валидация размера
    if (countDiffLines(diff) > 1000) {
        postComment(prNumber, "❌ PR too large for review (>1000 lines)")
        return
    }

    // 2. Фильтрация binary files
    val textDiff = filterBinaryFiles(diff)
    if (textDiff.isEmpty()) {
        postComment(prNumber, "✅ Only deletions/binary files, no review needed")
        return
    }

    // 3. Keyword extraction
    val keywords = extractKeywords(textDiff) // file paths + class/function names

    // 4. RAG context (degraded mode if unavailable)
    val ragContext = try {
        ragApiClient.search(keywords, limit = 5)
    } catch (e: Exception) {
        log.warn("RAG unavailable, continuing without context")
        emptyList()
    }

    // 5. LLM review
    val reviewResponse = try {
        reviewApiClient.requestReview(
            diff = textDiff,
            ragContext = ragContext,
            model = "gpt-oss-120b",
            temperature = 0.3,
            maxTokens = 1000,
            timeout = 120_000 // 2 minutes
        )
    } catch (e: Exception) {
        postComment(prNumber, "⚠️ Review failed: ${e.message}")
        return
    }

    // 6. Post review (даже если PR уже закрыт)
    val commentText = formatReviewComment(reviewResponse)
    postComment(prNumber, commentText)
}
```

#### 3.1.4 Keyword Extraction Strategy

**Метод**: Простой split по diff headers (без regex парсинга)

```kotlin
fun extractKeywords(diff: String): List<String> {
    val keywords = mutableListOf<String>()

    // 1. Извлечение file paths из diff headers
    // Pattern: "diff --git a/path/to/file.kt b/path/to/file.kt"
    val filePathRegex = """diff --git a/(.*?) b/""".toRegex()
    filePathRegex.findAll(diff).forEach { match ->
        keywords.add(match.groupValues[1])
    }

    // 2. Извлечение имен классов и функций (простые паттерны)
    val classRegex = """class\s+(\w+)""".toRegex()
    val funRegex = """fun\s+(\w+)""".toRegex()

    classRegex.findAll(diff).forEach { keywords.add(it.groupValues[1]) }
    funRegex.findAll(diff).forEach { keywords.add(it.groupValues[1]) }

    return keywords.distinct()
}
```

#### 3.1.5 System Prompt Template

**Структура**: Markdown секции для читаемости

```markdown
# Task

You are an AI code reviewer analyzing a GitHub Pull Request. Provide constructive feedback focusing on:

- Code style and conventions
- Architectural patterns (SOLID, separation of concerns)
- Potential bugs and edge cases (null checks, exception handling, race conditions)

# Code Diff

{diff_content}

# Codebase Context (from RAG)

{rag_chunks}

# Guidelines

- Be concise and actionable
- Reference specific lines when possible
- Suggest concrete improvements
- If no issues found, state "No issues found"
```

#### 3.1.6 Review Comment Format

**Префикс**: `🤖 AI Code Review (gpt-oss-120b)`

**Формат**:

```markdown
🤖 AI Code Review (gpt-oss-120b)

{LLM_generated_review}

---
*Note: This review was partially truncated due to token limit* (если обрыв)
*Binary files excluded: image.png, lib.jar* (если были binary)
```

#### 3.1.7 Configuration (application.conf)

```hocon
ktor {
  deployment {
    port = 8094
  }
}

github {
  # GitHub Personal Access Token для API
  token = ${?GITHUB_TOKEN}
}

openrouter {
  # OpenRouter API key (shared с server)
  api_key = ${?OPENAI_API_KEY}
  base_url = "https://openrouter.ai/api/v1"
  review_model = "gpt-oss-120b"
  review_temperature = 0.3
  review_max_tokens = 1000
  review_timeout = 120000
}

rag {
  api_url = "http://localhost:8091"
  search_limit = 5
}

mcp {
  github_reviewer_url = "http://localhost:8095"
}

webhook {
  # Максимальный размер diff для review
  max_diff_lines = 1000
}
```

#### 3.1.8 Error Handling

| Сценарий                         | Действие                                    |
|----------------------------------|---------------------------------------------|
| RAG unavailable                  | Degraded mode: review без RAG контекста     |
| OpenRouter API error             | Оставить error комментарий в PR             |
| GitHub API rate limit (429)      | Fail без retry, логировать ошибку           |
| PR закрыт после webhook          | Оставить комментарий в любом случае         |
| LLM response обрыв (token limit) | Оставить как есть + примечание о truncation |
| Невалидный webhook payload       | 400 Bad Request                             |

#### 3.1.9 Concurrency

**Стратегия**: Обрабатывать последовательно (без ограничений)
Ktor + Kotlin Coroutines автоматически управляют параллелизмом. Каждый webhook обрабатывается в отдельной корутине.

### 3.2 mcp:github-reviewer

**Порты**: 8095 (HTTP), 8451 (HTTPS)
**Назначение**: MCP server с tools для взаимодействия с GitHub API
**Технологии**: Ktor Server, kotlin-mcp-sdk, kohsuke/github-api

#### 3.2.1 MCP Tools

##### Tool 1: get_pr_diff

```json
{
  "name": "get_pr_diff",
  "description": "Get the diff for a pull request",
  "inputSchema": {
    "type": "object",
    "properties": {
      "owner": {
        "type": "string",
        "description": "Repository owner (e.g., 'octocat')"
      },
      "repo": {
        "type": "string",
        "description": "Repository name (e.g., 'Hello-World')"
      },
      "pr_number": {
        "type": "integer",
        "description": "Pull request number"
      }
    },
    "required": [
      "owner",
      "repo",
      "pr_number"
    ]
  }
}
```

**Реализация**: GitHub API `GET /repos/{owner}/{repo}/pulls/{pr_number}` с `Accept: application/vnd.github.diff`

##### Tool 2: post_pr_comment

```json
{
  "name": "post_pr_comment",
  "description": "Post a general comment to a pull request",
  "inputSchema": {
    "type": "object",
    "properties": {
      "owner": {
        "type": "string"
      },
      "repo": {
        "type": "string"
      },
      "pr_number": {
        "type": "integer"
      },
      "body": {
        "type": "string",
        "description": "Comment text (markdown supported)"
      }
    },
    "required": [
      "owner",
      "repo",
      "pr_number",
      "body"
    ]
  }
}
```

**Реализация**: GitHub API `POST /repos/{owner}/{repo}/issues/{pr_number}/comments`

##### Tool 3: get_file_content

```json
{
  "name": "get_file_content",
  "description": "Get the content of a file from the repository (max 1000 lines)",
  "inputSchema": {
    "type": "object",
    "properties": {
      "owner": {
        "type": "string"
      },
      "repo": {
        "type": "string"
      },
      "path": {
        "type": "string",
        "description": "File path in repository"
      },
      "ref": {
        "type": "string",
        "description": "Branch/tag/commit SHA (optional, defaults to default branch)"
      }
    },
    "required": [
      "owner",
      "repo",
      "path"
    ]
  }
}
```

**Реализация**: GitHub API `GET /repos/{owner}/{repo}/contents/{path}?ref={ref}`
**Ограничение**: Максимум 1000 строк файла

#### 3.2.2 GitHub API Client

**Библиотека**: kohsuke/github-api (Java)

```kotlin
// GitHubService.kt
class GitHubService(private val githubToken: String) {
    private val github = GitHubBuilder()
        .withOAuthToken(githubToken)
        .build()

    suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int): String {
        return withContext(Dispatchers.IO) {
            val repository = github.getRepository("$owner/$repo")
            val pr = repository.getPullRequest(prNumber)
            pr.getDiff() // Returns diff as String
        }
    }

    suspend fun postComment(owner: String, repo: String, prNumber: Int, body: String) {
        withContext(Dispatchers.IO) {
            val repository = github.getRepository("$owner/$repo")
            val issue = repository.getIssue(prNumber)
            issue.comment(body)
        }
    }

    suspend fun getFileContent(owner: String, repo: String, path: String, ref: String?): String {
        return withContext(Dispatchers.IO) {
            val repository = github.getRepository("$owner/$repo")
            val content = repository.getFileContent(path, ref)
            val fileContent = String(content.read().readBytes())

            // Ограничение 1000 строк
            val lines = fileContent.lines()
            if (lines.size > 1000) {
                lines.take(1000).joinToString("\n") + "\n\n... (truncated, file too large)"
            } else {
                fileContent
            }
        }
    }
}
```

#### 3.2.3 Configuration (application.conf)

```hocon
ktor {
  deployment {
    port = 8095
    ssl_port = 8451
  }
}

github {
  token = ${?GITHUB_TOKEN}
}

ssl {
  key_alias = "github-reviewer"
  keystore_password = ${?SSL_KEYSTORE_PASSWORD}
  key_password = ${?SSL_KEY_PASSWORD}
}
```

#### 3.2.4 SSL/TLS

**Стратегия**: Автоматическая генерация self-signed сертификатов (по аналогии с другими MCP)
**Keystore**: `mcp/github-reviewer/src/main/resources/keystore.jks`

### 3.3 Integration with Existing Components

#### 3.3.1 server Module

**Интеграция**: Не требуется прямая интеграция
webhook сервис создаст свой `ReviewApiClient` (отдельный класс) вместо повторного использования `OpenAIApiClient`

**Причина**: Separation of concerns - webhook service должен быть независим от server

#### 3.3.2 services:rag

**Интеграция**: HTTP client для запросов к RAG API

```kotlin
// RagApiClient.kt
class RagApiClient(private val ragBaseUrl: String) {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
        }
    }

    suspend fun search(keywords: List<String>, limit: Int = 5): List<String> {
        return try {
            val query = keywords.joinToString(" ")
            val response = httpClient.post("$ragBaseUrl/api/rag/search") {
                contentType(ContentType.Application.Json)
                setBody(SearchRequest(query = query, limit = limit))
            }.body<SearchResponse>()

            response.results
        } catch (e: Exception) {
            log.warn("RAG search failed: ${e.message}")
            emptyList() // Degraded mode
        }
    }
}
```

## 4. Data Models

### 4.1 Webhook Payload (simplified)

```kotlin
@Serializable
data class WebhookPayload(
    val action: String, // "opened", "synchronize", etc.
    @SerialName("pull_request") val pullRequest: PullRequestInfo,
    val repository: RepositoryInfo
)

@Serializable
data class PullRequestInfo(
    val number: Int,
    val title: String,
    @SerialName("diff_url") val diffUrl: String,
    val state: String, // "open", "closed"
    @SerialName("head") val head: BranchInfo,
    @SerialName("base") val base: BranchInfo
)

@Serializable
data class BranchInfo(
    val ref: String, // branch name
    val sha: String  // commit SHA
)

@Serializable
data class RepositoryInfo(
    @SerialName("full_name") val fullName: String, // "owner/repo"
    val owner: OwnerInfo
)

@Serializable
data class OwnerInfo(
    val login: String
)
```

### 4.2 Review Request/Response

```kotlin
@Serializable
data class ReviewRequest(
    val diff: String,
    val ragContext: List<String>,
    val model: String = "gpt-oss-120b",
    val temperature: Double = 0.3,
    val maxTokens: Int = 1000
)

@Serializable
data class ReviewResponse(
    val reviewText: String,
    val tokensUsed: Int,
    val truncated: Boolean = false
)
```

## 5. Gradle Configuration

### 5.1 services:github-webhook

**build.gradle.kts**:

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("ru.sber.cb.aichallenge_one.github.webhook.ApplicationKt")
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Ktor Client (для RAG и GitHub API)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Koin DI
    implementation(libs.koin.ktor)

    // Shared models
    implementation(project(":shared"))

    // Logging
    implementation(libs.logback.classic)
}

// Custom task для dev конфига
tasks.register<JavaExec>("runDev") {
    group = "application"
    mainClass.set(application.mainClass)
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("config.resource", "application-dev.conf")
}
```

### 5.2 mcp:github-reviewer

**build.gradle.kts**:

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("ru.sber.cb.aichallenge_one.mcp.github_reviewer.ApplicationKt")
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // MCP SDK
    implementation("org.modelcontextprotocol:kotlin-sdk:0.1.0") // версия уточнить

    // GitHub API
    implementation("org.kohsuke:github-api:1.321") // версия уточнить

    // Koin DI
    implementation(libs.koin.ktor)

    // Shared models
    implementation(project(":shared"))

    // Logging
    implementation(libs.logback.classic)
}

tasks.register<JavaExec>("runDev") {
    group = "application"
    mainClass.set(application.mainClass)
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("config.resource", "application-dev.conf")
}
```

### 5.3 settings.gradle.kts Update

```kotlin
include(":services:github-webhook")
include(":mcp:github-reviewer")
```

## 6. Testing Strategy

### 6.1 Local Testing

**Метод**: curl/Postman моки webhook payloads

**Пример тестового webhook**:

```bash
curl -X POST http://localhost:8094/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "action": "opened",
    "pull_request": {
      "number": 123,
      "title": "Test PR",
      "diff_url": "https://github.com/owner/repo/pull/123.diff",
      "state": "open",
      "head": { "ref": "feature-branch", "sha": "abc123" },
      "base": { "ref": "main", "sha": "def456" }
    },
    "repository": {
      "full_name": "owner/repo",
      "owner": { "login": "owner" }
    }
  }'
```

### 6.2 GitHub API Mocks

Для тестов MCP tools использовать mock responses от GitHub API:

```kotlin
// В тестах
val mockGitHub = mockk<GitHub>()
every { mockGitHub.getRepository(any()) } returns mockRepository
every { mockRepository.getPullRequest(any()) } returns mockPullRequest
every { mockPullRequest.getDiff() } returns "diff content"
```

## 7. Deployment & Operations

### 7.1 Environment Variables

```bash
# GitHub Authentication
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx

# OpenRouter API
OPENAI_API_KEY=sk-or-v1-xxxxxxxxxxxxx

# Optional overrides
RAG_API_URL=http://localhost:8091
MCP_GITHUB_REVIEWER_URL=http://localhost:8095
WEBHOOK_MAX_DIFF_LINES=1000
REVIEW_TEMPERATURE=0.3
REVIEW_MAX_TOKENS=1000
```

### 7.2 Run Commands

```bash
# Запуск webhook сервиса
.\gradlew.bat :services:github-webhook:run
.\gradlew.bat :services:github-webhook:runDev  # с dev конфигом

# Запуск MCP сервера
.\gradlew.bat :mcp:github-reviewer:run
.\gradlew.bat :mcp:github-reviewer:runDev

# Полный стек для PR reviewer
# Terminal 1: RAG service
.\gradlew.bat :services:rag:run

# Terminal 2: MCP GitHub Reviewer
.\gradlew.bat :mcp:github-reviewer:run

# Terminal 3: Webhook Service
.\gradlew.bat :services:github-webhook:run
```

### 7.3 Logging

**Формат**: Logback с текущими настройками проекта
**Уровень**: INFO для production, DEBUG для dev

**Ключевые логи**:

```kotlin
log.info("Webhook received: PR #$prNumber from ${repository.fullName}")
log.info("Diff size: $lineCount lines")
log.debug("Keywords extracted: $keywords")
log.info("RAG search returned ${ragContext.size} chunks")
log.info("OpenRouter response: $tokensUsed tokens used")
log.info("Review posted to PR #$prNumber")
log.error("Review failed for PR #$prNumber: ${e.message}")
```

## 8. Documentation Updates

### 8.1 CLAUDE.md Updates

Добавить следующие секции:

#### services:github-webhook

```markdown
### services:github-webhook

**Описание**: Webhook сервис для автоматического review Pull Request'ов с использованием LLM и RAG.

**Порт**: 8094

**Ключевые компоненты**:

- `Application.kt` - точка входа (Ktor Netty)
- `routing/WebhookRouting.kt` - POST /webhook endpoint
- `service/ReviewOrchestrationService.kt` - async review workflow
- `service/DiffAnalysisService.kt` - keyword extraction, size validation
- `client/ReviewApiClient.kt` - OpenRouter API client
- `client/RagApiClient.kt` - HTTP client для RAG
- `client/McpGitHubClient.kt` - MCP client для github-reviewer
- `di/WebhookModule.kt` - Koin DI

**Webhook API**:

- `POST /webhook` - принимает GitHub webhook events (pull_request.opened)

**Review Workflow**:

1. Валидация размера diff (max 1000 строк)
2. Фильтрация binary files
3. Keyword extraction (file paths + class/function names)
4. RAG search для контекста (degraded mode если unavailable)
5. OpenRouter API call (gpt-oss-120b, temp=0.3, max_tokens=1000)
6. Post review comment to PR

**Конфигурация** (application.conf):

```hocon
github {
  token = ${?GITHUB_TOKEN}
}
openrouter {
  review_model = "gpt-oss-120b"
  review_temperature = 0.3
  review_max_tokens = 1000
}
webhook {
  max_diff_lines = 1000
}
```

**Использование**:

```bash
.\gradlew.bat :services:github-webhook:run
.\gradlew.bat :services:github-webhook:runDev
```

```

#### mcp:github-reviewer
```markdown
### mcp:github-reviewer

**Описание**: MCP (Model Context Protocol) сервер для работы с GitHub REST API.

**Порты**: 8095 (HTTP), 8451 (HTTPS)

**Ключевые компоненты**:
- `Application.kt` - HTTP/HTTPS server setup с auto-generated SSL certificates
- `GitHubMcpConfiguration.kt` - MCP server с GitHub API tools
- `service/GitHubService.kt` - обертка над kohsuke/github-api

**MCP Tools**:
1. `get_pr_diff` - получить diff по pull request
   - Параметры: owner, repo, pr_number
2. `post_pr_comment` - оставить комментарий в pull request
   - Параметры: owner, repo, pr_number, body (markdown supported)
3. `get_file_content` - получить содержимое файла из репозитория
   - Параметры: owner, repo, path, ref (optional)
   - Ограничение: max 1000 строк

**SSL/TLS**:
- Автоматическая генерация self-signed сертификатов
- Keystore: `mcp/github-reviewer/src/main/resources/keystore.jks`
- Поддержка environment variables: `SSL_KEY_ALIAS`, `SSL_KEYSTORE_PASSWORD`, `SSL_KEY_PASSWORD`

**Конфигурация** (application.conf):
```hocon
ktor {
  deployment {
    port = 8095
    ssl_port = 8451
  }
}
github {
  token = ${?GITHUB_TOKEN}
}
```

**Использование**:

```bash
.\gradlew.bat :mcp:github-reviewer:run
.\gradlew.bat :mcp:github-reviewer:runDev
```

**Зависимости**:

- kohsuke/github-api 1.321 - GitHub REST API client

```

#### Ports Table Update
```markdown
| `services:github-webhook`  | 8094      | -          | Webhook endpoint для GitHub PR events       |
| `mcp:github-reviewer`      | 8095      | 8451       | MCP Server (GitHub API tools)                |
```

## 9. Security Considerations

### 9.1 Development Phase (Current Scope)

**Реализовано**:

- Personal Access Token аутентификация (GitHub API)
- Environment variables для secrets
- Без webhook HMAC валидации (упрощение для dev)

**Не реализовано** (для production):

- Webhook signature validation (X-Hub-Signature-256)
- IP whitelist для webhook endpoint
- Rate limiting
- Repository whitelist

### 9.2 Production Recommendations

**Критичные для production**:

1. **Webhook validation**: HMAC SHA-256 с webhook secret
2. **Repository whitelist**: ALLOWED_REPOS env variable
3. **Rate limiting**: защита от abuse
4. **HTTPS enforcement**: для webhook endpoint
5. **GitHub App вместо PAT**: более безопасная аутентификация

## 10. Edge Cases & Error Scenarios

| Сценарий                    | Обработка                                               |
|-----------------------------|---------------------------------------------------------|
| Diff > 1000 строк           | Оставить комментарий "PR too large", fail review        |
| Binary files в PR           | Пропустить, упомянуть в комментарии                     |
| Только deletions            | Оставить комментарий "Only deletions, no review needed" |
| RAG unavailable             | Degraded mode: review без RAG контекста                 |
| OpenRouter API error        | Оставить error комментарий в PR                         |
| LLM response truncated      | Оставить как есть + примечание                          |
| PR закрыт после webhook     | Оставить комментарий в любом случае                     |
| GitHub API rate limit (429) | Fail, логировать ошибку                                 |
| Concurrent webhooks         | Обрабатывать последовательно (Ktor handles)             |
| Невалидный webhook payload  | 400 Bad Request response                                |

## 11. Success Criteria

### 11.1 Functional Requirements

- ✅ Webhook endpoint принимает PR opened events
- ✅ Извлечение keywords из diff
- ✅ Интеграция с RAG для контекста
- ✅ OpenRouter API call с gpt-oss-120b
- ✅ Post review comment через GitHub API

### 11.2 Non-Functional Requirements

- ✅ Webhook response < 1 sec (immediate 200 OK)
- ✅ Review completion < 2 min (timeout)
- ✅ Graceful degradation (RAG unavailable)
- ✅ Error handling для всех edge cases
- ✅ Логирование всех операций

### 11.3 Code Quality

- ✅ Консистентная архитектура с существующими модулями
- ✅ Koin DI для всех зависимостей
- ✅ Separation of concerns (routing/service/client)
- ✅ Kotlin coroutines для async операций
- ✅ Environment-based конфигурация

## 12. Future Enhancements (Out of Scope)

**Не включено в текущую спецификацию**:

1. Line-level comments (только general PR comments)
2. Multiple PR events (только `opened`, не `synchronize`)
3. Webhook retry mechanism
4. Review approval/rejection через Checks API
5. Persistent review history (только логи)
6. Background job queue (простые coroutines)
7. Metrics и monitoring
8. Docker deployment

## 13. Implementation Plan

### Phase 1: MCP GitHub Reviewer (1-2 дня)

1. Создать модуль `mcp:github-reviewer`
2. Реализовать 3 MCP tools (get_pr_diff, post_pr_comment, get_file_content)
3. Интеграция с kohsuke/github-api
4. SSL сертификаты
5. Тестирование с curl

### Phase 2: Webhook Service (2-3 дня)

1. Создать модуль `services:github-webhook`
2. Реализовать POST /webhook endpoint
3. DiffAnalysisService (keyword extraction, size validation)
4. ReviewApiClient (OpenRouter integration)
5. RagApiClient (RAG integration)
6. ReviewOrchestrationService (workflow)

### Phase 3: Integration & Testing (1-2 дня)

1. End-to-end тестирование с моками
2. Интеграция с реальными GitHub PR
3. Error handling validation
4. Документация (CLAUDE.md updates)

**Общее время**: 4-7 дней разработки

## 14. Appendix

### 14.1 GitHub API References

- [Webhooks](https://docs.github.com/en/webhooks)
- [Pull Requests API](https://docs.github.com/en/rest/pulls/pulls)
- [Issues Comments API](https://docs.github.com/en/rest/issues/comments)
- [Repository Contents API](https://docs.github.com/en/rest/repos/contents)

### 14.2 Dependencies Versions

- Ktor: 3.3.3 (текущая в проекте)
- Kotlin: 2.2.21
- Koin: 4.1.0
- kohsuke/github-api: 1.321 (проверить актуальную)
- MCP Kotlin SDK: 0.1.0 (проверить актуальную)

### 14.3 Model Context Protocol Resources

- [MCP Specification](https://modelcontextprotocol.io/)
- [Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk)

---

**Спецификация готова к имплементации**
**Вопросы**: См. раздел 10 (Edge Cases) для граничных сценариев
