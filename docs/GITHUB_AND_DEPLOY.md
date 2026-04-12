# GitHub 更新和阿里云部署指南

本文档详细说明如何将代码更新到 GitHub 并部署到阿里云 ECS 服务器。

---

## 📋 目录

1. [更新代码到 GitHub](#1-更新代码到-github)
2. [部署到阿里云 ECS](#2-部署到阿里云-ecs)
3. [常见问题](#3-常见问题)

---

## 1. 更新代码到 GitHub

### 1.1 检查当前状态

```bash
# 进入项目目录
cd D:\wuziqi

# 查看当前状态
git status

# 查看当前分支
git branch
```

### 1.2 添加所有更改

```bash
# 添加所有更改
git add .

# 或者添加特定文件
git add README.md
git add docs/
git add src/
git add gobang-frontend/
```

### 1.3 提交更改

```bash
# 提交更改
git commit -m "feat: 完成五子棋在线对战系统开发

- ✨ 实现用户系统（注册、登录、JWT认证）
- ✨ 实现游戏系统（人机对战、快速匹配、房间对战）
- ✨ 实现好友系统（添加好友、实时聊天、游戏邀请）
- ✨ 实现观战系统（实时观看对局）
- ✨ 实现排行榜系统（积分排名、胜率排名）
- ✨ 实现对局记录系统（历史对局、棋谱回放）
- ✨ 实现残局挑战系统（棋谱解答）
- 🐛 修复聊天历史加载问题
- 🎨 优化UI界面（移除多余元素）
- 📝 完善项目文档

技术栈：
- 后端: Spring Boot 3 + Java 17 + MySQL 8.0 + Redis 7.0
- 前端: Vue 3 + TypeScript + Vite + Element Plus
- 通信: WebSocket (STOMP) + HTTP REST API
"
```

### 1.4 推送到 GitHub

#### 首次推送（如果远程仓库不存在）

```bash
# 添加远程仓库
git remote add origin https://github.com/your-username/gobang.git

# 推送到远程
git push -u origin main
```

#### 后续推送

```bash
# 推送到当前分支
git push

# 或指定远程和分支
git push origin main
```

### 1.5 验证推送

打开浏览器访问你的 GitHub 仓库，确认代码已更新。

---

## 2. 部署到阿里云 ECS

### 2.1 方式一：Docker 部署（推荐）

#### 2.1.1 服务器环境准备

```bash
# 1. 购买阿里云 ECS 服务器
# 推荐: 2核4G，系统: Ubuntu 20.04 或 CentOS 7+

# 2. 连接到服务器
# 使用 SSH 工具（如 PuTTY、Xshell、MobaXterm）

ssh root@your-server-ip

# 3. 更新系统
# Ubuntu/Debian
apt update && apt upgrade -y

# CentOS/RHEL
yum update -y
```

#### 2.1.2 安装 Docker

```bash
# Ubuntu/Debian
# 安装 Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# 启动 Docker
systemctl start docker
systemctl enable docker

# 安装 Docker Compose
curl -L "https://github.com/docker/compose/releases/download/v2.20.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# 验证安装
docker --version
docker-compose --version

# CentOS/RHEL
# 安装 Docker
yum install -y docker
systemctl start docker
systemctl enable docker

# 安装 Docker Compose
yum install -y docker-compose
```

#### 2.1.3 部署项目

```bash
# 1. 创建项目目录
mkdir -p /opt/gobang
cd /opt/gobang

# 2. 从 GitHub 拉取代码
git clone https://github.com/your-username/gobang.git
cd gobang

# 3. 配置环境变量
cp .env.example .env

# 编辑 .env 文件，设置生产环境配置
nano .env

# 必须修改的配置：
# - JWT_SECRET: 生成强密钥
# - MYSQL_PASSWORD: 设置数据库密码
# - REDIS_PASSWORD: 设置 Redis 密码

# 生成 JWT Secret
openssl rand -base64 32

# 4. 启动服务
docker-compose up -d

# 5. 查看日志
docker-compose logs -f

# 6. 检查服务状态
docker-compose ps
```

#### 2.1.4 配置 Nginx 反向代理（可选）

```bash
# 1. 安装 Nginx
# Ubuntu/Debian
apt install -y nginx

# CentOS/RHEL
yum install -y nginx

# 2. 配置 Nginx
nano /etc/nginx/sites-available/gobang

# 添加以下配置：
server {
    listen 80;
    server_name your-domain.com;  # 修改为你的域名或服务器 IP

    # 前端
    location / {
        proxy_pass http://localhost:5173;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 后端 API
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # WebSocket
    location /ws {
        proxy_pass http://localhost:8080/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}

# 3. 启用配置
ln -s /etc/nginx/sites-available/gobang /etc/nginx/sites-enabled/

# 4. 测试配置
nginx -t

# 5. 重启 Nginx
systemctl restart nginx
systemctl enable nginx

# 6. 开放防火墙端口
# Ubuntu/Debian
ufw allow 80
ufw allow 443
ufw allow 8080

# CentOS/RHEL
firewall-cmd --permanent --add-port=80/tcp
firewall-cmd --permanent --add-port=443/tcp
firewall-cmd --permanent --add-port=8080/tcp
firewall-cmd --reload
```

#### 2.1.5 配置 SSL 证书（可选）

```bash
# 使用 Let's Encrypt 免费证书

# 1. 安装 Certbot
# Ubuntu/Debian
apt install -y certbot python3-certbot-nginx

# CentOS/RHEL
yum install -y certbot python3-certbot-nginx

# 2. 申请证书
certbot --nginx -d your-domain.com

# 3. 自动续期
certbot renew --dry-run

# 4. 添加定时任务
crontab -e

# 添加以下行（每天凌晨2点续期）
0 2 * * * certbot renew --quiet
```

### 2.2 方式二：传统部署

#### 2.2.1 安装 Java 环境

```bash
# 安装 JDK 17
# Ubuntu/Debian
apt install -y openjdk-17-jdk

# CentOS/RHEL
yum install -y java-17-openjdk-devel

# 验证安装
java -version
```

#### 2.2.2 安装 MySQL

```bash
# 安装 MySQL 8.0
# Ubuntu/Debian
apt install -y mysql-server

# CentOS/RHEL
yum install -y mysql-server

# 启动 MySQL
systemctl start mysql
systemctl enable mysql

# 安全配置
mysql_secure_installation

# 创建数据库和用户
mysql -u root -p

CREATE DATABASE gobang CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'gobang'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON gobang.* TO 'gobang'@'localhost';
FLUSH PRIVILEGES;
EXIT;

# 导入表结构
mysql -u gobang -p gobang < /opt/gobang/src/main/resources/db/migration/V1__init_schema.sql
```

#### 2.2.3 安装 Redis

```bash
# 安装 Redis
# Ubuntu/Debian
apt install -y redis-server

# CentOS/RHEL
yum install -y redis

# 启动 Redis
systemctl start redis
systemctl enable redis

# 配置密码（可选）
nano /etc/redis/redis.conf

# 找到并修改这一行：
# requirepass your_redis_password

# 重启 Redis
systemctl restart redis
```

#### 2.2.4 部署后端

```bash
# 1. 进入项目目录
cd /opt/gobang

# 2. 修改配置文件
nano src/main/resources/application.yml

# 修改数据库和 Redis 配置

# 3. 构建项目
mvn clean package -DskipTests

# 4. 运行后端（前台测试）
java -jar target/gobang-1.0.0.jar

# 或使用 systemd 服务（后台运行）
nano /etc/systemd/system/gobang.service

[Unit]
Description=Gobang Game Server
After=network.target mysql.service redis.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/gobang
ExecStart=/usr/bin/java -jar /opt/gobang/target/gobang-1.0.0.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target

# 5. 启动服务
systemctl daemon-reload
systemctl start gobang
systemctl enable gobang

# 6. 查看日志
journalctl -u gobang -f
```

#### 2.2.5 部署前端

```bash
# 1. 安装 Node.js 18+
# Ubuntu/Debian
curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
apt install -y nodejs

# CentOS/RHEL
curl -fsSL https://rpm.nodesource.com/setup_18.x | bash -
yum install -y nodejs

# 2. 进入前端目录
cd /opt/gobang/gobang-frontend

# 3. 安装依赖
npm install

# 4. 构建生产版本
npm run build

# 5. 配置 Nginx 服务前端
nano /etc/nginx/sites-available/gobang

# 修改前端静态文件路径：
# location / {
#     root /opt/gobang/gobang-frontend/dist;
#     try_files $uri $uri/ /index.html;
# }

# 6. 重启 Nginx
systemctl restart nginx
```

### 2.3 阿里云安全组配置

```bash
# 1. 登录阿里云控制台
# 2. 进入 ECS 实例列表
# 3. 点击实例 ID 进入详情
# 4. 点击"安全组"
# 5. 配置入方向规则：

端口      协议      授权对象      描述
80       TCP      0.0.0.0/0      HTTP
443      TCP      0.0.0.0/0      HTTPS
8080     TCP      0.0.0.0/0      后端 API（可选）
22       TCP      你的IP/32      SSH（建议限制）

# 6. 点击"保存"
```

---

## 3. 常见问题

### 3.1 Git 相关问题

**问题: 推送时提示权限被拒绝**

```bash
# 解决方案：配置 SSH 密钥
# 1. 生成 SSH 密钥
ssh-keygen -t rsa -b 4096 -C "your_email@example.com"

# 2. 复制公钥到 GitHub
cat ~/.ssh/id_rsa.pub

# 3. 将公钥添加到 GitHub:
# GitHub -> Settings -> SSH and GPG keys -> New SSH key

# 4. 测试连接
ssh -T git@github.com

# 5. 更改远程仓库地址为 SSH
git remote set-url origin git@github.com:your-username/gobang.git

# 6. 重新推送
git push origin main
```

**问题: 提交信息写错了**

```bash
# 修改最后一次提交
git commit --amend -m "新的提交信息"

# 如果已推送，需要强制推送（谨慎使用）
git push origin main --force
```

### 3.2 Docker 相关问题

**问题: 容器无法启动**

```bash
# 查看容器日志
docker-compose logs -f

# 查看特定容器日志
docker logs gobang-backend

# 重启容器
docker-compose restart

# 重建容器
docker-compose up -d --build
```

**问题: 端口冲突**

```bash
# 检查端口占用
netstat -tunlp | grep :8080

# 修改 docker-compose.yml 中的端口映射
# 例如: "8080:8080" 改为 "9080:8080"
```

### 3.3 部署相关问题

**问题: 无法访问网站**

```bash
# 检查服务状态
systemctl status gobang
systemctl status nginx

# 检查防火墙
# Ubuntu
ufw status

# CentOS
firewall-cmd --list-all

# 检查端口监听
netstat -tunlp
```

**问题: 数据库连接失败**

```bash
# 检查 MySQL 状态
systemctl status mysql

# 测试连接
mysql -u gobang -p -h localhost

# 检查配置文件
cat /opt/gobang/src/main/resources/application.yml
```

### 3.4 性能优化

**配置 JVM 参数**

```bash
# 修改 systemd 服务文件
nano /etc/systemd/system/gobang.service

# 添加 JVM 参数
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/gobang/target/gobang-1.0.0.jar
```

**配置 MySQL 性能**

```bash
# 编辑 MySQL 配置
nano /etc/mysql/mysql.conf.d/mysqld.cnf

# 添加以下配置：
[mysqld]
max_connections = 500
innodb_buffer_pool_size = 256M
innodb_log_file_size = 64M
```

---

## 4. 监控和维护

### 4.1 查看日志

```bash
# 后端日志
journalctl -u gobang -f

# Docker 日志
docker-compose logs -f backend

# Nginx 日志
tail -f /var/log/nginx/access.log
tail -f /var/log/nginx/error.log
```

### 4.2 备份数据库

```bash
# 手动备份
mysqldump -u gobang -p gobang > backup_$(date +%Y%m%d).sql

# 自动备份脚本
nano /opt/backup.sh

#!/bin/bash
BACKUP_DIR="/opt/backups"
DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR
mysqldump -u gobang -pyour_password gobang > $BACKUP_DIR/gobang_$DATE.sql

# 添加定时任务
crontab -e

# 每天凌晨3点备份
0 3 * * * /opt/backup.sh
```

### 4.3 更新代码

```bash
# 1. 登录服务器
ssh root@your-server-ip

# 2. 进入项目目录
cd /opt/gobang

# 3. 拉取最新代码
git pull origin main

# 4. Docker 部署
docker-compose down
docker-compose up -d --build

# 5. 或传统部署
# 后端
systemctl stop gobang
mvn clean package -DskipTests
systemctl start gobang

# 前端
cd gobang-frontend
npm run build
systemctl restart nginx
```

---

## 5. 域名配置（可选）

### 5.1 购买域名

在阿里云或其他域名注册商购买域名。

### 5.2 配置 DNS 解析

```bash
# 1. 登录阿里云控制台
# 2. 进入"域名"列表
# 3. 点击"解析"
# 4. 添加解析记录：

记录类型    主机记录    记录值              TTL
A          @          你的服务器IP      600
A          www        你的服务器IP      600
```

### 5.3 配置域名到 Nginx

```bash
# 修改 Nginx 配置
nano /etc/nginx/sites-available/gobang

# 修改 server_name
server_name your-domain.com www.your-domain.com;

# 重启 Nginx
systemctl restart nginx
```

---

## 6. 完整部署流程总结

```bash
# 第一步：准备服务器
# 1. 购买 ECS 实例
# 2. 配置安全组（开放 80、443 端口）

# 第二步：连接服务器
ssh root@your-server-ip

# 第三步：安装 Docker
curl -fsSL https://get.docker.com | sh
systemctl start docker
systemctl enable docker

# 第四步：拉取代码
mkdir -p /opt/gobang
cd /opt/gobang
git clone https://github.com/your-username/gobang.git
cd gobang

# 第五步：配置环境
cp .env.example .env
nano .env  # 修改必要配置

# 第六步：启动服务
docker-compose up -d

# 第七步：配置 Nginx
apt install -y nginx
nano /etc/nginx/sites-available/gobang  # 配置反向代理
systemctl restart nginx

# 第八步：访问网站
# 浏览器打开: http://your-server-ip 或 http://your-domain.com
```

---

<div align="center">

**部署完成后，你的五子棋对战平台就可以访问了！** 🎉

</div>
