# Notes Service

## Overview

The Notes service is a REST API server that provides CRUD (Create, Read, Update, Delete) operations for managing notes
with PostgreSQL persistent storage. It serves as both a standalone microservice and a backend for the MCP Notes server.

**Port**: 8084
**Technology**: Ktor + Exposed ORM + PostgreSQL + HikariCP
**Pattern**: Repository pattern with service layer

## Architecture

```
HTTP Request → NotesRouting → NotesService → NoteRepository → PostgreSQL
                                    ↓
                            Validation & Business Logic
```

## Key Components

### 1. Application.kt

**Purpose**: Main entry point and Ktor server configuration

**Features**:

- Netty engine on port 8084
- CORS enabled (development mode)
- JSON serialization with kotlinx.serialization
- Koin dependency injection
- Automatic database initialization
- Health check endpoint

### 2. NotesRouting.kt

**Purpose**: REST API endpoint definitions

**Routes**:

- `POST /api/notes` - Create new note
- `GET /api/notes` - Get all notes
- `GET /api/notes/{id}` - Get note by ID
- `PUT /api/notes/{id}` - Update note
- `DELETE /api/notes/{id}` - Delete note

### 3. NotesService.kt

**Purpose**: Business logic layer

**Responsibilities**:

- Input validation
- Business rule enforcement
- Response formatting
- Error handling

**Methods**:

- `createNote(request: CreateNoteRequest): NoteResponse`
- `getAllNotes(): List<Note>`
- `getNoteById(id: Int): NoteResponse`
- `updateNote(id: Int, request: UpdateNoteRequest): NoteResponse`
- `deleteNote(id: Int): NoteResponse`

### 4. NoteRepository.kt

**Purpose**: Data access layer

**Responsibilities**:

- Database queries using Exposed DSL
- Transaction management
- Data mapping (ResultRow → Note)

### 5. Database Layer

#### Tables.kt - Schema Definition

```kotlin
object Notes : Table("notes") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 500)
    val content = text("content")
    val priority = varchar("priority", 50).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}
```

#### DatabaseFactory.kt - Connection Management

- HikariCP connection pooling (max 10 connections)
- Automatic schema creation with `SchemaUtils.create(Notes)`
- Transaction support with `newSuspendedTransaction`

## API Documentation

### 1. Create Note

**Endpoint**: `POST /api/notes`

**Request Body**:

```json
{
  "title": "Meeting Notes",
  "content": "Discuss Q4 roadmap",
  "priority": "high"  // optional: "low", "medium", "high"
}
```

**Response (201 Created)**:

```json
{
  "success": true,
  "message": "Note created successfully",
  "note": {
    "id": 1,
    "title": "Meeting Notes",
    "content": "Discuss Q4 roadmap",
    "priority": "high",
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T10:30:00Z"
  }
}
```

**Validation Rules**:

- `title`: Required, non-blank, max 500 characters
- `content`: Required, non-blank
- `priority`: Optional, must be "low", "medium", or "high"

### 2. Get All Notes

**Endpoint**: `GET /api/notes`

**Response (200 OK)**:

```json
[
  {
    "id": 1,
    "title": "Meeting Notes",
    "content": "Discuss Q4 roadmap",
    "priority": "high",
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T10:30:00Z"
  },
  {
    "id": 2,
    "title": "Todo List",
    "content": "Buy groceries, finish report",
    "priority": "medium",
    "createdAt": "2025-01-15T11:00:00Z",
    "updatedAt": "2025-01-15T11:00:00Z"
  }
]
```

**Notes**:

- Returns empty array `[]` if no notes exist
- Ordered by creation date (newest first - future enhancement)

### 3. Get Note by ID

**Endpoint**: `GET /api/notes/{id}`

**Request**: `GET /api/notes/1`

**Response (200 OK)**:

```json
{
  "success": true,
  "message": "Note retrieved successfully",
  "note": {
    "id": 1,
    "title": "Meeting Notes",
    "content": "Discuss Q4 roadmap",
    "priority": "high",
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T10:30:00Z"
  }
}
```

**Response (404 Not Found)**:

```json
{
  "success": false,
  "message": "Note not found"
}
```

### 4. Update Note

**Endpoint**: `PUT /api/notes/{id}`

**Request**: `PUT /api/notes/1`

```json
{
  "title": "Updated Meeting Notes",
  "content": "Discuss Q4 roadmap and budget",
  "priority": "medium"
}
```

**Response (200 OK)**:

```json
{
  "success": true,
  "message": "Note updated successfully",
  "note": {
    "id": 1,
    "title": "Updated Meeting Notes",
    "content": "Discuss Q4 roadmap and budget",
    "priority": "medium",
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T14:20:00Z"  // Updated timestamp
  }
}
```

