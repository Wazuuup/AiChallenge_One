# MCP NewsCRUD Server

## Overview

The MCP NewsCRUD server is a Model Context Protocol (MCP) server that provides AI tools for managing locally stored news
articles. It acts as an MCP wrapper around the News CRUD Service, enabling AI assistants to perform CRUD operations and
search through stored articles.

**Ports**: 8086 (HTTP), 8445 (HTTPS)
**Technology**: Ktor + MCP SDK + Auto-generated SSL
**Backend**: News CRUD Service (port 8087)

## Architecture

```
AI Assistant (via OpenRouter)
    ↓
Main Server (Tool Calling)
    ↓
MCP NewsCRUD Server (8086/8445)
    ↓
News CRUD Service (8087)
    ↓
PostgreSQL
```

## MCP Tools

### 1. get_all_articles

**Purpose**: Retrieve all stored articles with pagination

**Parameters**:

- `limit` (optional): Max articles to return (default: 100, max: 1000)
- `offset` (optional): Number of articles to skip (default: 0)

**Example**:

```json
{
  "name": "get_all_articles",
  "arguments": {
    "limit": 20,
    "offset": 0
  }
}
```

**Response**:

```json
{
  "articles": [
    {
      "id": 1,
      "title": "Kotlin 2.0 Released",
      "description": "JetBrains announces major update",
      "url": "https://example.com/kotlin-2.0",
      "publishedAt": "2025-01-15T10:00:00Z",
      "createdAt": "2025-01-15T11:00:00Z"
    }
  ],
  "total": 142
}
```

### 2. get_article_by_id

**Purpose**: Get a specific article by ID

**Parameters**:

- `id` (required): Article ID

**Example**:

```json
{
  "name": "get_article_by_id",
  "arguments": {
    "id": 1
  }
}
```

**Response**:

```json
{
  "article": {
    "id": 1,
    "sourceId": "techcrunch",
    "sourceName": "TechCrunch",
    "author": "John Doe",
    "title": "Kotlin 2.0 Released",
    "description": "JetBrains announces major update...",
    "url": "https://techcrunch.com/kotlin-2.0",
    "urlToImage": "https://techcrunch.com/images/kotlin.jpg",
    "publishedAt": "2025-01-15T10:00:00Z",
    "content": "Full article content here...",
    "createdAt": "2025-01-15T11:00:00Z",
    "updatedAt": "2025-01-15T11:00:00Z"
  }
}
```

### 3. search_articles

**Purpose**: Search articles by keywords

**Parameters**:

- `query` (required): Search query
- `limit` (optional): Max results (default: 100)

**Example**:

```json
{
  "name": "search_articles",
  "arguments": {
    "query": "artificial intelligence",
    "limit": 10
  }
}
```

**Response**:

```json
{
  "articles": [
    {
      "id": 5,
      "title": "AI Breakthrough in 2025",
      "description": "Major advancements in artificial intelligence...",
      "content": "Researchers have achieved..."
    }
  ],
  "matchCount": 15
}
```

**Search Behavior**:

- Case-insensitive
- Searches title, description, and content
- Uses SQL `LIKE` (partial matches)

### 4. create_article

**Purpose**: Create a new article in local database

**Parameters**:

- `title` (required): Article title
- `description` (optional): Article description
- `url` (optional): Source URL
- `content` (optional): Full content
- `sourceId` (optional): Source identifier
- `sourceName` (optional): Source name
- `author` (optional): Author name
- `urlToImage` (optional): Image URL
- `publishedAt` (optional): Publication date (ISO 8601)

**Example**:

```json
{
  "name": "create_article",
  "arguments": {
    "title": "Custom Article Title",
    "description": "This is a manually created article",
    "content": "Full article content goes here...",
    "author": "AI Assistant",
    "publishedAt": "2025-01-15T12:00:00Z"
  }
}
```

**Response**:

```json
{
  "success": true,
  "article": {
    "id": 143,
    "title": "Custom Article Title",
    "description": "This is a manually created article",
    "createdAt": "2025-01-15T14:30:00Z"
  }
}
```

### 5. update_article

**Purpose**: Update an existing article

**Parameters**:

- `id` (required): Article ID
- `title` (optional): New title
- `description` (optional): New description
- `content` (optional): New content
- `author` (optional): New author
- `url` (optional): New URL
- `urlToImage` (optional): New image URL

