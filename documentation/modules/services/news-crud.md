# News CRUD Service

## Overview

The News CRUD service is a REST API server that provides CRUD operations and full-text search for news articles with
PostgreSQL persistent storage. It integrates with external news sources (via MCP NewsAPI) and provides local storage for
news articles.

**Port**: 8087
**Technology**: Ktor + Exposed ORM + PostgreSQL + HikariCP
**Pattern**: Repository pattern with service layer

## Architecture

```
HTTP Request → NewsRouting → NewsService → NewsRepository → PostgreSQL
                                  ↓
                      Validation & Search Logic
```

## Key Components

### 1. Application.kt

**Purpose**: Main entry point and Ktor server configuration

**Features**:

- Netty engine on port 8087
- CORS enabled (development mode)
- JSON serialization with kotlinx.serialization
- Koin dependency injection
- Automatic database schema creation
- Health check endpoint

### 2. NewsRouting.kt

**Purpose**: REST API endpoint definitions

**Routes**:

- `POST /api/news` - Create new article
- `GET /api/news` - Get all articles (with pagination)
- `GET /api/news/search?q={query}` - Search articles
- `GET /api/news/{id}` - Get article by ID
- `PUT /api/news/{id}` - Update article
- `DELETE /api/news/{id}` - Delete article

### 3. NewsService.kt

**Purpose**: Business logic layer

**Responsibilities**:

- Input validation
- Search query processing
- Business rule enforcement
- Error handling

**Methods**:

- `createArticle(request: CreateArticleRequest): Article`
- `getAllArticles(limit: Int, offset: Long): List<Article>`
- `searchArticles(query: String, limit: Int): List<Article>`
- `getArticleById(id: Int): Article?`
- `updateArticle(id: Int, request: UpdateArticleRequest): Article?`
- `deleteArticle(id: Int): Boolean`

### 4. NewsRepository.kt

**Purpose**: Data access layer

**Responsibilities**:

- Database queries using Exposed DSL
- Full-text search implementation
- Transaction management
- Data mapping (ResultRow → Article)

### 5. Database Layer

#### ArticlesTable.kt - Schema Definition

```kotlin
object ArticlesTable : Table("articles") {
    val id = integer("id").autoIncrement()
    val sourceId = varchar("source_id", 100).nullable()
    val sourceName = varchar("source_name", 200).nullable()
    val author = varchar("author", 500).nullable()
    val title = varchar("title", 1000)
    val description = text("description").nullable()
    val url = varchar("url", 2000).nullable()
    val urlToImage = varchar("urlToImage", 2000).nullable()
    val publishedAt = varchar("publishedAt", 100).nullable()
    val content = text("content").nullable()
    val createdAt = timestamp("createdAt").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updatedAt").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}
```

#### DatabaseFactory.kt

- HikariCP connection pooling (max 10 connections)
- Automatic schema creation
- Transaction support with `newSuspendedTransaction`

## API Documentation

### 1. Create Article

**Endpoint**: `POST /api/news`

**Request Body**:

```json
{
  "sourceId": "techcrunch",
  "sourceName": "TechCrunch",
  "author": "John Doe",
  "title": "Kotlin 2.0 Released",
  "description": "JetBrains announces Kotlin 2.0 with major improvements",
  "url": "https://techcrunch.com/kotlin-2.0",
  "urlToImage": "https://techcrunch.com/images/kotlin.jpg",
  "publishedAt": "2025-01-15T10:00:00Z",
  "content": "Full article content here..."
}
```

**Response (201 Created)**:

```json
{
  "id": 1,
  "sourceId": "techcrunch",
  "sourceName": "TechCrunch",
  "author": "John Doe",
  "title": "Kotlin 2.0 Released",
  "description": "JetBrains announces Kotlin 2.0 with major improvements",
  "url": "https://techcrunch.com/kotlin-2.0",
  "urlToImage": "https://techcrunch.com/images/kotlin.jpg",
  "publishedAt": "2025-01-15T10:00:00Z",
  "content": "Full article content here...",
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T10:30:00Z"
}
```

