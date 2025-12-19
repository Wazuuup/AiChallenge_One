@echo off
setlocal

REM Setup SSH Key Authentication for Remote Server
REM This script copies your SSH public key to the remote server

set REMOTE_HOST=89.124.67.120
set REMOTE_USER=mcp
set SSH_KEY_PATH=%USERPROFILE%\.ssh\id_ed25519.pub

echo ========================================
echo SSH Key Setup for Remote Server
echo ========================================
echo Remote Host: %REMOTE_USER%@%REMOTE_HOST%
echo ========================================
echo.

REM Check if SSH key exists
if not exist "%SSH_KEY_PATH%" (
    echo SSH key not found at: %SSH_KEY_PATH%
    echo.
    echo Generating new SSH key...
    ssh-keygen -t ed25519 -C "%USERNAME%@%COMPUTERNAME%" -f "%USERPROFILE%\.ssh\id_ed25519"
    if errorlevel 1 (
        echo Error: Failed to generate SSH key
        exit /b 1
    )
    echo.
    echo SSH key generated successfully
    echo.
)

echo Found SSH public key: %SSH_KEY_PATH%
echo.
echo Copying public key to remote server...
echo You will be prompted for your SSH password.
echo.

REM Read the public key and send it to the server
type "%SSH_KEY_PATH%" | ssh %REMOTE_USER%@%REMOTE_HOST% "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && echo 'SSH key installed successfully'"

if errorlevel 1 (
    echo.
    echo Error: Failed to copy SSH key
    exit /b 1
)

echo.
echo ========================================
echo SSH Key Setup Complete!
echo ========================================
echo.
echo Testing connection...
echo.

ssh -o BatchMode=yes -o ConnectTimeout=5 %REMOTE_USER%@%REMOTE_HOST% "echo 'Success! You can now connect without password'" 2>nul

if errorlevel 1 (
    echo.
    echo Note: Key is installed, but you may need to restart your terminal
    echo or SSH agent for passwordless authentication to work.
) else (
    echo.
    echo ✓ Passwordless SSH authentication is working!
)

echo.
echo You can now use deployment scripts without entering password multiple times.
echo.

endlocal
