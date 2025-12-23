# Shared Module - Common Data Models

## Overview

The Shared module contains platform-independent data models and types used across frontend (Compose web), backend (Ktor
server), and all microservices. It's built with Kotlin Multiplatform to ensure type safety and code reuse.

**Technology**: Kotlin Multiplatform + kotlinx.serialization
**Source Sets**: `commonMain`, `jsMain`/`wasmJsMain`, `jvmMain`

## Architecture

```
Shared Module
├── commonMain (all platforms)
│   └── models/
│       ├── chat/     - Chat messages and responses
│       ├── notes/    - Note CRUD models
│       ├── news/     - News article models
│       ├── rag/      - RAG search models
│       └── vectorizer/ - Text vectorization models
├── jsMain (JS/Wasm frontend)
└── jvmMain (JVM backend/services)
```

## Module Structure

### commonMain

Platform-independent code shared by all targets:

- Data classes with `@Serializable` annotation
- Enums for type-safe constants
- Validation logic (future enhancement)

### jsMain / wasmJsMain

Frontend-specific extensions:

- Currently unused (all models in commonMain)
- Future: Browser-specific utilities

### jvmMain

Backend-specific extensions:

- Currently unused (all models in commonMain)
- Future: Server-side validation, database mapping utilities

## Data Models

### Chat Models

#### 1. ChatMessage

**Purpose**: Represents a single message in the conversation

**File**: `shared/src/commonMain/kotlin/.../models/ChatMessage.kt`

```kotlin
@Serializable
data class ChatMessage(
    val text: String,
    val sender: SenderType
)
```

**Fields**:

- `text`: Message content
- `sender`: Either `USER` or `BOT`

**Usage**:

- Frontend: Display in message list
- Backend: Store in conversation history

#### 2. SenderType

**Purpose**: Enum for message sender identification

```kotlin
@Serializable
enum class SenderType {
    @SerialName("USER") USER,
    @SerialName("BOT") BOT
}
```

**Values**:

- `USER`: Message from human user
- `BOT`: Message from AI assistant

#### 3. SendMessageRequest

**Purpose**: Request payload for sending a message to AI

**File**: `shared/src/commonMain/kotlin/.../models/SendMessageRequest.kt`

```kotlin
@Serializable
data class SendMessageRequest(
    val text: String,
    val systemPrompt: String = "",
    val temperature: Double = 0.7,
    val provider: String = "gigachat",
    val model: String? = null,
    val maxTokens: Int? = null,
    val enableTools: Boolean = true,
    val useRag: Boolean = false
)
```

**Fields**:

- `text`: User's message (required)
- `systemPrompt`: Custom system instructions (optional)
- `temperature`: Response randomness, 0.0-2.0 (default: 0.7)
- `provider`: AI provider, "gigachat" or "openrouter" (default: "gigachat")
- `model`: Model name for OpenRouter (optional, e.g., "anthropic/claude-3.5-sonnet")
- `maxTokens`: Max response length for OpenRouter (optional)
- `enableTools`: Enable MCP tool calling (default: true)
- `useRag`: Enable RAG context retrieval (default: false)

**Example**:

```json
{
  "text": "What is Kotlin Multiplatform?",
  "systemPrompt": "You are a Kotlin expert",
  "temperature": 0.7,
  "provider": "openrouter",
  "model": "anthropic/claude-3.5-sonnet",
  "maxTokens": 2000,
  "enableTools": true,
  "useRag": true
}
```

#### 4. ChatResponse

**Purpose**: Response from AI with metadata

**File**: `shared/src/commonMain/kotlin/.../models/ChatResponse.kt`

```kotlin
@Serializable
data class ChatResponse(
    val text: String,
    val status: ResponseStatus,
    val tokenUsage: TokenUsage? = null,
    val lastResponseTokenUsage: TokenUsage? = null,
    val responseTimeMs: Long? = null
)
```

**Fields**:

