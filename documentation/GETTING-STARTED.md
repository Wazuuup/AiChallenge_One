# Getting Started with AiChallenge_One

This guide will help you set up and run the AiChallenge_One application locally.

## Prerequisites

- **Java 17+** (Amazon Corretto 17 recommended)
- **Gradle** (wrapper included)
- **PostgreSQL 15+** (for services that require databases)
- **Docker** (optional, for containerized services)
- **Git**

## Installation Steps

### 1. Clone the Repository

```bash
git clone <repository-url>
cd AiChallenge_One
```

### 2. Set Up Environment Variables

#### Required for Main Server (GigaChat)

```bash
# Windows
set GIGACHAT_CLIENT_ID=your-gigachat-client-id
set GIGACHAT_CLIENT_SECRET=your-gigachat-client-secret

# Linux/Mac
export GIGACHAT_CLIENT_ID=your-gigachat-client-id
export GIGACHAT_CLIENT_SECRET=your-gigachat-client-secret
```

#### Optional for OpenRouter

```bash
# Windows
set OPENAI_BASE_URL=https://openrouter.ai/api/v1
set OPENAI_API_KEY=your-openrouter-api-key

# Linux/Mac
export OPENAI_BASE_URL=https://openrouter.ai/api/v1
export OPENAI_API_KEY=your-openrouter-api-key
```

### 3. Build the Project

```bash
.\gradlew.bat build
```

This will:

- Compile all Kotlin modules
- Run tests
- Generate distribution packages

## Running the Application

### Option 1: Full Stack (Recommended for Development)

#### Terminal 1: Start PostgreSQL (for services)

```bash
# For Notes Service
docker run -d --name notesdb -p 5432:5432 -e POSTGRES_DB=notesdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:15

# For News Service
docker run -d --name newsdb -p 5433:5433 -e POSTGRES_DB=newsdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:15

# For Vectorizer Service
docker run -d --name vectordb -p 5434:5432 -e POSTGRES_DB=vectordb -e POSTGRES_USER=vectoruser -e POSTGRES_PASSWORD=vectorpass postgres:15
```

#### Terminal 2: Start Ollama (for Vectorizer)

```bash
# Install Ollama from https://ollama.ai/
ollama serve

# Pull the embedding model
ollama pull nomic-embed-text
```

#### Terminal 3: Start Backend Services

```bash
# Notes Service
.\gradlew.bat :services:notes:run

# News CRUD Service (new terminal)
.\gradlew.bat :services:news-crud:run

# Vectorizer Service (new terminal)
.\gradlew.bat :services:vectorizer:run

# RAG Service (new terminal)
.\gradlew.bat :services:rag:run
```

#### Terminal 4: Start Main Server

```bash
.\gradlew.bat :server:run
```

#### Terminal 5: Start Frontend

```bash
# Wasm (recommended)
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun

# Or JavaScript
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun
```

### Option 2: Minimal Setup (Just Chat)

If you only want to test the chat functionality:

```bash
# Terminal 1: Main Server
.\gradlew.bat :server:run

# Terminal 2: Frontend
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

## Accessing the Application

- **Frontend UI**: http://localhost:8080
- **Server API**: http://localhost:8080/api/send-message

## Testing RAG Integration

1. Start Vectorizer and RAG services (see Full Stack above)
2. Add some test documents via Vectorizer API:
   ```bash
   curl -X POST http://localhost:8090/api/embed \
     -H "Content-Type: application/json" \
     -d '{"text": "Kotlin is a modern programming language", "metadata": {"source": "test"}}'
   ```
3. Enable "Use RAG" checkbox in the UI
4. Send a message related to your documents
5. The AI will use retrieved context in its response

## Development Workflow

### Running with Auto-Reload

```bash
# Server (supports hot reload)
.\gradlew.bat :server:run -t

# Frontend (webpack dev server with hot reload)
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

### Running Tests

```bash
# All tests
.\gradlew.bat test

# Specific module
.\gradlew.bat :server:test
.\gradlew.bat :services:notes:test
```

### Building for Production

```bash
# Server distribution
.\gradlew.bat :server:installDist
# Output: server/build/install/server/

# Frontend production build
.\gradlew.bat :composeApp:wasmJsBrowserDistribution
# Output: composeApp/build/dist/wasmJs/productionExecutable/
```

## Common Issues

### Issue: GigaChat SSL Certificate Error

**Solution**: The truststore.jks is included in the project. Ensure it's in `server/src/main/resources/`

### Issue: PostgreSQL Connection Failed

**Solution**: Verify Docker containers are running:

```bash
docker ps
```

### Issue: Ollama Model Not Found

**Solution**: Pull the model explicitly:

```bash
ollama pull nomic-embed-text
```

### Issue: Port Already in Use

**Solution**: Stop the conflicting service or change the port in `application.conf`

## Next Steps

- **[Architecture Overview](ARCHITECTURE.md)** - Understand the system design
- **[Module Documentation](modules/)** - Dive into specific modules
- **[Deployment Guide](deployment/DEPLOYMENT.md)** - Deploy to production

## Useful Commands

```bash
# Clean build
.\gradlew.bat clean build

# Run with custom config
.\gradlew.bat :server:run -Dconfig.file=application-dev.conf

# Check dependencies
.\gradlew.bat dependencies

# Generate API documentation
.\gradlew.bat dokkaHtml
```

## Support

For issues or questions, refer to:

- [Module Documentation](modules/)
- [Task History](tasks/) for implementation details
- Project README at repository root
