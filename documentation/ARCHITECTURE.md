# System Architecture

## Overview

AiChallenge_One is a Kotlin Multiplatform application that provides an AI-powered chat interface with
Retrieval-Augmented Generation (RAG) capabilities. The system is built using a microservices architecture with clear
separation of concerns.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend (Wasm/JS)                       │
│                    Compose Multiplatform UI                      │
│                         Port: 8080                               │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP/REST
┌────────────────────────────▼────────────────────────────────────┐
│                      Main Server (Ktor)                          │
│         AI Chat Orchestration + Tool Calling                     │
│                         Port: 8080                               │
└─┬──────┬──────┬─────────┬──────────┬────────────┬──────────┬───┘
  │      │      │         │          │            │          │
  │      │      │         │          │            │          │
┌─▼───┐ ┌▼────┐ ┌▼──────┐ ┌▼────────┐ ┌▼─────────┐ ┌▼────────┐ ┌▼────┐
│Giga │ │Open │ │Notes  │ │News     │ │Vectorizer│ │RAG      │ │MCP  │
│Chat │ │Router│ │Service│ │Service  │ │Service   │ │Service  │ │Svrs │
│API  │ │API  │ │:8084  │ │:8087    │ │:8090     │ │:8091    │ │:808x│
└─────┘ └─────┘ └───┬───┘ └────┬────┘ └────┬─────┘ └────┬────┘ └─────┘
                    │          │           │            │
                 ┌──▼──┐    ┌──▼──┐     ┌──▼──┐      ┌──▼──┐
                 │Notes│    │News │     │Vec  │      │Vec  │
                 │  DB │    │ DB  │     │ DB  │      │ DB  │
                 │:5432│    │:5433│     │:5434│      │:5434│
                 └─────┘    └─────┘     └─────┘      └─────┘
                                           ▲
                                           │
                                      ┌────▼─────┐
                                      │  Ollama  │
                                      │nomic-text│
                                      └──────────┘
```

## Core Components

### 1. Frontend Layer (composeApp)

**Technology**: Kotlin/JS + Compose Multiplatform (Wasm target)

**Responsibilities**:

- User interface rendering
- State management (MVVM pattern with StateFlow)
- HTTP API client for backend communication
- Real-time UI updates

**Key Components**:

- `ChatScreen.kt` - Main UI with chat interface
- `ChatViewModel.kt` - State management and business logic
- `ChatApi.kt` - HTTP client wrapper
- `App.kt` - Application entry point

**Communication**: REST API calls to server (port 8080)

### 2. Server Layer (server)

**Technology**: Ktor (Netty engine) + Koin DI

**Responsibilities**:

- Request routing and validation
- AI provider orchestration (Strategy pattern)
- Conversation history management
- Automatic summarization
- Tool calling coordination
- RAG context enrichment

**Architecture Patterns**:

- **Strategy Pattern**: `ProviderHandler<T>` for different AI providers
- **Adapter Pattern**: Adapters for GigaChat and OpenRouter APIs
- **Repository Pattern**: `MessageRepository` for data persistence
- **Dependency Injection**: Koin for loose coupling

**Key Services**:

- `ChatService` - Main orchestrator
- `ProviderHandler` - Generic message processing
- `SummarizationService` - Auto-summarization
- `ToolExecutionService` - MCP tool calling
- `RagClient` - RAG service integration

**Flow**:

```
Request → ChatRouting → ChatService → ProviderHandler → AI Client
                            ↓              ↓
                     RAG Context    Summarization
                            ↓              ↓
                     MessageRepository  Database
