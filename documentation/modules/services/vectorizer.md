# Vectorizer Service

## Overview

The Vectorizer service is a REST API that generates text embeddings using Ollama's `nomic-embed-text` model and stores
them in PostgreSQL with pgvector extension. It provides the foundation for semantic search and RAG (Retrieval-Augmented
Generation) capabilities.

**Port**: 8090
**Technology**: Ktor + PostgreSQL with pgvector + Ollama
**Embedding Model**: nomic-embed-text (768 dimensions)

## Architecture

```
Text Input → OllamaEmbeddingClient → Ollama API (port 11434)
                ↓
        768-dimensional embedding
                ↓
        PostgreSQL + pgvector
        HNSW index (cosine distance)
```

## Key Components

### 1. Application.kt

**Purpose**: Main entry point and Ktor server configuration

**Features**:

- Netty engine on port 8090
- CORS enabled (development mode)
- JSON serialization with kotlinx.serialization
- Koin dependency injection
- Health check endpoint

### 2. OllamaEmbeddingClient.kt

**Purpose**: HTTP client for Ollama embedding generation

**Configuration**:

```kotlin
baseUrl = "http://localhost:11434"  // Default Ollama URL
model = "nomic-embed-text"          // 768-dim embeddings
```

**Methods**:

- `generateEmbedding(text: String, model: String): FloatArray?`

### 3. VectorizerService.kt

**Purpose**: High-level orchestration for folder vectorization

**Features**:

- Recursive file processing
- Text chunking (500 chars with 100 char overlap)
- Batch embedding generation
- Database persistence
- Error handling and reporting

**Methods**:

- `vectorizeFolder(folderPath: String, model: String): VectorizeResult`

### 4. ChunkingService.kt

**Purpose**: Split large texts into overlapping chunks

**Strategy**:

- Chunk size: 500 characters (configurable)
- Overlap: 100 characters (configurable)
- Ensures context continuity across chunks

### 5. FileProcessingService.kt

**Purpose**: Read and filter files for vectorization

**Supported Files**:

- `.txt`, `.md`, `.kt`, `.java`, `.py`, `.js`, `.ts`
- UTF-8 encoding
- Recursive directory traversal

### 6. EmbeddingRepository.kt

**Purpose**: Database access layer for embeddings

**Schema**:

```sql
CREATE TABLE embeddings (
    id SERIAL PRIMARY KEY,
    chunk_text TEXT NOT NULL,
    embedding vector(768) NOT NULL,  -- pgvector type
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- HNSW index for fast similarity search
CREATE INDEX ON embeddings USING hnsw (embedding vector_cosine_ops);
```

**Methods**:

- `save(chunk: String, embedding: FloatArray, metadata: Map<String, String>)`
- `searchSimilar(embedding: FloatArray, limit: Int): List<String>`
- `deleteAll()`

## API Endpoints

### POST /api/vectorize

Generate embedding for a single text.

**Request**:

```json
{
  "text": "Your text to vectorize",
  "model": "nomic-embed-text"  // optional, default: nomic-embed-text
}
```

**Response**:

```json
{
  "embedding": [0.123, 0.456, ...],  // 768 floats
  "dimension": 768,
  "model": "nomic-embed-text"
}
```

**Status Codes**:

- `200 OK` - Success
- `400 Bad Request` - Empty text
- `500 Internal Server Error` - Ollama or processing error

### POST /api/vectorizeFolder

Vectorize all supported files in a folder recursively.

**Request**:

```json
{
  "folderPath": "/path/to/documents",
  "model": "nomic-embed-text"  // optional
}
```

**Response**:

```json
{
  "success": true,
  "filesProcessed": 15,
  "chunksCreated": 127,
  "filesSkipped": ["file1.bin", "file2.exe"],
  "errors": [],
  "message": "Successfully vectorized 15 files (127 chunks)"
}
```

**Status Codes**:

- `200 OK` - Full success
- `206 Partial Content` - Some files failed
- `400 Bad Request` - Invalid folder path
- `500 Internal Server Error` - Server error

## Configuration

### application.conf

