# AI Chat Web Application

This is a Kotlin Multiplatform web chat application that integrates with multiple AI providers (GigaChat and OpenRouter).
The application features a web-based chat interface built with Compose Multiplatform and a Ktor backend server.

## Features

- Web-based chat interface with message history
- **Multiple AI Providers**:
  - GigaChat API integration using OAuth 2.0
  - OpenRouter API integration (access to 100+ AI models including GPT-4, Claude, etc.)
- **Advanced Features**:
  - MCP (Model Context Protocol) tool calling support
  - RAG (Retrieval-Augmented Generation) with vector search
  - Real-time token usage statistics
  - Model selection from UI
  - Configurable parameters (temperature, max tokens, system prompt)
- MVVM architecture with state management
- Dependency injection with Koin
- CORS support for cross-origin requests

## Project Structure

* [/composeApp](./composeApp/src) - Compose Multiplatform web frontend
    - Chat UI components
    - ChatViewModel for state management
    - API client for server communication

* [/server](./server/src/main/kotlin) - Ktor backend server
    - GigaChat API client with OAuth 2.0 authentication
    - Chat service for processing messages
    - REST API endpoints
    - Koin dependency injection configuration

* [/shared](./shared/src) - Common code shared between frontend and backend
    - Data models (ChatMessage, ChatResponse, etc.)
    - Shared constants

## Prerequisites

### For GigaChat (Optional)

1. Obtain GigaChat API credentials from Sber
2. Set environment variables:
   ```bash
   set GIGACHAT_CLIENT_ID=your_client_id
   set GIGACHAT_CLIENT_SECRET=your_client_secret
   ```

### For OpenRouter (Recommended)

1. Create account at [OpenRouter](https://openrouter.ai/)
2. Get API key from [OpenRouter Keys](https://openrouter.ai/keys)
3. Set environment variables:
   ```bash
   set OPENAI_API_KEY=sk-or-v1-...
   set OPENAI_BASE_URL=https://openrouter.ai/api/v1
   ```
4. **Important**: For free models (`:free` suffix), enable "Allow training on data" at [Privacy Settings](https://openrouter.ai/settings/privacy)
   - ⚠️ Free models log all data for model training
   - For production, use paid models without `:free` suffix

**Note:** The `.env` file is not automatically loaded by JVM. You must use the `run-server.bat` script or set environment variables manually.

## How to Run

### Step 1: Build and Run Server

First, start the Ktor backend server:

**Windows (Recommended):**

Use the provided batch script that sets environment variables:

```shell
run-server.bat
```

Or set environment variables manually:

```shell
set GIGACHAT_CLIENT_ID=your_client_id
set GIGACHAT_CLIENT_SECRET=your_client_secret
.\gradlew.bat :server:run
```

**macOS/Linux:**

```shell
export GIGACHAT_CLIENT_ID=your_client_id
export GIGACHAT_CLIENT_SECRET=your_client_secret
./gradlew :server:run
```

The server will start on `http://localhost:8080`

### Step 2: Build and Run Web Application

In a new terminal, start the web application:

**For Wasm target (recommended - faster, modern browsers):**

Windows:

```shell
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

macOS/Linux:

```shell
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

**For JS target (broader browser compatibility):**

Windows:

```shell
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun
```

macOS/Linux:

```shell
./gradlew :composeApp:jsBrowserDevelopmentRun
```

The web application will automatically open in your browser.

## Usage

1. Once both the server and web application are running, open your browser to the web application URL (typically
   `http://localhost:8080` or as shown in the terminal)
2. Type your message in the input field at the bottom of the chat interface
3. Click "Отправить" (Send) or press Enter to send your message
4. Wait for GigaChat to process your request and display the response
5. Continue the conversation as needed

## Technologies Used

- **Kotlin**: 2.2.21
- **Compose Multiplatform**: 1.9.1
- **Ktor**: 3.3.3 (Server: Netty, Client: CIO)
- **Koin**: 4.1.0 (Dependency Injection)
- **Kotlinx Serialization**: 1.8.0
- **PostgreSQL**: With pgvector extension for RAG
- **Ollama**: For text embeddings (nomic-embed-text)
- **Logback**: 1.5.20

## Architecture

The application follows the MVVM (Model-View-ViewModel) architecture:

- **Model**: Data models in the shared module
- **View**: Composable UI components in composeApp
- **ViewModel**: ChatViewModel managing UI state and business logic

Backend uses a layered architecture:

- **Controller** (ChatRouting): REST API endpoints
- **Service** (ChatService): Business logic
- **Client** (GigaChatApiClient): External API integration

## API Endpoints

### Main Chat API
- `GET /` - Health check endpoint
- `POST /api/send-message` - Send a message to AI provider
    - Request body:
      ```json
      {
        "text": "your message",
        "systemPrompt": "optional system prompt",
        "temperature": 0.7,
        "provider": "openrouter",
        "model": "openai/gpt-3.5-turbo",
        "maxTokens": 1024,
        "enableTools": true,
        "useRag": false
      }
      ```
    - Response:
      ```json
      {
        "text": "response text",
        "status": "SUCCESS|ERROR",
        "tokenUsage": {"promptTokens": 10, "completionTokens": 20, "totalTokens": 30},
        "lastResponseTokenUsage": {...},
        "responseTimeMs": 1234
      }
      ```
- `POST /api/clear-history` - Clear conversation history
- `GET /api/history?provider=openrouter` - Get message history

### Additional Services
- Notes API (port 8084): CRUD operations for notes
- News API (port 8087): CRUD operations for news articles
- RAG API (port 8091): Vector search for knowledge base
- Vectorizer API (port 8090): Text vectorization with Ollama

## Troubleshooting

### OpenRouter: "No endpoints found matching your data policy"

This error occurs when using free models without enabling data training permission.

**Solution:**
1. Go to [OpenRouter Privacy Settings](https://openrouter.ai/settings/privacy)
2. Enable "Allow training on data" for free models
3. OR use paid models without `:free` suffix

### Token Statistics Not Showing

**Check:**
- Server logs for `"Token usage - Prompt: X, Completion: Y, Total: Z"`
- Some models don't return usage information
- Try a different model (gpt-3.5-turbo, gpt-4, claude-3)

### MCP Tools Not Working

**Check:**
- "Enable Tools" checkbox is enabled in UI
- MCP server is running (e.g., `.\gradlew.bat :mcp:rag:run`)
- Model supports function calling (gpt-4, gpt-3.5-turbo, claude-3)

## Documentation

For detailed documentation, see:

- **[.claude/CLAUDE.md](CLAUDE.md)** - Complete project documentation
- Includes architecture, module descriptions, configuration, troubleshooting

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/),
[OpenRouter](https://openrouter.ai/docs)