```

### 3. Shared Module (shared)

**Technology**: Kotlin Multiplatform (commonMain)

**Purpose**: Platform-independent data models shared between frontend and backend

**Models**:

- **Chat**: `ChatMessage`, `ChatResponse`, `SendMessageRequest`
- **Notes**: `Note`, `CreateNoteRequest`, `UpdateNoteRequest`
- **News**: `Article`, `CreateArticleRequest`, `UpdateArticleRequest`
- **RAG**: `SearchRequest`, `SearchResponse`
- **Vectorizer**: `TextVectorizeRequest`, `TextVectorizeResponse`

**Source Sets**:

- `commonMain` - Shared code
- `jsMain` / `wasmJsMain` - Frontend-specific
- `jvmMain` - Backend-specific

## Backend Services

### 4. Notes Service (services/notes)

**Port**: 8084

**Technology**: Ktor + Exposed ORM + HikariCP + PostgreSQL

**Responsibilities**:

- CRUD operations for notes
- Persistence with PostgreSQL
- RESTful API

**Database Schema**:

```sql
CREATE TABLE notes (
    id SERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    priority VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**API Endpoints**:

- `GET /api/notes` - List notes (pagination)
- `GET /api/notes/{id}` - Get note by ID
- `POST /api/notes` - Create note
- `PUT /api/notes/{id}` - Update note
- `DELETE /api/notes/{id}` - Delete note

### 5. News CRUD Service (services/news-crud)

**Port**: 8087

**Technology**: Ktor + Exposed ORM + PostgreSQL

**Responsibilities**:

- CRUD operations for news articles
- Full-text search
- Persistence with PostgreSQL

**Database Schema**:

```sql
CREATE TABLE articles (
    id SERIAL PRIMARY KEY,
    source_id VARCHAR(100),
    source_name VARCHAR(200),
    author VARCHAR(500),
    title VARCHAR(1000) NOT NULL,
    description TEXT,
    url VARCHAR(2000),
    urlToImage VARCHAR(2000),
    publishedAt TIMESTAMP,
    content TEXT,
    createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 6. Vectorizer Service (services/vectorizer)

**Port**: 8090

**Technology**: Ktor + PostgreSQL with pgvector + Ollama

**Responsibilities**:

- Text embedding generation using Ollama
- Vector storage with pgvector
- Similarity search infrastructure

**Architecture**:

```
Text Input → OllamaClient → Ollama API → nomic-embed-text model
                ↓
        768-dim embedding
                ↓
        PostgreSQL + pgvector
        HNSW index (cosine distance)
```

**API Endpoints**:

- `POST /api/embed` - Store text with embedding
- `POST /api/vectorize` - Get embedding for text
- `POST /api/search` - Vector similarity search

**Database**:

- pgvector extension for vector operations
- HNSW indexing for fast similarity search
- Cosine distance metric

### 7. RAG Service (services/rag) **NEW**

**Port**: 8091

**Technology**: Ktor + PostgreSQL (shared with Vectorizer)

**Responsibilities**:

- Semantic search over vectorized documents
- Context retrieval for LLM prompts
- Integration with main chat server

**Architecture**:

```
Query Text → VectorizerClient → Embedding
                ↓
        EmbeddingRepository
                ↓
        PostgreSQL cosine similarity search
                ↓
        Top-K similar chunks
                ↓
        Return as context
```

**API Endpoints**:

- `POST /api/rag/search` - Search similar text chunks

**Integration with Main Server**:

1. User enables "Use RAG" checkbox
2. Server calls RAG service with query
3. RAG returns relevant text chunks
4. Server enriches system prompt with context
5. LLM responds with knowledge-aware answer

## MCP Servers

### 8. MCP Notes (mcp/notes)

**Ports**: 8082 (HTTP), 8443 (HTTPS)

**Technology**: Ktor + MCP SDK

**Capabilities**:

- Notes management tools
- Currency exchange rates (ЦБ РФ API)
- SSL/TLS with auto-generated certificates

### 9. MCP NewsAPI (mcp/newsapi)

**Ports**: 8085 (HTTP), 8444 (HTTPS)

**Technology**: Ktor + MCP SDK

**Capabilities**:

- News search from NewsAPI.org
- Top headlines retrieval
- News source listing

### 10. MCP NewsCRUD (mcp/newscrud)

**Ports**: 8086 (HTTP), 8445 (HTTPS)

**Technology**: Ktor + MCP SDK

**Capabilities**:

- Proxy to News CRUD service
- CRUD operations as MCP tools
- Search functionality

### 11. MCP Notes Polling (mcp/notes-polling)

**Ports**: 8088 (HTTP), 8447 (HTTPS)

**Technology**: Ktor + Docker SDK

**Capabilities**:

- Docker container management
- Scheduler service control
- Automated polling triggers

## Data Flow

### Chat Message Flow

```
1. User types message in UI
   ↓
2. ChatViewModel.sendMessage()
   ↓
3. ChatApi.sendMessage() → HTTP POST /api/send-message
   ↓
4. Server: ChatRouting receives request
   ↓
5. ChatService.processUserMessage()
   ├─→ [IF useRag] RagClient.searchSimilar() → Enrich system prompt
   ├─→ Save to MessageRepository
   ├─→ Check summarization threshold
   ↓
6. Route to ProviderHandler (GigaChat or OpenRouter)
   ├─→ [IF enableTools] Fetch MCP tools
   ↓
7. AI API call with enriched context
   ↓
8. Response processing
   ├─→ [IF tool calls] Execute tools via ToolExecutionService
   ├─→ Save assistant message
   ↓
9. Return ChatResponse to frontend
   ↓
10. UI updates with bot message
```

### RAG-Enhanced Flow

```
User Query → RagClient → POST /api/rag/search
                ↓
        RagService.searchSimilar()
                ↓
        VectorizerClient.vectorize(query)
                ↓
        Query embedding (768-dim)
                ↓
        EmbeddingRepository.searchSimilar()
                ↓
        SELECT chunk_text FROM embeddings
        ORDER BY embedding <=> query_vector
        LIMIT 5
                ↓
        Return top-K chunks
                ↓
        ChatService enriches system prompt:
        "=== Relevant Context ===
         1. chunk1
         2. chunk2
         ...
         === End Context ==="
                ↓
        Send to AI with enriched context
```

## Design Patterns

### 1. Strategy Pattern

**Used in**: Chat provider handling

```kotlin
interface AiClient<T : ConversationMessage> {
    suspend fun sendMessage(messages: List<T>, ...): String
}

class GigaChatClientAdapter : AiClient<GigaChatMessage>
class OpenRouterClientAdapter : AiClient<OpenRouterMessage>
```

### 2. Adapter Pattern

**Used in**: AI API client wrappers

### 3. Repository Pattern

**Used in**: Data access layer

- `MessageRepository`
- `NoteRepository`
- `NewsRepository`
- `EmbeddingRepository`

### 4. Dependency Injection

**Used in**: All services (Koin)

### 5. MVVM Pattern

**Used in**: Frontend architecture

- Model: Shared data classes
- View: Composable UI components
- ViewModel: ChatViewModel with StateFlow

## Security Considerations

⚠️ **Current Status**: DEVELOPMENT ONLY

**Issues**:

- Open CORS policy (`anyHost()`)
- No authentication/authorization
- No rate limiting
- HTTP instead of HTTPS (except MCP servers)
- Secrets in configuration files

**Production Requirements**:

- JWT authentication
- HTTPS enforcement
- Rate limiting
- Input validation and sanitization
- Secrets management (env vars, vault)
- CORS whitelist

## Scalability

**Current Limitations**:

- In-memory conversation history (stateful)
- Single-instance deployment
- No load balancing

**Scalability Solutions**:

1. **Stateless Architecture**:
    - Move history to Redis/Database
    - Session affinity for routing

2. **Horizontal Scaling**:
    - Multiple server instances
    - Load balancer (nginx, HAProxy)
    - Shared cache (Redis)

3. **Database Optimization**:
    - Read replicas
    - Connection pooling (already using HikariCP)
    - Query optimization

4. **Caching**:
    - Redis for session data
    - API response caching
    - Embedding cache

## Technology Decisions

| Component     | Technology            | Rationale                            |
|---------------|-----------------------|--------------------------------------|
| Backend       | Ktor                  | Lightweight, Kotlin-first, async     |
| Frontend      | Compose MP            | Multiplatform UI, modern declarative |
| DI            | Koin                  | Kotlin-native, simple API            |
| ORM           | Exposed               | Kotlin DSL, type-safe                |
| Serialization | kotlinx.serialization | Multiplatform, fast                  |
| HTTP Client   | Ktor CIO              | Non-blocking, efficient              |
| Database      | PostgreSQL            | Robust, supports pgvector            |
| Vector Search | pgvector              | Native PG extension, efficient       |
| Embeddings    | Ollama                | Local, open-source, fast             |

## Future Enhancements

1. **Authentication & Authorization**
2. **WebSocket support** for real-time updates
3. **File upload** for document processing
4. **Multi-language support** (i18n)
5. **Advanced RAG**:
    - Document parsing (PDF, DOCX)
    - Hybrid search (semantic + keyword)
    - Re-ranking models
6. **Monitoring & Observability**:
    - Prometheus metrics
    - Grafana dashboards
    - Distributed tracing
7. **CI/CD Pipeline**
8. **Kubernetes deployment**
