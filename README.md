# 五子棋在线对战服务器

基于 WebSocket 的在线双人对战五子棋后端项目。

## 技术栈

- **网络层**: Netty 4.1.104.Final (WebSocket)
- **序列化**: Protobuf 3.25.1
- **存储**: MySQL 8.0 + Redis 7.0
- **认证**: JWT (jjwt 0.12.3)
- **ORM**: MyBatis 3.5.13
- **构建工具**: Maven
- **Java版本**: JDK 17+

## 项目结构

```
gobang-server/
├── pom.xml                                    # Maven配置
├── src/main/
│   ├── java/com/gobang/
│   │   ├── GobangServer.java                  # 主启动类
│   │   ├── core/                              # 核心逻辑
│   │   │   ├── netty/                         # Netty网络层
│   │   │   │   ├── NettyServer.java
│   │   │   │   ├── WebSocketServerInitializer.java
│   │   │   │   ├── WebSocketHandler.java
│   │   │   │   └── ChannelManager.java
│   │   │   ├── protocol/                      # 协议处理
│   │   │   │   ├── MessageHandler.java
│   │   │   │   ├── PacketCodec.java
│   │   │   │   └── MessageType.java
│   │   │   ├── match/                         # 匹配系统
│   │   │   │   ├── MatchMaker.java
│   │   │   │   └── Player.java
│   │   │   ├── room/                          # 房间管理
│   │   │   │   ├── RoomManager.java
│   │   │   │   └── GameRoom.java
│   │   │   ├── game/                          # 游戏逻辑
│   │   │   │   ├── Board.java
│   │   │   │   ├── GameState.java
│   │   │   │   └── WinChecker.java
│   │   │   ├── social/                        # 社交功能
│   │   │   │   ├── ObserverManager.java
│   │   │   │   ├── ChatManager.java
│   │   │   │   └── FriendManager.java
│   │   │   └── rating/                        # 积分系统
│   │   │       └── ELOCalculator.java
│   │   ├── service/                           # 业务服务层
│   │   │   ├── AuthService.java
│   │   │   ├── UserService.java
│   │   │   ├── GameService.java
│   │   │   ├── RecordService.java
│   │   │   ├── FriendService.java
│   │   │   └── ChatService.java
│   │   ├── model/                             # 数据模型
│   │   │   ├── entity/                        # 实体类
│   │   │   └── dto/                           # 数据传输对象
│   │   ├── mapper/                            # MyBatis Mapper
│   │   │   ├── UserMapper.java
│   │   │   ├── GameRecordMapper.java
│   │   │   ├── UserStatsMapper.java
│   │   │   ├── FriendMapper.java
│   │   │   └── ChatMessageMapper.java
│   │   ├── config/                            # 配置类
│   │   │   ├── NettyConfig.java
│   │   │   ├── RedisConfig.java
│   │   │   ├── DatabaseConfig.java
│   │   │   └── AppConfig.java
│   │   └── util/                              # 工具类
│   │       ├── JwtUtil.java
│   │       ├── RedisUtil.java
│   │       └── IdGenerator.java
│   ├── proto/
│   │   └── gobang.proto                       # Protobuf协议定义
│   └── resources/
│       ├── application.yml                    # 应用配置
│       ├── schema.sql                         # 数据库表结构
│       └── logback.xml                        # 日志配置
```

## 快速开始

### 1. 环境准备

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 7.0+

### 2. 数据库初始化

```bash
# 创建数据库
mysql -u root -p < src/main/resources/schema.sql
```

### 3. 配置修改

编辑 `src/main/resources/application.yml`，修改数据库和Redis连接信息：

```yaml
database:
  url: jdbc:mysql://localhost:3306/gobang?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
  username: your_username
  password: your_password

redis:
  host: localhost
  port: 6379
  password: your_redis_password
```

#### JWT 密钥配置（生产环境必需）

**⚠️ 安全警告**: 生产环境必须使用环境变量设置 JWT 密钥！

**方式一：使用环境变量（推荐）**
```bash
# Linux/Mac
export JWT_SECRET="$(openssl rand -base64 32)"

# Windows (CMD)
set JWT_SECRET=your-256-bit-secret-key

# Windows (PowerShell)
$env:JWT_SECRET="your-256-bit-secret-key"
```

**方式二：使用 .env 文件**
```bash
# 复制示例文件
cp .env.example .env

# 编辑 .env 文件，设置你的密钥
# JWT_SECRET=your-256-bit-secret-key
```

**生成安全的 JWT 密钥:**
```bash
# 方法一：使用 OpenSSL
openssl rand -base64 32

# 方法二：使用 Python
python -c "import secrets; print(secrets.token_urlsafe(32))"
```