**Validation Rules**:

- `title`: Required, non-blank, max 1000 characters
- All other fields: Optional

### 2. Get All Articles

**Endpoint**: `GET /api/news?limit={limit}&offset={offset}`

**Query Parameters**:

- `limit` (optional): Max articles to return (default: 100, max: 1000)
- `offset` (optional): Number of articles to skip (default: 0)

**Example**: `GET /api/news?limit=20&offset=0`

**Response (200 OK)**:

```json
[
  {
    "id": 1,
    "sourceId": "techcrunch",
    "sourceName": "TechCrunch",
    "author": "John Doe",
    "title": "Kotlin 2.0 Released",
    "description": "JetBrains announces Kotlin 2.0...",
    "url": "https://techcrunch.com/kotlin-2.0",
    "urlToImage": "https://techcrunch.com/images/kotlin.jpg",
    "publishedAt": "2025-01-15T10:00:00Z",
    "content": "Full article content...",
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T10:30:00Z"
  }
]
```

**Pagination Example**:

```bash
# Get first page (20 articles)
curl "http://localhost:8087/api/news?limit=20&offset=0"

# Get second page
curl "http://localhost:8087/api/news?limit=20&offset=20"

# Get third page
curl "http://localhost:8087/api/news?limit=20&offset=40"
```

### 3. Search Articles

**Endpoint**: `GET /api/news/search?q={query}&limit={limit}`

**Query Parameters**:

- `q` (required): Search query
- `limit` (optional): Max results (default: 100)

**Search Fields**:

- `title`
- `description`
- `content`

**Example**: `GET /api/news/search?q=Kotlin&limit=10`

**Response (200 OK)**:

```json
[
  {
    "id": 1,
    "title": "Kotlin 2.0 Released",
    "description": "JetBrains announces Kotlin 2.0...",
    ...
  },
  {
    "id": 5,
    "title": "Getting Started with Kotlin Multiplatform",
    ...
  }
]
```

**Search Behavior**:

- Case-insensitive
- Partial matches (using SQL `LIKE`)
- Searches across title, description, and content fields
- Returns articles matching ANY field

### 4. Get Article by ID

**Endpoint**: `GET /api/news/{id}`

**Example**: `GET /api/news/1`

**Response (200 OK)**:

```json
{
  "id": 1,
  "sourceId": "techcrunch",
  "sourceName": "TechCrunch",
  "author": "John Doe",
  "title": "Kotlin 2.0 Released",
  "description": "JetBrains announces Kotlin 2.0...",
  "url": "https://techcrunch.com/kotlin-2.0",
  "urlToImage": "https://techcrunch.com/images/kotlin.jpg",
  "publishedAt": "2025-01-15T10:00:00Z",
  "content": "Full article content...",
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T10:30:00Z"
}
```

**Response (404 Not Found)**:

```json
{
  "error": "Article not found"
}
```

### 5. Update Article

**Endpoint**: `PUT /api/news/{id}`

**Request**: `PUT /api/news/1`

```json
{
  "title": "Kotlin 2.1 Released - Updated",
  "description": "Updated description",
  "content": "Updated full content..."
}
```

**Response (200 OK)**:

```json
{
  "id": 1,
  "title": "Kotlin 2.1 Released - Updated",
  "description": "Updated description",
  "content": "Updated full content...",
  "updatedAt": "2025-01-15T14:20:00Z",
  ...
}
```

**Response (404 Not Found)**:

```json
{
  "error": "Article not found"
}
```

**Notes**:

- All fields in UpdateArticleRequest are optional
- Only provided fields are updated
- `updatedAt` timestamp is automatically updated

### 6. Delete Article

**Endpoint**: `DELETE /api/news/{id}`

**Example**: `DELETE /api/news/1`

**Response (200 OK)**:

