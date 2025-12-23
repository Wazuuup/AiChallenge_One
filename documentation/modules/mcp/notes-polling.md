# MCP Notes Polling Server

## Overview

The MCP Notes Polling server is a Model Context Protocol (MCP) server that provides AI tools for managing the
notes-scheduler Docker container. It enables AI assistants to start and stop automated polling/scheduling tasks without
manual Docker command execution.

**Ports**: 8088 (HTTP), 8447 (HTTPS)
**Technology**: Ktor + MCP SDK + Docker SDK + Auto-generated SSL
**Backend**: Docker Engine

## Architecture

```
AI Assistant (via OpenRouter)
    ↓
Main Server (Tool Calling)
    ↓
MCP Notes Polling Server (8088/8447)
    ↓
Docker Engine
    ↓
Notes Scheduler Container
    ↓
MCP Notes Server (8082) - Polling Target
```

## MCP Tools

### 1. trigger_notes_summary_polling

**Purpose**: Build and start the notes-scheduler Docker container with custom configuration

**Parameters**:

- `cron_expression` (optional): Cron schedule (default: "*/2 * * * *" - every 2 minutes)
- `mcp_server_url` (optional): MCP Notes server URL (default: "http://host.docker.internal:8082")

**Cron Expression Format**:

```
* * * * *
│ │ │ │ │
│ │ │ │ └─ Day of week (0-6, Sunday=0)
│ │ │ └─── Month (1-12)
│ │ └───── Day of month (1-31)
│ └─────── Hour (0-23)
└───────── Minute (0-59)
```

**Example**:

```json
{
  "name": "trigger_notes_summary_polling",
  "arguments": {
    "cron_expression": "0 */6 * * *",
    "mcp_server_url": "http://host.docker.internal:8082"
  }
}
```

**Response**:

```json
{
  "success": true,
  "message": "Notes summary polling started successfully",
  "containerId": "notes-scheduler-abc123",
  "configuration": {
    "cronExpression": "0 */6 * * *",
    "mcpServerUrl": "http://host.docker.internal:8082",
    "schedule": "Every 6 hours"
  }
}
```

**What It Does**:

1. Builds Docker image for notes-scheduler service
2. Stops existing notes-scheduler container (if running)
3. Starts new container with specified cron expression
4. Returns container ID and configuration

### 2. stop_notes_summary_polling

**Purpose**: Stop and remove the notes-scheduler Docker container

**Parameters**: None

**Example**:

```json
{
  "name": "stop_notes_summary_polling",
  "arguments": {}
}
```

**Response**:

```json
{
  "success": true,
  "message": "Notes summary polling stopped successfully"
}
```

**What It Does**:

1. Stops the notes-scheduler container (graceful shutdown)
2. Removes the container
3. Cleans up Docker resources

## Key Components

### 1. Application.kt

**Purpose**: HTTP/HTTPS server setup

**Features**:

- Dual protocol support (HTTP on 8088, HTTPS on 8447)
- Auto-generated self-signed SSL certificates
- CORS enabled (development mode)
- MCP endpoint routing

### 2. McpPollingConfiguration.kt

**Purpose**: MCP server configuration and Docker management

**Endpoints**:

- `GET /` - Health check
- `GET /tools/list` - List available tools
- `POST /tools/call` - Execute a tool

**Docker Integration**:

```kotlin
class DockerManager {
    private val dockerClient = DockerClientBuilder
        .getInstance()
        .build()

    fun buildSchedulerImage() {
        // Build from services/notes-scheduler/Dockerfile
    }

    fun startSchedulerContainer(
        cronExpression: String,
        mcpServerUrl: String
    ): String {
        // docker run -d --name notes-scheduler ...
    }

    fun stopSchedulerContainer() {
        // docker stop notes-scheduler
        // docker rm notes-scheduler
    }
}
```

## Docker Container Management

### Notes Scheduler Container

**Image**: Built from `services/notes-scheduler/Dockerfile`

**Environment Variables**:

- `CRON_EXPRESSION`: Schedule for polling (default: "*/2 * * * *")
- `MCP_SERVER_URL`: MCP Notes server endpoint (default: "http://host.docker.internal:8082")
- `SCHEDULER_ENABLED`: Enable/disable scheduler (default: "true")

**Networking**:

- Uses `host.docker.internal` to access host machine services
- `--add-host host.docker.internal:host-gateway` for Docker on Linux

**Example Docker Run Command**:

```bash
docker run -d \
  --name notes-scheduler \
  --add-host host.docker.internal:host-gateway \
  -e CRON_EXPRESSION="0 */6 * * *" \
  -e MCP_SERVER_URL="http://host.docker.internal:8082" \
  -e SCHEDULER_ENABLED="true" \
  notes-scheduler:latest
```

## Configuration

