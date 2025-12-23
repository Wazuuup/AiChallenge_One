# Server Module - AI Chat Orchestration

## Overview

The Server module is the heart of AiChallenge_One - a Ktor-based AI chat server that orchestrates conversations with
multiple AI providers (GigaChat and OpenRouter), manages conversation history with automatic summarization, supports MCP
tool calling, and provides RAG context enrichment.

**Port**: 8080
**Technology**: Ktor (Netty) + Koin DI + Exposed ORM
**Patterns**: Strategy Pattern, Adapter Pattern, Repository Pattern

## Architecture

```
HTTP Request → ChatRouting → ChatService → ProviderHandler → AI Client
                                ↓              ↓
                         RAG Context    Summarization
                                ↓              ↓
                         MessageRepository  Database
```

## Key Components

### 1. Application.kt

**Purpose**: Main entry point and server configuration

**Features**:

- Netty engine on port 8080
- CORS enabled (development mode - `anyHost()`)
- Content negotiation (JSON)
- Koin dependency injection with dynamic module configuration
- Routing configuration
- SSL/TLS truststore for GigaChat (truststore.jks)

**Configuration Loading**:

```kotlin
val config = ConfigFactory.systemEnvironment()
    .withFallback(ConfigFactory.systemProperties())
    .withFallback(ConfigFactory.load())
    .resolve()
```

Priority: **Environment Variables → System Properties → application.conf**

### 2. ChatRouting.kt

**Purpose**: REST API endpoint definitions

**Endpoints**:

#### POST /api/send-message

Send a message to AI and get response

**Request**:

```json
{
  "text": "What is Kotlin Multiplatform?",
  "systemPrompt": "You are a helpful assistant",
  "temperature": 0.7,
  "provider": "gigachat",
  "model": "qwen/qwen-2.5-72b-instruct",
  "maxTokens": 2000,
  "enableTools": true,
  "useRag": false
}
```

**Response**:

```json
{
  "text": "Kotlin Multiplatform is...",
  "status": "SUCCESS",
  "tokenUsage": {
    "promptTokens": 150,
    "completionTokens": 200,
    "totalTokens": 350
  },
  "lastResponseTokenUsage": {
    "promptTokens": 50,
    "completionTokens": 100,
    "totalTokens": 150
  },
  "responseTimeMs": 1250
}
```

#### POST /api/clear-history

Clear conversation history from both memory and database

**Response**:

```json
{
  "message": "History cleared"
}
```

#### GET /api/history?provider={provider}

Get conversation history for a specific provider

**Query Parameters**:

- `provider`: "gigachat" or "openrouter"

**Response**:

```json
[
  {
    "text": "Hello",
    "sender": "USER"
  },
  {
    "text": "Hi! How can I help?",
    "sender": "BOT"
  }
]
```

#### GET /

Health check endpoint

**Response**: `"GigaChat Chat Server is running"`

### 3. ChatService.kt

**Purpose**: Main orchestration service - Strategy pattern implementation

**Key Responsibilities**:

- Route requests to appropriate AI provider
- RAG context enrichment (when enabled)
- Token usage tracking (OpenRouter)
- Model change detection and state reset
- Conversation history loading from database

**Architecture Patterns**:

- **Strategy Pattern**: Different providers via `ProviderHandler<T>`
- **Dependency Injection**: Services injected via Koin
- **State Management**: In-memory history + database persistence

**Flow**:

```kotlin
processUserMessage(
    userText: String,
    systemPrompt: String,
    temperature: Double,
    provider: String,
    model: String?,
    maxTokens: Int?,
    enableTools: Boolean,
    useRag: Boolean
): ChatResponse {
    // 1. Parse provider
    val aiProvider = AiProvider.fromString(provider)

    // 2. RAG enrichment (if enabled)
    val enrichedUserText = if (useRag) {
        ragClient.searchSimilar(userText) + "\n\n" + userText
    } else userText

    // 3. Route to provider
    when (aiProvider) {
        GIGACHAT -> processGigaChatMessage(...)
        OPENROUTER -> processOpenRouterMessage(...)
    }
}
```

**RAG Integration** (when useRag=true):

```
User Query → RagClient → RAG Service (8091) → Search Similar Chunks
                ↓
        Enrich User Prompt:
        "=== Context ===
         1. chunk1
         2. chunk2
         ===
         User Question: {original}"
                ↓
        Send to AI Provider
```

### 4. ProviderHandler.kt

**Purpose**: Generic message processing for any AI provider

**Type Parameter**: `<T : ConversationMessage>`

**Features**:

