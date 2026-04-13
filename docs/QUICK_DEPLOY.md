# 🚀 五子棋项目 - 快速部署指南

<div align="center">

![ECS](https://img.shields.io/badge/ECS-阿里云-orange)
![Docker](https://img.shields.io/badge/Docker-支持-blue)

5 分钟将五子棋服务器部署到云服务器

[快速开始](QUICKSTART.md) • [ECS 完整指南](ECS_DEPLOYMENT_GUIDE.md) • [故障排查](TROUBLESHOOTING.md)

</div>

---

## 目录

1. [部署方式选择](#部署方式选择)
2. [本地打包](#本地打包)
3. [服务器准备](#服务器准备)
4. [上传部署](#上传部署)
5. [启动服务](#启动服务)
6. [验证部署](#验证部署)

---

## 部署方式选择

### 方式对比

| 方式 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **一键部署脚本** | 快速、自动化 | 需要 root 权限 | 快速测试 |
| **Docker** | 环境隔离、易管理 | 需要 Docker | 生产环境 |
| **手动部署** | 灵活、可控 | 步骤多 | 定制化部署 |

---

## 本地打包

### Windows 用户

```batch
REM 使用批处理脚本
build-and-package.bat

REM 或手动执行
cd D:\wuziqi
mvn clean package -DskipTests
```

### Linux/Mac 用户

```bash
cd /path/to/gobang-server

# 打包
mvn clean package -DskipTests

# 打包完成后，JAR 文件位于
# target/gobang-server-1.0.0.jar
```

### 打包验证

```bash
# 检查 JAR 文件
ls -lh target/gobang-server-1.0.0.jar

# 应显示约 50-80 MB
```

---

## 服务器准备

### 阿里云 ECS 推荐

| 配置 | 说明 |
|------|------|
| CPU | 2 核 |
| 内存 | 4 GB |
| 带宽 | 1 Mbps（公网） |
| 系统 | Ubuntu 20.04 / CentOS 7+ |

### 安全组配置

在阿里云控制台添加入方向规则：

| 协议 | 端口 | 授权对象 | 说明 |
|------|------|----------|------|
| TCP | 8083 | 0.0.0.0/0 | 游戏服务器 |
| TCP | 22 | 你的 IP | SSH |
| TCP | 3306 | localhost | MySQL（不对外开放） |
| TCP | 6379 | localhost | Redis（不对外开放） |

---

## 上传部署

### 方式一：SCP 命令行

```bash
# 在本地执行
scp backend/target/gobang-server-1.0.0.jar root@your_ecs_ip:/opt/gobang/

# 上传配置文件（可选）
scp backend/src/main/resources/application.yml root@your_ecs_ip:/opt/gobang/
```

### 方式二：WinSCP（Windows 推荐）

1. 下载 [WinSCP](https://winscp.net/)
2. 连接到 ECS
3. 上传 JAR 文件到 `/opt/gobang/`

### 方式三：FileZilla

1. 下载 [FileZilla](https://filezilla-project.org/)
2. SFTP 连接到 ECS
3. 上传文件

---

## 启动服务

### 一键部署脚本（推荐）

在 ECS 上执行：

```bash
# 下载部署脚本
wget https://raw.githubusercontent.com/your-repo/deploy.sh -O deploy.sh
chmod +x deploy.sh

# 执行部署
sudo ./deploy.sh
```

### 手动启动

#### 1. 创建目录

```bash
sudo mkdir -p /opt/gobang/logs
sudo mkdir -p /opt/gobang/backup
```

#### 2. 上传文件

```bash
# 设置权限
sudo chmod +x /opt/gobang/gobang-server-1.0.0.jar
```

#### 3. 创建 systemd 服务

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
ExecStart=/usr/bin/java -jar /opt/gobang/gobang-server-1.0.0.jar
Restart=on-failure
RestartSec=10

# 环境变量
Environment="JWT_SECRET=your-256-bit-secret-key"
Environment="DB_USERNAME=gobang"
Environment="DB_PASSWORD=your_password"

[Install]
WantedBy=multi-user.target
```

#### 4. 启动服务

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

### Docker 部署

```bash
# 创建 Dockerfile
cat > Dockerfile << 'EOF'
FROM openjdk:17-slim

WORKDIR /app
COPY target/gobang-server-1.0.0.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

# 构建镜像
docker build -t gobang-server .

# 运行容器
docker run -d \
  --name gobang \
  -p 8083:8083 \
  -e DB_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  --restart unless-stopped \
  gobang-server

# 查看日志
docker logs -f gobang
```

---

## 验证部署

### 1. 检查服务状态

```bash
# 查看服务状态
sudo systemctl status gobang

# 查看日志
sudo journalctl -u gobang -f

# 检查端口
netstat -tlnp | grep 8083
```

### 2. 测试 API

```bash
# 健康检查
curl http://localhost:8083/api/health

# 预期返回
{"success":true,"data":{"status":"ok",...}}
```

### 3. 浏览器访问

```
http://your_ecs_ip:8083/index.html
```

### 4. 测试 WebSocket

```javascript
// 在浏览器控制台执行
const ws = new WebSocket('ws://your_ecs_ip:8083/ws');

ws.onopen = () => console.log('连接成功');
ws.onerror = (e) => console.error('连接失败', e);
```

---

## 常用管理命令

### 服务管理

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

# 查看最近 100 行日志
sudo journalctl -u gobang -n 100
```

### 更新应用

```bash
# 1. 停止服务
sudo systemctl stop gobang

# 2. 备份当前版本
sudo cp /opt/gobang/gobang-server.jar /opt/gobang/backup/gobang-server-$(date +%Y%m%d).jar

# 3. 上传新版本
scp target/gobang-server-1.0.0.jar root@your_ecs_ip:/opt/gobang/

# 4. 替换文件
sudo mv /opt/gobang/gobang-server-1.0.0.jar /opt/gobang/gobang-server.jar

# 5. 启动服务
sudo systemctl start gobang

# 6. 验证
sudo systemctl status gobang
```

### 数据库备份

```bash
# 创建备份脚本
cat > /opt/gobang/backup.sh << 'EOF'
#!/bin/bash
BACKUP_DIR="/opt/gobang/backup"
DATE=$(date +%Y%m%d_%H%M%S)

# 备份数据库
mysqldump -u gobang -p'your_password' gobang > $BACKUP_DIR/gobang_$DATE.sql

# 保留最近 30 天
find $BACKUP_DIR -name "gobang_*.sql" -mtime +30 -delete
EOF

chmod +x /opt/gobang/backup.sh

# 添加定时任务
crontab -e

# 每天凌晨 3 点备份
0 3 * * * /opt/gobang/backup.sh
```

---

## Nginx 反向代理（可选）

### 安装 Nginx

```bash
# Ubuntu/Debian
sudo apt install nginx

# CentOS
sudo yum install nginx
```

### 配置反向代理

```bash
sudo nano /etc/nginx/sites-available/gobang
```

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 静态文件
    location / {
        proxy_pass http://localhost:8083;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # WebSocket 代理
    location /ws {
        proxy_pass http://localhost:8083/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
```

### 启用配置

```bash
# 创建软链接
sudo ln -s /etc/nginx/sites-available/gobang /etc/nginx/sites-enabled/

# 测试配置
sudo nginx -t

# 重启 Nginx
sudo systemctl restart nginx
```

---

## HTTPS 配置（可选）

### 使用 Certbot

```bash
# 安装 Certbot
sudo apt install certbot python3-certbot-nginx

# 获取证书
sudo certbot --nginx -d your-domain.com

# 自动续期
sudo certbot renew --dry-run
```

---

## 故障排查

### 服务无法启动

```bash
# 查看详细日志
sudo journalctl -u gobang -n 50 --no-pager

# 常见原因：
# 1. 数据库连接失败 → 检查 MySQL 状态
# 2. Redis 连接失败 → 检查 Redis 状态
# 3. 端口被占用 → netstat -tlnp | grep 8083
# 4. 内存不足 → free -h
```

### 无法访问网站

1. **检查安全组规则**
   - 登录阿里云控制台
   - 确认 8083 端口已开放

2. **检查防火墙**
```bash
# Ubuntu
sudo ufw status

# CentOS
sudo firewall-cmd --list-all
```

3. **检查服务状态**
```bash
sudo systemctl status gobang
netstat -tlnp | grep 8083
```

### 数据库连接失败

```bash
# 检查 MySQL 状态
sudo systemctl status mysql

# 测试连接
mysql -u gobang -p -h localhost

# 检查配置
cat /opt/gobang/application.yml
```

---

## 监控和维护

### 日志管理

```bash
# 配置日志轮转
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

### 性能监控

```bash
# CPU 和内存
top

# 磁盘使用
df -h

# 网络连接
netstat -an | grep 8083 | wc -l
```

---

## 完成！

部署完成后，你的五子棋游戏将在以下地址可访问：

| 地址 | 说明 |
|------|------|
| `http://your_ecs_ip:8083` | 游戏主页 |
| `http://your_ecs_ip:8083/api` | API 接口 |
| `ws://your_ecs_ip:8083/ws` | WebSocket |

<div align="center">

祝游戏运营顺利！🎮✨

[返回首页](../README.md) • [完整部署指南](ECS_DEPLOYMENT_GUIDE.md)

</div>