- `text`: AI's response message
- `status`: `SUCCESS` or `ERROR`
- `tokenUsage`: Cumulative token usage (OpenRouter only)
- `lastResponseTokenUsage`: Last response token usage (OpenRouter only)
- `responseTimeMs`: Response time in milliseconds (optional)

#### 5. ResponseStatus

**Purpose**: Enum for response status

```kotlin
@Serializable
enum class ResponseStatus {
    @SerialName("SUCCESS") SUCCESS,
    @SerialName("ERROR") ERROR
}
```

#### 6. TokenUsage

**Purpose**: Token usage tracking for OpenRouter

```kotlin
@Serializable
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
```

**Fields**:

- `promptTokens`: Tokens in user messages + system prompt + history
- `completionTokens`: Tokens in AI response
- `totalTokens`: Sum of prompt and completion tokens

**Example**:

```json
{
  "promptTokens": 150,
  "completionTokens": 200,
  "totalTokens": 350
}
```

### Notes Models

#### 1. Note

**Purpose**: Represents a note with metadata

**File**: `shared/src/commonMain/kotlin/.../models/notes/Note.kt`

```kotlin
@Serializable
data class Note(
    val id: Int,
    val title: String,
    val content: String,
    val priority: NotePriority? = null,
    val createdAt: String,
    val updatedAt: String
)
```

**Fields**:

- `id`: Unique identifier
- `title`: Note title (max 500 chars)
- `content`: Note content (text)
- `priority`: Optional priority level
- `createdAt`: ISO 8601 timestamp
- `updatedAt`: ISO 8601 timestamp

#### 2. NotePriority

**Purpose**: Enum for note priority levels

```kotlin
@Serializable
enum class NotePriority {
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH
}
```

#### 3. CreateNoteRequest

**Purpose**: Request for creating a new note

```kotlin
@Serializable
data class CreateNoteRequest(
    val title: String,
    val content: String,
    val priority: String? = null
)
```

**Validation** (server-side):

- `title`: Required, non-blank
- `content`: Required, non-blank
- `priority`: Optional, must be "low", "medium", or "high"

#### 4. UpdateNoteRequest

**Purpose**: Request for updating an existing note

```kotlin
@Serializable
data class UpdateNoteRequest(
    val title: String? = null,
    val content: String? = null,
    val priority: String? = null
)
```

**Note**: All fields are optional (partial update)

#### 5. NoteResponse

**Purpose**: Response for note operations

```kotlin
@Serializable
data class NoteResponse(
    val success: Boolean,
    val message: String,
    val note: Note? = null
)
```

### News Models

#### 1. Article

**Purpose**: Represents a news article

**File**: `shared/src/commonMain/kotlin/.../models/news/Article.kt`

```kotlin
@Serializable
data class Article(
    val id: Int? = null,
    val sourceId: String? = null,
    val sourceName: String? = null,
    val author: String? = null,
    val title: String,
    val description: String? = null,
    val url: String? = null,
    val urlToImage: String? = null,
    val publishedAt: String? = null,
    val content: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
```

**Fields**:

- `id`: Unique identifier (null for new articles)
- `sourceId`: Source identifier (e.g., "bbc-news")
- `sourceName`: Human-readable source name
- `author`: Article author
- `title`: Article title (required, max 1000 chars)
- `description`: Short description
- `url`: Original article URL
- `urlToImage`: Featured image URL
- `publishedAt`: Publication date (ISO 8601)
- `content`: Full article content
- `createdAt`: DB creation timestamp
- `updatedAt`: DB update timestamp

#### 2. Source

**Purpose**: News source information

```kotlin
@Serializable
data class Source(
    val id: String?,
    val name: String
)
```

#### 3. CreateArticleRequest

**Purpose**: Request for creating a news article

```kotlin
@Serializable
data class CreateArticleRequest(
    val sourceId: String? = null,
    val sourceName: String? = null,
    val author: String? = null,
    val title: String,
    val description: String? = null,
    val url: String? = null,
    val urlToImage: String? = null,
    val publishedAt: String? = null,
    val content: String? = null
)
```

