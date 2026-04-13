# 五子棋游戏服务器 - 阿里云ECS完整部署指南

<div align="center">

![ECS](https://img.shields.io/badge/阿里云-ECS-orange)
![Ubuntu](https://img.shields.io/badge/OS-Ubuntu_20.04-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)
![Redis](https://img.shields.io/badge/Redis-7.0-red)

从零开始部署五子棋服务器到阿里云 ECS

[快速部署](QUICK_DEPLOY.md) • [故障排查](TROUBLESHOOTING.md) • [API 文档](API.md)

</div>

---

## 📋 目录

1. [部署准备](#部署准备)
2. [购买 ECS](#购买-ecs)
3. [环境配置](#环境配置)
4. [数据库配置](#数据库配置)
5. [项目部署](#项目部署)
6. [域名配置](#域名配置)
7. [监控维护](#监控维护)

---

## 部署准备

### 需要准备的资源

| 资源 | 说明 | 费用参考 |
|------|------|----------|
| 阿里云 ECS | 云服务器 | 约 ¥100/月起 |
| 域名 | 可选 | 约 ¥50/年起 |
| 宽带 | 公网流量 | 按流量或带宽计费 |

### 本地环境

- ✅ JDK 17+
- ✅ Maven 3.6+
- ✅ 项目源码
- ✅ SSH 客户端（PuTTY / OpenSSH）

---

## 购买 ECS

### 推荐配置

| 配置 | 规格 | 说明 |
|------|------|------|
| 实例规格 | 2 核 4GB | 支持 100+ 并发 |
| 操作系统 | Ubuntu 20.04 LTS | 稳定易用 |
| 系统盘 | 40 GB ESSD | 系统运行 |
| 数据盘 | 20 GB ESSD | 可选，存储数据 |
| 公网带宽 | 1 Mbps | 10-50 人同时在线 |
| 安全组 | 默认 | 后续配置 |

### 购买步骤

1. 登录 [阿里云控制台](https://ecs.console.aliyun.com/)
2. 点击「创建实例」
3. 按推荐配置选择
4. 设置登录密码（或使用密钥对）
5. 确认订单并支付

### 获取连接信息

购买完成后，记录以下信息：

```
公网 IP: xxx.xxx.xxx.xxx
用户名: root
密码: your_password
```

---

## 环境配置

### 第一步：连接到 ECS

```bash
# 使用 SSH 连接（替换为你的 ECS 公网 IP）
ssh root@your_ecs_ip

# 或使用密钥
ssh -i your_key.pem root@your_ecs_ip
```

### 第二步：更新系统

```bash
# Ubuntu/Debian
sudo apt update && sudo apt upgrade -y

# CentOS
sudo yum update -y
```

### 第三步：安装 JDK 17

```bash
# Ubuntu/Debian
sudo apt install -y openjdk-17-jdk

# 验证安装
java -version
# 应显示: openjdk version "17.x.x"
```

### 第四步：安装 MySQL 8.0

```bash
# Ubuntu/Debian
sudo apt install -y mysql-server

# 启动 MySQL
sudo systemctl start mysql
sudo systemctl enable mysql

# 安全配置
sudo mysql_secure_installation
```

**mysql_secure_installation 配置建议**：

```
VALIDATE PASSWORD COMPONENT: Y
Password validation policy: 1 (MEDIUM)
Root password: [设置强密码]
Remove anonymous users: Y
Disallow root login remotely: Y
Remove test database: Y
Reload privilege tables: Y
```

### 第五步：安装 Redis

```bash
# Ubuntu/Debian
sudo apt install -y redis-server

# 启动 Redis
sudo systemctl start redis
sudo systemctl enable redis

# 测试 Redis
redis-cli ping
# 应返回: PONG
```

### 第六步：配置 Redis 密码（推荐）

```bash
# 编辑配置
sudo nano /etc/redis/redis.conf

# 找到并取消注释，设置密码
requirepass your_redis_password

# 重启 Redis
sudo systemctl restart redis
```

---

## 数据库配置

### 第一步：创建数据库

```bash
# 登录 MySQL
sudo mysql -u root -p

# 执行 SQL 命令
```

```sql
-- 创建数据库
CREATE DATABASE gobang CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建应用用户
CREATE USER 'gobang'@'localhost' IDENTIFIED BY 'your_secure_password';

-- 授权
GRANT ALL PRIVILEGES ON gobang.* TO 'gobang'@'localhost';
FLUSH PRIVILEGES;

-- 查看数据库
SHOW DATABASES;

-- 退出
EXIT;
```

### 第二步：导入表结构

```bash
# 在本地导出 SQL 文件
# 或在 ECS 上创建
```

在 ECS 上创建 schema.sql：

```bash
# 方法一：从本地上传
scp backend/src/main/resources/schema.sql root@your_ecs_ip:/root/

# 方法二：在 ECS 上创建
sudo nano /root/schema.sql
# 粘贴 SQL 内容后保存

# 导入数据库
sudo mysql -u gobang -p gobang < /root/schema.sql
```

### 第三步：验证数据库

```bash
# 登录数据库
sudo mysql -u gobang -p gobang

# 查看表
SHOW TABLES;

# 应显示以下表：
# user, user_stats, game_record, friend, chat_message, ...
```

---

## 项目部署

### 第一步：本地打包

```bash
# 在本地项目目录
cd /path/to/gobang-server

# 打包
mvn clean package -DskipTests

# 打包完成后的文件
# target/gobang-server-1.0.0.jar
```

### 第二步：上传到 ECS

```bash
# 使用 SCP 上传
scp target/gobang-server-1.0.0.jar root@your_ecs_ip:/opt/gobang/

# 或使用 WinSCP / FileZilla 上传
```

### 第三步：创建应用目录

```bash
# 在 ECS 上执行
sudo mkdir -p /opt/gobang/logs
sudo mkdir -p /opt/gobang/backup
sudo mkdir -p /opt/gobang/static
```

### 第四步：创建配置文件

```bash
sudo nano /opt/gobang/application.yml
```

```yaml
server:
  port: 8083
  host: 0.0.0.0

netty:
  port: 8083
  boss-threads: 1
  worker-threads: 4
  max-connections: 1000

database:
  driver: com.mysql.cj.jdbc.Driver
  url: jdbc:mysql://localhost:3306/gobang?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
  username: gobang
  password: "your_secure_password"
  pool-size: 10
  connection-timeout: 30000
  idle-timeout: 600000
  max-lifetime: 1800000

redis:
  host: localhost
  port: 6379
  password: "your_redis_password"
  database: 0
  timeout: 2000

jwt:
  secret: "${JWT_SECRET:your-256-bit-secret-key-change-in-production}"
  expiration: 604800
  issuer: gobang-server

game:
  board-size: 15
  win-count: 5
  move-timeout: 300
  reconnect-window: 600
  room-expire-time: 600

match:
  rating-diff: 100
  max-queue-time: 300
  check-interval: 3000
  test-mode: false
  test-match-delay: 0
```

### 第五步：设置环境变量

```bash
# 生成 JWT 密钥
export JWT_SECRET="$(openssl rand -base64 32)"

# 添加到环境变量
sudo nano /etc/environment

# 添加以下行
JWT_SECRET="your-generated-secret"
DB_USERNAME="gobang"
DB_PASSWORD="your_password"
REDIS_PASSWORD="your_redis_password"
```

### 第六步：创建 systemd 服务

```bash
sudo nano /etc/systemd/system/gobang.service
```

```ini
[Unit]
Description=Gobang Game Server
After=network.target mysql.service redis.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/gobang
ExecStart=/usr/bin/java -server -Xms512m -Xmx1g \
    -Dspring.config.location=/opt/gobang/application.yml \
    -jar /opt/gobang/gobang-server-1.0.0.jar
Restart=on-failure
RestartSec=10
StandardOutput=append:/opt/gobang/logs/app.log
StandardError=append:/opt/gobang/logs/error.log

# 环境变量
Environment="JWT_SECRET=your-256-bit-secret-key"
Environment="DB_USERNAME=gobang"
Environment="DB_PASSWORD=your_password"
Environment="REDIS_PASSWORD=your_redis_password"

[Install]
WantedBy=multi-user.target
```

### 第七步：启动服务

```bash
# 重载 systemd
sudo systemctl daemon-reload

# 启动服务
sudo systemctl start gobang

# 设置开机自启
sudo systemctl enable gobang

# 查看状态
sudo systemctl status gobang
```

### 第八步：配置防火墙

**阿里云安全组配置**：

1. 登录阿里云控制台
2. 进入 ECS 实例 → 安全组
3. 添加入方向规则：

| 协议类型 | 端口范围 | 授权对象 | 描述 |
|---------|---------|---------|------|
| TCP | 8083/8083 | 0.0.0.0/0 | 游戏服务器 |
| TCP | 80/80 | 0.0.0.0/0 | HTTP |
| TCP | 443/443 | 0.0.0.0/0 | HTTPS |
| TCP | 22/22 | 你的 IP | SSH |

**ECS 内部防火墙**（可选）：

```bash
# Ubuntu
sudo ufw allow 8083/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable

# CentOS
sudo firewall-cmd --permanent --add-port=8083/tcp
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --permanent --add-port=443/tcp
sudo firewall-cmd --reload
```

---

## 验证部署

### 测试 API

```bash
# 健康检查
curl http://localhost:8083/api/health

# 预期返回
{"success":true,"data":{"status":"ok"}}
```

### 浏览器访问

```
http://your_ecs_ip:8083/index.html
```

### 检查日志

```bash
# 查看应用日志
tail -f /opt/gobang/logs/app.log

# 查看 systemd 日志
sudo journalctl -u gobang -f
```

---

## 域名配置（可选）

### 第一步：购买域名

在域名服务商购买域名（如阿里云、腾讯云）

### 第二步：配置 DNS 解析

```
记录类型: A
主机记录: @ 或 www
记录值: your_ecs_public_ip
TTL: 600
```

### 第三步：配置 Nginx 反向代理

```bash
# 安装 Nginx
sudo apt install -y nginx

# 创建配置
sudo nano /etc/nginx/sites-available/gobang
```

```nginx
server {
    listen 80;
    server_name your-domain.com www.your-domain.com;

    # 静态文件
    location / {
        proxy_pass http://localhost:8083;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket 代理
    location /ws {
        proxy_pass http://localhost:8083/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    # API 代理
    location /api {
        proxy_pass http://localhost:8083/api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

```bash
# 启用配置
sudo ln -s /etc/nginx/sites-available/gobang /etc/nginx/sites-enabled/

# 测试配置
sudo nginx -t

# 重启 Nginx
sudo systemctl restart nginx
```

### 第四步：配置 SSL 证书

```bash
# 安装 Certbot
sudo apt install -y certbot python3-certbot-nginx

# 获取证书
sudo certbot --nginx -d your-domain.com -d www.your-domain.com

# 自动续期
sudo certbot renew --dry-run
```

---

## 监控维护

### 日志轮转

```bash
sudo nano /etc/logrotate.d/gobang
```

```
/opt/gobang/logs/*.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    create 0640 root root
    sharedscripts
    postrotate
        systemctl reload gobang > /dev/null 2>&1 || true
    endscript
}
```

### 自动备份

```bash
sudo nano /opt/gobang/backup.sh
```

```bash
#!/bin/bash
BACKUP_DIR="/opt/gobang/backup"
DATE=$(date +%Y%m%d_%H%M%S)

# 备份数据库
mysqldump -u gobang -p'your_password' gobang > $BACKUP_DIR/gobang_$DATE.sql

# 保留最近 30 天
find $BACKUP_DIR -name "gobang_*.sql" -mtime +30 -delete

echo "Backup completed at $(date)" >> /opt/gobang/backup.log
```

```bash
# 添加执行权限
sudo chmod +x /opt/gobang/backup.sh

# 添加定时任务
crontab -e

# 每天凌晨 3 点备份
0 3 * * * /opt/gobang/backup.sh
```

### 监控脚本

```bash
sudo nano /opt/gobang/monitor.sh
```

```bash
#!/bin/bash

# 检查服务状态
if ! systemctl is-active --quiet gobang; then
    echo "Gobang service is down, restarting..."
    systemctl start gobang
    echo "Service restarted at $(date)" >> /opt/gobang/restart.log
fi

# 检查磁盘空间
DISK_USAGE=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//')
if [ $DISK_USAGE -gt 80 ]; then
    echo "Warning: Disk usage is ${DISK_USAGE}%" | mail -s "Disk Alert" admin@example.com
fi
```

---

## 常用命令

```bash
# 服务管理
sudo systemctl start gobang      # 启动
sudo systemctl stop gobang       # 停止
sudo systemctl restart gobang    # 重启
sudo systemctl status gobang     # 状态

# 日志查看
sudo journalctl -u gobang -f     # 实时日志
sudo journalctl -u gobang -n 100 # 最近 100 行
tail -f /opt/gobang/logs/app.log # 应用日志

# 数据库
sudo systemctl status mysql      # MySQL 状态
sudo mysql -u gobang -p gobang   # 登录数据库

# Redis
sudo systemctl status redis      # Redis 状态
redis-cli -a your_password ping  # 测试连接
```

---

## 更新应用

```bash
# 1. 停止服务
sudo systemctl stop gobang

# 2. 备份当前版本
sudo cp /opt/gobang/gobang-server-1.0.0.jar /opt/gobang/backup/gobang-server-$(date +%Y%m%d).jar

# 3. 上传新版本
scp target/gobang-server-1.0.0.jar root@your_ecs_ip:/opt/gobang/

# 4. 重启服务
sudo systemctl start gobang

# 5. 验证
sudo systemctl status gobang
```

---

## 故障排查

### 问题诊断流程

1. **检查服务状态**
```bash
sudo systemctl status gobang
```

2. **查看日志**
```bash
sudo journalctl -u gobang -n 50 --no-pager
```

3. **检查端口**
```bash
netstat -tlnp | grep 8083
```

4. **检查数据库**
```bash
sudo systemctl status mysql
sudo mysql -u gobang -p -e "SELECT 1"
```

5. **检查 Redis**
```bash
sudo systemctl status redis
redis-cli -a your_password ping
```

### 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|----------|----------|
| 服务无法启动 | 数据库连接失败 | 检查 MySQL 状态和密码 |
| 无法访问网站 | 安全组未配置 | 添加 8083 端口规则 |
| 连接频繁断开 | 内存不足 | 增加 ECS 配置 |
| 数据丢失 | 未备份 | 配置自动备份 |

---

## 成本优化

### 按量付费 vs 包年包月

| 方式 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| 按量付费 | 灵活 | 单价高 | 测试环境 |
| 包年包月 | 价格优惠 | 不灵活 | 生产环境 |

### 带宽选择

| 带宽 | 支持人数 | 月费用参考 |
|------|----------|------------|
| 1 Mbps | 10-50 人 | 约 ¥30 |
| 3 Mbps | 50-200 人 | 约 ¥80 |
| 5 Mbps | 200-500 人 | 约 ¥130 |

---

## 完成！

你的五子棋游戏已成功部署：

| 地址 | 说明 |
|------|------|
| `http://your_ecs_ip:8083` | 游戏主页 |
| `http://your_domain.com` | 域名访问（已配置） |

<div align="center">

🎉 部署完成！祝游戏运营顺利！

[返回首页](../README.md) • [快速部署](QUICK_DEPLOY.md) • [故障排查](TROUBLESHOOTING.md)

</div>
