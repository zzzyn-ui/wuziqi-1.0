@echo off
chcp 65001 >nul
cls
echo ========================================
echo 强制重启服务器 - 最新代码
echo ========================================
echo.

cd /d D:\wuziqi

echo [步骤 1/7] 强制停止所有 Java 进程...
taskkill /F /IM java.exe >nul 2>&1
taskkill /F /IM javaw.exe >nul 2>&1
wmic process where "name like '%%java%%'" call terminate >nul 2>&1
timeout /t 3 /nobreak >nul
echo     完成
echo.

echo [步骤 2/7] 等待端口释放...
:wait_loop
timeout /t 2 /nobreak >nul
netstat -an | findstr ":9090.*LISTEN" >nul
if errorlevel 1 (
    echo     端口已释放
) else (
    echo     端口仍被占用，再次清理...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":9090.*LISTEN"') do (
        taskkill /F /PID %%a >nul 2>&1
    )
    goto wait_loop
)
echo.

echo [步骤 3/7] 清理旧日志...
del /q logs\gobang-server.log 2>nul
del /q logs\console-output.log 2>nul
del /q logs\server.log 2>nul
echo     完成
echo.

echo [步骤 4/7] 重新编译最新代码...
echo     (这可能需要 30-60 秒)
call mvn compile -DskipTests -q
if errorlevel 1 (
    echo.
    echo     ✗ 编译失败！
    echo     请检查错误信息
    pause
    exit /b 1
)
echo     ✓ 编译成功
echo.

echo [步骤 5/7] 验证修复...
findstr /C:"port = 8083" target\classes\com\gobang\core\netty\NettyServer.class >nul 2>&1
if errorlevel 1 (
    echo     ✓ 端口配置已修复
) else (
    echo     ! 警告: 可能使用了缓存的类文件
)
echo.

echo [步骤 6/7] 启动服务器...
echo     日志: logs\gobang-server.log
echo     使用 Ctrl+C 可以停止服务器
echo.
start /B java -cp "target/classes;target/dependency/*" com.gobang.GobangServer > logs\gobang-server.log 2>&1
timeout /t 12 /nobreak >nul
echo.

echo [步骤 7/7] 验证服务器启动...
netstat -an | findstr ":9090.*LISTEN" >nul
if errorlevel 1 (
    echo     ✗ 服务器启动失败！
    echo.
    echo     查看错误日志:
    type logs\gobang-server.log | findstr /C:"ERROR" /C:"Failed" /C:"Exception"
    pause
    exit /b 1
) else (
    echo     ✓ 服务器正在运行
)
echo.

echo ========================================
echo 启动成功！现在测试登录功能
echo ========================================
echo.

python test_login_now.py

echo.
echo ========================================
echo 服务器日志（最后 30 行）
echo ========================================
powershell -Command "Get-Content logs\gobang-server.log -Tail 30 -ErrorAction SilentlyContinue"

echo.
echo ========================================
echo 服务器运行中...
echo ========================================
echo.
echo 监控日志:
echo   powershell -Command "Get-Content logs\gobang-server.log -Wait"
echo.
echo 测试登录:
echo   python test_login_now.py
echo.
echo 按 Ctrl+C 停止服务器
echo.

pause
