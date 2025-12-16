@echo off
setlocal

REM View MCP Server Logs from Remote Ubuntu Server
REM Usage: view-logs.bat [lines]
REM   lines: number of lines to show (default: 50, use "live" for tail -f)

set REMOTE_HOST=89.124.67.120
set REMOTE_USER=mcp
set REMOTE_DIR=/home/mcp/mcp-server
set LINES=%~1

if "%LINES%"=="" set LINES=50

echo ========================================
echo MCP Server Logs
echo ========================================
echo Remote Host: %REMOTE_USER%@%REMOTE_HOST%
echo ========================================
echo.

if /I "%LINES%"=="live" (
    echo Showing live logs (Ctrl+C to exit)...
    echo.
    ssh -o StrictHostKeyChecking=no %REMOTE_USER%@%REMOTE_HOST% "tail -f %REMOTE_DIR%/logs/server.log 2>/dev/null || echo 'Log file not found'"
) else (
    echo Showing last %LINES% lines...
    echo.
    ssh -o StrictHostKeyChecking=no %REMOTE_USER%@%REMOTE_HOST% "tail -%LINES% %REMOTE_DIR%/logs/server.log 2>/dev/null || echo 'Log file not found'"
)

endlocal