### application.conf

```hocon
ktor {
  deployment {
    httpPort = 8088
    httpPort = ${?HTTP_PORT}
    httpsPort = 8447
    httpsPort = ${?HTTPS_PORT}
  }
}

ssl {
  keyAlias = "mcppolling"
  keyAlias = ${?SSL_KEY_ALIAS}
  keystorePassword = "changeit"
  keystorePassword = ${?SSL_KEYSTORE_PASSWORD}
  keyPassword = "changeit"
  keyPassword = ${?SSL_KEY_PASSWORD}
}

docker {
  schedulerImageName = "notes-scheduler"
  schedulerImageName = ${?SCHEDULER_IMAGE_NAME}
  schedulerContainerName = "notes-scheduler"
  schedulerContainerName = ${?SCHEDULER_CONTAINER_NAME}
}
```

### Environment Variables

```bash
# Ports
HTTP_PORT=8088
HTTPS_PORT=8447

# SSL Configuration
SSL_KEY_ALIAS="mcppolling"
SSL_KEYSTORE_PASSWORD="changeit"
SSL_KEY_PASSWORD="changeit"

# Docker Configuration
SCHEDULER_IMAGE_NAME="notes-scheduler"
SCHEDULER_CONTAINER_NAME="notes-scheduler"
```

## Running the Server

### Prerequisites

**Docker Installation**:

- Windows: Docker Desktop
- Linux: Docker Engine
- macOS: Docker Desktop

**Verify Docker**:

```bash
docker --version
docker ps
```

### Development

```bash
# Terminal 1: Start MCP Notes Server (polling target)
.\gradlew.bat :mcp:notes:run

# Terminal 2: Start MCP Notes Polling Server
.\gradlew.bat :mcp:notes-polling:run
```

### Production

```bash
# Build distribution
.\gradlew.bat :mcp:notes-polling:installDist

# Run
mcp\notes-polling\build\install\notes-polling\bin\notes-polling.bat
```

## Testing

### Manual Testing

#### Health Check

```bash
curl http://localhost:8088/
```

#### List Tools

```bash
curl http://localhost:8088/tools/list
```

#### Start Polling (Every 5 Minutes)

```bash
curl -X POST http://localhost:8088/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "trigger_notes_summary_polling",
    "arguments": {
      "cron_expression": "*/5 * * * *"
    }
  }'
```

**Expected Response**:

```json
{
  "success": true,
  "message": "Notes summary polling started successfully",
  "containerId": "notes-scheduler-abc123"
}
```

#### Verify Container Running

```bash
docker ps | grep notes-scheduler
```

**Expected Output**:

```
notes-scheduler   notes-scheduler:latest   Up 2 minutes
```

#### View Scheduler Logs

```bash
docker logs notes-scheduler
```

**Expected Output**:

```
Scheduler started with cron expression: */5 * * * *
MCP server URL: http://host.docker.internal:8082
Waiting for next scheduled run...
[2025-01-15 14:05:00] Executing scheduled task...
[2025-01-15 14:05:01] Calling MCP Notes server...
[2025-01-15 14:05:02] Notes summary completed
```

#### Stop Polling

```bash
curl -X POST http://localhost:8088/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "name": "stop_notes_summary_polling",
    "arguments": {}
  }'
```

**Verify Container Stopped**:

```bash
docker ps -a | grep notes-scheduler
# Should show no running containers
```

### Integration Testing (via Main Server)

```bash
curl -X POST http://localhost:8080/api/send-message \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Start notes summary polling every hour",
    "provider": "openrouter",
    "model": "anthropic/claude-3.5-sonnet",
    "enableTools": true
  }'
```

**AI Flow**:

1. Parse "every hour" → cron: "0 * * * *"
2. Call `trigger_notes_summary_polling`
3. Confirm polling started

## Use Cases

### 1. Automated Note Summarization

**User**: "Start summarizing my notes every 6 hours"

**AI Tool Call**:

```json
{
  "name": "trigger_notes_summary_polling",
  "arguments": {
    "cron_expression": "0 */6 * * *"
  }
}
```

**Result**: Scheduler runs every 6 hours, calling MCP Notes server to summarize notes

### 2. Daily Digest

**User**: "Create a daily summary of my notes at 9 AM"

**AI Tool Call**:

```json
{
  "name": "trigger_notes_summary_polling",
  "arguments": {
    "cron_expression": "0 9 * * *"
  }
}
```

### 3. Disable Polling

**User**: "Stop the automated note summaries"

**AI Tool Call**:

```json
{
  "name": "stop_notes_summary_polling"
}
```

## Cron Expression Examples

### Common Schedules

