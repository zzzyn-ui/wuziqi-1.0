@echo off
chcp 65001 >nul
cls
echo ========================================
echo 实时监控服务器日志并测试
echo ========================================
echo.
echo 这个脚本会:
echo 1. 清空旧日志
echo 2. 启动服务器
echo 3. 实时显示日志
echo 4. 测试登录功能
echo.
echo 请确保:
echo - 没有其他 Java 进程在运行
echo - 端口 9090 没有被占用
echo.
pause

cd /d D:\wuziqi

echo.
echo [1] 清空旧日志...
del /q logs\gobang-server.log 2>nul
del /q logs\console-output.log 2>nul
echo     完成

echo.
echo [2] 启动服务器 (日志将同时显示在下方)...
echo.

REM 启动服务器并重定向输出
start /B java -cp "target/classes;target/dependency/*" com.gobang.GobangServer > logs\gobang-server.log 2>&1

echo 等待服务器启动...
timeout /t 10 /nobreak >nul

echo.
echo [3] 检查服务器状态...
netstat -an | findstr ":9090.*LISTEN" >nul
if errorlevel 1 (
    echo     ✗ 服务器启动失败！
    echo     查看错误日志:
    type logs\gobang-server.log
    pause
    exit /b 1
) else (
    echo     ✓ 服务器正在运行
)

echo.
echo [4] 显示服务器日志 (最后 30 行):
echo ========================================
powershell -Command "Get-Content logs\gobang-server.log -Tail 30 -ErrorAction SilentlyContinue"
echo ========================================

echo.
echo [5] 现在测试登录功能...
python deep_diagnose.py

echo.
echo ========================================
echo 如果登录测试仍然失败，请查看上方的日志
echo 查找 "Received JSON message" 或 "AUTH_LOGIN" 关键词
echo 如果没有这些日志，说明消息没有被接收
echo ========================================
echo.

echo 监控实时日志 (Ctrl+C 退出监控，服务器继续运行):
echo.
powershell -Command "Get-Content logs\gobang-server.log -Wait -ErrorAction SilentlyContinue"

