@echo off
echo ========================================
echo Clean Build - Tool Calling Fix
echo ========================================
echo.

echo Step 1: Deleting Gradle configuration cache...
if exist .gradle\configuration-cache (
    rmdir /s /q .gradle\configuration-cache
    echo   Configuration cache deleted
) else (
    echo   No cache found
)
echo.

echo Step 2: Running clean...
call .\gradlew.bat clean --no-configuration-cache
echo   Clean completed
echo.

echo Step 3: Building server module...
call .\gradlew.bat :server:build --no-configuration-cache --warning-mode all
echo.

if errorlevel 1 (
    echo ========================================
    echo BUILD FAILED
    echo ========================================
    pause
    exit /b 1
) else (
    echo ========================================
    echo BUILD SUCCESSFUL
    echo ========================================
    echo.
    echo You can now run:
    echo   .\gradlew.bat :mcp-server:run    (Terminal 1)
    echo   .\gradlew.bat :server:run         (Terminal 2)
    echo.
)

pause