- Provider-agnostic conversation management
- Automatic summarization (threshold: 10 messages)
- Database persistence for history
- Generic error handling

**Key Methods**:

```kotlin
suspend fun processMessage(
    userText: String,
    systemPrompt: String,
    temperature: Double
): String

suspend fun loadHistory()  // Load from DB on startup
suspend fun clearHistory() // Clear memory + DB
```

### 5. OpenRouterProviderHandler.kt

**Purpose**: OpenRouter-specific handler with tool calling support

**Extends**: `ProviderHandler<OpenRouterMessage>`

**Additional Features**:

- MCP tool calling integration
- Token usage tracking
- Response metadata (tokens, timing)

**Tool Calling Flow**:

```
User Message → OpenRouter API (with tools) → Tool Call Response?
                                                 ↓ YES
                                          ToolExecutionService
                                                 ↓
                                          Execute MCP Tools
                                                 ↓
                                          Send Results Back
                                                 ↓
                                          Final Response
```

### 6. AI Client Adapters

#### GigaChatClientAdapter.kt

**Purpose**: Adapter for GigaChat API

**Features**:

- Implements `AiClient<GigaChatMessage>`
- OAuth 2.0 token management (automatic refresh)
- SSL/TLS with custom truststore
- Chat completions API

**Configuration**:

```hocon
gigachat {
    baseUrl = "https://gigachat.devices.sberbank.ru/api/v1"
    authUrl = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
    clientId = ${?GIGACHAT_CLIENT_ID}
    clientSecret = ${?GIGACHAT_CLIENT_SECRET}
    scope = "GIGACHAT_API_PERS"
}
```

#### OpenRouterClientAdapter.kt

**Purpose**: Adapter for OpenRouter/OpenAI API

**Features**:

- Implements `AiClient<OpenRouterMessage>`
- Compatible with OpenAI API format
- Supports multiple models via model parameter
- Optional tool calling

**Configuration**:

```hocon
openai {
    baseUrl = "https://openrouter.ai/api/v1"
    baseUrl = ${?OPENAI_BASE_URL}
    apiKey = ${?OPENAI_API_KEY}
}
```

### 7. SummarizationService.kt

**Purpose**: Automatic conversation summarization

**Features**:

- Provider-agnostic (works with any `AiClient<T>`)
- Language-specific prompts (RU for GigaChat, EN for OpenRouter)
- Configurable threshold (default: 10 messages)
- Temperature: 0.3 (deterministic summaries)

**Flow**:

```
Conversation reaches threshold (10 messages)
    ↓
Extract conversation text
    ↓
Send to AI with summarization prompt
    ↓
Replace history with summary message
    ↓
Continue conversation with compact history
```

**Prompt (Russian - GigaChat)**:

```
Ты - ассистент для суммаризации диалогов.
Создай краткое резюме следующего диалога...
```

**Prompt (English - OpenRouter)**:

```
You are a dialog summarization assistant.
Create a concise summary of the following conversation...
```

### 8. MessageRepository.kt

**Purpose**: Database persistence for conversation history

**Schema**:

```sql
CREATE TABLE messages (
    id SERIAL PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,  -- 'gigachat' or 'openrouter'
    role VARCHAR(20) NOT NULL,       -- 'user' or 'assistant'
    content TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_provider ON messages(provider);
CREATE INDEX idx_messages_timestamp ON messages(timestamp DESC);
```

**Methods**:

```kotlin
suspend fun save(provider: String, role: String, content: String)
suspend fun getHistory(provider: String): List<MessageEntity>
suspend fun deleteHistory(provider: String)
```

**Use Cases**:

- Persist conversations across server restarts
- Load history on startup
- Clear history per provider

### 9. MCP Integration

#### IMcpClientService.kt

**Purpose**: Interface for MCP client services

**Methods**:

```kotlin
suspend fun listTools(): List<Tool>
fun getServerUrl(): String
```

#### McpClientService.kt

**Purpose**: HTTP client for MCP servers

**Features**:

- Auto-discovery of MCP server tools
- HTTPS support with SSL certificate handling
- Error handling and logging

**Configured MCP Servers**:

```kotlin
val mcpServers = listOf(
    "https://localhost:8443",  // MCP Notes
    "https://localhost:8444",  // MCP NewsAPI
    "https://localhost:8445",  // MCP NewsCRUD
    "https://localhost:8447"   // MCP Notes Polling
)
```

#### ToolAdapterService.kt

**Purpose**: Convert MCP tools to OpenAI function format

**Transformation**:

```
MCP Tool Schema → OpenAI Function Schema
{
  name: "get_note_by_id",
  description: "...",
  inputSchema: {...}
}
↓
{
  type: "function",
  function: {
    name: "get_note_by_id",
    description: "...",
    parameters: {...}
  }
}
```

#### ToolExecutionService.kt

**Purpose**: Execute AI tool call requests against MCP servers

**Flow**:

```
AI requests tool call
    ↓
Parse tool name and arguments
    ↓
Find matching MCP server
    ↓
POST /tools/call {name, arguments}
    ↓
Return result to AI
```

### 10. RagClient.kt

**Purpose**: HTTP client for RAG service integration

**Features**:

- Query text vectorization
- Semantic similarity search
- Top-K chunk retrieval
- Error resilience (graceful degradation)

**API Call**:

```kotlin
suspend fun searchSimilar(
    query: String,
    limit: Int = 5
): List<String>? {
    POST http://localhost:8091/api/rag/search
    Body: {"query": query, "limit": limit}

    Returns: ["chunk1", "chunk2", ...]
}
```

## Configuration

### application.conf

```hocon
ktor {
  deployment {
    port = 8080
    port = ${?PORT}
  }
  application {
    modules = [ru.sber.cb.aichallenge_one.ApplicationKt.module]
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
  baseUrl = ${?OPENAI_BASE_URL}
  apiKey = ${?OPENAI_API_KEY}
}

database {
  url = "jdbc:postgresql://localhost:5432/chathistory"
  url = ${?DATABASE_URL}
  driver = "org.postgresql.Driver"
  user = "chatuser"
  user = ${?DATABASE_USER}
  password = "chatpass"
  password = ${?DATABASE_PASSWORD}
  maxPoolSize = 10
}
```

### Required Environment Variables

```bash
# GigaChat (Required)
GIGACHAT_CLIENT_ID="your-gigachat-client-id"
GIGACHAT_CLIENT_SECRET="your-gigachat-client-secret"

# OpenRouter (Optional - enables OpenRouter provider)
OPENAI_BASE_URL="https://openrouter.ai/api/v1"
OPENAI_API_KEY="sk-or-v1-..."

# Database (Optional - uses in-memory if not set)
DATABASE_URL="jdbc:postgresql://localhost:5432/chathistory"
DATABASE_USER="chatuser"
DATABASE_PASSWORD="chatpass"
```

## Running the Server

### Development

```bash
# Set environment variables
set GIGACHAT_CLIENT_ID=your-id
set GIGACHAT_CLIENT_SECRET=your-secret

# Optional: Enable OpenRouter
set OPENAI_BASE_URL=https://openrouter.ai/api/v1
set OPENAI_API_KEY=sk-or-v1-...

# Run server
.\gradlew.bat :server:run

# Or with custom config
.\gradlew.bat :server:runDev  # Uses application-dev.conf
```

### Production

```bash
# Build distribution
.\gradlew.bat :server:installDist

# Run
server\build\install\server\bin\server.bat
```

### With Full Stack

```bash
# Terminal 1: PostgreSQL (for history persistence)
docker run -d --name chatdb -p 5432:5432 \
  -e POSTGRES_DB=chathistory \
  -e POSTGRES_USER=chatuser \
  -e POSTGRES_PASSWORD=chatpass \
  postgres:15

# Terminal 2: RAG Service (optional)
.\gradlew.bat :services:rag:run

# Terminal 3: MCP Servers (optional)
.\gradlew.bat :mcp:notes:run

# Terminal 4: Main Server
.\gradlew.bat :server:run
```

## API Usage Examples

### Example 1: Simple Chat (GigaChat)

```bash
curl -X POST http://localhost:8080/api/send-message \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Что такое Kotlin?",
    "provider": "gigachat",
    "temperature": 0.7
  }'
```

### Example 2: Chat with OpenRouter + Tools

```bash
curl -X POST http://localhost:8080/api/send-message \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Create a note: Buy groceries tomorrow",
    "provider": "openrouter",
    "model": "anthropic/claude-3.5-sonnet",
    "temperature": 0.5,
    "enableTools": true
  }'
```

### Example 3: RAG-Enhanced Query

```bash
curl -X POST http://localhost:8080/api/send-message \
  -H "Content-Type: application/json" \
  -d '{
    "text": "How does the vectorizer service work?",
    "provider": "openrouter",
    "model": "qwen/qwen-2.5-72b-instruct",
    "useRag": true
  }'
```

### Example 4: Custom System Prompt

```bash
curl -X POST http://localhost:8080/api/send-message \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Write a haiku about Kotlin",
    "systemPrompt": "You are a poetic assistant who responds only in haiku format",
    "provider": "gigachat",
    "temperature": 1.2
  }'
```

