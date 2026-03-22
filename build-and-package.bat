@echo off
REM 五子棋项目打包脚本 - Windows版本
REM 用于在打包前清理、编译并打包项目

echo ========================================
echo   五子棋项目 - 打包脚本
echo ========================================
echo.

REM 检查Maven是否安装
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 未找到 Maven，请先安装 Maven
    echo 下载地址: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

echo [1/4] 清理旧的编译文件...
call mvn clean
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 清理失败
    pause
    exit /b 1
)

echo.
echo [2/4] 编译项目...
call mvn compile
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 编译失败
    pause
    exit /b 1
)

echo.
echo [3/4] 打包项目（跳过测试）...
call mvn package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 打包失败
    pause
    exit /b 1
)

echo.
echo [4/4] 检查打包结果...
if exist "target\gobang-server-1.0.0.jar" (
    echo [成功] 打包完成！
    echo.
    echo ========================================
    echo   JAR 文件位置
    echo ========================================
    echo.
    echo   target\gobang-server-1.0.0.jar
    echo.
    echo ========================================
    echo   文件大小
    echo ========================================
    for %%A in ("target\gobang-server-1.0.0.jar") do echo   %%~zA 字节
    echo.
    echo ========================================
    echo   下一步：上传到ECS
    echo ========================================
    echo.
    echo   使用SCP命令上传：
    echo   scp target\gobang-server-1.0.0.jar root@your_ecs_ip:/opt/gobang/
    echo.
    echo   或使用图形化工具（WinSCP、FileZilla）
    echo.
) else (
    echo [错误] 打包文件不存在
    pause
    exit /b 1
)

pause
