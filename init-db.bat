@echo off
REM 五子棋游戏数据库快速初始化脚本 (Windows)
REM 使用方法: init-db.bat [mysql_user] [mysql_password]

setlocal enabledelayedexpansion

echo ==================================
echo 五子棋游戏数据库初始化脚本
echo ==================================
echo.

set DB_USER=%1
set DB_PASSWORD=%2

if "%DB_USER%"=="" set DB_USER=root
if "%DB_PASSWORD%"=="" (
    set /p DB_PASSWORD="请输入MySQL密码 (用户: %DB_USER%): "
)

echo 检查MySQL服务...
where mysql >nul 2>nul
if errorlevel 1 (
    echo 错误: 未找到MySQL命令
    echo 请先安装MySQL: https://dev.mysql.com/downloads/mysql/
    pause
    exit /b 1
)

echo √ MySQL找到
echo.

echo 测试MySQL连接...
mysql -u%DB_USER% -p%DB_PASSWORD% -e "SELECT 1" >nul 2>nul
if errorlevel 1 (
    echo 错误: MySQL连接失败，请检查用户名和密码
    pause
    exit /b 1
)

echo √ MySQL连接成功
echo.

echo 执行数据库初始化...
if exist "init-database.sql" (
    mysql -u%DB_USER% -p%DB_PASSWORD% < init-database.sql
    echo √ 数据库初始化完成
) else if exist "src\main\resources\schema.sql" (
    mysql -u%DB_USER% -p%DB_PASSWORD% < src\main\resources\schema.sql
    echo √ 数据库初始化完成
) else (
    echo 错误: 未找到数据库初始化脚本
    echo 请确保 init-database.sql 或 src\main\resources\schema.sql 文件存在
    pause
    exit /b 1
)

echo.
echo 验证数据库结构...
for /f %%i in ('mysql -u%DB_USER% -p%DB_PASSWORD% -D gobang -e "SHOW TABLES" -s ^| find /c /v ""') do set TABLES=%%i
if !TABLES! GEQ 5 (
    echo √ 数据库表创建成功 (共 !TABLES! 个表)
) else (
    echo 警告: 只找到 !TABLES! 个表，可能初始化不完整
)

echo.
echo ==================================
echo 数据库信息
echo ==================================
mysql -u%DB_USER% -p%DB_PASSWORD% -D gobball -e "SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'gobang' ORDER BY TABLE_NAME;"
echo.

echo √ 初始化完成！
echo.
echo 下一步:
echo 1. 编辑 src\main\resources\application.yml 配置数据库连接
echo 2. 运行项目: mvn clean compile exec:java -Dexec.mainClass="com.gobang.GobangServer"
echo.
pause