**Example**:

```json
{
  "name": "update_article",
  "arguments": {
    "id": 1,
    "description": "Updated description with more details",
    "content": "Updated full content..."
  }
}
```

**Response**:

```json
{
  "success": true,
  "article": {
    "id": 1,
    "title": "Kotlin 2.0 Released",
    "description": "Updated description with more details",
    "updatedAt": "2025-01-15T15:00:00Z"
  }
}
```

### 6. delete_article

**Purpose**: Delete an article from database

**Parameters**:

- `id` (required): Article ID

**Example**:

```json
{
  "name": "delete_article",
  "arguments": {
    "id": 1
  }
}
```

**Response**:

```json
{
  "success": true,
  "message": "Article deleted successfully"
}
```

## Key Components

### 1. Application.kt

**Purpose**: HTTP/HTTPS server setup

**Features**:

- Dual protocol support (HTTP on 8086, HTTPS on 8445)
- Auto-generated self-signed SSL certificates
- CORS enabled (development mode)
- MCP endpoint routing

### 2. NewsCrudMcpConfiguration.kt

**Purpose**: MCP server configuration and tool definitions

**Endpoints**:

- `GET /` - Health check
- `GET /tools/list` - List available tools
- `POST /tools/call` - Execute a tool

**Tool Registration**:

```kotlin
val tools = listOf(
    getAllArticlesTool,
    getArticleByIdTool,
    searchArticlesTool,
    createArticleTool,
    updateArticleTool,
    deleteArticleTool
)
```

### 3. NewsCrudService.kt

**Purpose**: HTTP client for News CRUD Service

**Base URL**: `http://localhost:8087`

**Methods**:

- `getAllArticles(limit, offset): List<Article>`
- `getArticleById(id): Article?`
- `searchArticles(query, limit): List<Article>`
- `createArticle(request): Article`
- `updateArticle(id, request): Article?`
- `deleteArticle(id): Boolean`

## Configuration

### application.conf

```hocon
ktor {
  deployment {
    httpPort = 8086
    httpPort = ${?HTTP_PORT}
    httpsPort = 8445
    httpsPort = ${?HTTPS_PORT}
  }
}

ssl {
  keyAlias = "mcpnewscrud"
  keyAlias = ${?SSL_KEY_ALIAS}
  keystorePassword = "changeit"
  keystorePassword = ${?SSL_KEYSTORE_PASSWORD}
  keyPassword = "changeit"
  keyPassword = ${?SSL_KEY_PASSWORD}
}

newscrud {
  apiUrl = "http://localhost:8087"
  apiUrl = ${?NEWSCRUD_API_URL}
}
```

### Environment Variables

```bash
# Ports
HTTP_PORT=8086
HTTPS_PORT=8445

# SSL Configuration
SSL_KEY_ALIAS="mcpnewscrud"
SSL_KEYSTORE_PASSWORD="changeit"
SSL_KEY_PASSWORD="changeit"

# News CRUD Service URL
NEWSCRUD_API_URL="http://localhost:8087"
```

## Running the Server

### Development

```bash
# Terminal 1: Start PostgreSQL (if not running)
docker run -d --name newsdb -p 5432:5432 \
  -e POSTGRES_DB=newsdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  postgres:15

# Terminal 2: Start News CRUD Service
.\gradlew.bat :services:news-crud:run

# Terminal 3: Start MCP NewsCRUD Server
.\gradlew.bat :mcp:newscrud:run
```

### Production

```bash
# Build distribution
.\gradlew.bat :mcp:newscrud:installDist

# Run
mcp\newscrud\build\install\newscrud\bin\newscrud.bat
```

## Testing

### Manual Testing

#### Health Check

```bash
curl http://localhost:8086/
```

#### List Tools

```bash
curl http://localhost:8086/tools/list
```

#### Get All Articles

```bash
curl -X POST http://localhost:8086/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "get_all_articles",
    "arguments": {
      "limit": 5
    }
  }'
```

#### Search Articles

```bash
curl -X POST http://localhost:8086/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "search_articles",
    "arguments": {
      "query": "technology"
    }
  }'
```

#### Create Article