```hocon
ktor {
  deployment {
    port = 8090
    port = ${?PORT}
  }
}

database {
  url = "jdbc:postgresql://localhost:5433/vectordb"
  url = ${?DATABASE_URL}
  driver = "org.postgresql.Driver"
  user = "vectoruser"
  user = ${?DATABASE_USER}
  password = "vectorpass"
  password = ${?DATABASE_PASSWORD}
  maxPoolSize = 10
}

ollama {
  baseUrl = "http://localhost:11434"
  baseUrl = ${?OLLAMA_BASE_URL}
  model = "nomic-embed-text"
  model = ${?OLLAMA_MODEL}
}
```

### Environment Variables

```bash
# Database
DATABASE_URL="jdbc:postgresql://localhost:5433/vectordb"
DATABASE_USER="vectoruser"
DATABASE_PASSWORD="vectorpass"

# Ollama
OLLAMA_BASE_URL="http://localhost:11434"
OLLAMA_MODEL="nomic-embed-text"

# Server
PORT=8090
```

## Database Setup

### Docker PostgreSQL with pgvector

```bash
# Start PostgreSQL with pgvector
docker run -d \
  --name vectordb \
  -p 5433:5432 \
  -e POSTGRES_USER=vectoruser \
  -e POSTGRES_PASSWORD=vectorpass \
  -e POSTGRES_DB=vectordb \
  ankane/pgvector:latest

# The service automatically:
# 1. Enables pgvector extension
# 2. Creates embeddings table
# 3. Creates HNSW index
```

### Manual Database Setup

```sql
-- Connect to database
\c vectordb

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create embeddings table (auto-created by service)
CREATE TABLE IF NOT EXISTS embeddings (
    id SERIAL PRIMARY KEY,
    chunk_text TEXT NOT NULL,
    embedding vector(768) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create HNSW index for fast similarity search
CREATE INDEX IF NOT EXISTS embeddings_embedding_idx
ON embeddings USING hnsw (embedding vector_cosine_ops);
```

## Ollama Setup

### Installation

```bash
# Download from https://ollama.ai/
# Or use package manager
# Windows: Download installer
# macOS: brew install ollama
# Linux: curl https://ollama.ai/install.sh | sh
```

### Start Ollama Service

```bash
# Start Ollama server (default port: 11434)
ollama serve
```

### Pull Embedding Model

```bash
# Download nomic-embed-text model (~274 MB)
ollama pull nomic-embed-text

# Verify model is available
ollama list
```

## Running the Service

### Option 1: Gradle

```bash
# With default config
.\gradlew.bat :services:vectorizer:run

# With custom config
.\gradlew.bat :services:vectorizer:run -Dconfig.file=application-dev.conf
```

### Option 2: Production Distribution

```bash
# Build distribution
.\gradlew.bat :services:vectorizer:installDist

# Run
services\vectorizer\build\install\vectorizer\bin\vectorizer.bat
```

## Usage Examples

### Example 1: Vectorize Single Text

```bash
curl -X POST http://localhost:8090/api/vectorize \
  -H "Content-Type: application/json" \
  -d '{"text": "Kotlin is a modern programming language"}'
```

**Response**:

```json
{
  "embedding": [0.0234, -0.0567, 0.0891, ...],
  "dimension": 768,
  "model": "nomic-embed-text"
}
```

### Example 2: Vectorize Documentation Folder

```bash
curl -X POST http://localhost:8090/api/vectorizeFolder \
  -H "Content-Type: application/json" \
  -d '{"folderPath": "C:/docs/project"}'
```

**Response**:

```json
{
  "success": true,
  "filesProcessed": 42,
  "chunksCreated": 387,
  "filesSkipped": [],
  "errors": [],
  "message": "Successfully vectorized 42 files (387 chunks)"
}
```

### Example 3: Search Similar Chunks (via RAG Service)

The vectorizer service stores embeddings. Searching is done via the RAG service:

```bash
curl -X POST http://localhost:8091/api/rag/search \
  -H "Content-Type: application/json" \
  -d '{"query": "Kotlin programming", "limit": 5}'
```

## Integration with RAG Service

The Vectorizer service is used by the RAG service for:

1. **Embedding Generation**: RAG calls `/api/vectorize` to convert query text to embeddings
2. **Shared Database**: Both services use the same PostgreSQL database (port 5433)
3. **Similarity Search**: RAG performs cosine similarity search on stored embeddings

**Flow**:

```
User Query → RAG Service → Vectorizer (/api/vectorize) → Embedding
                ↓
        Database Query (cosine similarity)
                ↓
        Top-K Similar Chunks → Chat LLM
```

