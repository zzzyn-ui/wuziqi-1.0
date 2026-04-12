# 五子棋在线对战服务器 - 快速开始指南

<div align="center">

![Java](https://img.shields.io/badge/Java-17-orange)
![Maven](https://img.shields.io/badge/Maven-3.6+-red)

5 分钟快速启动五子棋服务器

[返回首页](../README.md) • [API 文档](API.md) • [故障排查](TROUBLESHOOTING.md)

</div>

---

## 目录

1. [系统要求](#系统要求)
2. [快速安装](#快速安装)
3. [配置指南](#配置指南)
4. [启动服务](#启动服务)
5. [验证部署](#验证部署)
6. [下一步](#下一步)

---

## 系统要求

### 必需组件

| 组件 | 版本要求 | 用途 | 检查命令 |
|------|----------|------|----------|
| JDK | 17+ | Java 运行环境 | `java -version` |
| Maven | 3.6+ | 项目构建工具 | `mvn -version` |
| MySQL | 8.0+ | 数据存储 | `mysql --version` |
| Redis | 7.0+ | 缓存/会话 | `redis-cli ping` |

### 推荐配置

- **CPU**: 2 核心以上
- **内存**: 4GB 以上
- **磁盘**: 10GB 以上

---

## 快速安装

### Windows 用户

#### 1. 安装 JDK 17

```powershell
# 使用 winget 安装
winget install EclipseAdoptium.Temurin.17.JDK

# 或访问 https://adoptium.net/ 下载安装
```

#### 2. 安装 Maven

```powershell
# 使用 winget 安装
winget install Apache.Maven

# 或访问 https://maven.apache.org/download.cgi 下载
```

#### 3. 安装 MySQL

```powershell
# 使用 winget 安装
winget install Oracle.MySQL

# 或访问 https://dev.mysql.com/downloads/mysql/ 下载
```

#### 4. 安装 Redis

下载 [Redis for Windows](https://github.com/microsoftarchive/redis/releases)

```powershell
# 解压后启动
redis-server.exe
```

### Linux 用户

#### Ubuntu/Debian

```bash
# 更新包列表
sudo apt update

# 安装所有依赖
sudo apt install -y openjdk-17-jdk maven mysql-server redis-server

# 验证安装
java -version
mvn -version
mysql --version
redis-cli ping
```

#### CentOS/RHEL

```bash
# 安装 Java 17
sudo yum install -y java-17-openjdk-devel

# 安装 Maven
sudo yum install -y maven

# 安装 MySQL
sudo yum install -y mysql-server

# 安装 Redis
sudo yum install -y redis

# 启动服务
sudo systemctl start mysql
sudo systemctl start redis
sudo systemctl enable mysql
sudo systemctl enable redis
```

### macOS 用户

```bash
# 使用 Homebrew 安装
brew install openjdk@17 maven mysql redis

# 启动服务
brew services start mysql
brew services start redis
```

---

## 配置指南

### 1. 创建数据库

```bash
# 登录 MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE gobang CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 创建用户（可选）
CREATE USER 'gobang'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON gobang.* TO 'gobang'@'localhost';
FLUSH PRIVILEGES;

# 退出
EXIT;
```

### 2. 导入表结构

```bash
# 方式一：使用 MySQL 命令
mysql -u root -p gobang < src/main/resources/sql/schema.sql

# 方式二：在 MySQL 中执行
mysql -u root -p
USE gobang;
source /path/to/schema.sql;
```

### 3. 配置应用

编辑 `src/main/resources/application.yml`：

```yaml
# 服务器配置
server:
  port: 8083
  host: 0.0.0.0

# Netty 配置
netty:
  port: 8083
  boss-threads: 1
  worker-threads: 4

# 数据库配置
database:
  driver: com.mysql.cj.jdbc.Driver
  url: jdbc:mysql://localhost:3306/gobang?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
  username: root              # 修改为你的用户名
  password: your_password      # 修改为你的密码
  pool-size: 10

# Redis 配置
redis:
  host: localhost
  port: 6379
  password: ""                # 如有密码请填写
  database: 0
  timeout: 2000

# JWT 配置
jwt:
  secret: your-256-bit-secret-key-change-in-production  # 生产环境必须修改
  expiration: 604800          # 7 天
  issuer: gobang-server

# 游戏配置
game:
  board-size: 15
  win-count: 5
  move-timeout: 300           # 落子超时 5 分钟
  reconnect-window: 600       # 断线重连 10 分钟

# 匹配配置
match:
  rating-diff: 100            # 匹配积分差
  max-queue-time: 300         # 最大匹配时间 5 分钟
  check-interval: 3000        # 检查间隔 3 秒
```

### 4. 安全配置（生产环境）

```bash
# 生成安全的 JWT 密钥
export JWT_SECRET="$(openssl rand -base64 32)"

# 或在 application.yml 中使用环境变量
jwt:
  secret: ${JWT_SECRET}
```

---

## 启动服务

### 方式一：使用 Maven

```bash
# 编译项目
mvn clean compile

# 运行服务器
mvn exec:java -Dexec.mainClass="com.gobang.GobangServer"
```

### 方式二：打包后运行

```bash
# 打包（跳过测试）
mvn clean package -DskipTests

# 运行 JAR
java -jar target/gobang-server-1.0.0.jar

# 或指定 JVM 参数
java -Xmx1g -Xms512m -jar target/gobang-server-1.0.0.jar
```

### 方式三：使用 IDE

**IntelliJ IDEA**:
1. 打开项目
2. 找到 `GobangServer.java`
3. 右键 → Run 'GobangServer'

**Eclipse**:
1. 导入项目为 Maven 项目
2. 找到 `GobangServer.java`
3. 右键 → Run As → Java Application

### 方式四：后台运行

```bash
# Linux/Mac
nohup java -jar target/gobang-server-1.0.0.jar > logs/app.log 2>&1 &

# Windows (使用 PowerShell)
Start-Process java -ArgumentList "-jar target/gobang-server-1.0.0.jar" -WindowStyle Hidden
```

---

## 验证部署

### 1. 检查服务状态

服务器启动成功后，你应该看到：

```
========================================
  五子棋在线对战服务器启动中...
========================================
✓ 数据库连接正常
✓ 用户表存在，用户总数: 0
✓ Redis 连接正常，已启用分布式匹配队列
✓ 残局数据已就绪
========================================
  服务器启动完成！
  服务端点: http://0.0.0.0:8083
  WebSocket: ws://0.0.0.0:8083/ws
  HTTP API: http://0.0.0.0:8083/api
========================================
```

### 2. 测试 API

```bash
# 健康检查
curl http://localhost:8083/api/health

# 预期返回
{"success":true,"data":{"status":"ok",...}}
```

### 3. 测试 WebSocket

```javascript
// 在浏览器控制台执行
const ws = new WebSocket('ws://localhost:8083/ws');

ws.onopen = () => {
    console.log('✓ WebSocket 连接成功');
};

ws.onmessage = (event) => {
    console.log('✓ 收到消息:', event.data);
};

ws.onerror = (error) => {
    console.error('✗ 连接错误:', error);
};
```

### 4. 访问前端

```
主页:     http://localhost:8083/index.html
登录页:   http://localhost:8083/login.html
游戏页:   http://localhost:8083/game.html
排行榜:   http://localhost:8083/rank.html
```

---

## 下一步

### 开发模式

1. **注册测试账号**
   - 访问 http://localhost:8083/login.html
   - 点击「注册」创建账号

2. **开始对战**
   - 快速匹配或创建房间
   - 邀请好友一起游戏

3. **查看 API 文档**
   - [API.md](API.md) - 完整的接口文档

### 生产部署

1. **阅读部署指南**
   - [QUICK_DEPLOY.md](QUICK_DEPLOY.md) - 快速部署
   - [ECS_DEPLOYMENT_GUIDE.md](ECS_DEPLOYMENT_GUIDE.md) - ECS 部署

2. **配置安全**
   - 修改 JWT 密钥
   - 设置数据库密码
   - 配置防火墙规则

3. **性能优化**
   - 调整 JVM 参数
   - 配置 Nginx 反向代理
   - 启用 HTTPS

---

## 故障排查

### 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 编译失败 | JDK 版本不对 | 安装 JDK 17 |
| 数据库连接失败 | MySQL 未启动 | `sudo systemctl start mysql` |
| Redis 连接失败 | Redis 未启动 | `redis-server` 或 `sudo systemctl start redis` |
| 端口被占用 | 8083 端口已被使用 | 修改 application.yml 中的端口 |
| 找不到主类 | 编译不完整 | 运行 `mvn clean compile` |

### 调试模式

启用调试日志：

```yaml
# application.yml
logging:
  level:
    com.gobang: DEBUG
    io.netty: INFO
```

### 查看日志

```bash
# 实时日志
tail -f logs/gobang-server.log

# 错误日志
grep ERROR logs/gobang-server.log
```

详细排查请查看 [TROUBLESHOOTING.md](TROUBLESHOOTING.md)

---

## 命令速查

```bash
# 编译
mvn clean compile

# 打包
mvn clean package -DskipTests

# 运行
mvn exec:java -Dexec.mainClass="com.gobang.GobangServer"

# 数据库操作
mysql -u root -p gobang < src/main/resources/sql/schema.sql

# Redis 操作
redis-cli FLUSHDB  # 清空缓存

# 日志查看
tail -f logs/gobang-server.log
```

---

## 获取帮助

- 📖 [完整文档](../README.md)
- 🐛 [问题反馈](https://github.com/your-username/gobang-server/issues)
- 💬 [讨论区](https://github.com/your-username/gobang-server/discussions)

---

<div align="center">

祝使用愉快！🎮

</div>
