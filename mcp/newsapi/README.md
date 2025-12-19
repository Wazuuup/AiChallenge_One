# MCP NewsAPI Server

A Model Context Protocol (MCP) server that provides tools for fetching news from [NewsAPI.org](https://newsapi.org).

## Overview

This module implements an MCP server that exposes two news-related tools:

- **search_news**: Search for news articles using the `/everything/` endpoint
- **get_top_headlines**: Get breaking news headlines using the `/top-headlines/` endpoint

## Features

- HTTP and HTTPS support (ports 8085 and 8444)
- Auto-generated self-signed SSL certificates
- HOCON configuration for API key management
- Full NewsAPI integration with validation
- Detailed error handling and response formatting

## Prerequisites

1. **NewsAPI API Key**: Get your free API key from [newsapi.org](https://newsapi.org/register)
2. **Java 17+**: Required for running Kotlin/JVM applications
3. **Gradle**: Included via Gradle wrapper

## Configuration

### Environment Variable (Recommended)

Set your NewsAPI key as an environment variable:

```bash
# Windows (Command Prompt)
set NEWSAPI_API_KEY=your_api_key_here

# Windows (PowerShell)
$env:NEWSAPI_API_KEY="your_api_key_here"

# Linux/macOS
export NEWSAPI_API_KEY=your_api_key_here
```

### Configuration File

The API key is loaded from `application.conf`:

```hocon
newsapi {
    baseUrl = "https://newsapi.org/v2"
    apiKey = ${?NEWSAPI_API_KEY}  # Loaded from environment variable
}
```

## Running the Server

### Development Mode

```bash
# Windows
.\gradlew.bat :mcp-newsapi:run

# Linux/macOS
./gradlew :mcp-newsapi:run
```

The server will start on:

- HTTP: `http://localhost:8085`
- HTTPS: `https://localhost:8444`

### Production Build

```bash
# Build distribution
.\gradlew.bat :mcp-newsapi:installDist

# Run the distribution
mcp-newsapi\build\install\mcp-newsapi\bin\mcp-newsapi.bat
```

## Available Tools

### 1. search_news

Search for news articles by keywords, date range, and language.

**Parameters:**

- `query` (required): Keywords or phrase to search for
- `from` (required): Start date in ISO-8601 format (e.g., "2025-12-01")
- `sortBy` (required): Sort by "relevancy", "popularity", or "publishedAt"
- `language` (required): 2-letter ISO-639-1 language code (e.g., "en", "ru")
- `page` (optional): Page number for pagination

**Example:**

```json
{
  "query": "artificial intelligence",
  "from": "2025-12-01",
  "sortBy": "publishedAt",
  "language": "en",
  "page": 1
}
```

### 2. get_top_headlines

Get breaking news headlines by country and category.

**Parameters:**

- `country` (required): 2-letter ISO 3166-1 country code (e.g., "us", "ru")
- `category` (optional): "business", "entertainment", "general", "health", "science", "sports", "technology"
- `q` (optional): Keywords to search for
- `pageSize` (optional): Results per page (max 100)
- `page` (optional): Page number

**Example:**

```json
{
  "country": "us",
  "category": "technology",
  "pageSize": 10
}
```

## API Endpoints

### Health Check

```
GET /health
```

Returns server status.

### MCP Endpoint

```
POST /mcp
```

MCP protocol endpoint for tool invocation.

## SSL/TLS Configuration

The server automatically generates a self-signed certificate on first startup:

- **Keystore location**: `mcp-newsapi/src/main/resources/keystore.jks`
- **Default password**: `changeit`
- **Alias**: `newsapiserver`

### Custom SSL Configuration

Use environment variables:

```bash
set SSL_KEY_ALIAS=myalias
set SSL_KEYSTORE_PASSWORD=mypassword
set SSL_KEY_PASSWORD=mykeypassword
```

## Response Format

### Success Response

```
Found 100 total results
Showing 10 articles:

--- Article 1 ---
Title: Example Article Title
Author: John Doe
Source: TechNews
Description: Article description...
URL: https://example.com/article
Published: 2025-12-18T10:00:00Z
```

### Error Response

```
Error fetching news: Invalid API key (Code: apiKeyInvalid)
```

## Project Structure

```
mcp-newsapi/
├── src/main/
│   ├── kotlin/ru/sber/cb/aichallenge_one/mcp_newsapi/
│   │   ├── Application.kt           # Entry point with SSL setup
│   │   ├── NewsApiConfiguration.kt  # MCP tool definitions
│   │   ├── models/
│   │   │   └── NewsApiModels.kt    # Data models
│   │   └── service/
│   │       └── NewsApiService.kt   # HTTP client for NewsAPI
│   └── resources/
│       ├── application.conf        # HOCON configuration
│       ├── logback.xml            # Logging configuration
│       └── keystore.jks           # Auto-generated SSL certificate
└── build.gradle.kts               # Build configuration
```

## Dependencies

- **MCP SDK**: `io.modelcontextprotocol:kotlin-sdk:0.8.1`
- **Ktor**: 3.3.3 (Server: Netty, Client: CIO)
- **Kotlinx Serialization**: 1.8.0
- **Typesafe Config**: 1.4.2 (HOCON)
- **Logback**: 1.5.20

## Error Handling

The server validates all inputs and provides detailed error messages:

- Empty or invalid parameters
- Invalid country codes (must be 2 letters)
- Invalid categories
- Invalid sortBy options
- PageSize out of range (1-100)
- API errors from NewsAPI

## Limitations

- **Rate Limits**: Free NewsAPI tier has usage limits (check [newsapi.org/pricing](https://newsapi.org/pricing))
- **Historical Data**: Free tier limited to articles from the last 30 days
- **Sources**: Some sources may require paid subscription

## Troubleshooting

### "API key invalid" error

- Check that `NEWSAPI_API_KEY` environment variable is set
- Verify your API key at [newsapi.org/account](https://newsapi.org/account)

### SSL certificate errors

- Delete `keystore.jks` and restart to regenerate
- Check that port 8444 is not blocked by firewall

### "No articles found"

- Try broader search terms
- Check date range (free tier: last 30 days only)
- Verify language code is valid

## Related Modules

- **mcp-server**: Original MCP server with currency and notes tools (ports 8082/8443)
- **mcp-client**: CLI client for testing MCP connections
- **server**: Main Ktor backend server (port 8080)

## References

- [NewsAPI Documentation](https://newsapi.org/docs)
- [MCP Protocol Specification](https://modelcontextprotocol.io/)
- [Kotlin MCP SDK](https://github.com/modelcontextprotocol/kotlin-sdk)
- [Ktor Documentation](https://ktor.io/)

## License

Part of AiChallenge_One project.
