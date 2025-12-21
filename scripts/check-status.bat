@echo off
setlocal

REM Check MCP Server Status on Remote Ubuntu Server
REM Usage: check-status.bat

set REMOTE_HOST=89.124.67.120
set REMOTE_USER=mcp
set REMOTE_DIR=/home/mcp/mcp-server

echo ========================================
echo MCP Server Status Check
echo ========================================
echo Remote Host: %REMOTE_USER%@%REMOTE_HOST%
echo ========================================
echo.

echo Checking server status...
echo.
ssh -o StrictHostKeyChecking=no %REMOTE_USER%@%REMOTE_HOST% "bash -s" << 'EOF'
REMOTE_DIR=/home/mcp/mcp-server

echo "=== Process Status ==="
if [ -f $REMOTE_DIR/mcp-server.pid ]; then
    PID=$(cat $REMOTE_DIR/mcp-server.pid)
    if ps -p $PID > /dev/null 2>&1; then
        echo "✓ Server is RUNNING"
        echo "  PID: $PID"
        echo "  Uptime: $(ps -p $PID -o etime= | tr -d ' ')"
        echo "  Memory: $(ps -p $PID -o rss= | awk '{printf "%.2f MB", $1/1024}')"
    else
        echo "✗ Server is NOT running (stale PID file)"
        echo "  PID file contains: $PID"
    fi
else
    echo "✗ Server is NOT running (no PID file)"
fi

echo ""
echo "=== Port Status ==="
if command -v netstat &> /dev/null; then
    if netstat -tuln 2>/dev/null | grep -q ':8082 '; then
        echo "✓ Port 8082 (HTTP) is in use"
    else
        echo "✗ Port 8082 (HTTP) is not in use"
    fi
    if netstat -tuln 2>/dev/null | grep -q ':8443 '; then
        echo "✓ Port 8443 (HTTPS) is in use"
    else
        echo "✗ Port 8443 (HTTPS) is not in use"
    fi
elif command -v ss &> /dev/null; then
    if ss -tuln 2>/dev/null | grep -q ':8082 '; then
        echo "✓ Port 8082 (HTTP) is in use"
    else
        echo "✗ Port 8082 (HTTP) is not in use"
    fi
    if ss -tuln 2>/dev/null | grep -q ':8443 '; then
        echo "✓ Port 8443 (HTTPS) is in use"
    else
        echo "✗ Port 8443 (HTTPS) is not in use"
    fi
fi

echo ""
echo "=== Disk Usage ==="
if [ -d $REMOTE_DIR ]; then
    du -sh $REMOTE_DIR 2>/dev/null || echo "Cannot calculate"
fi

echo ""
echo "=== Recent Log Entries ==="
if [ -f $REMOTE_DIR/logs/server.log ]; then
    echo "Last 5 lines:"
    tail -5 $REMOTE_DIR/logs/server.log
else
    echo "No log file found"
fi

echo ""
echo "=== Server URLs ==="
echo "  HTTP:  http://89.124.67.120:8082"
echo "  HTTPS: https://89.124.67.120:8443"
EOF

echo.
echo ========================================
echo Status check complete
echo ========================================
echo.
echo Quick test:
echo   curl http://%REMOTE_HOST%:8082
echo.

endlocal
