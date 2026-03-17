# 五子棋在线对战服务器 - 快速开始指南

## 项目文件统计

- **总文件数**: 54个
- **Java类**: 48个
- **配置文件**: 6个

## 项目结构

```
gobang-server/
├── pom.xml                                    # Maven配置
├── README.md                                  # 项目说明
├── QUICKSTART.md                              # 本文件
├── src/main/
│   ├── java/com/gobang/
│   │   ├── GobangServer.java                  # [主启动类]
│   │   ├── config/                            # [4个配置类]
│   │   │   ├── AppConfig.java
│   │   │   ├── DatabaseConfig.java
│   │   │   ├── NettyConfig.java
│   │   │   └── RedisConfig.java
│   │   ├── core/                              # [核心逻辑 18个类]
│   │   │   ├── game/                          # 游戏逻辑 (3)
│   │   │   │   ├── Board.java                 # 15x15棋盘
│   │   │   │   ├── GameState.java             # 游戏状态
│   │   │   │   └── WinChecker.java            # 四向胜负判定
│   │   │   ├── handler/                       # [6个消息处理器]
│   │   │   │   ├── AuthHandler.java           # 认证处理
│   │   │   │   ├── MatchHandler.java          # 匹配处理
│   │   │   │   ├── GameHandler.java           # 游戏处理
│   │   │   │   ├── ChatHandler.java           # 聊天处理
│   │   │   │   ├── ObserverHandler.java       # 观战处理
│   │   │   │   └── FriendHandler.java         # 好友处理
│   │   │   ├── match/                         # 匹配系统 (2)
│   │   │   │   ├── MatchMaker.java            # 无锁匹配队列
│   │   │   │   └── Player.java                # 玩家信息
│   │   │   ├── netty/                         # 网络层 (4)
│   │   │   │   ├── NettyServer.java           # Netty服务器
│   │   │   │   ├── WebSocketHandler.java      # 消息路由
│   │   │   │   ├── WebSocketServerInitializer.java
│   │   │   │   └── ChannelManager.java        # 连接管理
│   │   │   ├── protocol/                      # 协议层 (3)
│   │   │   │   ├── MessageType.java           # 消息类型枚举
│   │   │   │   ├── MessageHandler.java        # 处理器接口
│   │   │   │   └── PacketCodec.java           # 编解码器
│   │   │   ├── rating/                        # 积分系统 (1)
│   │   │   │   └── ELOCalculator.java         # ELO算法
│   │   │   ├── room/                          # 房间管理 (2)
│   │   │   │   ├── RoomManager.java           # 房间生命周期
│   │   │   │   └── GameRoom.java              # 游戏房间
│   │   │   └── social/                        # 社交功能 (3)
│   │   │       ├── ObserverManager.java       # 观战管理
│   │   │       ├── ChatManager.java           # 聊天管理
│   │   │       └── FriendManager.java         # 好友管理
│   │   ├── mapper/                            # [5个数据访问层]
│   │   │   ├── UserMapper.java
│   │   │   ├── GameRecordMapper.java
│   │   │   ├── UserStatsMapper.java
│   │   │   ├── FriendMapper.java
│   │   │   └── ChatMessageMapper.java
│   │   ├── model/entity/                      # [5个实体类]
│   │   │   ├── User.java
│   │   │   ├── GameRecord.java
│   │   │   ├── UserStats.java
│   │   │   ├── Friend.java
│   │   │   └── ChatMessage.java
│   │   ├── service/                           # [6个业务服务]
│   │   │   ├── AuthService.java               # 认证服务
│   │   │   ├── UserService.java               # 用户服务
│   │   │   ├── GameService.java               # 游戏服务
│   │   │   ├── ChatService.java               # 聊天服务
│   │   │   ├── FriendService.java             # 好友服务
│   │   │   └── RecordService.java             # 记录服务
│   │   └── util/                              # [3个工具类]
│   │       ├── JwtUtil.java                   # JWT工具
│   │       ├── RedisUtil.java                 # Redis工具
│   │       └── IdGenerator.java               # 雪花ID生成
│   ├── proto/
│   │   └── gobang.proto                       # Protobuf协议定义
│   └── resources/
│       ├── application.yml                    # 应用配置
│       ├── schema.sql                         # 数据库表结构
│       └── logback.xml                        # 日志配置
```

