# 五子棋游戏服务器 - 部署到阿里云ECS完整指南

## 📋 部署准备清单

### 本地环境
- [x] JDK 17+
- [x] Maven 3.x
- [x] 项目源码

### ECS服务器要求
- [ ] 阿里云ECS（推荐配置：2核4G内存以上）
- [ ] 操作系统：Ubuntu 20.04 / CentOS 7+ / Debian 10+
- [ ] 公网IP
- [ ] 域名（可选，建议配置）

### ECS需要的软件
- [ ] JDK 17
- [ ] MySQL 8.0+
- [ ] Redis 6.0+

---

## 🚀 第一步：本地打包项目

### 1.1 清理并打包
```bash
cd D:\wuziqi

# 清理旧的编译文件
mvn clean

# 打包成可执行JAR
mvn package -DskipTests
```

### 1.2 找到打包文件
打包完成后，在 `target` 目录下会生成：
```
target/gobang-server-1.0.0.jar  （可执行JAR，约50-80MB）
```

---

## 🌐 第二步：ECS服务器环境配置

### 2.1 连接到ECS服务器
```bash
# 使用SSH连接（替换为你的ECS公网IP）
ssh root@your_ecs_ip

# 或使用密钥
ssh -i your_key.pem root@your_ecs_ip
```

### 2.2 安装JDK 17
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y openjdk-17-jdk

# CentOS
sudo yum install -y java-17-openjdk-devel

# 验证安装
java -version
```

### 2.3 安装MySQL 8.0
```bash
# Ubuntu/Debian
sudo apt install -y mysql-server

# CentOS
sudo yum install -y mysql-server

# 启动MySQL
sudo systemctl start mysql
sudo systemctl enable mysql

# 设置root密码
sudo mysql_secure_installation
```

### 2.4 安装Redis
```bash
# Ubuntu/Debian
sudo apt install -y redis-server

# CentOS
sudo yum install -y redis

# 启动Redis
sudo systemctl start redis
sudo systemctl enable redis

# 测试Redis
redis-cli ping
# 应返回：PONG
```

---

## 📁 第三步：创建数据库

### 3.1 登录MySQL并创建数据库
```bash
mysql -u root -p
```

```sql
-- 创建数据库
CREATE DATABASE gobang CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户并授权
CREATE USER 'gobang'@'localhost' IDENTIFIED BY 'your_secure_password';
GRANT ALL PRIVILEGES ON gobang.* TO 'gobang'@'localhost';
FLUSH PRIVILEGES;