## Performance Considerations

### Embedding Generation

- **Speed**: ~50-100ms per text (depends on Ollama GPU/CPU)
- **Batch Processing**: Process files concurrently (future enhancement)
- **Model Size**: nomic-embed-text is lightweight (~274 MB)

### Database Performance

- **HNSW Index**: Approximate nearest neighbor search (99%+ recall)
- **Cosine Distance**: Operator `<=>` is optimized by pgvector
- **Connection Pool**: HikariCP with max 10 connections

### Scalability

- **Current**: Single Ollama instance, local PostgreSQL
- **Production**: Consider Ollama cluster, PostgreSQL replicas

## Monitoring

### Health Check

```bash
curl http://localhost:8090/
# Response: "Vectorizer Service is running"
```

### Logs

```bash
# Service logs
tail -f services/vectorizer/logs/application.log

# Ktor request logs
# Embedded in console output
```

## Error Handling

### Common Errors

**Ollama Not Running**:

```
Error: Failed to generate embedding
Solution: Start Ollama with `ollama serve`
```

**Model Not Found**:

```
Error: model 'nomic-embed-text' not found
Solution: Pull model with `ollama pull nomic-embed-text`
```

**Database Connection Failed**:

```
Error: Connection to localhost:5433 refused
Solution: Start PostgreSQL container (see Database Setup)
```

**pgvector Extension Missing**:

```
Error: type "vector" does not exist
Solution: Use ankane/pgvector Docker image or install pgvector extension
```

## Dependencies

### Gradle (build.gradle.kts)

```kotlin
dependencies {
    implementation(project(":shared"))

    // Ktor Server
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.server.content.negotiation.jvm)
    implementation(libs.kotlinx.serialization.json)

    // Ktor Client (for Ollama)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // Dependency Injection
    implementation(libs.koin.ktor)

    // Logging
    implementation(libs.logback.classic)
}
```

## Security Considerations

⚠️ **DEVELOPMENT ONLY** - Not production-ready

**Issues**:

- Open CORS policy (`anyHost()`)
- No authentication/authorization
- No rate limiting
- Arbitrary file system access (vectorizeFolder endpoint)
- No input validation for file paths

**Production TODO**:

- Restrict CORS to known origins
- Add API key authentication
- Implement rate limiting (per IP, per API key)
- Whitelist allowed folders for vectorization
- Path traversal prevention
- HTTPS enforcement
- Request size limits

## Future Enhancements

1. **Batch Processing**: Parallel file vectorization
2. **File Upload**: HTTP multipart file upload instead of file path
3. **Document Parsers**: PDF, DOCX, HTML support
4. **Chunk Strategies**: Sentence-based, paragraph-based chunking
5. **Multiple Models**: Support for other Ollama embedding models
6. **Metadata Search**: Filter embeddings by metadata (file type, date, etc.)
7. **Update Mechanism**: Re-vectorize changed files
8. **Deletion API**: Delete embeddings by metadata
9. **Statistics**: Embedding count, storage usage, search performance

## Troubleshooting

### Issue: Slow Embedding Generation

**Symptoms**: `/api/vectorize` takes >1 second
**Solutions**:

- Check Ollama is using GPU (if available)
- Reduce chunk size in ChunkingService
- Use a smaller/faster model

### Issue: Out of Memory (PostgreSQL)

**Symptoms**: Database connection errors during large vectorizations
**Solutions**:

- Increase PostgreSQL memory limits
- Process files in smaller batches
- Increase connection pool size

### Issue: Inconsistent Search Results

**Symptoms**: RAG returns irrelevant chunks
**Solutions**:

- Check chunk size (too small = no context, too large = mixed topics)
- Verify HNSW index is created
- Increase search limit (get more candidates)
- Consider re-ranking results (future enhancement)

## Related Documentation

- [RAG Service](rag.md) - Semantic search using vectorizer embeddings
- [Server Module](../server.md) - Main chat server that uses RAG
- [Architecture Overview](../../ARCHITECTURE.md) - System design

## References

- [pgvector Documentation](https://github.com/pgvector/pgvector)
- [Ollama Documentation](https://github.com/ollama/ollama)
- [nomic-embed-text Model](https://ollama.ai/library/nomic-embed-text)
- [Ktor Documentation](https://ktor.io/)
