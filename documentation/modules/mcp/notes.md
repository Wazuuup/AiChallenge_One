# MCP Notes Server

## Overview

The MCP Notes server is a Model Context Protocol (MCP) server that provides AI tools for notes management and Russian
Central Bank (ЦБ РФ) currency exchange rates. It exposes these capabilities through an MCP interface that can be called
by AI assistants via the main server's tool calling feature.

**Ports**: 8082 (HTTP), 8443 (HTTPS)
**Technology**: Ktor + MCP SDK + Auto-generated SSL
**Backend**: Notes Service (port 8084)

## Architecture

```
AI Assistant (via OpenRouter)
    ↓
Main Server (Tool Calling)
    ↓
MCP Notes Server (8082/8443)
    ├─→ NotesApiService → Notes Service (8084) → PostgreSQL
    └─→ CurrencyExchangeService → ЦБ РФ API
```

## MCP Tools

### Notes Management Tools

#### 1. create_note

**Purpose**: Create a new note

**Parameters**:

- `title` (required): Note title
- `content` (required): Note content
- `priority` (optional): "low", "medium", or "high"

**Example**:

```json
{
  "name": "create_note",
  "arguments": {
    "title": "Meeting Notes",
    "content": "Discuss Q4 roadmap",
    "priority": "high"
  }
}
```

**Response**:

```json
{
  "success": true,
  "note": {
    "id": 1,
    "title": "Meeting Notes",
    "content": "Discuss Q4 roadmap",
    "priority": "high"
  }
}
```

#### 2. get_all_notes

**Purpose**: Retrieve all notes

**Parameters**: None

**Response**:

```json
{
  "notes": [
    {
      "id": 1,
      "title": "Meeting Notes",
      "content": "Discuss Q4 roadmap",
      "priority": "high"
    }
  ]
}
```

#### 3. get_note_by_id

**Purpose**: Get a specific note by ID

**Parameters**:

- `id` (required): Note ID

**Example**:

```json
{
  "name": "get_note_by_id",
  "arguments": {
    "id": 1
  }
}
```

#### 4. update_note

**Purpose**: Update an existing note

**Parameters**:

- `id` (required): Note ID
- `title` (optional): New title
- `content` (optional): New content
- `priority` (optional): New priority

**Example**:

```json
{
  "name": "update_note",
  "arguments": {
    "id": 1,
    "priority": "low"
  }
}
```

#### 5. delete_note

**Purpose**: Delete a note

**Parameters**:

- `id` (required): Note ID

**Response**:

```json
{
  "success": true,
  "message": "Note deleted successfully"
}
```

### Currency Exchange Tool

#### 6. get_exchange_rates

**Purpose**: Get currency exchange rates from ЦБ РФ (Central Bank of Russia)

**Parameters**:

- `date` (optional): Date in DD.MM.YYYY format (default: today)

**Example**:

```json
{
  "name": "get_exchange_rates",
  "arguments": {
    "date": "15.01.2025"
  }
}
```

**Response**:

```json
{
  "date": "15.01.2025",
  "rates": [
    {
      "charCode": "USD",
      "name": "Доллар США",
      "value": 92.5,
      "nominal": 1
    },
    {
      "charCode": "EUR",
      "name": "Евро",
      "value": 100.3,
      "nominal": 1
    }
  ]
}
```

## Key Components

### 1. Application.kt

**Purpose**: HTTP/HTTPS server setup

**Features**:

- Dual protocol support (HTTP on 8082, HTTPS on 8443)
- Auto-generated self-signed SSL certificates
- CORS enabled (development mode)
- MCP endpoint routing

**SSL Configuration**:

```kotlin
val keyStore = generateKeyStore(
    alias = sslKeyAlias,
    keystorePassword = keystorePassword,
    keyPassword = keyPassword
)
```

**Keystore Location**: `mcp/notes/src/main/resources/keystore.jks`

### 2. McpConfiguration.kt

**Purpose**: MCP server configuration and tool definitions

**Endpoints**:

- `GET /` - Health check
- `GET /tools/list` - List available tools
- `POST /tools/call` - Execute a tool

