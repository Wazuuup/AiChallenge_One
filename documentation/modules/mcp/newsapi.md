# MCP NewsAPI Server

## Overview

The MCP NewsAPI server is a Model Context Protocol (MCP) server that provides AI tools for fetching news articles from
NewsAPI.org - a popular news aggregation service with access to thousands of news sources worldwide.

**Ports**: 8085 (HTTP), 8444 (HTTPS)
**Technology**: Ktor + MCP SDK + Auto-generated SSL
**Backend**: NewsAPI.org External API

## Architecture

```
AI Assistant (via OpenRouter)
    ↓
Main Server (Tool Calling)
    ↓
MCP NewsAPI Server (8085/8444)
    ↓
NewsAPI.org API (https://newsapi.org/v2/)
```

## MCP Tools

### 1. search_news

**Purpose**: Search for news articles by keywords

**Parameters**:

- `query` (required): Search keywords
- `language` (optional): Language code (e.g., "en", "ru"), default: "en"
- `pageSize` (optional): Number of results (max 100), default: 20
- `sortBy` (optional): "relevancy", "popularity", or "publishedAt", default: "publishedAt"

**Example**:

```json
{
  "name": "search_news",
  "arguments": {
    "query": "artificial intelligence",
    "language": "en",
    "pageSize": 10,
    "sortBy": "relevancy"
  }
}
```

**Response**:

```json
{
  "status": "ok",
  "totalResults": 1247,
  "articles": [
    {
      "source": {
        "id": "techcrunch",
        "name": "TechCrunch"
      },
      "author": "John Doe",
      "title": "AI Breakthrough in 2025",
      "description": "Major advancements in artificial intelligence...",
      "url": "https://techcrunch.com/2025/01/15/ai-breakthrough",
      "urlToImage": "https://techcrunch.com/images/ai.jpg",
      "publishedAt": "2025-01-15T10:00:00Z",
      "content": "Full article content..."
    }
  ]
}
```

### 2. get_top_headlines

**Purpose**: Get top headlines from major news sources

**Parameters**:

- `country` (optional): Country code (e.g., "us", "ru"), default: "us"
- `category` (optional): "business", "entertainment", "general", "health", "science", "sports", "technology"
- `pageSize` (optional): Number of results (max 100), default: 20

**Example**:

```json
{
  "name": "get_top_headlines",
  "arguments": {
    "country": "us",
    "category": "technology",
    "pageSize": 5
  }
}
```

**Response**:

```json
{
  "status": "ok",
  "totalResults": 38,
  "articles": [
    {
      "source": {
        "id": "the-verge",
        "name": "The Verge"
      },
      "title": "Apple Announces New Product Line",
      "description": "Apple unveils latest innovations...",
      "url": "https://theverge.com/2025/01/15/apple-announcement",
      "publishedAt": "2025-01-15T09:00:00Z"
    }
  ]
}
```

### 3. get_sources

**Purpose**: Get available news sources

**Parameters**:

- `category` (optional): Filter by category
- `language` (optional): Filter by language
- `country` (optional): Filter by country

**Example**:

```json
{
  "name": "get_sources",
  "arguments": {
    "category": "technology",
    "language": "en"
  }
}
```

**Response**:

```json
{
  "status": "ok",
  "sources": [
    {
      "id": "techcrunch",
      "name": "TechCrunch",
      "description": "Technology news and analysis",
      "url": "https://techcrunch.com",
      "category": "technology",
      "language": "en",
      "country": "us"
    },
    {
      "id": "the-verge",
      "name": "The Verge",
      "description": "Tech news and media",
      "url": "https://theverge.com",
      "category": "technology",
      "language": "en",
      "country": "us"
    }
  ]
}
```

## Key Components

### 1. Application.kt

**Purpose**: HTTP/HTTPS server setup

**Features**:

- Dual protocol support (HTTP on 8085, HTTPS on 8444)
- Auto-generated self-signed SSL certificates
- CORS enabled (development mode)
- MCP endpoint routing

