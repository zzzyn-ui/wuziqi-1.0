@echo off
cd /d D:\wuziqi

echo Killing old processes...
taskkill /F /IM java.exe 2>nul
timeout /t 2 /nobreak >nul

echo ========================================
echo Compiling with fixes...
echo ========================================
call mvn compile -DskipTests -q

if errorlevel 1 (
    echo Compilation FAILED!
    pause
    exit /b 1
)

echo ========================================
echo Starting server on port 9090...
echo ========================================
echo Logs will be written to logs/gobang-server.log
echo ========================================

REM Start server in background
start /B java -cp "target/classes;target/dependency/*" com.gobang.GobangServer

echo Waiting for server to start...
timeout /t 10 /nobreak >nul

echo.
echo Checking server status:
netstat -an | findstr ":9090.*LISTEN"
echo.

echo ========================================
echo Server started!
echo Monitor logs with: tail -f logs/gobang-server.log
echo ========================================
