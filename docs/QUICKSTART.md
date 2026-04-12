# 五子棋在线对战系统 - 快速开始指南

<div align="center">

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Vue 3](https://img.shields.io/badge/Vue-3.4-brightgreen)
![Node.js](https://img.shields.io/badge/Node.js-18+-brightgreen)

5 分钟快速启动五子棋在线对战系统

[返回首页](../README.md) • [API 文档](API.md) • [故障排查](TROUBLESHOOTING.md) • [代码结构](CODE_STRUCTURE.md)

</div>

---

## 目录

1. [系统要求](#系统要求)
2. [快速安装](#快速安装)
3. [配置指南](#配置指南)
4. [启动服务](#启动服务)
5. [验证部署](#验证部署)
6. [常见问题](#常见问题)

---

## 系统要求

### 方式一：Docker 部署（最简单）

| 组件 | 版本要求 | 用途 |
|------|----------|------|
| Docker | 20.10+ | 容器运行时 |
| Docker Compose | 2.0+ | 容器编排 |

### 方式二：本地开发

#### 后端要求

| 组件 | 版本要求 | 用途 | 检查命令 |
|------|----------|------|----------|
| JDK | 17+ | Java 运行环境 | `java -version` |
| Maven | 3.6+ | 项目构建工具 | `mvn -version` |
| MySQL | 8.0+ | 数据存储 | `mysql --version` |
| Redis | 7.0+ | 缓存/会话 | `redis-cli ping` |

#### 前端要求

| 组件 | 版本要求 | 用途 | 检查命令 |
|------|----------|------|----------|
| Node.js | 18+ | JavaScript 运行环境 | `node -v` |
| npm | 9.0+ | 包管理器 | `npm -v` |

### 推荐配置

- **CPU**: 2 核心以上
- **内存**: 4GB 以上
- **磁盘**: 10GB 以上

---

## 快速安装

### 方式一：Docker 部署（推荐）

这是最快的方式，无需手动安装依赖。

#### 1. 安装 Docker

**Windows / macOS:**
```
下载并安装 Docker Desktop: https://www.docker.com/products/docker-desktop
```

**Linux (Ubuntu/Debian):**
```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
```

#### 2. 克隆项目

```bash
git clone https://github.com/your-username/gobang.git
cd gobang
```

#### 3. 启动服务

```bash
# 一键启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f
```

#### 4. 访问应用

```
前端: http://localhost:5173
后端: http://localhost:8080
```

### 方式二：本地开发

#### Windows 用户

##### 1. 安装 JDK 17

```powershell
# 使用 winget 安装
winget install EclipseAdoptium.Temurin.17.JDK

# 或访问 https://adoptium.net/ 下载安装
```

##### 2. 安装 Maven

```powershell
# 使用 winget 安装
winget install Apache.Maven

# 或访问 https://maven.apache.org/download.cgi 下载
```

##### 3. 安装 MySQL 8.0

```powershell
# 使用 winget 安装
winget install Oracle.MySQL

# 或访问 https://dev.mysql.com/downloads/mysql/ 下载
```

##### 4. 安装 Redis

下载 [Redis for Windows](https://github.com/microsoftarchive/redis/releases)

```powershell
# 解压后启动
redis-server.exe
```

##### 5. 安装 Node.js 18+

```powershell
# 使用 winget 安装
winget install OpenJS.NodeJS.LTS

# 或访问 https://nodejs.org/ 下载
```

#### Linux 用户

##### Ubuntu/Debian

```bash
# 更新包列表
sudo apt update

# 安装 JDK 17
sudo apt install -y openjdk-17-jdk

# 安装 Maven
sudo apt install -y maven

# 安装 MySQL
sudo apt install -y mysql-server

# 安装 Redis
sudo apt install -y redis-server

# 安装 Node.js
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs

# 验证安装
java -version
mvn -version
node -v
npm -v
```

##### CentOS/RHEL

```bash
# 安装 JDK 17
sudo yum install -y java-17-openjdk-devel

# 安装 Maven
sudo yum install -y maven

# 安装 MySQL
sudo yum install -y mysql-server

# 安装 Redis
sudo yum install -y redis

# 安装 Node.js
curl -fsSL https://rpm.nodesource.com/setup_18.x | sudo bash -
sudo yum install -y nodejs
```

#### macOS 用户

```bash
# 使用 Homebrew 安装
brew install openjdk@17
brew install maven
brew install mysql
brew install redis
brew install node

# 启动服务
brew services start mysql
brew services start redis
```

---

## 配置指南

### 1. 配置 MySQL

```bash
# 登录 MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE gobang CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 创建用户（可选）
CREATE USER 'gobang'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON gobang.* TO 'gobang'@'localhost';
FLUSH PRIVILEGES;

# 导入表结构
USE gobang;
source D:/wuziqi/src/main/resources/db/migration/V1__init_schema.sql;
```

### 2. 配置 Redis

```bash
# 编辑 redis.conf
# 设置密码（可选）
requirepass your_redis_password

# 启动 Redis
redis-server

# 或 Windows
redis-server.exe
```

### 3. 配置后端

编辑 `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gobang?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4
    username: root
    password: your_mysql_password
    driver-class-name: com.mysql.cj.jdbc.Driver

  data:
    redis:
      host: localhost
      port: 6379
      password: # 如果设置了密码请填写
      database: 0

jwt:
  secret: your-jwt-secret-key-at-least-256-bits-long
  expiration: 604800  # 7天，单位：秒

server:
  port: 8080
```

### 4. 配置前端

创建 `gobang-frontend/.env.development`:

```env
# API 地址
VITE_API_BASE_URL=http://localhost:8080

# WebSocket 地址
VITE_WS_BASE_URL=ws://localhost:8080
```

---

## 启动服务

### 启动后端

```bash
# 进入项目目录
cd D:\wuziqi

# 方式一：使用 Maven 运行
mvn clean install
mvn spring-boot:run

# 方式二：打包后运行
mvn clean package -DskipTests
java -jar target/gobang-1.0.0.jar

# 后端将在 http://localhost:8080 启动
```

### 启动前端

```bash
# 进入前端目录
cd gobang-frontend

# 安装依赖（首次运行）
npm install

# 启动开发服务器
npm run dev

# 前端将在 http://localhost:5173 启动
```

---

## 验证部署

### 1. 检查后端健康状态

```bash
# 访问健康检查接口
curl http://localhost:8080/actuator/health

# 预期输出
{"status":"UP"}
```

### 2. 检查前端

浏览器访问: `http://localhost:5173`

应该能看到登录页面。

### 3. 测试注册和登录

1. 点击"注册"按钮
2. 填写用户信息：
   - 用户名: testuser
   - 密码: password123
   - 昵称: 测试用户
3. 点击"注册"
4. 使用注册的账号登录

### 4. 测试 WebSocket 连接

打开浏览器开发者工具 (F12)，切换到 Console 标签：

```javascript
// 应该能看到 WebSocket 连接成功的日志
// [WebSocket] ✅ 连接成功
```

---

## 常见问题

### Q1: 端口被占用

**问题**: `Address already in use: 8080`

**解决方案**:

```bash
# Windows - 查找占用端口的进程
netstat -ano | findstr :8080
taskkill /F /PID <进程ID>

# Linux/macOS
lsof -ti:8080 | xargs kill -9

# 或修改配置文件中的端口
# application.yml: server.port=8081
```

### Q2: 数据库连接失败

**问题**: `Communications link failure`

**解决方案**:

```bash
# 检查 MySQL 是否启动
# Windows
net start MySQL

# Linux
sudo systemctl start mysql

# macOS
brew services start mysql

# 检查连接配置
# 确认 application.yml 中的数据库地址、端口、用户名、密码正确
```

### Q3: Redis 连接失败

**问题**: `Unable to connect to Redis`

**解决方案**:

```bash
# 检查 Redis 是否启动
# Windows
redis-server.exe

# Linux
sudo systemctl start redis

# macOS
brew services start redis

# 测试连接
redis-cli ping
# 应该返回 PONG
```

### Q4: 前端无法连接后端

**问题**: `Network Error` 或 `CORS Error`

**解决方案**:

1. 确认后端已启动
2. 检查 CORS 配置
3. 检查 API 地址配置

```bash
# 测试后端 API
curl http://localhost:8080/api/health

# 检查前端代理配置
# gobang-frontend/vite.config.ts
```

### Q5: Maven 依赖下载失败

**问题**: `Could not resolve dependencies`

**解决方案**:

```bash
# 配置国内镜像源
# 编辑 ~/.m2/settings.xml

<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <name>Aliyun Maven</name>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>

# 重新下载依赖
mvn clean install -U
```

### Q6: npm install 失败

**问题**: `npm ERR!`

**解决方案**:

```bash
# 清除缓存
npm cache clean --force

# 使用国内镜像
npm config set registry https://registry.npmmirror.com

# 重新安装
rm -rf node_modules package-lock.json
npm install
```

### Q7: JWT Secret 未设置

**问题**: `JWT secret key is required`

**解决方案**:

```bash
# 生成 JWT Secret
openssl rand -base64 32

# 将生成的密钥配置到 application.yml
jwt:
  secret: <生成的密钥>
```

---

## 下一步

### 开发模式

现在你已经成功启动了开发环境，可以开始开发：

1. 📖 阅读 [代码结构文档](CODE_STRUCTURE.md)
2. 🔌 查看 [API 文档](API.md)
3. 🛠️ 阅读 [贡献指南](CONTRIBUTING.md)

### 生产部署

准备部署到生产环境？

1. 📦 阅读 [部署指南](QUICK_DEPLOY.md)
2. 🚀 阅读 [ECS 部署指南](ECS_DEPLOYMENT_GUIDE.md)
3. 🔒 查看 [安全最佳实践](../README.md#安全最佳实践)

### 用户指南

- 👀 [用户使用指南](USER_GUIDE.md)
- ❓ [故障排查](TROUBLESHOOTING.md)
- 📝 [更新日志](CHANGELOG.md)

---

## 获取帮助

如果遇到问题：

1. 查看 [故障排查文档](TROUBLESHOOTING.md)
2. 搜索 [GitHub Issues](https://github.com/your-username/gobang/issues)
3. 提交新的 [Issue](https://github.com/your-username/gobang/issues/new)

---

<div align="center">

**祝你使用愉快！** 🎉

如有问题，请随时提 Issue

</div>
