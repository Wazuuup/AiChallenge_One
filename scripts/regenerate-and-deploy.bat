@echo off
setlocal

echo ========================================
echo Regenerate SSL Certificate and Deploy
echo ========================================
echo.

REM Step 1: Delete all keystore files
echo [1/5] Deleting old keystore files...
if exist "..\mcp-server\src\main\resources\keystore.jks" (
    del "..\mcp-server\src\main\resources\keystore.jks"
    echo   - Deleted source keystore
)
if exist "..\mcp-server\build\resources\main\keystore.jks" (
    del "..\mcp-server\build\resources\main\keystore.jks"
    echo   - Deleted build keystore
)
echo   Done
echo.

REM Step 2: Build mcp-server
echo [2/5] Building mcp-server...
call ..\gradlew.bat :mcp-server:clean :mcp-server:build --no-daemon --console=plain
if errorlevel 1 (
    echo   ERROR: Build failed!
    pause
    exit /b 1
)
echo   Build completed
echo.

REM Step 3: Run server briefly to generate keystore
echo [3/5] Generating new SSL keystore...
echo   Starting server to auto-generate keystore with domains:
echo     - 89.124.67.120 (remote IP)
echo     - 127.0.0.1 (localhost)
echo     - v573465.hosted-by-vdsina.com (hostname)
echo.
start /B "" cmd /c "..\gradlew.bat :mcp-server:run --no-daemon >nul 2>&1"
echo   Waiting 25 seconds for keystore generation...
timeout /t 25 /nobreak >nul

REM Kill gradle processes
taskkill /F /IM java.exe /FI "WINDOWTITLE eq gradle*" >nul 2>&1
echo   Server stopped
echo.

REM Step 4: Verify keystore was created
echo [4/5] Verifying keystore...
if exist "..\mcp-server\src\main\resources\keystore.jks" (
    echo   ✓ Keystore created successfully
    echo.
    echo   Certificate domains:
    keytool -list -v -keystore ..\mcp-server\src\main\resources\keystore.jks -storepass changeit 2>nul | findstr /C:"DNSName" /C:"IPAddress"
    echo.
) else (
    echo   ✗ ERROR: Keystore was not generated!
    echo.
    echo   Try running manually:
    echo     ..\gradlew.bat :mcp-server:run
    echo   Look for message: "SSL keystore generated at: ..."
    pause
    exit /b 1
)

REM Step 5: Ask user if they want to deploy
echo [5/5] Deployment
echo.
choice /C YN /M "Deploy to remote server (89.124.67.120)"
if errorlevel 2 (
    echo.
    echo Deployment skipped.
    echo.
    echo To deploy later, run:
    echo   deploy-mcp-server-simple.bat
    goto :end
)

echo.
echo Starting deployment to remote server...
call deploy-mcp-server-simple.bat
if errorlevel 1 (
    echo.
    echo Deployment failed!
    pause
    exit /b 1
)

:end
echo.
echo ========================================
echo Process Complete!
echo ========================================
echo.
echo SSL certificate now includes:
echo   - 89.124.67.120 (remote IP)
echo   - 127.0.0.1 (localhost)
echo   - v573465.hosted-by-vdsina.com (hostname)
echo.
echo You can now connect from external MCP clients to:
echo   https://89.124.67.120:8443
echo.
echo To test locally:
echo   ..\gradlew.bat :mcp-server:run
echo   ..\gradlew.bat :mcp-client:runExchangeRateSSL
echo.

endlocal
