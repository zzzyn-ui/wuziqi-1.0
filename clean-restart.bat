@echo off
echo ========================================
echo STEP 1: Kill ALL Java processes
echo ========================================
taskkill /F /IM java.exe 2>nul
timeout /t 3 /nobreak >nul

echo ========================================
echo STEP 2: Verify no process on port 9090
echo ========================================
netstat -ano | findstr ":9090.*LISTEN"
if errorlevel 1 (
    echo ✓ Port 9090 is free
) else (
    echo ✗ Port 9090 still in use, killing...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":9090" ^| findstr "LISTENING"') do (
        taskkill /F /PID %%a 2>nul
    )
    timeout /t 2 /nobreak >nul
)

echo ========================================
echo STEP 3: Clear old logs
echo ========================================
del /q logs\gobang-server.log 2>nul
del /q logs\console-output.log 2>nul

echo ========================================
echo STEP 4: Start server
echo ========================================
cd /d D:\wuziqi
start "Gobang Server" java -cp "target/classes;target/dependency/*" com.gobang.GobangServer

echo Waiting for server to start...
timeout /t 8 /nobreak >nul

echo ========================================
echo STEP 5: Check server status
echo ========================================
netstat -an | findstr ":9090.*LISTEN"

echo.
echo ========================================
echo Testing login...
echo ========================================
timeout /t 2 /nobreak >nul
python test_login_now.py

echo.
echo ========================================
echo Monitoring logs (Ctrl+C to stop)...
echo ========================================
powershell -Command "Get-Content logs/gobang-server.log -Wait -Tail 20"