-- 退出
EXIT;
```

### 3.2 导入数据库表结构
```bash
# 将SQL文件上传到ECS（在本地执行）
scp src/main/resources/sql/*.sql root@your_ecs_ip:/root/

# 在ECS上导入
mysql -u gobang -p gobang < /root/schema.sql
```

---

## 📤 第四步：上传项目到ECS

### 4.1 上传JAR文件
```bash
# 在本地执行（替换为你的ECS IP）
scp target/gobang-server-1.0.0.jar root@your_ecs_ip:/root/gobang-server.jar

# 或使用SCP工具（如WinSCP、FileZilla）
```

### 4.2 上传配置文件（可选）
如果需要自定义配置：
```bash
# 上传配置文件
scp src/main/resources/application.yml root@your_ecs_ip:/root/gobang-config.yml
```

---

## ⚙️ 第五步：配置应用

### 5.1 创建应用目录
```bash
# 在ECS上执行
mkdir -p /opt/gobang
mkdir -p /opt/gobang/logs
mkdir -p /opt/gobang/backup

# 移动JAR文件
mv /root/gobang-server.jar /opt/gobang/
cd /opt/gobang
```

### 5.2 创建配置文件
```bash
nano /opt/gobang/application.yml
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
  password: "your_secure_password"  # 修改为实际密码
  pool-size: 10

redis:
  host: localhost
  port: 6379
  password: ""
  database: 0

jwt:
  secret: "your-256-bit-secret-key-change-in-production"  # 生产环境必须修改
  expiration: 604800  # 7天

game:
  board-size: 15
  win-count: 5
```

### 5.3 设置环境变量（可选，用于敏感信息）
```bash
# 编辑环境变量
nano /etc/environment

# 添加以下内容
DB_USERNAME=gobang
DB_PASSWORD=your_secure_password
JWT_SECRET=your-256-bit-secret-key-change-in-production

# 重新加载
source /etc/environment
```

---

## 🎯 第六步：启动服务

### 6.1 测试启动
```bash
cd /opt/gobang

# 前台启动（测试用）
java -jar gobang-server.jar

# 按 Ctrl+C 停止
```

### 6.2 后台启动（生产环境）

#### 方法1：使用nohup
```bash
nohup java -jar gobang-server.jar > logs/app.log 2>&1 &

# 查看日志
tail -f logs/app.log
```

#### 方法2：使用systemd（推荐）
```bash
# 创建systemd服务文件
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
ExecStart=/usr/bin/java -jar /opt/gobang/gobang-server.jar
Restart=on-failure
RestartSec=10

# 环境变量
Environment="DB_USERNAME=gobang"
Environment="DB_PASSWORD=your_secure_password"
Environment="JWT_SECRET=your-256-bit-secret-key"

[Install]
WantedBy=multi-user.target
```

```bash
# 重载systemd
sudo systemctl daemon-reload

# 启动服务
sudo systemctl start gobang

# 设置开机自启
sudo systemctl enable gobang

# 查看状态
sudo systemctl status gobang

# 查看日志
sudo journalctl -u gobang -f
```

---

## 🔥 第七步：配置防火墙

### 7.1 阿里云安全组配置
在阿里云控制台：
1. 进入 ECS 实例 → 安全组
2. 添加入方向规则：
   - 端口：8083，协议：TCP，授权对象：0.0.0.0/0
   - 端口：80（HTTP），协议：TCP，授权对象：0.0.0.0/0
   - 端口：443（HTTPS），协议：TCP，授权对象：0.0.0.0/0

### 7.2 ECS内部防火墙（如果有）
```bash
# Ubuntu/Debian
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

## 🌍 第八步：配置域名（可选）

### 8.1 配置DNS解析
在域名服务商处添加A记录：
```
记录类型：A
主机记录：@ 或 www
记录值：your_ecs_public_ip
TTL：600
```

### 8.2 配置Nginx反向代理（推荐）
```bash
# 安装Nginx
sudo apt install -y nginx  # Ubuntu/Debian
sudo yum install -y nginx  # CentOS

# 创建配置文件
sudo nano /etc/nginx/sites-available/gobang
```

```nginx
server {
    listen 80;
    server_name your-domain.com www.your-domain.com;

    # 静态文件
    location / {
        root /opt/gobang/static;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # API代理
    location /api {
        proxy_pass http://localhost:8083/api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # WebSocket代理
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
}
```

```bash
# 启用配置
sudo ln -s /etc/nginx/sites-available/gobang /etc/nginx/sites-enabled/

# 测试配置
sudo nginx -t

# 重启Nginx
sudo systemctl restart nginx
```

### 8.3 配置SSL证书（HTTPS）
```bash
# 安装Certbot
sudo apt install -y certbot python3-certbot-nginx

# 获取证书
sudo certbot --nginx -d your-domain.com -d www.your-domain.com

# 自动续期
sudo certbot renew --dry-run
```

---

## ✅ 第九步：验证部署

### 9.1 检查服务状态
```bash
# 检查Java进程
ps aux | grep gobang

# 检查端口监听
netstat -tlnp | grep 8083

# 检查日志
tail -f /opt/gobang/logs/app.log
```

### 9.2 测试API
```bash
# 测试健康检查
curl http://your_ecs_ip:8083/api/health

# 预期返回
{"success":true,"data":{"status":"ok",...}}
```

### 9.3 测试前端访问
浏览器访问：
- `http://your_ecs_ip:8083/index.html`
- 或 `http://your-domain.com`（如果配置了域名和Nginx）

---

## 📊 第十步：监控和维护

### 10.1 设置日志轮转
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

### 10.2 设置自动备份
```bash
# 创建备份脚本
nano /opt/gobang/backup.sh
```

```bash
#!/bin/bash
BACKUP_DIR="/opt/gobang/backup"
DATE=$(date +%Y%m%d_%H%M%S)

# 备份数据库
mysqldump -u gobang -p'your_password' gobang > $BACKUP_DIR/gobang_$DATE.sql

# 保留最近30天的备份
find $BACKUP_DIR -name "gobang_*.sql" -mtime +30 -delete
```

```bash
# 添加定时任务
chmod +x /opt/gobang/backup.sh
crontab -e

# 每天凌晨3点备份
0 3 * * * /opt/gobang/backup.sh
```

### 10.3 监控脚本
```bash
nano /opt/gobang/monitor.sh
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
    echo "Warning: Disk usage is ${DISK_USAGE}%"
fi
```

---

## 🔧 常见问题排查

### 问题1：服务无法启动
```bash
# 检查日志
journalctl -u gobang -n 50

# 常见原因：
# 1. 端口被占用
# 2. 数据库连接失败
# 3. Redis连接失败
# 4. 内存不足
```

### 问题2：无法访问网站
```bash
# 检查防火墙
sudo ufw status

# 检查阿里云安全组
# 登录阿里云控制台检查

# 检查服务状态
sudo systemctl status gobang
```

### 问题3：数据库连接失败
```bash
# 检查MySQL状态
sudo systemctl status mysql

# 测试连接
mysql -u gobang -p -h localhost

# 检查配置
cat /opt/gobang/application.yml
```

---

## 📝 快速命令参考

```bash
# 启动服务
sudo systemctl start gobang

# 停止服务
sudo systemctl stop gobang

# 重启服务
sudo systemctl restart gobang

# 查看状态
sudo systemctl status gobang

# 查看实时日志
sudo journalctl -u gobang -f

# 查看最近100行日志
sudo journalctl -u gobang -n 100

# 更新JAR文件
sudo systemctl stop gobang
cp gobang-server-new.jar /opt/gobang/gobang-server.jar
sudo systemctl start gobang
```

---

## 🎉 部署完成！

部署完成后，你的五子棋游戏将在以下地址可访问：

- **服务器IP**: `http://your_ecs_ip:8083`
- **域名**: `http://your-domain.com`（如果配置）
- **WebSocket**: `ws://your_ecs_ip:8083/ws`

祝你的五子棋游戏运行顺利！🎮