### Example 5: Get Conversation History

```bash
# Get GigaChat history
curl "http://localhost:8080/api/history?provider=gigachat"

# Get OpenRouter history
curl "http://localhost:8080/api/history?provider=openrouter"
```

### Example 6: Clear History

```bash
curl -X POST http://localhost:8080/api/clear-history
```

## Design Patterns

### 1. Strategy Pattern

**Purpose**: Support multiple AI providers without code duplication

```kotlin
interface AiClient<T : ConversationMessage> {
    suspend fun sendMessage(
        messages: List<T>,
        systemPrompt: String?,
        temperature: Double,
        enableTools: Boolean
    ): String
}

// Implementations
class GigaChatClientAdapter : AiClient<GigaChatMessage>
class OpenRouterClientAdapter : AiClient<OpenRouterMessage>
```

### 2. Adapter Pattern

**Purpose**: Adapt different AI APIs to uniform interface

```kotlin
// Adapts GigaChat API to AiClient interface
class GigaChatClientAdapter(
    private val gigaChatApiClient: GigaChatApiClient
) : AiClient<GigaChatMessage> {
    override suspend fun sendMessage(...) {
        // Transform common interface to GigaChat-specific calls
        val response = gigaChatApiClient.chat(...)
        return response.choices.first().message.content
    }
}
```

### 3. Repository Pattern

**Purpose**: Abstract database access

```kotlin
class MessageRepository(database: Database) {
    suspend fun save(provider: String, role: String, content: String)
    suspend fun getHistory(provider: String): List<MessageEntity>
    suspend fun deleteHistory(provider: String)
}
```

### 4. Dependency Injection (Koin)

**Purpose**: Loose coupling, testability

```kotlin
val appModule = module {
    single { GigaChatApiClient(...) }
    single { OpenAIApiClient(...) }
    single { ChatService(get(), get(), get(), get(), get(), get(), get()) }
    single { RagClient(get()) }
}
```

## Data Flow

### Standard Chat Flow

```
1. User sends message via frontend
   ↓
2. ChatRouting receives POST /api/send-message
   ↓
3. ChatService.processUserMessage()
   ├─ [IF useRag] RagClient.searchSimilar() → Enrich user text
   ├─ Save user message to DB
   ├─ Route to provider (GigaChat or OpenRouter)
   ↓
4. ProviderHandler.processMessage()
   ├─ Build conversation history
   ├─ [IF threshold reached] Summarize history
   ├─ Call AI client
   ↓
5. AI Client Adapter
   ├─ [GigaChat] OAuth token refresh if needed
   ├─ [OpenRouter] Add tools if enabled
   ├─ HTTP POST to AI API
   ↓
6. AI Response
   ├─ [IF tool calls] ToolExecutionService.execute()
   ├─ Save assistant message to DB
   ├─ Track token usage (OpenRouter only)
   ↓
7. Return ChatResponse to frontend
```

### Tool Calling Flow (OpenRouter Only)

```
1. User: "Create a note with title 'Meeting' and content 'Q4 planning'"
   ↓
2. ChatService → OpenRouterProviderHandler (enableTools=true)
   ↓
3. Fetch available tools from MCP servers
   ↓
4. Send message + tools to OpenRouter API
   ↓
5. AI responds with tool_calls: [{ name: "create_note", arguments: {...} }]
   ↓
6. ToolExecutionService.execute()
   ├─ POST https://localhost:8443/tools/call
   ├─ Body: { name: "create_note", arguments: { title: "Meeting", content: "Q4 planning" } }
   ↓
7. MCP Server executes tool → Returns result
   ↓
8. Send tool results back to OpenRouter
   ↓
9. AI generates final response: "I've created the note 'Meeting' with..."
   ↓
10. Return to user
```

## Security Considerations

⚠️ **DEVELOPMENT ONLY** - Not production-ready

**Critical Issues**:

1. **Open CORS**: `anyHost()` allows any origin
2. **No Authentication**: Anyone can access API
3. **No Rate Limiting**: Vulnerable to abuse
4. **Credentials in Config**: Secrets in files (should use env vars or vault)
5. **HTTP Only**: No HTTPS enforcement
6. **No Input Validation**: Max message length not enforced

**Production Requirements**:

1. **Authentication**: JWT or API key
2. **HTTPS**: TLS/SSL certificates
3. **CORS Whitelist**: Only allow known origins
4. **Rate Limiting**: Per IP/user throttling
5. **Input Validation**: Max lengths, sanitization
6. **Secrets Management**: Vault, AWS Secrets Manager, etc.
7. **Audit Logging**: Track who said what
8. **Content Moderation**: Filter inappropriate content

