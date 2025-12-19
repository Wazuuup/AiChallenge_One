@echo off
echo Switching SSL URL to remote server...
echo.
echo This will change:
echo   FROM: https://127.0.0.1:8443
echo   TO:   https://89.124.67.120:8443
echo.
echo Note: This requires that the server certificate includes 89.124.67.120
echo       Run rebuild-with-new-cert.bat first if you haven't already!
echo.
pause

powershell -Command "(Get-Content '..\mcp-client\src\main\kotlin\ru\sber\cb\aichallenge_one\mcp_client\Application.kt') -replace 'https://127.0.0.1:8443', 'https://89.124.67.120:8443' | Set-Content '..\mcp-client\src\main\kotlin\ru\sber\cb\aichallenge_one\mcp_client\Application.kt'"

echo.
echo ✓ URL changed to remote server
echo.
echo Now rebuild and run:
echo   ..\gradlew.bat :mcp-client:build
echo   ..\gradlew.bat :mcp-client:runExchangeRateSSL
