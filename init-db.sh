#!/bin/bash

# 五子棋游戏数据库快速初始化脚本
# 使用方法: ./init-db.sh [mysql_user] [mysql_password]

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 获取参数
DB_USER=${1:-root}
DB_PASSWORD=${2:-}

echo -e "${GREEN}==================================${NC}"
echo -e "${GREEN}五子棋游戏数据库初始化脚本${NC}"
echo -e "${GREEN}==================================${NC}"
echo ""

# 检查MySQL是否运行
echo -e "${YELLOW}检查MySQL服务...${NC}"
if ! command -v mysql &> /dev/null; then
    echo -e "${RED}错误: 未找到MySQL命令${NC}"
    echo "请先安装MySQL: https://dev.mysql.com/downloads/mysql/"
    exit 1
fi

# 测试MySQL连接
if [ -z "$DB_PASSWORD" ]; then
    echo -e "${YELLOW}请输入MySQL密码 (用户: $DB_USER):${NC}"
    read -s DB_PASSWORD
    echo ""
fi

if ! mysql -u"$DB_USER" -p"$DB_PASSWORD" -e "SELECT 1" &> /dev/null; then
    echo -e "${RED}错误: MySQL连接失败，请检查用户名和密码${NC}"
    exit 1
fi

echo -e "${GREEN}✓ MySQL连接成功${NC}"
echo ""

# 执行数据库初始化脚本
echo -e "${YELLOW}执行数据库初始化...${NC}"
if [ -f "init-database.sql" ]; then
    mysql -u"$DB_USER" -p"$DB_PASSWORD" < init-database.sql
    echo -e "${GREEN}✓ 数据库初始化完成${NC}"
elif [ -f "src/main/resources/schema.sql" ]; then
    mysql -u"$DB_USER" -p"$DB_PASSWORD" < src/main/resources/schema.sql
    echo -e "${GREEN}✓ 数据库初始化完成${NC}"
else
    echo -e "${RED}错误: 未找到数据库初始化脚本${NC}"
    echo "请确保 init-database.sql 或 src/main/resources/schema.sql 文件存在"
    exit 1
fi

echo ""

# 验证数据库和表是否创建成功
echo -e "${YELLOW}验证数据库结构...${NC}"
TABLES=$(mysql -u"$DB_USER" -p"$DB_PASSWORD" -D gobang -e "SHOW TABLES" -s | wc -l)
if [ "$TABLES" -ge 5 ]; then
    echo -e "${GREEN}✓ 数据库表创建成功 (共 $TABLES 个表)${NC}"
else
    echo -e "${RED}警告: 只找到 $TABLES 个表，可能初始化不完整${NC}"
fi

echo ""

# 显示数据库信息
echo -e "${GREEN}==================================${NC}"
echo -e "${GREEN}数据库信息${NC}"
echo -e "${GREEN}==================================${NC}"
mysql -u"$DB_USER" -p"$DB_PASSWORD" -D gobang -e "SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'gobang' ORDER BY TABLE_NAME;"
echo ""

echo -e "${GREEN}✓ 初始化完成！${NC}"
echo -e "${YELLOW}下一步:${NC}"
echo "1. 编辑 src/main/resources/application.yml 配置数据库连接"
echo "2. 运行项目: mvn clean compile exec:java -Dexec.mainClass=\"com.gobang.GobangServer\""
echo ""
