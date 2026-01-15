# Спецификация: AI-агент поддержки пользователей

## Обзор

Модификация приложения для работы в режиме AI-агента поддержки пользователей с MCP сервером для управления тикетами
поддержки в PostgreSQL.

## Архитектура

### Новые компоненты

1. **mcp:tickets** — MCP сервер с прямым подключением к PostgreSQL (без отдельного REST API)
2. **TicketsMcpClientService** — клиент в server модуле
3. **Команда /support** — активация режима поддержки

### Порты

| Компонент            | HTTP | HTTPS |
|----------------------|------|-------|
| mcp:tickets          | 8096 | 8452  |
| PostgreSQL ticketsdb | 5434 | -     |

## Модель данных

### Таблица `tickets`

| Поле        | Тип                | Обязательное | Default | Описание                                       |
|-------------|--------------------|--------------|---------|------------------------------------------------|
| id          | SERIAL PRIMARY KEY | auto         | auto    | Уникальный идентификатор                       |
| title       | VARCHAR(255)       | ✓            | -       | Название тикета                                |
| description | TEXT               | ✓            | -       | Описание проблемы                              |
| initiator   | VARCHAR(255)       | ✗            | null    | Инициатор (строка, AI спрашивает при создании) |
| priority    | INTEGER            | ✗            | 3       | Приоритет 1-5 (1=низкий, 5=критический)        |
| status      | VARCHAR(20)        | ✗            | 'open'  | Статус: 'open' или 'closed'                    |
| created_at  | TIMESTAMP          | auto         | now()   | Дата создания                                  |
| updated_at  | TIMESTAMP          | auto         | now()   | Дата обновления                                |

### Жизненный цикл тикета

```
open → closed (простой, без промежуточных состояний)
```

### Политика удаления

**Удаление запрещено** — тикеты можно только закрывать (status='closed')

## MCP Tools

### Стандартный набор (10 tools)

#### CRUD операции

1. **create_ticket**
    - Параметры: `title` (required), `description` (required), `initiator` (optional), `priority` (optional, 1-5,
      default: 3)
    - Возвращает: созданный тикет

2. **get_ticket**
    - Параметры: `id` (required)
    - Возвращает: тикет или ошибку

3. **update_ticket**
    - Параметры: `id` (required), `title`, `description`, `initiator`, `priority`, `status`
    - Возвращает: обновлённый тикет

4. **list_tickets**
    - Параметры: нет (возвращает до 50 записей)
    - Возвращает: список тикетов

#### Операции фильтрации

5. **filter_by_initiator**
    - Параметры: `initiator` (required)
    - Возвращает: тикеты инициатора (до 50)

6. **filter_by_title**
    - Параметры: `title` (required) — ILIKE поиск
    - Возвращает: тикеты с совпадением в названии (до 50)

7. **filter_by_priority**
    - Параметры: `priority` (required), `operator` (optional: '=', '>=', '<=', default: '=')
    - Возвращает: тикеты по приоритету (до 50)

8. **filter_by_status**
    - Параметры: `status` (required: 'open' или 'closed')
    - Возвращает: тикеты по статусу (до 50)

9. **search_description**
    - Параметры: `query` (required) — ILIKE полнотекстовый поиск
    - Возвращает: тикеты с совпадением в описании (до 50)

10. **close_ticket**
    - Параметры: `id` (required)
    - Возвращает: закрытый тикет (shortcut для update с status='closed')

### Ограничения

- **Жёсткий лимит**: все list/filter операции возвращают максимум 50 записей
- **Без пагинации**: offset не поддерживается
- **Без удаления**: DELETE операция отсутствует

## Режим поддержки

### Активация

Команда `/support <вопрос>` — разовый запрос (аналогично `/help`)

### System Prompt (русский)

```
Ты — специалист технической поддержки приложения AiChallenge_One.

Твои обязанности:
1. Отвечать на вопросы пользователей о приложении
2. Создавать тикеты поддержки для новых проблем
3. Находить и обновлять существующие тикеты
4. Закрывать решённые тикеты

При ответе на вопросы:
- Используй RAG для поиска информации в кодовой базе
- Используй MCP tools для работы с тикетами
- Оба источника опрашивай параллельно

О приложении:
- Kotlin Multiplatform веб-приложение для AI чата (GigaChat/OpenRouter)
- Compose Multiplatform frontend, Ktor backend
- MCP серверы для различных интеграций
- RAG на базе векторного поиска (Ollama + pgvector)

При создании тикета:
- Обязательно спроси имя/email инициатора
- Установи приоритет на основе срочности проблемы
- Дай понятное название и описание

Отвечай чётко и по делу. Не используй эмодзи.
```

### Поведение AI