### 4. 编译运行

```bash
# 编译项目（会自动生成Protobuf类）
mvn clean compile

# 运行服务器
mvn exec:java -Dexec.mainClass="com.gobang.GobangServer"
```

### 5. 测试连接

WebSocket服务端点: `ws://localhost:9090/ws`

## 核心功能

### 1. 用户认证
- 用户注册/登录
- JWT令牌验证
- 密码 BCrypt 加密（工作因子 12）
- 安全随机数生成器（SecureRandom）
- **数据库持久化用户信息**

### 2. 匹配系统
- 基于ELO积分的匹配
- 无锁并发队列
- 匹配超时机制
- 休闲模式/竞技模式

### 3. 游戏逻辑
- 15x15标准棋盘
- 四向扫描胜负判定
- 落子验证和状态管理
- **自动保存对局记录到数据库**

### 4. 积分系统
- ELO积分算法
- 等级和经验值
- 统计数据追踪
- **积分变化自动保存**

### 5. 数据存储
- **对局记录自动保存**
- **用户统计实时更新**
- **完整的历史数据查询**
- **排行榜数据持久化**

### 6. 社交功能
- 实时聊天
- 好友系统
- 观战模式

## 📊 数据库存储功能

### 自动保存的数据

游戏结束时，系统会自动保存以下数据到MySQL数据库：

| 数据类型 | 存储位置 | 说明 |
|---------|---------|------|
| 用户信息 | `user` 表 | 用户名、昵称、积分、等级等 |
| 对局记录 | `game_record` 表 | 完整的对局数据（棋盘、落子、积分变化） |
| 用户统计 | `user_stats` 表 | 胜率、连胜、总局数等统计数据 |
| 好友关系 | `friend` 表 | 好友列表和申请状态 |
| 聊天记录 | `chat_message` 表 | 公私聊历史记录 |

### 积分系统 (ELO)

- 使用标准ELO算法计算积分变化
- K值 = 32（标准值）
- 积分变化范围：-32 ~ +32
- 经验值：胜利+10 | 失败+2 | 平局+5

### 数据查询示例

```java
// 获取用户对局历史
List<GameRecord> history = recordService.getUserGameHistory(userId, 20);

// 获取排行榜
List<User> leaderboard = userService.getLeaderboard(100);

// 获取用户统计
UserStats stats = userService.getUserStats(userId);
double winRate = stats.getWinRate();
```

📖 **详细的数据库使用说明请参考 [DATABASE_STORAGE_GUIDE.md](DATABASE_STORAGE_GUIDE.md)**

## API协议

所有消息使用Protobuf编码，格式如下：

```protobuf
message Packet {
  MessageType type = 1;
  int64 sequence_id = 2;
  int64 timestamp = 3;
  bytes body = 4;
}
```

详细消息定义见 `src/main/proto/gobang.proto`。

## 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| server.port | 服务端口 | 9090 |
| netty.worker-threads | 工作线程数 | 4 |
| game.room-expire-time | 房间过期时间(秒) | 600 |
| match.rating-diff | 匹配积分差 | 100 |
| jwt.expiration | JWT过期时间(秒) | 604800 |

## 常见问题

### 编译失败

确保安装了JDK 17和Maven 3.6+，Protobuf编译插件会自动生成代码。

### 数据库连接失败

检查MySQL服务是否运行，以及配置文件中的连接信息是否正确。

### Redis连接失败

检查Redis服务是否运行，Windows用户可以从官网下载Redis for Windows。

## 安全最佳实践

### 生产环境部署检查清单

- [ ] 使用环境变量设置 `JWT_SECRET`（至少256位）
- [ ] 修改数据库默认密码
- [ ] 设置 Redis 密码
- [ ] 使用 HTTPS/WSS 连接
- [ ] 配置防火墙规则
- [ ] 启用请求限流
- [ ] 定期备份数据库

### 环境变量参考

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `JWT_SECRET` | JWT签名密钥（必需） | `base64编码的32字节随机数` |
| `DB_URL` | 数据库连接URL（可选） | `jdbc:mysql://localhost:3306/gobang` |
| `DB_USERNAME` | 数据库用户名（可选） | `root` |
| `DB_PASSWORD` | 数据库密码（可选） | `your-password` |
| `REDIS_HOST` | Redis主机（可选） | `localhost` |
| `REDIS_PORT` | Redis端口（可选） | `6379` |
| `REDIS_PASSWORD` | Redis密码（可选） | `your-password` |

## 许可证

MIT License
