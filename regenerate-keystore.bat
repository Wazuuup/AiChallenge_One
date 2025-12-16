@echo off
setlocal

REM Regenerate SSL Keystore for MCP Server
REM This script deletes the old keystore so a new one will be generated with updated domains

set KEYSTORE_PATH=mcp-server\src\main\resources\keystore.jks

echo ========================================
echo Regenerate SSL Keystore
echo ========================================
echo.

if exist "%KEYSTORE_PATH%" (
    echo Found existing keystore: %KEYSTORE_PATH%
    echo Deleting...
    del "%KEYSTORE_PATH%"
    if errorlevel 1 (
        echo Error: Failed to delete keystore
        exit /b 1
    )
    echo ✓ Keystore deleted
) else (
    echo No existing keystore found
)

echo.
echo A new keystore will be automatically generated when you start the server.
echo.
echo The new certificate will include these domains:
echo   - 127.0.0.1
echo   - localhost
echo   - 0.0.0.0
echo   - 89.124.67.120
echo   - v573465.hosted-by-vdsina.com
echo.
echo To generate the new keystore, run:
echo   .\gradlew.bat :mcp-server:run
echo.
echo ========================================

endlocal