```json
{
  "success": true,
  "message": "Article deleted successfully"
}
```

**Response (404 Not Found)**:

```json
{
  "error": "Article not found"
}
```

## Configuration

### application.conf

```hocon
ktor {
  deployment {
    port = 8087
    port = ${?PORT}
  }
  application {
    modules = [ru.sber.cb.aichallenge_one.news.ApplicationKt.module]
  }
}

database {
  url = "jdbc:postgresql://localhost:5432/newsdb"
  url = ${?DATABASE_URL}
  driver = "org.postgresql.Driver"
  user = "postgres"
  user = ${?DATABASE_USER}
  password = "postgres"
  password = ${?DATABASE_PASSWORD}
  maxPoolSize = 10
}
```

### Environment Variables

```bash
# Server
PORT=8087

# Database
DATABASE_URL="jdbc:postgresql://localhost:5432/newsdb"
DATABASE_USER="postgres"
DATABASE_PASSWORD="postgres"
```

## Database Setup

### Docker (Recommended)

```bash
docker run -d \
  --name newsdb \
  -p 5432:5432 \
  -e POSTGRES_DB=newsdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  postgres:15

# Service automatically creates schema on startup
```

### Manual Setup

```sql
-- Create database
CREATE DATABASE newsdb;

-- Service will auto-create 'articles' table on first run
```

### Database Schema

Automatically created on service startup:

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
    publishedAt VARCHAR(100),
    content TEXT,
    createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Future enhancement: Full-text search index
CREATE INDEX idx_articles_title ON articles USING gin(to_tsvector('english', title));
CREATE INDEX idx_articles_description ON articles USING gin(to_tsvector('english', description));
CREATE INDEX idx_articles_content ON articles USING gin(to_tsvector('english', content));
```

## Running the Service

### Development

```bash
# Start PostgreSQL
docker run -d --name newsdb -p 5432:5432 \
  -e POSTGRES_DB=newsdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  postgres:15

# Run service
.\gradlew.bat :services:news-crud:run
```

### Production

```bash
# Build distribution
.\gradlew.bat :services:news-crud:installDist

# Run
services\news-crud\build\install\news-crud\bin\news-crud.bat
```

### Health Check

```bash
curl http://localhost:8087/
# Response: "News CRUD API is running"
```

## Usage Examples

### Example 1: Import News from External API

```bash
# Fetch from NewsAPI (via MCP NewsAPI server)
# Then save to local CRUD database

curl -X POST http://localhost:8087/api/news \
  -H "Content-Type: application/json" \
  -d '{
    "sourceId": "bbc-news",
    "sourceName": "BBC News",
    "author": "BBC Editorial",
    "title": "Breaking: Major Tech Announcement",
    "description": "A major tech company announces new product",
    "url": "https://bbc.com/article",
    "publishedAt": "2025-01-15T12:00:00Z"
  }'
```

### Example 2: Search for AI-Related News

```bash
curl "http://localhost:8087/api/news/search?q=artificial%20intelligence&limit=5"
```

### Example 3: Get Latest Articles with Pagination

```bash
# Get latest 10 articles
curl "http://localhost:8087/api/news?limit=10&offset=0"

# Get next 10 articles
curl "http://localhost:8087/api/news?limit=10&offset=10"
```

## Integration with MCP NewsCRUD Server

The News CRUD service is consumed by the MCP NewsCRUD server (ports 8086/8445):

```
MCP Client → MCP NewsCRUD Server (8086) → News CRUD Service (8087) → PostgreSQL
                    ↓
            Tool Calling Interface
```

**MCP Tools** that use News CRUD Service:

- `get_all_articles` → `GET /api/news`
- `get_article_by_id` → `GET /api/news/{id}`
- `search_articles` → `GET /api/news/search?q={query}`
- `create_article` → `POST /api/news`
- `update_article` → `PUT /api/news/{id}`
- `delete_article` → `DELETE /api/news/{id}`

## Data Models

### Article (shared/src/commonMain/kotlin/.../models/news/Article.kt)

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
    val publishedAt: String? = null,  // ISO 8601
    val content: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
```