## Performance Considerations

### Latency Breakdown

- **GigaChat**: ~2-4 seconds per request (OAuth + API call)
- **OpenRouter**: ~1-3 seconds (varies by model)
- **RAG Enrichment**: +200-500ms (vectorization + search)
- **Tool Calling**: +500-2000ms (MCP server calls)

### Optimization Strategies

1. **Connection Pooling**: Reuse HTTP connections (already implemented)
2. **Token Caching**: GigaChat tokens cached until expiry
3. **Database Connection Pool**: HikariCP with max 10 connections
4. **Async Processing**: Ktor coroutines for non-blocking I/O

### Scalability Limitations

- **Stateful**: Conversation history in memory (not horizontally scalable)
- **Single Instance**: No load balancing support
- **Database Bottleneck**: Single PostgreSQL instance

**Solutions for Production**:

1. Move history to Redis (shared state)
2. Load balancer with sticky sessions
3. Database read replicas
4. Async job queue for summarization

## Monitoring & Observability

### Logging

```kotlin
private val logger = LoggerFactory.getLogger(ChatService::class.java)

logger.info("Processing message [provider=..., model=...]")
logger.error("Error processing message", exception)
```

**Log Levels**:

- `INFO`: Request processing, provider routing
- `DEBUG`: Token usage, response timing
- `WARN`: RAG service unavailable, tool execution failures
- `ERROR`: API failures, exceptions

### Future Enhancements

1. **Prometheus Metrics**: `/metrics` endpoint
2. **Grafana Dashboards**: Request rates, latencies, error rates
3. **Distributed Tracing**: OpenTelemetry integration
4. **Health Checks**: `/health` endpoint with dependency checks

## Testing

### Manual Testing

```bash
# Health check
curl http://localhost:8080/

# Send message
curl -X POST http://localhost:8080/api/send-message \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello","provider":"gigachat"}'

# Check history
curl http://localhost:8080/api/history?provider=gigachat

# Clear history
curl -X POST http://localhost:8080/api/clear-history
```

### Unit Tests (Future)

```bash
.\gradlew.bat :server:test
```

## Dependencies

```kotlin
dependencies {
    implementation(project(":shared"))

    // Ktor Server
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.server.content.negotiation.jvm)
    implementation(libs.ktor.server.cors.jvm)
    implementation(libs.kotlinx.serialization.json)

    // Ktor Client (for AI APIs, RAG, MCP)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // Dependency Injection
    implementation(libs.koin.ktor)

    // Configuration
    implementation(libs.typesafe.config)

    // Logging
    implementation(libs.logback.classic)

    // Testing
    testImplementation(libs.ktor.server.test.host.jvm)
    testImplementation(libs.kotlin.test.junit)
}
```

## Troubleshooting

### Issue: GigaChat SSL Certificate Error

**Error**: `SSLHandshakeException: unable to find valid certification path`

**Solution**: Ensure `truststore.jks` exists in `server/src/main/resources/`

### Issue: OpenRouter Not Available

**Error**: `OpenRouter не настроен`

**Solution**: Set environment variables:

```bash
set OPENAI_BASE_URL=https://openrouter.ai/api/v1
set OPENAI_API_KEY=sk-or-v1-...
```

### Issue: RAG Service Unavailable

**Symptom**: Chat works but RAG context not added

**Solution**:

1. Verify RAG service is running: `curl http://localhost:8091/`
2. Check vectorizer database has embeddings
3. Review server logs for RAG client errors

### Issue: Tool Calling Not Working

**Symptom**: AI doesn't call tools even when asked

**Solutions**:

1. Verify `enableTools=true` in request
2. Check MCP servers are running (ports 8443, 8444, 8445, 8447)
3. Verify SSL certificates for MCP servers
4. Check OpenRouter API key has tool calling permissions

## Related Documentation

- [Frontend Module](frontend.md) - Web UI that consumes this API
- [RAG Service](services/rag.md) - Context retrieval service
- [MCP Servers](mcp/) - Tool calling backends
- [Architecture Overview](../ARCHITECTURE.md) - System design
- [Getting Started](../GETTING-STARTED.md) - Setup guide

## References

- [Ktor Documentation](https://ktor.io/)
- [GigaChat API](https://developers.sber.ru/docs/ru/gigachat/overview)
- [OpenRouter API](https://openrouter.ai/docs)
- [Koin DI](https://insert-koin.io/)
- [Exposed ORM](https://github.com/JetBrains/Exposed)
