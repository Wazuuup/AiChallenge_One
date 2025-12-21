@echo off
setlocal EnableDelayedExpansion

REM Deploy MCP Server to Remote Ubuntu Server
REM Usage: deploy-mcp-server.bat <password>

if "%~1"=="" (
    echo Error: Password parameter is required
    echo Usage: %~nx0 ^<password^>
    exit /b 1
)

set PASSWORD=%~1
set REMOTE_HOST=89.124.67.120
set REMOTE_USER=mcp
set REMOTE_DIR=/home/mcp/mcp-server
set LOCAL_BUILD_DIR=..\mcp-server\build\install\mcp-server

echo ========================================
echo MCP Server Deployment Script
echo ========================================
echo Remote Host: %REMOTE_USER%@%REMOTE_HOST%
echo Remote Directory: %REMOTE_DIR%
echo ========================================
echo.

REM Step 1: Build the mcp-server locally
echo [1/6] Building mcp-server...
call ..\gradlew.bat :mcp-server:clean :mcp-server:installDist --no-daemon
if errorlevel 1 (
    echo Error: Build failed
    exit /b 1
)
echo Build completed successfully
echo.

REM Step 2: Check if build directory exists
if not exist "%LOCAL_BUILD_DIR%" (
    echo Error: Build directory not found: %LOCAL_BUILD_DIR%
    exit /b 1
)

REM Step 3: Create deployment archive
echo [2/6] Creating deployment archive...
set ARCHIVE_NAME=mcp-server-deploy.tar.gz
cd ..\mcp-server\build\install
tar -czf %ARCHIVE_NAME% mcp-server
if errorlevel 1 (
    echo Error: Failed to create archive
    cd ..\..\..\scripts
    exit /b 1
)
cd ..\..\..\scripts
echo Archive created: %ARCHIVE_NAME%
echo.

REM Step 4: Transfer files to remote server
echo [3/6] Transferring files to remote server...
echo Using password authentication...

REM Transfer archive
echo|set /p="Uploading archive... "
sshpass -p "%PASSWORD%" scp -o StrictHostKeyChecking=no ..\mcp-server\build\install\%ARCHIVE_NAME% %REMOTE_USER%@%REMOTE_HOST%:/tmp/%ARCHIVE_NAME%
if errorlevel 1 (
    echo FAILED
    echo.
    echo Note: If sshpass is not available, install it or use key-based authentication
    echo Trying without sshpass...
    scp -o StrictHostKeyChecking=no ..\mcp-server\build\install\%ARCHIVE_NAME% %REMOTE_USER%@%REMOTE_HOST%:/tmp/%ARCHIVE_NAME%
    if errorlevel 1 (
        echo Error: File transfer failed
        exit /b 1
    )
)
echo DONE
echo.

REM Transfer deployment scripts
echo|set /p="Uploading deployment scripts... "
sshpass -p "%PASSWORD%" scp -o StrictHostKeyChecking=no ..\deploy-remote.sh ..\start-remote.sh %REMOTE_USER%@%REMOTE_HOST%:/tmp/
if errorlevel 1 (
    scp -o StrictHostKeyChecking=no ..\deploy-remote.sh ..\start-remote.sh %REMOTE_USER%@%REMOTE_HOST%:/tmp/
    if errorlevel 1 (
        echo FAILED
        exit /b 1
    )
)
echo DONE
echo.

REM Step 5: Execute deployment on remote server
echo [4/6] Preparing environment on remote server...
sshpass -p "%PASSWORD%" ssh -o StrictHostKeyChecking=no %REMOTE_USER%@%REMOTE_HOST% "chmod +x /tmp/deploy-remote.sh && /tmp/deploy-remote.sh"
if errorlevel 1 (
    ssh -o StrictHostKeyChecking=no %REMOTE_USER%@%REMOTE_HOST% "chmod +x /tmp/deploy-remote.sh && /tmp/deploy-remote.sh"
    if errorlevel 1 (
        echo Error: Remote deployment failed
        exit /b 1
    )
)
echo.

REM Step 6: Start the server
echo [5/6] Starting mcp-server on remote host...
sshpass -p "%PASSWORD%" ssh -o StrictHostKeyChecking=no %REMOTE_USER%@%REMOTE_HOST% "chmod +x /tmp/start-remote.sh && /tmp/start-remote.sh"
if errorlevel 1 (
    ssh -o StrictHostKeyChecking=no %REMOTE_USER%@%REMOTE_HOST% "chmod +x /tmp/start-remote.sh && /tmp/start-remote.sh"
    if errorlevel 1 (
        echo Error: Failed to start server
        echo Checking logs...
        ssh -o StrictHostKeyChecking=no %REMOTE_USER%@%REMOTE_HOST% "tail -50 %REMOTE_DIR%/logs/server.log"
        exit /b 1
    )
)
echo.

REM Step 7: Verify deployment
echo [6/6] Verifying deployment...
sshpass -p "%PASSWORD%" ssh -o StrictHostKeyChecking=no %REMOTE_USER%@%REMOTE_HOST% "ps aux | grep mcp-server | grep -v grep"
if errorlevel 1 (
    ssh -o StrictHostKeyChecking=no %REMOTE_USER%@%REMOTE_HOST% "ps aux | grep mcp-server | grep -v grep"
)
echo.

echo ========================================
echo Deployment completed successfully!
echo ========================================
echo Server is running at:
echo   HTTP:  http://%REMOTE_HOST%:8082
echo   HTTPS: https://%REMOTE_HOST%:8443
echo.
echo Useful commands:
echo   View logs:
echo     ssh %REMOTE_USER%@%REMOTE_HOST% "tail -f %REMOTE_DIR%/logs/server.log"
echo.
echo   Stop server:
echo     ssh %REMOTE_USER%@%REMOTE_HOST% "kill \$(cat %REMOTE_DIR%/mcp-server.pid)"
echo.
echo   Check status:
echo     ssh %REMOTE_USER%@%REMOTE_HOST% "ps -p \$(cat %REMOTE_DIR%/mcp-server.pid)"
echo.
echo   Test HTTP endpoint:
echo     curl http://%REMOTE_HOST%:8082
echo ========================================

endlocal
