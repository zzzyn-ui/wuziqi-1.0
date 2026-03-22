# 五子棋在线对战服务器

<div align="center">

![Java](https://img.shields.io/badge/Java-17-orange)
![Netty](https://img.shields.io/badge/Netty-4.1.104-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)
![Redis](https://img.shields.io/badge/Redis-7.0-red)
![License](https://img.shields.io/badge/License-MIT-yellow)

基于 WebSocket 的在线双人对战五子棋后端项目

[在线演示](#) • [快速开始](#快速开始) • [API 文档](docs/API.md) • [用户手册](docs/USER_GUIDE.md)

</div>

---

## 项目简介

一个功能完整的五子棋在线对战平台，支持实时双人对战、人机对战、好友系统、观战模式等功能。采用高性能的 Netty 框架处理 WebSocket 连接，配合 MySQL 和 Redis 实现数据持久化和缓存。

### 核心特性

| 特性 | 说明 |
|------|------|
| 🎮 **实时对战** | WebSocket 长连接，毫秒级响应 |
| 🤖 **人机对战** | 多难度 AI，适合不同水平玩家 |
| 👥 **社交系统** | 好友、聊天、观战一应俱全 |
| 🏆 **积分排名** | ELO 算法积分，实时排行榜 |
| 📊 **残局挑战** | 精选残局，提升棋艺 |
| 💾 **数据持久** | 完整对局记录，支持复盘 |

---

## 技术栈

### 后端技术

| 技术 | 版本 | 用途 |
|------|------|------|
| ![Java](https://img.shields.io/badge/Java-17-orange) | JDK 17+ | 编程语言 |
| ![Netty](https://img.shields.io/badge/Netty-4.1.104-brightgreen) | 4.1.104 | WebSocket 服务器 |
| ![MyBatis](https://img.shields.io/badge/MyBatis-3.5.13-purple) | 3.5.13 | ORM 框架 |
| ![MySQL](https://img.shields.io/badge/MySQL-8.0-blue) | 8.0+ | 关系型数据库 |
| ![Redis](https://img.shields.io/badge/Redis-7.0-red) | 7.0+ | 缓存/会话 |
| ![JWT](https://img.shields.io/badge/JJWT-0.12.3-green) | 0.12.3 | 认证授权 |
| ![HikariCP](https://img.shields.io/badge/HikariCP-5.0.1-cyan) | 5.0.1 | 数据库连接池 |

### 前端技术

| 技术 | 说明 |
|------|------|
| 原生 JavaScript | 无框架依赖 |
| WebSocket | 实时通信 |
| Canvas API | 棋盘渲染 |

---

## 快速开始

### 1. 环境准备

| 组件 | 版本要求 | 下载地址 |
|------|----------|----------|
| JDK | 17+ | [Adoptium](https://adoptium.net/) |
| Maven | 3.6+ | [Maven](https://maven.apache.org/download.cgi) |
| MySQL | 8.0+ | [MySQL](https://dev.mysql.com/downloads/mysql/) |
| Redis | 7.0+ | [Redis](https://redis.io/download) |

### 2. 克隆项目

```bash
git clone https://github.com/your-username/gobang-server.git
cd gobang-server
```

### 3. 初始化数据库

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE gobang CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 导入表结构
mysql -u root -p gobang < src/main/resources/sql/schema.sql
```

### 4. 配置文件

编辑 `src/main/resources/application.yml`：

```yaml
database:
  url: jdbc:mysql://localhost:3306/gobang?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
  username: your_username
  password: your_password

redis:
  host: localhost
  port: 6379
  password: ""  # 如有密码请填写
```

**⚠️ 生产环境安全配置**

```bash
# 生成 JWT 密钥
export JWT_SECRET="$(openssl rand -base64 32)"
```

### 5. 编译运行

```bash
# 编译项目
mvn clean compile

# 运行服务器
mvn exec:java -Dexec.mainClass="com.gobang.GobangServer"

# 或打包后运行
mvn clean package -DskipTests
java -jar target/gobang-server-1.0.0.jar
```

### 6. 访问应用

```
主页:     http://localhost:8083/index.html
API:      http://localhost:8083/api
WebSocket: ws://localhost:8083/ws
```

---

## 项目结构

```
gobang-server/
├── pom.xml                                    # Maven 配置
├── README.md                                  # 项目说明
├── CHANGELOG.md                               # 更新日志
├── CONTRIBUTING.md                            # 贡献指南
├── docs/                                      # 文档目录
│   ├── API.md                                 # API 接口文档
│   ├── USER_GUIDE.md                          # 用户手册
│   ├── TROUBLESHOOTING.md                     # 故障排查
│   ├── QUICKSTART.md                          # 快速开始
│   ├── QUICK_DEPLOY.md                        # 快速部署
│   ├── ECS_DEPLOYMENT_GUIDE.md                # ECS 部署指南
│   └── PERFORMANCE_TEST_REPORT.md             # 性能测试报告
├── src/main/
│   ├── java/com/gobang/
│   │   ├── GobangServer.java                  # 主启动类
│   │   ├── config/                            # 配置类 (4)
│   │   ├── cli/                               # 命令行工具
│   │   ├── controller/                        # 控制器
│   │   ├── core/                              # 核心逻辑
│   │   │   ├── game/                          # 游戏逻辑 (3)
│   │   │   │   ├── Board.java                 # 15×15 棋盘
│   │   │   │   ├── GameState.java             # 游戏状态
│   │   │   │   └── WinChecker.java            # 胜负判定
│   │   │   ├── handler/                       # 消息处理器 (6)
│   │   │   ├── match/                         # 匹配系统 (2)
│   │   │   ├── netty/                         # 网络层
│   │   │   │   ├── NettyServer.java           # Netty 服务器
│   │   │   │   ├── WebSocketHandler.java      # 消息路由
│   │   │   │   ├── ChannelManager.java        # 连接管理
│   │   │   │   └── api/                       # HTTP API (17)
│   │   │   ├── protocol/                      # 协议层 (3)
│   │   │   ├── rating/                        # 积分系统
│   │   │   │   ├── ELOCalculator.java         # ELO 算法
│   │   │   │   └── RatingCalculator.java      # 积分计算
│   │   │   ├── room/                          # 房间管理 (2)
│   │   │   └── social/                        # 社交功能 (3)
│   │   ├── mapper/                            # MyBatis Mapper (13)
│   │   ├── model/                             # 数据模型
│   │   │   ├── entity/                        # 实体类
│   │   │   └── dto/                           # 数据传输对象
│   │   ├── service/                           # 业务服务 (13)
│   │   └── util/                              # 工具类 (10)
│   ├── proto/
│   │   └── gobang.proto                       # Protobuf 协议
│   └── resources/
│       ├── application.yml                    # 应用配置
│       ├── logback.xml                        # 日志配置
│       ├── sql/                               # SQL 脚本
│       └── static/                            # 静态资源 (35+ HTML)
└── deploy.sh                                  # 部署脚本
```

---

## 核心功能

### 1. 用户系统

- ✅ 用户注册/登录
- ✅ JWT 令牌认证
- ✅ BCrypt 密码加密（工作因子 12）
- ✅ 用户信息管理
- ✅ 在线状态追踪

### 2. 匹配系统

- ✅ ELO 积分匹配
- ✅ 无锁并发队列
- ✅ 匹配超时机制
- ✅ 休闲/竞技模式
- ✅ 分布式匹配队列（Redis）

### 3. 游戏功能

| 功能 | 说明 |
|------|------|
| 🎯 匹配对战 | 根据积分自动匹配对手 |
| 🏠 房间系统 | 创建/加入私人房间 |
| 🤖 人机对战 | 简单/中等/困难 AI |
| 👀 观战模式 | 实时观看对局 |
| 📜 对局回放 | 完整复盘历史对局 |
| 🧩 残局挑战 | 三级难度残局闯关 |

### 4. 社交功能

| 功能 | 说明 |
|------|------|
| 💬 公共聊天 | 所有在线玩家可见 |
| 📮 私聊消息 | 好友一对一聊天 |
| 👥 好友系统 | 添加/删除/管理好友 |
| 📨 游戏邀请 | 邀请好友对战 |
| ⭐ 收藏功能 | 收藏精彩对局 |

### 5. 积分系统

```
初始积分: 1200 分

ELO 算法参数:
- K 值: 32
- 积分变化范围: -32 ~ +32

等级划分:
1级 (入门):   0 - 1399
2级 (初级):   1400 - 1599
3级 (中级):   1600 - 1799
4级 (高级):   1800 - 1999
5级 (大师):   2000 - 2199
6级 (宗师):   2200+
```

### 6. 数据存储

| 数据类型 | 存储位置 | 说明 |
|---------|---------|------|
| 用户信息 | user 表 | 账号、昵称、积分等 |
| 对局记录 | game_record 表 | 完整对局数据 |
| 用户统计 | user_stats 表 | 胜率、总局数等 |
| 好友关系 | friend 表 | 好友列表、申请状态 |
| 聊天记录 | chat_message 表 | 公私聊历史 |
| 残局记录 | puzzle_record 表 | 残局通关记录 |
| 游戏收藏 | game_favorite 表 | 收藏的对局 |

---

## API 文档

### REST API

```
GET  /api/health           # 健康检查
POST /api/auth/login       # 用户登录
POST /api/auth/register    # 用户注册
GET  /api/user             # 获取用户信息
GET  /api/leaderboard      # 获取排行榜
GET  /api/records          # 对局记录
GET  /api/friends          # 好友列表
POST /api/friends/request  # 发送好友请求
```

### WebSocket 消息

```javascript
// 连接
const ws = new WebSocket('ws://localhost:8083/ws');

// 登录
ws.send(JSON.stringify({
  type: 'login',
  data: { token: 'jwt_token' }
}));

// 加入匹配
ws.send(JSON.stringify({
  type: 'match_join',
  data: { mode: 'ranked' }
}));
```

详细 API 文档请查看 [docs/API.md](docs/API.md)

---

## 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| server.port | 服务端口 | 8083 |
| netty.boss-threads | Boss 线程数 | 1 |
| netty.worker-threads | Worker 线程数 | 4 |
| jwt.expiration | JWT 过期时间(秒) | 604800 (7天) |
| match.rating-diff | 匹配积分差 | 100 |
| match.max-queue-time | 匹配超时(秒) | 300 |
| game.move-timeout | 落子超时(秒) | 300 |
| game.reconnect-window | 断线重连窗口(秒) | 600 |

---

## 部署指南

### Docker 部署（推荐）

```bash
# 构建镜像
docker build -t gobang-server .

# 运行容器
docker run -d \
  -p 8083:8083 \
  -e DB_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  --name gobang \
  gobang-server
```

### ECS 部署

详细步骤请查看 [docs/ECS_DEPLOYMENT_GUIDE.md](docs/ECS_DEPLOYMENT_GUIDE.md)

---

## 性能指标

| 指标 | 目标值 | 实测值 |
|------|--------|--------|
| 并发连接 | 1000+ | 100 ✅ |
| 消息延迟 | <50ms | 待测 |
| CPU 占用 | <30% | 待测 |
| 内存占用 | <500MB | 待测 |
| 连接稳定性 | 99.9% | 100% ✅ |

详细报告请查看 [docs/PERFORMANCE_TEST_REPORT.md](docs/PERFORMANCE_TEST_REPORT.md)

---

## 开发指南

### 添加新功能

1. 在 `core/netty/api/` 创建 API Handler
2. 在 `service/` 创建业务逻辑
3. 在 `mapper/` 添加数据访问
4. 在 `static/` 添加前端页面

### 代码规范

- Java: 遵循阿里巴巴 Java 开发手册
- JavaScript: 遵循 Airbnb 风格指南
- 注释: 关键逻辑必须添加注释

详细指南请查看 [CONTRIBUTING.md](CONTRIBUTING.md)

---

## 故障排查

| 问题 | 解决方案 |
|------|----------|
| 端口被占用 | 修改 application.yml 中的端口 |
| 数据库连接失败 | 检查 MySQL 服务和配置 |
| Redis 连接失败 | 检查 Redis 服务和配置 |
| 编译失败 | 检查 JDK 版本（需要 17+） |

详细排查请查看 [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)

---

## 安全最佳实践

### 生产环境检查清单

- [ ] 使用环境变量设置 JWT_SECRET（至少 256 位）
- [ ] 修改数据库默认密码
- [ ] 设置 Redis 密码
- [ ] 使用 HTTPS/WSS 连接
- [ ] 配置防火墙规则
- [ ] 启用请求限流
- [ ] 定期备份数据库
- [ ] 配置日志轮转

---

## 文档导航

| 文档 | 说明 |
|------|------|
| [API.md](docs/API.md) | 完整的 API 接口文档 |
| [USER_GUIDE.md](docs/USER_GUIDE.md) | 面向玩家的使用手册 |
| [TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | 常见问题解决方案 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 开发者贡献指南 |
| [CHANGELOG.md](CHANGELOG.md) | 版本更新记录 |

---

## 路线图

### v1.3.0 (计划中)
- [ ] 自定义棋盘大小
- [ ] 更多残局关卡
- [ ] 移动端适配
- [ ] 主题系统

### v1.4.0 (规划中)
- [ ] 锦标赛模式
- [ ] 战绩分析
- [ ] 成就系统
- [ ] 公会功能

---

## 贡献者

感谢所有贡献者！

<a href="https://github.com/your-username/gobang-server/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=your-username/gobang-server" />
</a>

---

## 许可证

[MIT License](LICENSE)

---

## 联系方式

- 📧 Email: support@example.com
- 🐛 Issues: [GitHub Issues](https://github.com/your-username/gobang-server/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/your-username/gobang-server/discussions)

---

<div align="center">

**如果觉得这个项目不错，请给个 ⭐️ Star 支持一下！**

Made with ❤️ by [Your Name]

</div>
