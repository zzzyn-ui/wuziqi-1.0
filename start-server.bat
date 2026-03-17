@echo off
echo ========================================
echo   五子棋服务器启动脚本
echo ========================================
echo.

cd /d D:\wuziqi

echo 正在启动服务器...
echo.

java -jar target\gobang-server-1.0.0.jar

pause