```bash
curl -X POST http://localhost:8086/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "create_article",
    "arguments": {
      "title": "Test Article",
      "content": "This is a test article"
    }
  }'
```

### Integration Testing (via Main Server)

```bash
curl -X POST http://localhost:8080/api/send-message \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Search for articles about Kotlin programming",
    "provider": "openrouter",
    "model": "anthropic/claude-3.5-sonnet",
    "enableTools": true
  }'
```

## Use Cases

### 1. Article Archival

**User**: "Save this article to my database: [article details]"

**AI Flow**:

1. Parse article details from user message
2. Call `create_article` with extracted data
3. Confirm article saved with ID

### 2. Article Search

**User**: "Find all articles about machine learning in my database"

**AI Tool Call**:

```json
{
  "name": "search_articles",
  "arguments": {
    "query": "machine learning",
    "limit": 20
  }
}
```

### 3. Article Management

**User**: "Update article 5 to add more details about the author"

**AI Tool Call**:

```json
{
  "name": "update_article",
  "arguments": {
    "id": 5,
    "author": "Dr. Jane Smith, AI Researcher"
  }
}
```

### 4. Workflow: Fetch → Store → Retrieve

**Step 1**: Use MCP NewsAPI to fetch news

```json
{
  "name": "search_news",
  "arguments": {
    "query": "climate change"
  }
}
```

**Step 2**: Use MCP NewsCRUD to store locally

```json
{
  "name": "create_article",
  "arguments": {
    "title": "...",
    "description": "...",
    "url": "..."
  }
}
```

**Step 3**: Later retrieve from local database

```json
{
  "name": "search_articles",
  "arguments": {
    "query": "climate change"
  }
}
```

## Integration with MCP NewsAPI

**Complementary Services**:

- **MCP NewsAPI**: Fetch fresh news from NewsAPI.org
- **MCP NewsCRUD**: Store and manage news locally

**Common Workflow**:

```
User: "Get the latest AI news and save to my database"

AI:
1. Call MCP NewsAPI -> search_news("artificial intelligence")
2. For each article:
   Call MCP NewsCRUD -> create_article(...)
3. Response: "I've saved 10 articles about AI to your database"
```

## Error Handling

### Common Errors

**News CRUD Service Unavailable**:

```json
{
  "error": "Failed to connect to News CRUD Service at http://localhost:8087"
}
```

**Solution**: Start News CRUD Service

**Article Not Found**:

```json
{
  "error": "Article with ID 999 not found"
}
```

**Solution**: Verify article ID exists

**Invalid Search Query**:

```json
{
  "error": "Search query cannot be empty"
}
```

**Solution**: Provide non-empty query string

## Dependencies

```kotlin
dependencies {
    implementation(project(":shared"))

    // Ktor Server
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.network.tls.certificates)

    // Ktor Client (for News CRUD Service)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // MCP SDK
    implementation("io.modelcontextprotocol:sdk:0.1.0")
}
```

## Future Enhancements

1. **Bulk Operations**: Create/update/delete multiple articles at once
2. **Advanced Search**: Filter by date range, source, author
3. **Article Summarization**: AI-generated summaries
4. **Duplicate Detection**: Prevent storing duplicate articles
5. **Tags/Categories**: Organize articles with tags
6. **Export**: Export articles to PDF, Markdown, JSON
7. **Analytics**: Most viewed articles, trending topics
8. **Webhooks**: Notify on new articles

## Troubleshooting

### Issue: Empty Results

**Symptom**: `get_all_articles` returns empty array

**Solutions**:

1. Check database has data: `curl http://localhost:8087/api/news`
2. Verify News CRUD Service connection
3. Check PostgreSQL is running

### Issue: Search Not Working

**Symptom**: Search returns no results for valid queries

**Solutions**:

1. Verify articles exist with that content
2. Try broader search terms
3. Check case sensitivity (should be case-insensitive)

### Issue: SSL Certificate Error

**Symptom**: Main server can't connect via HTTPS

**Solution**: Same as other MCP servers - update truststore or use HTTP (dev only)

## Related Documentation

- [News CRUD Service](../services/news-crud.md) - Backend API
- [MCP NewsAPI Server](newsapi.md) - External news fetching
- [Server Module](../server.md) - Main server with tool calling

## References

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [News CRUD API Documentation](../services/news-crud.md)