**Response (404 Not Found)**:

```json
{
  "success": false,
  "message": "Note not found"
}
```

**Notes**:

- All fields in UpdateNoteRequest are optional
- Only provided fields are updated
- `updatedAt` timestamp is automatically updated

### 5. Delete Note

**Endpoint**: `DELETE /api/notes/{id}`

**Request**: `DELETE /api/notes/1`

**Response (200 OK)**:

```json
{
  "success": true,
  "message": "Note deleted successfully"
}
```

**Response (404 Not Found)**:

```json
{
  "success": false,
  "message": "Note not found"
}
```

## Configuration

### application.conf

```hocon
ktor {
  deployment {
    port = 8084
    port = ${?PORT}
  }
  application {
    modules = [ru.sber.cb.aichallenge_one.notes.ApplicationKt.module]
  }
}

database {
  url = "jdbc:postgresql://localhost:5432/aichallenge"
  url = ${?DATABASE_URL}
  driver = "org.postgresql.Driver"
  user = "aichallenge"
  user = ${?DATABASE_USER}
  password = "aichallenge"
  password = ${?DATABASE_PASSWORD}
  maxPoolSize = 10
}
```

### Environment Variables

```bash
# Server
PORT=8084

# Database
DATABASE_URL="jdbc:postgresql://localhost:5432/aichallenge"
DATABASE_USER="aichallenge"
DATABASE_PASSWORD="aichallenge"
```

## Database Setup

### Option 1: Docker (Recommended)

```bash
docker run -d \
  --name notesdb \
  -p 5432:5432 \
  -e POSTGRES_DB=aichallenge \
  -e POSTGRES_USER=aichallenge \
  -e POSTGRES_PASSWORD=aichallenge \
  postgres:15

# The service will automatically create the schema on startup
```

### Option 2: Existing PostgreSQL

```sql
-- Create database
CREATE DATABASE aichallenge;

-- Create user
CREATE USER aichallenge WITH PASSWORD 'aichallenge';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE aichallenge TO aichallenge;

-- The service will auto-create the 'notes' table on first run
```

### Database Schema

The service automatically creates this schema on startup:

```sql
CREATE TABLE notes (
    id SERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    priority VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Future enhancement: Index on created_at for sorting
CREATE INDEX idx_notes_created_at ON notes(created_at DESC);
```

## Running the Service

### Development Mode

```bash
# Start PostgreSQL (if using Docker)
docker run -d --name notesdb -p 5432:5432 \
  -e POSTGRES_DB=aichallenge \
  -e POSTGRES_USER=aichallenge \
  -e POSTGRES_PASSWORD=aichallenge \
  postgres:15

# Run service with Gradle
.\gradlew.bat :services:notes:run

# Or with custom config
.\gradlew.bat :services:notes:run -Dconfig.file=application-dev.conf
```

### Production Distribution

```bash
# Build distribution
.\gradlew.bat :services:notes:installDist

# Run
services\notes\build\install\notes\bin\notes.bat
```

### Health Check

```bash
curl http://localhost:8084/
# Response: "Notes API is running"
```

## Usage Examples

### Example 1: Create and Retrieve Note

```bash
# Create note
curl -X POST http://localhost:8084/api/notes \
  -H "Content-Type: application/json" \
  -d '{"title": "Todo", "content": "Finish documentation", "priority": "high"}'

# Get all notes
curl http://localhost:8084/api/notes

# Get specific note
curl http://localhost:8084/api/notes/1
```

### Example 2: Update Note Priority

```bash
curl -X PUT http://localhost:8084/api/notes/1 \
  -H "Content-Type: application/json" \
  -d '{"priority": "low"}'
```

### Example 3: Delete Old Note

```bash
curl -X DELETE http://localhost:8084/api/notes/1
```

## Integration with MCP Notes Server

The Notes service is consumed by the MCP Notes server (port 8082/8443):

```
MCP Client → MCP Notes Server (8082) → Notes Service (8084) → PostgreSQL
                    ↓
            Tool Calling Interface
```

**MCP Tools** that use Notes Service:

- `create_note` → `POST /api/notes`
- `get_all_notes` → `GET /api/notes`
- `get_note_by_id` → `GET /api/notes/{id}`
- `update_note` → `PUT /api/notes/{id}`
- `delete_note` → `DELETE /api/notes/{id}`

## Data Models

### Note (shared/src/commonMain/kotlin/.../models/notes/Note.kt)

```kotlin
@Serializable
data class Note(
    val id: Int,
    val title: String,
    val content: String,
    val priority: NotePriority? = null,
    val createdAt: String,  // ISO 8601 timestamp
    val updatedAt: String
)
```

### NotePriority Enum

