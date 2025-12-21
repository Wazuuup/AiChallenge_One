@echo off
setlocal

echo ========================================
echo Rebuild MCP Server with New SSL Cert
echo ========================================
echo.

REM Step 1: Delete old keystores
echo [1/4] Removing old keystore files...
powershell -Command "if (Test-Path '..\mcp-server\src\main\resources\keystore.jks') { Remove-Item '..\mcp-server\src\main\resources\keystore.jks' -Force }"
powershell -Command "if (Test-Path '..\mcp-server\build\resources\main\keystore.jks') { Remove-Item '..\mcp-server\build\resources\main\keystore.jks' -Force }"
echo Done
echo.

REM Step 2: Clean build
echo [2/4] Cleaning build directory...
call ..\gradlew.bat :mcp-server:clean --no-daemon --quiet
echo Done
echo.

REM Step 3: Build
echo [3/4] Building mcp-server...
call ..\gradlew.bat :mcp-server:build --no-daemon
if errorlevel 1 (
    echo Build failed!
    exit /b 1
)
echo Done
echo.

REM Step 4: Generate keystore by running server briefly
echo [4/4] Generating SSL keystore...
echo.
echo Starting server to generate keystore (will run for 20 seconds)...
start /B "" cmd /c "..\gradlew.bat :mcp-server:run --no-daemon >nul 2>&1"

REM Wait for server to start and generate keystore
timeout /t 20 /nobreak >nul

REM Kill all gradle processes
taskkill /F /IM java.exe /FI "WINDOWTITLE eq gradle*" >nul 2>&1

echo.
echo Verifying keystore...
if exist "..\mcp-server\src\main\resources\keystore.jks" (
    echo.
    echo ========================================
    echo ✓ SUCCESS! New SSL certificate created
    echo ========================================
    echo.
    echo Certificate includes domains:
    keytool -list -v -keystore ..\mcp-server\src\main\resources\keystore.jks -storepass changeit 2>nul | findstr /C:"DNSName" /C:"IPAddress"
    echo.
    echo ========================================
    echo.
    echo Next steps:
    echo   1. Test locally:
    echo      ..\gradlew.bat :mcp-server:run
    echo      ..\gradlew.bat :mcp-client:runExchangeRateSSL
    echo.
    echo   2. Deploy to remote server:
    echo      deploy-mcp-server-simple.bat
    echo.
) else (
    echo ✗ Keystore generation failed
    echo.
    echo Try running the server manually:
    echo   ..\gradlew.bat :mcp-server:run
    echo.
    echo Look for: "SSL keystore generated at: ..."
)

endlocal
