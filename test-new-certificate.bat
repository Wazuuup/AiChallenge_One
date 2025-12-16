@echo off
setlocal

echo ========================================
echo Test New SSL Certificate Locally
echo ========================================
echo.

REM Check if keystore exists
if not exist "mcp-server\src\main\resources\keystore.jks" (
    echo ERROR: Keystore not found!
    echo.
    echo Run this first to generate the keystore:
    echo   regenerate-and-deploy.bat
    echo.
    pause
    exit /b 1
)

echo Keystore found. Checking certificate domains...
echo.
keytool -list -v -keystore mcp-server\src\main\resources\keystore.jks -storepass changeit 2>nul | findstr /C:"DNSName" /C:"IPAddress"
echo.

echo ========================================
echo Starting MCP Server...
echo ========================================
echo.
echo Server will run on:
echo   HTTP:  http://localhost:8082
echo   HTTPS: https://localhost:8443
echo.
echo After server starts, open another terminal and run:
echo   .\gradlew.bat :mcp-client:runExchangeRateSSL
echo.
echo Press Ctrl+C to stop the server when done testing
echo.
pause

.\gradlew.bat :mcp-server:run --no-daemon

endlocal