```kotlin
@Serializable
enum class NotePriority {
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH
}
```

### CreateNoteRequest

```kotlin
@Serializable
data class CreateNoteRequest(
    val title: String,
    val content: String,
    val priority: String? = null  // "low", "medium", or "high"
)
```

### UpdateNoteRequest

```kotlin
@Serializable
data class UpdateNoteRequest(
    val title: String? = null,
    val content: String? = null,
    val priority: String? = null
)
```

### NoteResponse

```kotlin
@Serializable
data class NoteResponse(
    val success: Boolean,
    val message: String,
    val note: Note? = null
)
```

## Error Handling

### Validation Errors (400 Bad Request)

```json
{
  "success": false,
  "message": "Title cannot be blank"
}
```

### Not Found Errors (404)

```json
{
  "success": false,
  "message": "Note not found"
}
```

### Server Errors (500)

```json
{
  "success": false,
  "message": "Internal server error"
}
```

**Logs**: Detailed errors are logged to console and log files

## Testing

### Manual Testing with curl

```bash
# Test complete workflow
# 1. Create
NOTE_ID=$(curl -s -X POST http://localhost:8084/api/notes \
  -H "Content-Type: application/json" \
  -d '{"title":"Test","content":"Testing"}' | jq -r '.note.id')

# 2. Read
curl http://localhost:8084/api/notes/$NOTE_ID

# 3. Update
curl -X PUT http://localhost:8084/api/notes/$NOTE_ID \
  -H "Content-Type: application/json" \
  -d '{"priority":"high"}'

# 4. Delete
curl -X DELETE http://localhost:8084/api/notes/$NOTE_ID
```

### Unit Tests (Future Enhancement)

```bash
.\gradlew.bat :services:notes:test
```

## Performance Considerations

### Connection Pooling

- HikariCP with max 10 connections
- Suitable for moderate load (~100 req/s)
- Increase `maxPoolSize` for higher concurrency

### Database Queries

- Simple CRUD operations (single table, indexed PK)
- No complex joins
- Future: Add pagination for large datasets

### Response Time

- **Target**: <50ms per request (local DB)
- **Actual**: ~10-30ms for CRUD operations

## Security Considerations

⚠️ **DEVELOPMENT ONLY** - Not production-ready

**Issues**:

- Open CORS policy (`anyHost()`)
- No authentication/authorization
- No rate limiting
- No input sanitization (SQL injection protected by Exposed DSL)
- No field-level permissions

**Production TODO**:

- JWT or API key authentication
- User-scoped notes (multi-tenancy)
- HTTPS enforcement
- Rate limiting per user
- Audit logging (who created/updated/deleted)
- Field validation (max lengths, allowed characters)
- CORS whitelist

## Monitoring

### Logs

```bash
# Application logs
tail -f services/notes/logs/application.log

# Ktor access logs (console)
# Shows request method, path, status, duration
```

### Metrics (Future Enhancement)

- Prometheus endpoint: `/metrics`
- Grafana dashboard for:
    - Request rate
    - Error rate
    - Response time (p50, p95, p99)
    - Database connection pool usage

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

1. **Pagination**: `GET /api/notes?limit=20&offset=0`
2. **Search**: `GET /api/notes/search?q=meeting`
3. **Tags**: Add tagging system for categorization
4. **Attachments**: File upload support
5. **Sharing**: Share notes with other users
6. **Versioning**: Track note edit history
7. **Soft Delete**: Mark as deleted instead of hard delete
8. **Batch Operations**: Bulk create/update/delete
9. **Export**: Export notes to PDF, Markdown, etc.
10. **Reminders**: Set reminders for notes with due dates

## Troubleshooting

### Issue: Port 8084 Already in Use

**Solution**:

```bash
# Find process using port
netstat -ano | findstr :8084

# Kill process or change port in application.conf
```

### Issue: Database Connection Failed

**Symptoms**: `Connection refused` or `authentication failed`

**Solutions**:

- Verify PostgreSQL is running: `docker ps | grep notesdb`
- Check credentials match `application.conf`
- Verify port 5432 is accessible

### Issue: Schema Not Created

**Symptoms**: `relation "notes" does not exist`

**Solutions**:

- Verify database name is correct
- Check user has CREATE TABLE privileges
- Restart service to trigger schema creation

## Related Documentation

- [MCP Notes Server](../mcp/notes.md) - MCP wrapper for this service
- [Shared Module](../shared.md) - Data models
- [Getting Started](../../GETTING-STARTED.md) - Setup instructions

## References

- [Ktor Server Documentation](https://ktor.io/docs/servers.html)
- [Exposed ORM](https://github.com/JetBrains/Exposed)
- [HikariCP](https://github.com/brettwooldridge/HikariCP)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