### 2. NewsApiConfiguration.kt

**Purpose**: MCP server configuration and tool definitions

**Endpoints**:

- `GET /` - Health check
- `GET /tools/list` - List available tools
- `POST /tools/call` - Execute a tool

**Tool Registration**:

```kotlin
val tools = listOf(
    searchNewsTool,
    getTopHeadlinesTool,
    getSourcesTool
)
```

### 3. NewsApiService.kt

**Purpose**: HTTP client for NewsAPI.org

**Base URL**: `https://newsapi.org/v2`

**Authentication**: API key in header

```
Authorization: Bearer YOUR_API_KEY
```

**Methods**:

- `searchNews(query, language, pageSize, sortBy): NewsResponse`
- `getTopHeadlines(country, category, pageSize): NewsResponse`
- `getSources(category, language, country): SourcesResponse`

## Configuration

### application.conf

```hocon
ktor {
  deployment {
    httpPort = 8085
    httpPort = ${?HTTP_PORT}
    httpsPort = 8444
    httpsPort = ${?HTTPS_PORT}
  }
}

ssl {
  keyAlias = "mcpnewsapi"
  keyAlias = ${?SSL_KEY_ALIAS}
  keystorePassword = "changeit"
  keystorePassword = ${?SSL_KEYSTORE_PASSWORD}
  keyPassword = "changeit"
  keyPassword = ${?SSL_KEY_PASSWORD}
}

newsapi {
  apiKey = ${?NEWSAPI_API_KEY}
  baseUrl = "https://newsapi.org/v2"
}
```

### Environment Variables

```bash
# Required
NEWSAPI_API_KEY="your-newsapi-key"

# Optional
HTTP_PORT=8085
HTTPS_PORT=8444
SSL_KEY_ALIAS="mcpnewsapi"
SSL_KEYSTORE_PASSWORD="changeit"
SSL_KEY_PASSWORD="changeit"
```

### Getting NewsAPI.org API Key

1. Visit https://newsapi.org/
2. Sign up for free account
3. Get API key from dashboard
4. Free tier: 100 requests/day, 1 month history

**Pricing**:

- **Free**: 100 req/day, 1-month history
- **Developer**: $449/month, 250k req/month
- **Business**: Custom pricing

## Running the Server

### Development

```bash
# Set API key
set NEWSAPI_API_KEY=your-api-key

# Start server
.\gradlew.bat :mcp:newsapi:run
```

### Production

```bash
# Build distribution
.\gradlew.bat :mcp:newsapi:installDist

# Run with environment variable
set NEWSAPI_API_KEY=your-api-key
mcp\newsapi\build\install\newsapi\bin\newsapi.bat
```

## Testing

### Manual Testing

#### Health Check

```bash
curl http://localhost:8085/
```

#### List Tools

```bash
curl http://localhost:8085/tools/list
```

#### Search News

```bash
curl -X POST http://localhost:8085/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "search_news",
    "arguments": {
      "query": "Kotlin programming",
      "pageSize": 5
    }
  }'
```

#### Get Top Headlines

```bash
curl -X POST http://localhost:8085/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "get_top_headlines",
    "arguments": {
      "country": "us",
      "category": "technology"
    }
  }'
```

### Integration Testing (via Main Server)

```bash
curl -X POST http://localhost:8080/api/send-message \
  -H "Content-Type: application/json" \
  -d '{
    "text": "What are the latest technology news headlines?",
    "provider": "openrouter",
    "model": "anthropic/claude-3.5-sonnet",
    "enableTools": true
  }'
```

## Use Cases

### 1. Current Events Lookup

**User**: "What's happening in the world of AI today?"

**AI Tool Call**:

```json
{
  "name": "search_news",
  "arguments": {
    "query": "artificial intelligence",
    "sortBy": "publishedAt"
  }
}
```

### 2. Topic Research

