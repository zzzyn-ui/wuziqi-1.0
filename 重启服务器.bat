@echo off
chcp 65001 >nul
echo ========================================
echo 五子棋服务器 - 完整重启脚本
echo ========================================
echo.

cd /d D:\wuziqi

echo [1/6] 停止所有 Java 进程...
taskkill /F /IM java.exe >nul 2>&1
taskkill /F /IM javaw.exe >nul 2>&1
echo     ✓ Java 进程已停止
echo.

echo [2/6] 等待端口释放...
timeout /t 3 /nobreak >nul
echo     ✓ 等待完成
echo.

echo [3/6] 检查端口状态...
netstat -an | findstr ":9090.*LISTEN" >nul
if errorlevel 1 (
    echo     ✓ 端口 9090 已释放
) else (
    echo     ! 端口 9090 仍被占用，强制清理...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":9090.*LISTEN"') do (
        taskkill /F /PID %%a >nul 2>&1
    )
    timeout /t 2 /nobreak >nul
)
echo.

echo [4/6] 清理旧日志...
del /q logs\gobang-server.log 2>nul
del /q logs\console-output.log 2>nul
echo     ✓ 日志已清理
echo.

echo [5/6] 启动服务器...
echo     服务器将在后台启动
echo     日志将写入: logs\gobang-server.log
echo.

start /B java -cp "target/classes;target/dependency/*" com.gobang.GobangServer > logs\gobang-server.log 2>&1

echo [6/6] 等待服务器启动...
timeout /t 10 /nobreak >nul
echo.

echo ========================================
echo 检查服务器状态
echo ========================================
netstat -an | findstr ":9090.*LISTEN" >nul
if errorlevel 1 (
    echo     ✗ 服务器启动失败
    echo     请查看日志: logs\gobang-server.log
) else (
    echo     ✓ 服务器正在运行
    echo     ✓ WebSocket 端口: 9090
    echo     ✓ HTTP API 端口: 8080
)
echo.

echo ========================================
echo 最新日志 (最后 20 行)
echo ========================================
powershell -Command "Get-Content logs\gobang-server.log -Tail 20 -ErrorAction SilentlyContinue"
echo.

echo ========================================
echo 测试登录功能
echo ========================================
python test_login_now.py
echo.

echo ========================================
echo 服务器重启完成！
echo ========================================
echo.
echo 监控日志命令:
echo   tail -f logs\gobang-server.log
echo.
echo 或者在新的命令提示符中运行:
echo   powershell -Command "Get-Content logs\gobang-server.log -Wait"
echo.

pause
