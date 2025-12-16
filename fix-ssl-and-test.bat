@echo off
setlocal

echo ========================================
echo SSL Certificate Fix and Test
echo ========================================
echo.

echo Step 1: Verify old keystore files are deleted...
if exist "mcp-server\src\main\resources\keystore.jks" (
    echo Deleting source keystore...
    del "mcp-server\src\main\resources\keystore.jks"
)
if exist "mcp-server\build\resources\main\keystore.jks" (
    echo Deleting build keystore...
    del "mcp-server\build\resources\main\keystore.jks"
)
echo Done
echo.

echo Step 2: Clean and build mcp-server...
call .\gradlew.bat :mcp-server:clean :mcp-server:build --no-daemon
if errorlevel 1 (
    echo Build failed!
    pause
    exit /b 1
)
echo.

echo Step 3: Start server to generate new keystore...
echo The server will start and generate a new SSL certificate with domains:
echo   - 89.124.67.120
echo   - 127.0.0.1
echo   - localhost
echo   - 0.0.0.0
echo   - v573465.hosted-by-vdsina.com
echo.
echo Press Ctrl+C to stop the server after you see "SSL keystore generated"
echo.
pause
start "MCP Server" cmd /c ".\gradlew.bat :mcp-server:run --no-daemon"
echo.

echo Waiting 15 seconds for server to start and generate keystore...
timeout /t 15 /nobreak
echo.

echo Step 4: Verify keystore was created...
if exist "mcp-server\src\main\resources\keystore.jks" (
    echo ✓ Keystore generated successfully!
    echo.
    echo Step 5: Verify certificate domains...
    keytool -list -v -keystore mcp-server\src\main\resources\keystore.jks -storepass changeit | findstr /C:"SubjectAlternativeName" /C:"DNSName" /C:"IPAddress"
    echo.
    echo ========================================
    echo.
    echo The new keystore is ready!
    echo.
    echo You can now:
    echo 1. Stop the MCP server (Ctrl+C in the server window)
    echo 2. Test the SSL client:
    echo    .\gradlew.bat :mcp-client:runExchangeRateSSL
    echo.
    echo 3. Deploy to remote server:
    echo    deploy-mcp-server-simple.bat
    echo.
    echo ========================================
) else (
    echo ✗ Keystore was not generated!
    echo.
    echo Please check if the server started successfully.
    echo Look for this message in the server window:
    echo   "SSL keystore generated at: ..."
    echo.
)

endlocal