**User**: "Find articles about climate change from the past week"

**AI Tool Call**:

```json
{
  "name": "search_news",
  "arguments": {
    "query": "climate change",
    "pageSize": 20,
    "sortBy": "relevancy"
  }
}
```

### 3. News Source Discovery

**User**: "What technology news sources are available?"

**AI Tool Call**:

```json
{
  "name": "get_sources",
  "arguments": {
    "category": "technology"
  }
}
```

## Error Handling

### Common Errors

**API Key Missing**:

```json
{
  "error": "NEWSAPI_API_KEY environment variable not set"
}
```

**Solution**: Set `NEWSAPI_API_KEY` environment variable

**Rate Limit Exceeded**:

```json
{
  "status": "error",
  "code": "rateLimited",
  "message": "You have made too many requests recently"
}
```

**Solution**: Wait for rate limit reset or upgrade plan

**Invalid API Key**:

```json
{
  "status": "error",
  "code": "apiKeyInvalid",
  "message": "Your API key is invalid"
}
```

**Solution**: Verify API key from NewsAPI.org dashboard

## Rate Limiting

### Free Tier Limits

- **100 requests per day**
- **Resets at midnight UTC**

### Best Practices

1. Cache results when possible
2. Use specific queries (reduces API calls)
3. Set appropriate `pageSize` (don't fetch more than needed)
4. Monitor daily usage

### Upgrade Considerations

If hitting rate limits frequently:

- Developer plan: 250,000 req/month
- Business plan: Custom limits
- Consider caching layer (Redis)

## Data Models

### Article

```kotlin
@Serializable
data class Article(
    val source: Source,
    val author: String?,
    val title: String,
    val description: String?,
    val url: String,
    val urlToImage: String?,
    val publishedAt: String,
    val content: String?
)
```

### Source

```kotlin
@Serializable
data class Source(
    val id: String?,
    val name: String
)
```

### NewsResponse

```kotlin
@Serializable
data class NewsResponse(
    val status: String,
    val totalResults: Int,
    val articles: List<Article>
)
```

## Dependencies

```kotlin
dependencies {
    implementation(project(":shared"))

    // Ktor Server
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.network.tls.certificates)

    // Ktor Client (for NewsAPI.org)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // MCP SDK
    implementation("io.modelcontextprotocol:sdk:0.1.0")
}
```

## Future Enhancements

1. **Caching**: Redis cache for popular queries (1-hour TTL)
2. **Sentiment Analysis**: Analyze article sentiment
3. **Summarization**: AI-generated article summaries
4. **Translation**: Translate articles to user's language
5. **Image OCR**: Extract text from article images
6. **Trending Topics**: Identify trending news topics
7. **Custom Filters**: Filter by date range, sources
8. **Save to Database**: Store articles in News CRUD service

## Troubleshooting

### Issue: API Key Not Working

**Symptoms**: All requests return "apiKeyInvalid"

**Solutions**:

1. Verify API key in NewsAPI.org dashboard
2. Check for extra spaces in environment variable
3. Regenerate API key if necessary

### Issue: No Results Returned

**Symptoms**: `totalResults: 0` for valid queries

**Solutions**:

1. Try broader search terms
2. Remove language/country filters
3. Check if query is too specific
4. Verify news exists for date range

### Issue: SSL Certificate Error

**Symptom**: Main server can't connect to HTTPS endpoint

**Solution**: Same as MCP Notes - update truststore or use HTTP endpoint (dev only)

## Related Documentation

- [Server Module](../server.md) - Main server with tool calling
- [News CRUD Service](../services/news-crud.md) - Local news storage
- [MCP NewsCRUD Server](newscrud.md) - CRUD operations via MCP

## References

- [NewsAPI.org Documentation](https://newsapi.org/docs)
- [NewsAPI.org Endpoints](https://newsapi.org/docs/endpoints)
- [Model Context Protocol](https://modelcontextprotocol.io/)