- **Полный CRUD доступ**: AI может создавать, читать, изменять тикеты
- **Без ограничений**: нет лимитов на количество операций за сессию
- **Автоматический tool calling**: tools всегда доступны, AI решает когда использовать
- **Параллельный поиск**: RAG и tickets опрашиваются одновременно
- **Текстовые ответы**: без специального UI форматирования

### Определение инициатора

AI должен спросить у пользователя имя/email при создании нового тикета.

## База данных

### Конфигурация PostgreSQL

```hocon
database {
  url = "jdbc:postgresql://localhost:5434/ticketsdb"
  url = ${?DATABASE_URL}
  driver = "org.postgresql.Driver"
  user = "tickets"
  user = ${?DATABASE_USER}
  password = "tickets"
  password = ${?DATABASE_PASSWORD}
  maxPoolSize = 10
}
```

### Docker Compose

Добавить в существующий `docker-compose.yml`:

```yaml
ticketsdb:
  image: postgres:15
  container_name: ticketsdb
  environment:
    POSTGRES_DB: ticketsdb
    POSTGRES_USER: tickets
    POSTGRES_PASSWORD: tickets
  ports:
    - "5434:5432"
  volumes:
    - ticketsdb_data:/var/lib/postgresql/data

volumes:
  ticketsdb_data:
```

### Миграции

**Auto-migrate**: `SchemaUtils.create(TicketsTable)` при старте приложения

### Seed Data

5-10 тестовых тикетов при первом запуске:

```kotlin
// Примеры тикетов
Ticket(title = "Не работает авторизация GigaChat", description = "При попытке отправить сообщение получаю ошибку 401", initiator = "user@example.com", priority = 4, status = "open")
Ticket(title = "Медленная загрузка frontend", description = "Compose App грузится более 10 секунд", initiator = "dev@company.ru", priority = 2, status = "open")
Ticket(title = "Ошибка в RAG поиске", description = "Поиск возвращает нерелевантные результаты", initiator = "tester@qa.com", priority = 3, status = "closed")
// ... ещё 2-7 тикетов
```

## Интеграция с server

### AppModule.kt

```kotlin
// Добавить регистрацию клиента
single { TicketsMcpClientService(get()) }

// Добавить в список MCP клиентов
single<List<McpClientService>> {
    listOfNotNull(
        get<RagMcpClientService>(),
        get<GitMcpClientService>(),
        get<TicketsMcpClientService>()  // NEW
    )
}
```

### Обработка команды /support

В `ChatService` добавить обработку команды `/support`:

- Подставить support system prompt
- Включить tools автоматически
- Использовать модель из конфига (или указанную пользователем)

## Модель OpenRouter

**По умолчанию**: Использовать `OPENAI_MODEL` из env var (пользователь указал бесплатную модель `gpt-oss-120`)

## Структура файлов

```
mcp/
└── tickets/
    ├── build.gradle.kts
    └── src/main/
        ├── kotlin/ru/sber/cb/aichallenge_one/mcp_tickets/
        │   ├── Application.kt
        │   ├── TicketsMcpConfiguration.kt
        │   ├── database/
        │   │   ├── DatabaseFactory.kt
        │   │   └── TicketsTable.kt
        │   ├── repository/
        │   │   └── TicketsRepository.kt
        │   └── service/
        │       └── TicketsService.kt
        └── resources/
            ├── application.conf
            └── logback.xml

shared/
└── src/commonMain/kotlin/.../models/tickets/
    ├── Ticket.kt
    ├── CreateTicketRequest.kt
    └── UpdateTicketRequest.kt

server/
└── src/main/kotlin/.../service/mcp/impl/
    └── TicketsMcpClientService.kt
```

## Зависимости

### mcp:tickets

- Ktor Server (Netty)
- Ktor Server SSL
- Exposed (Core, DAO, JDBC)
- PostgreSQL Driver
- HikariCP
- kotlinx-serialization-json
- mcp-kotlin (MCP SDK)
- Koin

## Чек-лист реализации

- [ ] Создать модели в shared (Ticket, CreateTicketRequest, UpdateTicketRequest)
- [ ] Создать mcp:tickets модуль
- [ ] Реализовать DatabaseFactory с auto-migrate
- [ ] Реализовать TicketsTable (Exposed)
- [ ] Реализовать TicketsRepository
- [ ] Реализовать TicketsService
- [ ] Реализовать 10 MCP tools в TicketsMcpConfiguration
- [ ] Добавить seed data
- [ ] Создать TicketsMcpClientService в server
- [ ] Зарегистрировать в AppModule
- [ ] Добавить обработку /support в ChatService
- [ ] Создать support system prompt
- [ ] Обновить docker-compose.yml
- [ ] Обновить CLAUDE.md с документацией
