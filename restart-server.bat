@echo off
echo ========================================
echo Stopping existing server...
echo ========================================

REM Kill existing Java process on port 9090
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :9090 ^| findstr LISTENING') do (
    echo Killing process %%a
    taskkill /F /PID %%a 2>nul
)

timeout /t 2 /nobreak >nul

echo ========================================
echo Building project...
echo ========================================
cd /d D:\wuziqi
call mvn clean compile -DskipTests -q

if errorlevel 1 (
    echo Build FAILED!
    pause
    exit /b 1
)

echo ========================================
echo Starting server with debug logs...
echo ========================================
echo Logs will be written to logs/gobang-server.log
echo Press Ctrl+C to stop the server
echo ========================================

REM Start server with classpath
start /B java -cp "target/classes;target/dependency/*" com.gobang.GobangServer > logs/console-output.log 2>&1

echo Server starting...
timeout /t 5 /nobreak >nul

echo.
echo Check if server is running:
netstat -ano | findstr :9090

echo.
echo ========================================
echo Tailing log file (Ctrl+C to stop)...
echo ========================================

REM Tail the log file
powershell -Command "Get-Content logs/gobang-server.log -Wait -Tail 50"