**Tool Registration**:

```kotlin
val tools = listOf(
    createNoteTool,
    getAllNotesTool,
    getNoteByIdTool,
    updateNoteTool,
    deleteNoteTool,
    getExchangeRatesTool
)
```

### 3. NotesApiService.kt

**Purpose**: HTTP client for Notes Service API

**Base URL**: `http://localhost:8084`

**Methods**:

- `createNote(title, content, priority): NoteResponse`
- `getAllNotes(): List<Note>`
- `getNoteById(id): Note?`
- `updateNote(id, title, content, priority): Note?`
- `deleteNote(id): Boolean`

### 4. CurrencyExchangeService.kt

**Purpose**: HTTP client for ЦБ РФ API

**Base URL**: `https://www.cbr.ru/scripts/XML_daily.asp`

**Methods**:

- `getExchangeRates(date: String?): ExchangeRatesResponse`

**XML Parsing**:
Uses Kotlin's XML parsing to extract currency data from ЦБ РФ XML response.

## Configuration

### application.conf

```hocon
ktor {
  deployment {
    httpPort = 8082
    httpPort = ${?HTTP_PORT}
    httpsPort = 8443
    httpsPort = ${?HTTPS_PORT}
  }
}

ssl {
  keyAlias = "mcpnotes"
  keyAlias = ${?SSL_KEY_ALIAS}
  keystorePassword = "changeit"
  keystorePassword = ${?SSL_KEYSTORE_PASSWORD}
  keyPassword = "changeit"
  keyPassword = ${?SSL_KEY_PASSWORD}
}

notes {
  apiUrl = "http://localhost:8084"
  apiUrl = ${?NOTES_API_URL}
}
```

### Environment Variables

```bash
# Ports
HTTP_PORT=8082
HTTPS_PORT=8443

# SSL Configuration
SSL_KEY_ALIAS="mcpnotes"
SSL_KEYSTORE_PASSWORD="changeit"
SSL_KEY_PASSWORD="changeit"

# Notes Service URL
NOTES_API_URL="http://localhost:8084"
```

## Running the Server

### Development

```bash
# Terminal 1: Start Notes Service (dependency)
.\gradlew.bat :services:notes:run

# Terminal 2: Start MCP Notes Server
.\gradlew.bat :mcp:notes:run
```

### Production

```bash
# Build distribution
.\gradlew.bat :mcp:notes:installDist

# Run
mcp\notes\build\install\notes\bin\notes.bat
```

## Testing

### Manual Testing

#### Health Check

```bash
# HTTP
curl http://localhost:8082/

# HTTPS (ignore self-signed cert)
curl -k https://localhost:8443/
```

#### List Tools

```bash
curl http://localhost:8082/tools/list
```

**Response**:

```json
{
  "tools": [
    {
      "name": "create_note",
      "description": "Create a new note",
      "inputSchema": {...}
    },
    {
      "name": "get_exchange_rates",
      "description": "Get currency exchange rates from ЦБ РФ",
      "inputSchema": {...}
    }
  ]
}
```

#### Call Tool

```bash
curl -X POST http://localhost:8082/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "create_note",
    "arguments": {
      "title": "Test Note",
      "content": "This is a test"
    }
  }'
```

### Integration Testing (via Main Server)

```bash
# Enable tools in chat
curl -X POST http://localhost:8080/api/send-message \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Create a note with title Meeting and content Discuss budget",
    "provider": "openrouter",
    "model": "anthropic/claude-3.5-sonnet",
    "enableTools": true
  }'
```

## SSL/TLS Certificate Management

### Auto-Generation

On first run, the server automatically generates a self-signed certificate:

```kotlin
fun generateKeyStore(
    alias: String,
    keystorePassword: String,
    keyPassword: String
): KeyStore {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    val keyPair = keyPairGenerator.generateKeyPair()

    // Generate X.509 certificate
    val cert = generateCertificate(keyPair, "CN=localhost")

    // Store in keystore
    val keyStore = KeyStore.getInstance("JKS")
    keyStore.load(null, null)
    keyStore.setKeyEntry(
        alias,
        keyPair.private,
        keyPassword.toCharArray(),
        arrayOf(cert)
    )

    return keyStore
}
```