## 环境要求

| 组件 | 版本要求 | 用途 |
|------|----------|------|
| JDK | 17+ | Java运行环境 |
| Maven | 3.6+ | 构建工具 |
| MySQL | 8.0+ | 数据存储 |
| Redis | 7.0+ | 缓存/会话 |

## 快速启动

### 1. 安装依赖

**Windows:**
- 下载JDK 17: https://adoptium.net/
- 下载Maven: https://maven.apache.org/download.cgi
- 下载MySQL: https://dev.mysql.com/downloads/mysql/
- 下载Redis: https://github.com/microsoftarchive/redis/releases

**Linux:**
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk maven mysql-server redis-server

# CentOS/RHEL
sudo yum install java-17-openjdk maven mysql-server redis
```

### 2. 初始化数据库

```bash
# 创建数据库
mysql -u root -p < src/main/resources/schema.sql
```

### 3. 修改配置

编辑 `src/main/resources/application.yml`:

```yaml
database:
  url: jdbc:mysql://localhost:3306/gobang?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
  username: root          # 改为你的MySQL用户名
  password: your_password  # 改为你的MySQL密码

redis:
  host: localhost
  port: 6379
  password: ""            # 如果有密码则填写
```

### 4. 编译运行

```bash
# 编译项目（会自动生成Protobuf类）
mvn clean compile

# 运行服务器
mvn exec:java -Dexec.mainClass="com.gobang.GobangServer"
```

### 5. 验证服务

服务器启动后，你应该看到：
```
========================================
  五子棋在线对战服务器启动中...
========================================
...
========================================
  服务器启动完成！
  WebSocket端点: ws://0.0.0.0:9090/ws
========================================
```

## WebSocket连接测试

使用浏览器控制台或Postman测试连接：

```javascript
// 浏览器控制台
const ws = new WebSocket('ws://localhost:9090/ws');

ws.onopen = () => {
    console.log('已连接到服务器');
    // 发送登录消息（需要实现前端客户端）
};

ws.onmessage = (event) => {
    console.log('收到消息:', event.data);
};
```

## 核心功能说明

### 1. 用户认证
- 注册: 用户名、密码、昵称
- 登录: 返回JWT令牌
- 密码加密: SHA-256

### 2. 匹配系统
- 基于ELO积分匹配
- 积分差默认±100
- 匹配超时: 5分钟

### 3. 游戏逻辑
- 15x15标准棋盘
- 黑先白后
- 五子连珠获胜
- 支持认输

### 4. 积分系统
- 使用ELO算法
- 胜利+积分，失败-积分
- 等级= (积分-1200)/200 + 2

### 5. 社交功能
- 实时聊天（公屏/私聊）
- 好友系统（添加/接受/删除）
- 观战模式

## 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| server.port | 9090 | WebSocket端口 |
| netty.worker-threads | 4 | 工作线程数 |
| jwt.expiration | 604800 | JWT过期时间(秒) |
| match.rating-diff | 100 | 匹配积分差 |
| match.max-queue-time | 300 | 匹配超时(秒) |
| game.reconnect-window | 600 | 断线重连窗口(秒) |

## 故障排查

### 编译失败
```bash
# 清理并重新编译
mvn clean
mvn compile
```

### 数据库连接失败
- 检查MySQL服务是否运行
- 验证配置文件中的用户名密码
- 确保数据库已创建

### Redis连接失败
- 检查Redis服务是否运行
- Windows: 运行 `redis-server.exe`
- Linux: `sudo systemctl start redis`

### 端口被占用
```bash
# Windows
netstat -ano | findstr 9090

# Linux
lsof -i:9090
```

## 下一步

1. **开发客户端**: 实现Web/移动端客户端
2. **部署上线**: 使用Docker容器化部署
3. **压力测试**: 测试并发连接数
4. **功能扩展**: 添加排行榜、成就系统等

## 技术支持

- 查看日志: `./logs/gobang-server.log`
- 调试模式: 修改 `application.yml` 中 `logging.level: DEBUG`
