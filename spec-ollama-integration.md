# Ollama Integration Technical Specification

**Version:** 1.0
**Date:** 2026-01-19
**Status:** Draft (Ready for Implementation)

## Overview

Add support for **Ollama** as a local LLM provider in the AI Chat Server. This integration enables users to run AI
models locally on their machines with full privacy (no data leaves the device).

**Key Benefits:**

- Privacy-first: all processing happens locally
- No API costs: free to use after initial model download
- Offline capability: works without internet connection
- Full MCP tools support: function calling capabilities

**Default Model:** `gemma3:1b` (lightweight, fast, suitable for most hardware)

---

## Table of Contents

1. [Technical Architecture](#technical-architecture)
2. [API Integration Strategy](#api-integration-strategy)
3. [UI/UX Design](#uiux-design)
4. [Configuration](#configuration)
5. [Error Handling](#error-handling)
6. [Streaming Implementation](#streaming-implementation)
7. [Testing Strategy](#testing-strategy)
8. [Documentation Requirements](#documentation-requirements)
9. [Implementation Checklist](#implementation-checklist)
10. [Tradeoffs & Decisions](#tradeoffs--decisions)

---

## Technical Architecture

### Component Structure

```
server/src/main/kotlin/ru/sber/cb/aichallenge_one/
├── client/
│   ├── OllamaApiClient.kt           # HTTP client for Ollama API
│   └── OllamaClientAdapter.kt       # Implements AiClient<OllamaMessage>
├── domain/
│   └── OllamaMessage.kt             # Data class implementing ConversationMessage
├── service/
│   └── OllamaProviderHandler.kt     # Extends ProviderHandler<OllamaMessage>
└── di/
    └── AppModule.kt                 # Add Ollama dependencies

shared/src/commonMain/kotlin/ru/sber/cb/aichallenge_one/models/
└── chat/
    └── AiProvider.kt                # Add OLLAMA to enum
```

### Integration Pattern

**Decision:** Full integration into existing architecture

- Implement `AiClient<OllamaMessage>` interface
- Create `OllamaProviderHandler` extending `ProviderHandler<OllamaMessage>`
- Reuse `ToolExecutionService` for MCP tools support
- Koin DI for dependency injection (singleton scope)
- Follow existing patterns from GigaChat/OpenRouter providers

### Dependency Injection

```kotlin
// AppModule.kt
val appModule = module {
    // Existing dependencies...
    single { OllamaApiClient(get()) }
    single { OllamaClientAdapter(get()) }
    single { OllamaProviderHandler(get(), get()) }
}
```

---

## API Integration Strategy

### Approach: Hybrid OpenAI-Compatible + Native Ollama

**Rationale:** Ollama provides OpenAI-compatible API mode but also has native endpoints for model management.

**Implementation:**

1. **Base Chat Functionality:** Use OpenAI-compatible mode
    - Leverage existing `OpenAIApiClient` structure
    - Add Ollama-specific configuration (baseUrl, timeout)
    - Reuse request/response formats

2. **Model Management (Future):** Native Ollama API
    - Separate methods for `listModels()`, `pullModel()`, `deleteModel()`
    - Not in initial scope (user manages models via CLI)

3. **Streaming:** Native Ollama streaming
    - Endpoint: `POST /api/generate` with `stream: true`
    - SSE transport to frontend
    - Custom parsing for Ollama response format

### Ollama API Endpoints Used

| Endpoint        | Method | Purpose                             | Streaming |
|-----------------|--------|-------------------------------------|-----------|
| `/api/chat`     | POST   | Chat completion (OpenAI-compatible) | Yes       |
| `/api/generate` | POST   | Native generation                   | Yes       |
| `/api/tags`     | GET    | List installed models               | No        |

### Message Format

**OllamaMessage Data Class:**

```kotlin
@Serializable
data class OllamaMessage(
    override val role: String,  // "user", "assistant", "system"
    override val content: String,
    val timestamp: Long = System.currentTimeMillis()
) : ConversationMessage
```

**Request Body:**

```json
{
  "model": "gemma3:1b",
  "messages": [
    {"role": "user", "content": "Hello"}
  ],
  "stream": true,
  "options": {
    "temperature": 0.7,
    "num_predict": 2048
  }
}
```

---

## UI/UX Design

### Provider Selection

**Decision:** Add Ollama to existing dropdown

- Location: Same dropdown as GigaChat/OpenRouter
- Label: "Ollama (Local)"
- No explicit privacy indicators (implicit understanding)
- No visual distinction from cloud providers

### Streaming UX

**Decision:** Streaming + Progress Bar

Implementation requirements:

1. **Server-Side:** SSE endpoint for real-time streaming
2. **Frontend:** EventSource for receiving stream chunks
3. **Visual Feedback:**
    - Pulsing loading indicator during generation
    - Progressive text rendering (token-by-token appearance)
    - "Stop generation" button (optional, future enhancement)

### Model Selection

**Decision:** Hardcoded `gemma3:1b` in config

- No dropdown for model selection in UI
- Model name configured in `application.conf`
- Users can edit config file to change model
- Future: Add model discovery API for dynamic list

### Metrics Display

**Decision:** Show performance metrics in UI

After response completion, display:

- **Response Time:** Total generation time (seconds)
- **Tokens/Second:** Generation speed
- **Model Used:** `gemma3:1b` (or configured model)

**UI Location:** Below message bubble, same level as token usage stats

---

## Configuration

### application.conf Structure

**Decision:** Nested `ollama {}` section

```hocon
ollama {
    # Base URL for Ollama API
    baseUrl = "http://localhost:11434"
    baseUrl = ${?OLLAMA_BASE_URL}

    # Default model to use
    model = "gemma3:1b"
    model = ${?OLLAMA_MODEL}

    # Request timeout in milliseconds (higher than cloud providers)
    timeout = 120000  # 2 minutes default
    timeout = ${?OLLAMA_TIMEOUT}

    # Enable summarization for chat history (default: false for local models)
    enableSummarization = false
    enableSummarization = ${?OLLAMA_ENABLE_SUMMARIZATION}

    # Enable MCP tools/function calling (default: true)
    enableTools = true
    enableTools = ${?OLLAMA_ENABLE_TOOLS}

    # Streaming buffer size in milliseconds
    streamFlushInterval = 50  # Flush every 50ms
    streamFlushInterval = ${?OLLAMA_STREAM_FLUSH_INTERVAL}
}
```

### Environment Variables

```bash
# Override Ollama configuration
set OLLAMA_BASE_URL=http://localhost:11434
set OLLAMA_MODEL=gemma3:1b
set OLLAMA_TIMEOUT=120000
set OLLAMA_ENABLE_SUMMARIZATION=false
set OLLAMA_ENABLE_TOOLS=true
```

### Default Values

| Parameter             | Default                  | Rationale                         |
|-----------------------|--------------------------|-----------------------------------|
| `baseUrl`             | `http://localhost:11434` | Ollama standard port              |
| `model`               | `gemma3:1b`              | Lightweight, fast, good quality   |
| `timeout`             | `120000` (2 min)         | Local models slower than cloud    |
| `enableSummarization` | `false`                  | Local models have no token limits |
| `enableTools`         | `true`                   | Full feature parity with cloud    |
| `streamFlushInterval` | `50ms`                   | Balance latency vs throughput     |

---

## Error Handling

### Approach: Custom Error Messages

**Decision:** Provide actionable error messages for common issues

### Error Scenarios & Messages

| Error Type             | Detection                          | User Message                                                                                      | Action                  |
|------------------------|------------------------------------|---------------------------------------------------------------------------------------------------|-------------------------|
| **Connection Refused** | `ConnectException` on port 11434   | "Ollama is not running. Please start Ollama by running `ollama serve` in your terminal."          | Start Ollama            |
| **Model Not Found**    | API returns 404 for model          | "Model 'gemma3:1b' not found. Please download it: `ollama pull gemma3:1b`"                        | Pull model              |
| **Out of Memory**      | API returns 500 or timeout         | "Ollama ran out of memory. Try a smaller model or close other applications."                      | Reduce model size       |
| **Timeout**            | Request exceeds configured timeout | "Generation timed out after 2 minutes. Try reducing conversation history or use a smaller model." | Retry with less context |
| **Invalid Response**   | Malformed API response             | "Ollama returned an invalid response. Check server logs for details."                             | Check logs              |

### Error Handling Flow

```
User sends message
    ↓
OllamaApiClient.send()
    ↓
Error occurs?
    ↓ Yes
Map to user-friendly message
    ↓
Return ChatResponse(status=ERROR, text=customMessage)
    ↓
Display error in UI
```

### Validation Strategy

**Decision:** Simple validation (no health-check, no retries)

- Validate connection on first request (not at startup)
- Return error immediately if Ollama unavailable
- No automatic retry logic (keep it simple)
- User must fix issues manually

---

## Streaming Implementation

### Transport Protocol: Server-Sent Events (SSE)

**Decision:** SSE endpoint + EventSource on frontend

### Server-Side Implementation

```kotlin
// OllamaRouting.kt
routing {
    route("/api/send-message-ollama") {
        post {
            val request = call.receive<SendMessageRequest>
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                ollamaService.streamMessage(request, this)
            }
        }
    }
}
```

### OllamaApiClient Streaming

```kotlin
suspend fun streamGenerate(
    request: OllamaRequest,
    output: Writer
) {
    val httpResponse = client.post("$baseUrl/api/generate") {
        setBody(request)
        parameter("stream", "true")
    }

    httpResponse.bodyAsChannel().toByteArray().let { data ->
        // Parse SSE format: data: {...}
        val lines = data.decodeToString().split("\n")
        for (line in lines) {
            if (line.startsWith("data: ")) {
                val json = line.removePrefix("data: ")
                val chunk = Json.decodeFromString<OllamaStreamChunk>(json)
                output.write("data: ${Json.encodeToString(chunk)}\n\n")
                output.flush()
            }
        }
    }
}
```

### Frontend Implementation

```kotlin
// ChatApi.kt
suspend fun streamOllamaMessage(
    request: SendMessageRequest,
    onChunk: (String) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    val eventSource = EventSource("$baseUrl/api/send-message-ollama") {
        // Configure SSE connection
    }

    eventSource.addEventListener("message") { event ->
        val chunk = Json.decodeFromString<OllamaStreamChunk>(event.data)
        onChunk(chunk.response ?: "")
    }

    eventSource.addEventListener("error") { event ->
        onError(event.data ?: "Stream error")
    }

    eventSource.addEventListener("done") {
        onComplete()
    }
}
```

### Stream Chunk Format

```json
{
  "model": "gemma3:1b",
  "created_at": "2026-01-19T10:30:00Z",
  "response": "Hello",  // Partial text
  "done": false
}
```

```json
{
  "model": "gemma3:1b",
  "created_at": "2026-01-19T10:30:05Z",
  "response": "",
  "done": true,
  "context": [1, 2, 3, ...],
  "eval_count": 42,
  "eval_duration": 3500000000
}
```

### Retry on Partial Failure

**Decision:** Automatic retry with reduced context

If streaming fails mid-generation:

1. Detect failure (no chunks for 30 seconds)
2. Retry automatically with 50% of conversation history
3. If fails again, show error message
4. Max retries: 2

```kotlin
private fun shouldRetry(failureCount: Int, historySize: Int): Boolean {
    return failureCount < 2 && historySize > 2
}

private suspend fun retryWithReducedContext(
    originalRequest: SendMessageRequest
): SendMessageRequest {
    val reducedHistory = originalRequest.history.take(originalRequest.history.size / 2)
    return originalRequest.copy(history = reducedHistory)
}
```

---

## Testing Strategy

### Approach: Unit + Integration Tests

**Decision:** Write comprehensive tests (existing code has none)

### Unit Tests

**Test File:** `OllamaApiClientTest.kt`

```kotlin
class OllamaApiClientTest {
    @Test
    fun `sendMessage returns valid response`() {
        // Mock HttpClient
        // Test request serialization
        // Test response parsing
    }

    @Test
    fun `streamGenerate emits chunks correctly`() {
        // Mock SSE stream
        // Verify chunk parsing
        // Test error handling
    }

    @Test
    fun `connection refused throws custom exception`() {
        // Mock connection failure
        // Verify exception type
    }
}
```

### Integration Tests

**Test File:** `OllamaIntegrationTest.kt`

**Requirements:**

- Running Ollama instance (or Docker container in CI)
- Pre-pulled `gemma3:1b` model
- Test server startup

```kotlin
class OllamaIntegrationTest {
    @BeforeTest
    fun setup() {
        // Ensure Ollama is running
        // Pull test model if needed
    }

    @Test
    fun `end-to-end chat message flow`() {
        // Start Ktor test server
        // Send real message to Ollama
        // Verify response
    }

    @Test
    fun `streaming works end-to-end`() {
        // Start server
        // Send streaming request
        // Verify SSE chunks received
    }

    @Test
    fun `MCP tools integration`() {
        // Test tool calling workflow
        // Verify tool execution
    }
}
```

### Test Coverage Goals

| Component             | Coverage Target     |
|-----------------------|---------------------|
| OllamaApiClient       | 90%                 |
| OllamaClientAdapter   | 85%                 |
| OllamaProviderHandler | 80%                 |
| Routing               | 75%                 |
| Integration Tests     | Critical paths only |

### CI/CD Integration

```yaml
# .github/workflows/ollama-test.yml
name: Ollama Integration Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Start Ollama
        run: docker run -d -p 11434:11434 ollama/ollama
      - name: Pull Model
        run: docker exec <container> ollama pull gemma3:1b
      - name: Run Tests
        run: ./gradlew :server:test --tests OllamaIntegrationTest
```

---

## Documentation Requirements

### Approach: Full Section in CLAUDE.md

**Decision:** Comprehensive documentation update

### Documentation Structure

```markdown
## Ollama Integration

### Overview
- What is Ollama?
- Why local LLM?
- Privacy benefits

### Installation
1. Install Ollama
   - Windows: Download installer
   - Linux: curl install script
   - macOS: Homebrew

2. Pull default model
   ```bash
   ollama pull gemma3:1b
   ```

3. Start Ollama server
   ```bash
   ollama serve
   ```

### Configuration

- application.conf settings
- Environment variables
- Model selection

### Usage

- Select Ollama in dropdown
- Start chatting
- Streaming behavior

### Troubleshooting

- Connection refused → Start Ollama
- Model not found → Pull model
- Out of memory → Use smaller model
- Slow generation → Expected behavior

### Performance Tips

- Model size recommendations
- Hardware requirements
- Benchmarking metrics

### Advanced

- Changing models
- Multiple model setups
- Custom model parameters

```

### Code Documentation

```kotlin
/**
 * Ollama API Client for local LLM integration.
 *
 * Supports chat completions and streaming generation using Ollama's OpenAI-compatible API.
 *
 * @property baseUrl Ollama server URL (default: http://localhost:11434)
 * @property model Default model to use (default: gemma3:1b)
 * @property timeout Request timeout in milliseconds (default: 120000)
 *
 * @see <a href="https://github.com/ollama/ollama">Ollama Documentation</a>
 */
class OllamaApiClient(
    private val baseUrl: String,
    private val model: String,
    private val timeout: Long
) { ... }
```

---

## Implementation Checklist

### Phase 1: Core Infrastructure (Days 1-2)

- [ ] Create `OllamaMessage` data class in shared module
- [ ] Add `OLLAMA` to `AiProvider` enum
- [ ] Implement `OllamaApiClient` with basic chat support
- [ ] Create `OllamaClientAdapter` implementing `AiClient<OllamaMessage>`
- [ ] Add configuration loading from `application.conf`
- [ ] Register dependencies in `AppModule.kt`

### Phase 2: Handler & Service Integration (Days 3-4)

- [ ] Implement `OllamaProviderHandler` extending `ProviderHandler<OllamaMessage>`
- [ ] Add Ollama support to `ChatService.processOllamaMessage()`
- [ ] Integrate with `ToolExecutionService` for MCP tools
- [ ] Configure summarization (disabled by default)
- [ ] Add error mapping to user-friendly messages

### Phase 3: Streaming Implementation (Days 5-6)

- [ ] Implement SSE streaming endpoint in routing
- [ ] Add `streamGenerate()` method to `OllamaApiClient`
- [ ] Parse Ollama SSE response format
- [ ] Implement retry logic with reduced context
- [ ] Frontend: Add EventSource for SSE consumption
- [ ] Frontend: Update UI to render streaming tokens

### Phase 4: UI Integration (Days 7-8)

- [ ] Add "Ollama (Local)" to provider dropdown
- [ ] Implement metrics display (response time, tokens/sec)
- [ ] Add progress indicator for streaming
- [ ] Test error message display in UI
- [ ] Verify provider switching works correctly

### Phase 5: Testing (Days 9-10)

- [ ] Write unit tests for `OllamaApiClient`
- [ ] Write unit tests for `OllamaClientAdapter`
- [ ] Write unit tests for `OllamaProviderHandler`
- [ ] Set up integration test environment
- [ ] Write integration tests with real Ollama
- [ ] Configure CI/CD pipeline
- [ ] Achieve test coverage targets

### Phase 6: Documentation & Polish (Days 11-12)

- [ ] Write full Ollama section in CLAUDE.md
- [ ] Add code documentation (KDoc comments)
- [ ] Update README.md with Ollama mention
- [ ] Create troubleshooting guide
- [ ] Add performance benchmarking section
- [ ] Final code review and refactoring

### Total Estimated Time: 12 working days

---

## Tradeoffs & Decisions

### Decision Record

| Decision                     | Option Chosen                       | Rejected Options                                     | Rationale                                       |
|------------------------------|-------------------------------------|------------------------------------------------------|-------------------------------------------------|
| **Connection Handling**      | Simple validation                   | Health-check + retries, Configurable URL             | Keep it simple, user manages Ollama lifecycle   |
| **Default Model**            | `gemma3:1b`                         | gemma3:4b, gemma3:12b, gemma3:27b, All in dropdown   | Balance quality vs speed, runs on most hardware |
| **API Strategy**             | Hybrid (OpenAI-compatible + Native) | Pure OpenAI mode, Pure native Ollama                 | Maximize code reuse + future flexibility        |
| **Streaming Protocol**       | SSE + EventSource                   | WebSocket, Long polling                              | Fits existing architecture, simpler than WS     |
| **Model Management**         | CLI only                            | MCP tools, REST API, No management                   | Out of scope, keep app focused                  |
| **Model Selection UI**       | Hardcoded in config                 | Predefined list, Free input, API autodiscovery       | Simplicity, flexibility via config              |
| **Privacy Indicators**       | Implicit (none)                     | Bright indicators, Settings section, Minimalist icon | Users understand local = private                |
| **Timeout Strategy**         | Configurable (default: 2min)        | Differentiated per provider, Unified 60s             | Flexibility for different hardware              |
| **System Prompt**            | Empty (DIY)                         | Add Ollama-specific, Reuse existing                  | Give users full control                         |
| **Architecture Integration** | Full integration                    | Minimal subset, Separate path                        | Consistency, feature parity                     |
| **Summarization**            | Configurable (default: off)         | Yes unify, No separate                               | No token limits locally, but useful option      |
| **MCP Tools**                | Immediate support                   | No (later), Experimental flag                        | Feature parity, Ollama supports it              |
| **UI Controls**              | Existing dropdown                   | Separate section, CLI command                        | Consistency, minimal UI changes                 |
| **Streaming Failure**        | Auto retry with reduced context     | User choice, Wait indefinitely                       | Graceful degradation                            |
| **Testing**                  | Unit + Integration                  | Integration only, Manual only                        | Quality, regression prevention                  |
| **API Versioning**           | No versioning (flexible)            | Version v1, Semver                                   | Ollama is experimental, accept breaking changes |
| **Fallback Logic**           | Explicit error message              | Auto-fallback, Configurable                          | User control, no surprises                      |
| **Dependency Injection**     | Koin (singleton)                    | Manual, Lazy init                                    | Consistency with existing code                  |
| **Logging Level**            | DEBUG with bodies                   | INFO endpoints, TRACE all                            | Troubleshooting local issues                    |
| **Documentation**            | Full section in CLAUDE.md           | Brief note, Separate file                            | Comprehensive, discoverable                     |
| **Metrics**                  | Show in UI                          | Logs only, None                                      | Performance awareness, benchmarking             |

### Key Tradeoffs Explained

#### 1. Simple Validation vs Health-Check

**Chosen:** Simple validation
**Tradeoff:** Faster startup vs No proactive error detection
**Mitigation:** Clear error messages guide user to fix issues

#### 2. Hardcoded Model vs Dynamic Selection

**Chosen:** Hardcoded in config
**Tradeoff:** Less flexibility vs Much simpler UI
**Mitigation:** Users can edit config file, future: add model discovery

#### 3. No Fallback vs Automatic Cloud Fallback

**Chosen:** Explicit error
**Tradeoff:** Interrupts workflow vs Clear user intent
**Mitigation:** Error messages are actionable, user can manually switch

#### 4. Summarization Disabled by Default

**Chosen:** Configurable (off)
**Tradeoff:** Large history context vs Summarization available if needed
**Mitigation:** Config flag `enableSummarization` for power users

#### 5. Testing Investment

**Chosen:** Unit + Integration tests
**Tradeoff:** Implementation time vs Quality assurance
**Mitigation:** Start with critical paths, expand coverage over time

---

## Future Enhancements (Out of Scope)

1. **Model Management UI**
    - List installed models
    - Pull new models
    - Delete unused models
    - Model comparison tool

2. **Multi-Model Support**
    - Run multiple models simultaneously
    - A/B testing responses
    - Model routing by task type

3. **Advanced Streaming**
    - Cancel generation mid-stream
    - Pause/resume generation
    - Partial result caching

4. **Performance Optimization**
    - Response caching
    - Batch request processing
    - Model quantization options

5. **Monitoring & Analytics**
    - Generation time tracking
    - Token usage statistics
    - Model performance metrics

6. **Custom Model Training**
    - Fine-tuning support
    - Custom model upload
    - LoRA adapter support

---

## References

- [Ollama GitHub Repository](https://github.com/ollama/ollama)
- [Ollama API Documentation](https://github.com/ollama/ollama/blob/main/docs/api.md)
- [Ollama Models Library](https://ollama.com/library)
- [Gemma 3 Model Card](https://ollama.com/library/gemma3)
- [Server-Sent Events Specification](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [Ktor SSE Documentation](https://ktor.io/docs/server-sse.html)

---

## Appendix: Configuration Examples

### Development Configuration

```hocon
ollama {
    baseUrl = "http://localhost:11434"
    model = "gemma3:1b"
    timeout = 120000
    enableSummarization = false
    enableTools = true
    streamFlushInterval = 50
}
```

### Production Configuration (Power User)

```hocon
ollama {
    baseUrl = "http://localhost:11434"
    model = "gemma3:12b"  # Larger model for better quality
    timeout = 300000  # 5 minutes for complex queries
    enableSummarization = true  # Summarize after 20 messages
    enableTools = true
    streamFlushInterval = 30  # Faster updates
}
```

### Remote Ollama Server

```hocon
ollama {
    baseUrl = "http://gpu-server:11434"  # Ollama on powerful machine
    model = "gemma3:27b"  # Largest model
    timeout = 600000  # 10 minutes
    enableSummarization = false
    enableTools = true
}
```

---

**End of Specification**