#### 4. UpdateArticleRequest

**Purpose**: Request for updating an article

```kotlin
@Serializable
data class UpdateArticleRequest(
    val title: String? = null,
    val description: String? = null,
    val content: String? = null,
    val author: String? = null,
    val url: String? = null,
    val urlToImage: String? = null
)
```

### RAG Models

#### 1. SearchRequest

**Purpose**: Request for RAG semantic search

**File**: `shared/src/commonMain/kotlin/.../models/rag/SearchRequest.kt`

```kotlin
@Serializable
data class SearchRequest(
    val query: String,
    val limit: Int = 5
)
```

**Fields**:

- `query`: Search query text
- `limit`: Max number of results (default: 5)

**Example**:

```json
{
  "query": "How does Kotlin Multiplatform work?",
  "limit": 5
}
```

#### 2. SearchResponse

**Purpose**: Response with similar text chunks

```kotlin
@Serializable
data class SearchResponse(
    val results: List<String>
)
```

**Fields**:

- `results`: List of similar text chunks (ordered by relevance)

**Example**:

```json
{
  "results": [
    "Kotlin Multiplatform allows you to share code between platforms...",
    "The Kotlin compiler generates platform-specific code...",
    "Compose Multiplatform extends Kotlin Multiplatform to UI..."
  ]
}
```

### Vectorizer Models

#### 1. TextVectorizeRequest

**Purpose**: Request to vectorize a single text

**File**: `shared/src/commonMain/kotlin/.../models/vectorizer/TextVectorizeRequest.kt`

```kotlin
@Serializable
data class TextVectorizeRequest(
    val text: String,
    val model: String? = null
)
```

**Fields**:

- `text`: Text to vectorize (required)
- `model`: Embedding model (optional, default: "nomic-embed-text")

#### 2. TextVectorizeResponse

**Purpose**: Response with text embedding

```kotlin
@Serializable
data class TextVectorizeResponse(
    val embedding: List<Double>,
    val dimension: Int,
    val model: String
)
```

**Fields**:

- `embedding`: 768-dimensional vector
- `dimension`: Vector dimension (always 768 for nomic-embed-text)
- `model`: Model used for embedding

#### 3. VectorizeRequest

**Purpose**: Request to vectorize a folder of documents

```kotlin
@Serializable
data class VectorizeRequest(
    val folderPath: String,
    val model: String? = null
)
```

#### 4. VectorizeResponse

**Purpose**: Response for folder vectorization

```kotlin
@Serializable
data class VectorizeResponse(
    val success: Boolean,
    val filesProcessed: Int,
    val chunksCreated: Int,
    val filesSkipped: List<String>,
    val errors: List<String>,
    val message: String
)
```

**Example**:

```json
{
  "success": true,
  "filesProcessed": 15,
  "chunksCreated": 127,
  "filesSkipped": [],
  "errors": [],
  "message": "Successfully vectorized 15 files (127 chunks)"
}
```

## Constants

### SERVER_PORT

**File**: `shared/src/commonMain/kotlin/.../Constants.kt`

```kotlin
const val SERVER_PORT = 8080
```

**Usage**: Frontend uses this to construct server URLs

## Serialization

### kotlinx.serialization

All models use `@Serializable` annotation for automatic JSON serialization/deserialization:

```kotlin
@Serializable
data class ChatMessage(
    val text: String,
    val sender: SenderType
)
```

**Features**:

- Compile-time serialization (no reflection)
- Multiplatform support
- Custom serializers available
- Format-agnostic (JSON, ProtoBuf, CBOR)

### JSON Configuration

**Backend** (Ktor):

```kotlin
install(ContentNegotiation) {
    json(Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    })
}
```

**Frontend** (Ktor Client):

```kotlin
HttpClient(Js) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
}
```

### @SerialName

Used for custom JSON field names:

```kotlin
@Serializable
enum class SenderType {
    @SerialName("USER") USER,
    @SerialName("BOT") BOT
}
```

**JSON**: `"sender": "USER"` (not `"sender": "SenderType.USER"`)

## Validation

### Current State

Validation is done server-side (in service layers):

- Notes: Title and content non-blank checks
- News: Title non-blank check
- Chat: Text non-blank check

### Future Enhancement

Move validation to shared module:

```kotlin
@Serializable
data class CreateNoteRequest(
    val title: String,
    val content: String,
    val priority: String? = null
) {
    init {
        require(title.isNotBlank()) { "Title cannot be blank" }
        require(content.isNotBlank()) { "Content cannot be blank" }
        priority?.let {
            require(it in listOf("low", "medium", "high")) {
                "Priority must be low, medium, or high"
            }
        }
    }
}
```

**Benefits**:

- Catch errors earlier (client-side)
- Consistent validation across platforms
- Better error messages

## Adding New Models

### Step 1: Create Model in commonMain

```kotlin
// shared/src/commonMain/kotlin/.../models/example/Example.kt
package ru.sber.cb.aichallenge_one.models.example

import kotlinx.serialization.Serializable

@Serializable
data class Example(
    val id: Int,
    val name: String,
    val createdAt: String
)
```

### Step 2: Use in Frontend

```kotlin
// composeApp/src/webMain/kotlin/...
import ru.sber.cb.aichallenge_one.models.example.Example

val example = Example(id = 1, name = "Test", createdAt = "2025-01-15T10:00:00Z")
```

### Step 3: Use in Backend

```kotlin
// server/src/main/kotlin/...
import ru.sber.cb.aichallenge_one.models.example.Example

suspend fun getExample(): Example {
    return Example(id = 1, name = "Test", createdAt = Instant.now().toString())
}
```

## Build Configuration

### build.gradle.kts

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val jsMain by getting
        val wasmJsMain by getting
        val jvmMain by getting
    }
}
```

### Version Catalog (libs.versions.toml)

```toml
[versions]
kotlin = "2.2.21"
kotlinx-serialization = "1.8.0"

[libraries]
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
```

## Testing

### Current State

No tests for models (they're simple data classes).

### Future Enhancement

Add validation tests:

```kotlin
class CreateNoteRequestTest {
    @Test
    fun `blank title should throw exception`() {
        assertThrows<IllegalArgumentException> {
            CreateNoteRequest(title = "", content = "Content")
        }
    }

    @Test
    fun `invalid priority should throw exception`() {
        assertThrows<IllegalArgumentException> {
            CreateNoteRequest(title = "Title", content = "Content", priority = "invalid")
        }
    }
}
```

## Best Practices

### 1. Immutable Data Classes

Use `val` (immutable) instead of `var` (mutable):

```kotlin
// Good
@Serializable
data class Example(val name: String)

// Bad
@Serializable
data class Example(var name: String)
```

### 2. Optional Fields with Defaults

Use default values for optional fields:

```kotlin
@Serializable
data class SendMessageRequest(
    val text: String,
    val temperature: Double = 0.7,  // Default value
    val provider: String = "gigachat"
)
```

### 3. Nullable vs Default

- **Nullable**: Field can be absent in JSON
- **Default**: Field has a fallback value

```kotlin
val model: String? = null          // Can be absent
val temperature: Double = 0.7      // Defaults to 0.7 if absent
```

### 4. Descriptive Names

Use clear, descriptive names:

```kotlin
// Good
data class CreateNoteRequest(val title: String, val content: String)

// Bad
data class CreateReq(val t: String, val c: String)
```

## Related Documentation

- [Server Module](server.md) - Uses models for API
- [Frontend Module](frontend.md) - Uses models for UI state
- [Services Documentation](services/) - CRUD operations with models
- [Architecture Overview](../ARCHITECTURE.md) - System design

## References

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Kotlin Data Classes](https://kotlinlang.org/docs/data-classes.html)