```bash
# Every minute
"* * * * *"

# Every 5 minutes
"*/5 * * * *"

# Every hour at minute 0
"0 * * * *"

# Every 6 hours
"0 */6 * * *"

# Every day at 9:00 AM
"0 9 * * *"

# Every day at 9:00 AM and 6:00 PM
"0 9,18 * * *"

# Every weekday at 9:00 AM
"0 9 * * 1-5"

# Every Monday at 9:00 AM
"0 9 * * 1"

# First day of month at midnight
"0 0 1 * *"
```

### Cron Expression Builder

Use [crontab.guru](https://crontab.guru/) to build and validate expressions.

## Error Handling

### Common Errors

**Docker Not Running**:

```json
{
  "error": "Docker daemon is not running. Please start Docker Desktop."
}
```

**Solution**: Start Docker Desktop

**Image Build Failed**:

```json
{
  "error": "Failed to build scheduler image: Dockerfile not found"
}
```

**Solution**: Verify `services/notes-scheduler/Dockerfile` exists

**Container Already Running**:

```json
{
  "warning": "Stopping existing notes-scheduler container",
  "success": true
}
```

**Note**: This is expected behavior - old container is stopped before starting new one

**Invalid Cron Expression**:

```json
{
  "error": "Invalid cron expression: '60 * * * *' (minute must be 0-59)"
}
```

**Solution**: Use valid cron syntax (validate at crontab.guru)

**MCP Server Unreachable**:

```
Docker container logs show:
[ERROR] Failed to connect to http://host.docker.internal:8082
```

**Solution**:

1. Verify MCP Notes server is running: `curl http://localhost:8082/`
2. Check Docker networking (use `host.docker.internal` on Windows/Mac)
3. On Linux, use `--network host` or proper gateway configuration

## Docker Permissions

### Windows

**Requires**: Docker Desktop installed and running

**Permissions**: Automatically managed by Docker Desktop

### Linux

**Requires**: Docker Engine installed

**User Permissions**:

```bash
# Add user to docker group
sudo usermod -aG docker $USER

# Logout and login to apply
# Or use newgrp
newgrp docker

# Verify
docker ps
```

### macOS

**Requires**: Docker Desktop for Mac

**Permissions**: Automatically managed by Docker Desktop

## Dependencies

```kotlin
dependencies {
    implementation(project(":shared"))

    // Ktor Server
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.network.tls.certificates)

    // Docker SDK (Java Docker Client)
    implementation("com.github.docker-java:docker-java:3.3.4")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.4")

    // MCP SDK
    implementation("io.modelcontextprotocol:sdk:0.1.0")

    // Logging
    implementation(libs.logback.classic)
}
```

## Future Enhancements

1. **Container Status Monitoring**: Check if scheduler is running
2. **Logs Retrieval**: Get scheduler container logs via MCP tool
3. **Multiple Schedulers**: Manage different polling tasks
4. **Configuration Presets**: Pre-defined schedules (daily, weekly, etc.)
5. **Notifications**: Alert when scheduler starts/stops
6. **Health Checks**: Verify scheduler is running correctly
7. **Resource Limits**: Set CPU/memory limits for container
8. **Restart Policy**: Auto-restart on failure
9. **Statistics**: Track polling execution count, success rate
10. **Custom Actions**: Schedule arbitrary tasks, not just note summaries

## Troubleshooting

### Issue: Docker Build Fails

**Symptom**: "ERROR: failed to solve: failed to compute cache key"

**Solutions**:

1. Check Dockerfile syntax
2. Verify all referenced files exist
3. Run `docker system prune` to clean build cache
4. Check Docker Desktop storage space

### Issue: Container Exits Immediately

**Symptom**: Container starts but stops after a few seconds

**Solutions**:

1. Check container logs: `docker logs notes-scheduler`
2. Verify cron expression is valid
3. Check MCP server URL is accessible from container
4. Verify environment variables are set correctly

### Issue: Polling Not Triggering

**Symptom**: Container running but no polling happens

**Solutions**:

1. Verify cron expression syntax
2. Check container logs for errors
3. Verify MCP Notes server is accessible
4. Test MCP Notes server manually: `curl http://localhost:8082/`

### Issue: Permission Denied (Linux)

**Symptom**: "Permission denied while trying to connect to Docker daemon"

**Solution**: Add user to docker group (see Docker Permissions section)

## Related Documentation

- [Notes Scheduler Service](../../services/notes-scheduler.md) - Container workload
- [MCP Notes Server](notes.md) - Polling target
- [Server Module](../server.md) - Main server with tool calling

## References

- [Docker Documentation](https://docs.docker.com/)
- [Docker Java SDK](https://github.com/docker-java/docker-java)
- [Cron Expression Syntax](https://en.wikipedia.org/wiki/Cron)
- [Crontab Guru](https://crontab.guru/) - Cron expression tester
- [Model Context Protocol](https://modelcontextprotocol.io/)
