#!/bin/bash
set -e

REMOTE_DIR=/home/mcp/mcp-server

cd $REMOTE_DIR

# Set up environment variables
export SSL_KEY_ALIAS=mcpserver
export SSL_KEYSTORE_PASSWORD=changeit
export SSL_KEY_PASSWORD=changeit
export PORT=8082

# Start server in background
echo "Starting mcp-server..."
nohup bin/mcp-server > logs/server.log 2>&1 &
echo $! > mcp-server.pid

sleep 3

# Check if server is running
if ps -p $(cat mcp-server.pid) > /dev/null 2>&1; then
    echo "✓ MCP Server started successfully"
    echo "  PID: $(cat mcp-server.pid)"
    echo "  HTTP:  http://0.0.0.0:8082"
    echo "  HTTPS: https://0.0.0.0:8443"
    echo "  Logs: $REMOTE_DIR/logs/server.log"
    exit 0
else
    echo "✗ Failed to start server. Check logs at $REMOTE_DIR/logs/server.log"
    tail -20 logs/server.log
    exit 1
fi
