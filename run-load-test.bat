@echo off
echo ========================================
echo   五子棋压力测试启动脚本
echo ========================================
echo.

cd /d D:\wuziqi

echo 正在编译压力测试客户端...
call mvn test-compile

if %ERRORLEVEL% NEQ 0 (
    echo 编译失败！
    pause
    exit /b 1
)

echo.
echo 启动压力测试...
echo 配置: 100个用户，5分钟测试时间
echo.

call mvn exec:java -Dexec.mainClass="com.gobang.stress.LoadTestClient" -Dexec.classpathScope=test

pause
