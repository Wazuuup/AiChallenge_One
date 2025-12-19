# MCP Notes Polling Server

MCP (Model Context Protocol) server for controlling the notes-scheduler service via Docker.

## Overview

This MCP server provides tools to start and stop the notes-scheduler service as a Docker container. The scheduler
periodically triggers the notes summary endpoint on the mcp:notes server.

## Ports

- **HTTP**: 8088
- **HTTPS**: 8447

## MCP Tools

### 1. trigger_notes_summary_polling

Builds and starts the notes-scheduler service as a Docker container.

**Parameters**:

- `cron_expression` (optional): Cron expression for scheduling (default: "*/2 * * * *" - every 2 minutes)
- `mcp_server_url` (optional): MCP server URL (default: "http://host.docker.internal:8082")

**Example**:

```json
{
  "cron_expression": "*/5 * * * *",
  "mcp_server_url": "http://host.docker.internal:8082"
}
```

### 2. stop_notes_summary_polling

Stops and removes the notes-scheduler Docker container.

**Parameters**: None

## Usage

### Start the MCP server:

```bash
.\gradlew.bat :mcp:notes-polling:run
```

### Use with MCP client:

The server exposes MCP tools that can be called by any MCP client to control the scheduler.

## Prerequisites

- Docker must be installed and running
- mcp:notes server should be running on port 8082
- services:notes should be running on port 8084

## How it works

1. **trigger_notes_summary_polling** tool:
    - Builds the Docker image for notes-scheduler
    - Starts the container with specified cron expression
    - Scheduler makes HTTP requests to `http://host.docker.internal:8082/trigger-summary`

2. **stop_notes_summary_polling** tool:
    - Stops the running notes-scheduler container
    - Removes the container

## Configuration

Configuration is handled via environment variables:

- `SSL_KEY_ALIAS`: SSL key alias (default: "mcppolling")
- `SSL_KEYSTORE_PASSWORD`: Keystore password (default: "changeit")
- `SSL_KEY_PASSWORD`: Private key password (default: "changeit")