### Regenerating Certificate

```bash
# Delete existing keystore
del mcp\notes\src\main\resources\keystore.jks

# Restart server (auto-generates new certificate)
.\gradlew.bat :mcp:notes:run
```

### Using Custom Certificate

```bash
# Generate with keytool
keytool -genkeypair -alias mcpnotes -keyalg RSA -keysize 2048 \
  -validity 365 -keystore keystore.jks \
  -storepass changeit -keypass changeit \
  -dname "CN=localhost, OU=MCP, O=AiChallenge, L=Moscow, C=RU"

# Place in resources
copy keystore.jks mcp\notes\src\main\resources\keystore.jks
```

## Error Handling

### Common Errors

**Notes Service Unavailable**:

```json
{
  "error": "Failed to connect to Notes Service at http://localhost:8084"
}
```

**Solution**: Start Notes Service first

**ЦБ РФ API Error**:

```json
{
  "error": "Failed to fetch exchange rates: Connection timeout"
}
```

**Solution**: Check internet connection, ЦБ РФ API may be down

**Invalid Tool Call**:

```json
{
  "error": "Unknown tool: invalid_tool_name"
}
```

**Solution**: Use `/tools/list` to see available tools

## Use Cases

### 1. AI-Driven Note Taking

**User**: "Create a note reminding me to review the Q4 budget tomorrow"

**AI Tool Call**:

```json
{
  "name": "create_note",
  "arguments": {
    "title": "Review Q4 Budget",
    "content": "Review and finalize Q4 budget tomorrow",
    "priority": "high"
  }
}
```

### 2. Currency Rate Lookup

**User**: "What's the current USD exchange rate?"

**AI Tool Call**:

```json
{
  "name": "get_exchange_rates"
}
```

**AI Response**: "The current USD exchange rate is 92.5 RUB per 1 USD."

### 3. Note Management

**User**: "Show me all my high priority notes"

**AI Flow**:

1. Call `get_all_notes`
2. Filter notes with `priority == "high"`
3. Return formatted list

## Dependencies

```kotlin
dependencies {
    implementation(project(":shared"))

    // Ktor Server
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.server.cors.jvm)
    implementation(libs.ktor.network.tls.certificates)

    // Ktor Client (for Notes Service + ЦБ РФ API)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // MCP SDK
    implementation("io.modelcontextprotocol:sdk:0.1.0")

    // XML Parsing (for ЦБ РФ)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-xml:1.x.x")
}
```

## Future Enhancements

1. **OAuth 2.0**: Secure authentication for tool calls
2. **Rate Limiting**: Prevent API abuse
3. **Caching**: Cache ЦБ РФ exchange rates (1-day TTL)
4. **More Currencies**: Support more currency APIs
5. **Note Search**: Add semantic search tool
6. **Batch Operations**: Create/update multiple notes at once
7. **Webhooks**: Notify on note changes
8. **Analytics**: Track most-used tools

## Troubleshooting

### Issue: SSL Certificate Error (Main Server)

**Error**: `SSLHandshakeException: unable to find valid certification path`

**Solution**: Main server needs to trust the self-signed certificate. Update main server's truststore or disable SSL
verification (dev only).

### Issue: Connection Refused (Notes Service)

**Symptom**: Tool calls fail with connection error

**Solution**:

1. Verify Notes Service is running: `curl http://localhost:8084/`
2. Check `NOTES_API_URL` environment variable
3. Verify PostgreSQL is running (Notes Service dependency)

## Related Documentation

- [Notes Service](../services/notes.md) - Backend API
- [Server Module](../server.md) - Main server with tool calling
- [MCP Specification](https://modelcontextprotocol.io/) - MCP protocol docs

## References

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [ЦБ РФ API](https://www.cbr.ru/development/)
- [Ktor TLS Certificates](https://ktor.io/docs/ssl.html)
