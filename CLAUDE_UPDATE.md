## СЕКЦИИ ДЛЯ ВСТАВКИ В CLAUDE.md

### 1. services:github-webhook (вставить после services:rag, перед ### mcp:rag)

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

### 2. mcp:github-reviewer (вставить после mcp:git, перед ## Распределение портов)

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