### CreateArticleRequest

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

### UpdateArticleRequest

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

## Error Handling

### Validation Errors (400 Bad Request)

```json
{
  "error": "Title cannot be blank"
}
```

### Not Found (404)

```json
{
  "error": "Article not found"
}
```

### Server Errors (500)

```json
{
  "error": "Internal server error"
}
```

## Performance Considerations

### Search Performance

- Current: SQL `LIKE` queries (suitable for small datasets)
- Future: PostgreSQL full-text search with GIN indexes
- Recommendation: Add text search indexes for >10K articles

### Pagination

- Use `limit` and `offset` for large result sets
- Recommended page size: 20-50 articles
- Database indexed by primary key (fast offset queries)

### Connection Pooling

- HikariCP with max 10 connections
- Suitable for moderate load
- Increase for high-concurrency scenarios

## Security Considerations

⚠️ **DEVELOPMENT ONLY** - Not production-ready

**Issues**:

- Open CORS policy
- No authentication/authorization
- No rate limiting
- Arbitrary content submission
- No URL validation (potential XSS via urlToImage)

**Production TODO**:

- JWT authentication
- User-scoped articles
- Content moderation
- URL whitelist/validation
- HTTPS enforcement
- Rate limiting
- Input sanitization

## Testing

### Manual Testing

```bash
# Complete workflow test
# 1. Create article
ARTICLE_ID=$(curl -s -X POST http://localhost:8087/api/news \
  -H "Content-Type: application/json" \
  -d '{"title":"Test Article","description":"Testing"}' | jq -r '.id')

# 2. Search
curl "http://localhost:8087/api/news/search?q=Test"

# 3. Update
curl -X PUT http://localhost:8087/api/news/$ARTICLE_ID \
  -H "Content-Type: application/json" \
  -d '{"content":"Updated content"}'

# 4. Delete
curl -X DELETE http://localhost:8087/api/news/$ARTICLE_ID
```

### Unit Tests (Future)

```bash
.\gradlew.bat :services:news-crud:test
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

## Future Enhancements

1. **Full-Text Search**: PostgreSQL `tsvector` and GIN indexes
2. **Categories/Tags**: Article categorization
3. **Sentiment Analysis**: Classify article sentiment
4. **Duplicate Detection**: Prevent duplicate articles by URL
5. **RSS Feed**: Subscribe to news sources via RSS
6. **Caching**: Redis cache for popular searches
7. **Archive**: Mark articles as archived instead of deleting
8. **User Bookmarks**: Let users save favorite articles
9. **Article Summary**: AI-generated article summaries
10. **Export**: Export articles to PDF, JSON, CSV

## Troubleshooting

### Issue: Search Returns No Results

**Causes**:

- Query is case-sensitive (should be case-insensitive)
- No matching articles in database

**Solutions**:

- Verify data exists: `curl http://localhost:8087/api/news`
- Check search query spelling
- Use broader search terms

### Issue: Pagination Returns Duplicate Articles

**Cause**: Articles being created during pagination

**Solution**: Use `createdAt` timestamp-based pagination (future enhancement)

## Related Documentation

- [MCP NewsCRUD Server](../mcp/newscrud.md) - MCP wrapper for this service
- [MCP NewsAPI Server](../mcp/newsapi.md) - External news fetching
- [Shared Module](../shared.md) - Data models
- [Getting Started](../../GETTING-STARTED.md) - Setup guide

## References

- [Ktor Server](https://ktor.io/docs/servers.html)
- [Exposed ORM](https://github.com/JetBrains/Exposed)
- [PostgreSQL Full-Text Search](https://www.postgresql.org/docs/current/textsearch.html)
- [NewsAPI.org](https://newsapi.org/) - External news source
