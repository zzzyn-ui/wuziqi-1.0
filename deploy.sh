#!/bin/bash
# 五子棋服务器一键部署脚本
# 适用于 Ubuntu 20.04 / Debian 10+

set -e

echo "========================================"
echo "  五子棋游戏服务器 - 一键部署脚本"
echo "========================================"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置变量
DB_NAME="gobang"
DB_USER="gobang"
DB_PASS="Gobang2024!ChangeMe"  # 请修改为强密码
APP_DIR="/opt/gobang"
JAR_FILE="gobang-server.jar"
SERVER_PORT="8083"

# 检查是否为root用户
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}错误：请使用 root 用户执行此脚本${NC}"
    exit 1
fi

echo -e "${GREEN}步骤 1/10: 更新系统软件包${NC}"
apt update && apt upgrade -y

echo -e "${GREEN}步骤 2/10: 安装 JDK 17${NC}"
apt install -y openjdk-17-jdk
java -version

echo -e "${GREEN}步骤 3/10: 安装 MySQL 8.0${NC}"
debconf-set-selections <<< "mysql-server mysql-server/root_password password $DB_PASS"
debconf-set-selections <<< "mysql-server mysql-server/root_password_again password $DB_PASS"
apt install -y mysql-server
systemctl start mysql
systemctl enable mysql

echo -e "${GREEN}步骤 4/10: 安装 Redis${NC}"
apt install -y redis-server
systemctl start redis
systemctl enable redis
redis-cli ping

echo -e "${GREEN}步骤 5/10: 创建数据库${NC}"
mysql -u root -p"$DB_PASS" <<EOF
CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON $DB_NAME.* TO '$DB_USER'@'localhost';
FLUSH PRIVILEGES;
EOF

echo -e "${GREEN}步骤 6/10: 创建应用目录${NC}"
mkdir -p $APP_DIR
mkdir -p $APP_DIR/logs
mkdir -p $APP_DIR/backup

echo -e "${GREEN}步骤 7/10: 配置防火墙${NC}"
if command -v ufw &> /dev/null; then
    ufw allow $SERVER_PORT/tcp
    ufw allow 80/tcp
    ufw allow 443/tcp
    echo "防火墙规则已添加"
fi

echo -e "${GREEN}步骤 8/10: 创建 systemd 服务${NC}"
cat > /etc/systemd/system/gobang.service <<EOF
[Unit]
Description=Gobang Game Server
After=network.target mysql.service redis.service

[Service]
Type=simple
User=root
WorkingDirectory=$APP_DIR
ExecStart=/usr/bin/java -jar $APP_DIR/$JAR_FILE
Restart=on-failure
RestartSec=10
Environment="DB_USERNAME=$DB_USER"
Environment="DB_PASSWORD=$DB_PASS"
Environment="JWT_SECRET=your-256-bit-secret-key-change-in-production"

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload

echo -e "${GREEN}步骤 9/10: 上传 JAR 文件${NC}"
echo -e "${YELLOW}请将 gobang-server.jar 上传到 $APP_DIR/ 目录${NC}"
echo -e "${YELLOW}使用命令: scp gobang-server.jar root@your_ip:$APP_DIR/${NC}"
echo ""
read -p "JAR 文件已上传并放置在 $APP_DIR/ 目录？(y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}跳过。请稍后手动上传 JAR 文件并启动服务。${NC}"
    exit 0
fi

# 检查JAR文件是否存在
if [ ! -f "$APP_DIR/$JAR_FILE" ]; then
    echo -e "${RED}错误：找不到 JAR 文件 $APP_DIR/$JAR_FILE${NC}"
    echo "请先上传 JAR 文件到服务器"
    exit 1
fi

echo -e "${GREEN}步骤 10/10: 启动服务${NC}"
systemctl start gobang
systemctl enable gobang

# 等待服务启动
sleep 5

# 检查服务状态
if systemctl is-active --quiet gobang; then
    echo -e "${GREEN}✓ 服务启动成功！${NC}"
    echo ""
    echo "服务状态："
    systemctl status gobang --no-pager
    echo ""
    echo "访问地址："
    echo "  HTTP: http://$(hostname -I | awk '{print $1}'):$SERVER_PORT"
    echo "  WebSocket: ws://$(hostname -I | awk '{print $1}'):$SERVER_PORT/ws"
    echo ""
    echo "查看日志："
    echo "  sudo journalctl -u gobang -f"
else
    echo -e "${RED}✗ 服务启动失败${NC}"
    echo "查看日志："
    journalctl -u gobang -n 50
    exit 1
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  部署完成！${NC}"
echo -e "${GREEN}========================================${NC}"
