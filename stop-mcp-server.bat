@echo off
setlocal

REM Stop MCP Server on Remote Ubuntu Server
REM Usage: stop-mcp-server.bat

set REMOTE_HOST=89.124.67.120
set REMOTE_USER=mcp
set REMOTE_DIR=/home/mcp/mcp-server

echo ========================================
echo Stopping MCP Server
echo ========================================
echo Remote Host: %REMOTE_USER%@%REMOTE_HOST%
echo ========================================
echo.

echo Checking if server is running...
ssh -o StrictHostKeyChecking=no %REMOTE_USER%@%REMOTE_HOST% "if [ -f %REMOTE_DIR%/mcp-server.pid ]; then PID=\$(cat %REMOTE_DIR%/mcp-server.pid); if ps -p \$PID > /dev/null 2>&1; then echo \"Server is running with PID: \$PID\"; kill \$PID && echo \"Server stopped successfully\" && rm -f %REMOTE_DIR%/mcp-server.pid || (kill -9 \$PID && echo \"Server force-stopped\" && rm -f %REMOTE_DIR%/mcp-server.pid); else echo \"PID file exists but process is not running\"; rm -f %REMOTE_DIR%/mcp-server.pid; fi; else echo \"Server is not running (no PID file found)\"; fi"

echo.
echo ========================================
echo Done
echo ========================================

endlocal
