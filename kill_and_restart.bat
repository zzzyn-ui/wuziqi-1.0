@echo off
echo Killing all Java processes...
taskkill /F /IM java.exe /T 2>nul
taskkill /F /IM javaw.exe /T 2>nul
echo Waiting...
timeout /t 5 /nobreak
echo Checking port...
netstat -an | findstr ":9090"
echo.
echo Done.
pause
