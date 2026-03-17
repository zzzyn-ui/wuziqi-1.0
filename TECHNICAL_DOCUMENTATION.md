# 五子棋在线对战平台 - 技术文档

## 目录
1. [项目概述](#项目概述)
2. [技术方案](#技术方案)
3. [架构设计](#架构设计)
4. [框架选型](#框架选型)
5. [游戏规则定义](#游戏规则定义)
6. [规则配置](#规则配置)
7. [系统设置](#系统设置)
8. [API定义](#api定义)
9. [前后端关联图](#前后端关联图)
10. [数据库设计](#数据库设计)
11. [消息协议](#消息协议)
12. [部署方案](#部署方案)

---

## 项目概述

### 项目简介
五子棋在线对战平台是一个基于WebSocket的实时对战游戏系统，支持玩家匹配、房间对战、观战、聊天、好友等功能。

### 核心功能
- 用户系统：注册、登录、个人资料、等级系统
- 对战系统：快速匹配、房间对战、人机对战
- 观战系统：实时观战、观战列表
- 社交系统：好友管理、私聊公屏
- 排行榜系统：ELO积分排名、历史记录
- 回放系统：对局记录回放

### 项目特点
- 基于Netty的高性能WebSocket服务器
- Protocol Buffers二进制协议
- ELO积分匹配算法
- 完整的房间管理系统
- 支持断线重连

---

## 技术方案

### 后端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| Netty | 4.1.104 | WebSocket服务器框架 |
| MyBatis | 3.5.13 | ORM持久化框架 |
| MySQL | 8.0.33 | 关系型数据库 |
| Redis | - | 缓存和会话管理 |
| Protocol Buffers | 3.25.1 | 序列化协议 |
| JWT | 0.12.3 | 身份认证 |
| HikariCP | 5.0.1 | 数据库连接池 |
| SLF4J + Logback | 2.0.9 / 1.4.11 | 日志框架 |
| Jackson | 2.16.0 | JSON处理 |
| Gson | 2.10.1 | JSON处理 |
| BCrypt | 0.10.2 | 密码加密 |

### 前端技术栈

| 技术 | 说明 |
|------|------|
| HTML5 | 页面结构 |
| CSS3 | 样式设计（内联） |
| JavaScript (ES6+) | 交互逻辑（内联） |
| WebSocket API | 实时通信 |
| LocalStorage | 本地存储 |

### 开发工具

- Maven 3.x - 项目构建
- Git - 版本控制
- Docker - 容器化部署
- Nginx - 反向代理

---

## 架构设计

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         客户端层                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │ 登录页面 │  │ 匹配大厅 │  │ 游戏对局 │  │ 房间系统 │      │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘      │
└─────────────────────────────────────────────────────────────────┘
                              │
                         WebSocket
                              │
┌─────────────────────────────────────────────────────────────────┐
│                    Netty WebSocket服务器                        │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   HTTP请求处理器                          │  │
│  │  静态文件服务 /api端点 WebSocket升级                      │  │
│  └──────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   消息分发层                              │  │
│  │  JSON消息处理器 Protobuf消息处理器                        │  │
│  └──────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   业务处理层                              │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │  │
│  │  │认证处理器│ │匹配处理器│ │游戏处理器│ │房间处理器│  │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │  │
│  │  │聊天处理器│ │好友处理器│ │观战处理器│ │回放处理器│  │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   核心服务层                              │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │  │
│  │  │用户服务  │ │匹配服务  │ │游戏服务  │ │房间服务  │  │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐               │  │
│  │  │聊天服务  │ │好友服务  │ │记录服务  │               │  │
│  │  └──────────┘ └──────────┘ └──────────┘               │  │
│  └──────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   游戏引擎层                              │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │  │
│  │  │棋盘管理  │ │胜负判定  │ │ELO积分   │ │匹配算法  │  │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
    ┌────▼────┐         ┌────▼────┐         ┌────▼────┐
    │  MySQL  │         │  Redis  │         │房间管理器│
    │ 数据库  │         │  缓存   │         │(内存)    │
    └─────────┘         └─────────┘         └─────────┘
```

### 分层架构说明

#### 1. 客户端层
- 纯前端HTML/CSS/JavaScript实现
- 通过WebSocket与服务器通信
- 使用LocalStorage存储用户信息

#### 2. Netty服务器层
- HTTP请求处理：静态文件服务、API端点
- WebSocket升级：处理HTTP到WebSocket的协议升级
- 消息分发：JSON和Protobuf双协议支持

#### 3. 业务处理层
- 8个功能处理器：认证、匹配、游戏、房间、聊天、好友、观战、回放
- 每个处理器负责特定业务逻辑

#### 4. 核心服务层
- 7个核心服务：用户、匹配、游戏、房间、聊天、好友、记录


- 提供业务逻辑抽象和数据访问

#### 5. 游戏引擎层
- 棋盘管理：15x15棋盘状态管理
- 胜负判定：五子连珠检测算法
- ELO积分：积分变化计算
- 匹配算法：基于积分的玩家匹配

#### 6. 数据持久层
- MySQL：关系型数据库存储
- Redis：缓存和会话管理
- 房间管理器：内存中的房间状态管理

---

## 框架选型

### 为什么选择Netty？

| 优势 | 说明 |
|------|------|
| 高性能 | 基于NIO的异步事件驱动架构 |
| 高并发 | 单机支持数千并发连接 |
| 低延迟 | 适合实时游戏场景 |
| 成熟稳定 | 广泛应用于大型项目 |
| 灵活性 | 支持多种协议定制 |

### 为什么选择Protocol Buffers？

| 优势 | 说明 |
|------|------|
| 高效 | 二进制序列化，体积小 |
| 快速 | 序列化/反序列化速度快 |
| 跨语言 | 支持多语言互通 |
| 向后兼容 | 协议升级友好 |
| 类型安全 | 强类型定义 |

### 为什么选择MyBatis？

| 优势 | 说明 |
|------|------|
| 灵活 | SQL可控，适合复杂查询 |
| 轻量 | 相比JPA更简洁 |
| 性能 | 优秀的SQL优化能力 |
| 学习成本低 | SQL技能可复用 |

---

## 游戏规则定义

### 基本规则

| 规则项 | 配置值 | 说明 |
|--------|--------|------|
| 棋盘大小 | 15×15 | 标准五子棋棋盘 |
| 胜利条件 | 5连子 | 横、竖、斜任意方向连成5子 |
| 先手 | 黑棋 | 黑棋先行 |
| 落子时间 | 5分钟 | 单步落子超时时间 |
| 超时判负 | 是 | 超时者直接判负 |
| 禁手规则 | 无 | 暂未实现三三禁手等 |

### 游戏状态机

```
┌─────────┐
│ WAITING │ 等待开始
└────┬────┘
     │ 开始游戏
     ▼
┌─────────┐
│ PLAYING │ 对局中
└────┬────┘
     │ 胜利/平局/认输/超时
     ▼
┌─────────┐
│FINISHED│ 已结束
└─────────┘
```

### 胜负判定

```java
// 四向扫描算法
public static boolean checkWin(Board board, int lastX, int lastY, int color) {
    return checkDirection(board, lastX, lastY, color, 1, 0)   // 水平
        || checkDirection(board, lastX, lastY, color, 0, 1)   // 垂直
        || checkDirection(board, lastX, lastY, color, 1, 1)   // 主对角线
        || checkDirection(board, lastX, lastY, color, 1, -1); // 副对角线
}
```

### 游戏结束原因

| 原因代码 | 说明 | 处理 |
|----------|------|------|
| 0 | 胜利 | 五子连珠 |
| 1 | 失败 | 对手获胜 |
| 2 | 平局 | 棋盘填满 |
| 3 | 认输 | 玩家主动认输 |
| 4 | 超时 | 落子超时 |

---

## 规则配置

### application.yml 配置项

```yaml
# 游戏规则配置
game:
  board-size: 15          # 棋盘大小
  win-count: 5            # 胜利连子数
  match-timeout: 300      # 匹配超时(秒)
  room-expire-time: 600   # 房间过期时间(秒)
  reconnect-window: 600   # 重连窗口期(秒)

# 匹配算法配置
match:
  rating-diff: 200              # 初始积分差异范围
  check-interval: 50            # 匹配检查间隔(毫秒)
  max-queue-time: 300           # 最大等待时间(秒)
  enable-time-expansion: true   # 启用时间膨胀
  expansion-rate: 20            # 每秒扩大积分范围
  max-expansion-rating: 500     # 最大扩大积分范围
  enable-segmented-match: true  # 启用分段匹配
  segment-size: 200             # 分段大小
  test-mode: false              # 测试模式开关

# Netty配置
netty:
  boss-threads: 1               # Boss线程数
  worker-threads: 4             # Worker线程数
  max-connections: 1000         # 最大连接数
  read-timeout: 300             # 读超时(秒)
  write-timeout: 60             # 写超时(秒)
  heartbeat-interval: 30        # 心跳间隔(秒)
```

---

## 系统设置

### 服务器配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| server.port | 9091 | HTTP服务端口 |
| server.host | 0.0.0.0 | 监听地址 |

### 数据库配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| database.url | jdbc:mysql://localhost:3306/gobang | 数据库连接 |
| database.username | root | 用户名 |
| database.password | password | 密码 |
| database.pool-size | 10 | 连接池大小 |

### Redis配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| redis.host | localhost | Redis主机 |
| redis.port | 6379 | Redis端口 |
| redis.database | 0 | 数据库编号 |

### JWT配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| jwt.secret | - | JWT密钥（环境变量） |
| jwt.expiration | 604800 | 过期时间（7天） |
| jwt.issuer | gobang-server | 发行者 |

---

## API定义

### HTTP API端点

#### 1. 静态文件服务

| 端点 | 方法 | 说明 |
|------|------|------|
| / | GET | 首页 |
| /index.html | GET | 登录页 |
| /match.html | GET | 匹配大厅 |
| /game.html | GET | 游戏页面 |
| /create-room.html | GET | 创建房间 |
| /join.html | GET | 加入房间 |
| /replay.html | GET | 回放页面 |
| /rank.html | GET | 排行榜 |
| /pve.html | GET | 人机对战 |

#### 2. 数据API

| 端点 | 方法 | 返回值 | 说明 |
|------|------|--------|------|
| /api/rooms/playing | GET | {count: int, rooms: []} | 正在进行的对局数 |

#### 3. WebSocket端点

| 端点 | 协议 | 说明 |
|------|------|------|
| /ws | WebSocket | 主WebSocket连接 |

### WebSocket消息协议

详见[消息协议](#消息协议)章节

---

## 前后端关联图

### 通信流程

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端页面                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │index.html│  │match.html│  │game.html │  │room.html │      │
│  └─────┬────┘  └─────┬────┘  └─────┬────┘  └─────┬────┘      │
│        │              │              │              │          │
│        │              │              │              │          │
│  ┌─────▼──────────────▼──────────────▼──────────────▼────┐    │
│  │              WebSocket通信 (JSON格式)                    │    │
│  │  发送: {type, sequence_id, timestamp, body?}           │    │
│  │  接收: {type, sequence_id, timestamp, body?}           │    │
│  └──────────────────────┬─────────────────────────────────┘    │
└─────────────────────────┼─────────────────────────────────────┘
                          │
                    WebSocket
                          │
┌─────────────────────────▼─────────────────────────────────────┐
│                      后端消息处理器                            │
│  ┌────────────────────────────────────────────────────────┐   │
│  │                   JsonMessageHandler                    │   │
│  │  根据消息类型分发到对应的处理器:                        │   │
│  │  - type 1/2/3   → AuthHandler (认证)                   │   │
│  │  - type 10-14  → MatchHandler (匹配)                   │   │
│  │  - type 15-17  → RoomHandler (房间)                    │   │
│  │  - type 20-28  → GameHandler (游戏)                    │   │
│  │  - type 30-33  → ObserverHandler (观战)                │   │
│  │  - type 40-42  → ChatHandler (聊天)                    │   │
│  │  - type 50-56  → FriendHandler (好友)                  │   │
│  │  - type 60-71  → 相关服务处理器                         │   │
│  └────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 前端页面与后端处理器映射

| 前端页面 | 发送消息类型 | 接收消息类型 | 后端处理器 |
|----------|-------------|-------------|------------|
| login.html | AUTH_REGISTER(2) | AUTH_RESPONSE(3) | AuthHandler |
| login.html | AUTH_LOGIN(1) | AUTH_RESPONSE(3) | AuthHandler |
| match.html | MATCH_START(10) | MATCH_SUCCESS(12) | MatchHandler |
| match.html | - | GAME_STATE(22) | GameHandler |
| create-room.html | CREATE_ROOM(15) | CREATE_ROOM响应 | RoomHandler |
| create-room.html | - | ROOM_JOINED(17) | RoomHandler |
| create-room.html | - | GAME_STATE(22) | GameHandler |
| join.html | JOIN_ROOM(16) | JOIN_ROOM响应 | RoomHandler |
| join.html | - | GAME_STATE(22) | GameHandler |
| game.html | GAME_MOVE(20) | GAME_MOVE_RESULT(21) | GameHandler |
| game.html | GAME_RESIGN(24) | GAME_OVER(23) | GameHandler |
| game.html | GAME_UNDO_REQUEST(26) | GAME_UNDO_RESPONSE(27) | GameHandler |
| replay.html | GAME_REPLAY_REQUEST(70) | GAME_REPLAY_DATA(71) | ReplayHandler |

### 消息格式示例

#### 认证请求
```javascript
// 前端发送
{
  "type": 1,              // AUTH_LOGIN
  "sequence_id": 1,
  "timestamp": 1234567890,
  "body": {
    "username": "player1",
    "password": "hashed_password"
  }
}

// 后端响应
{
  "type": 3,              // AUTH_RESPONSE
  "sequence_id": 1,
  "timestamp": 1234567891,
  "body": {
    "success": true,
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user_info": {
      "user_id": "123456",
      "username": "player1",
      "nickname": "棋手一号",
      "rating": 1200,
      "level": 1
    }
  }
}
```

#### 游戏落子
```javascript
// 前端发送
{
  "type": 20,             // GAME_MOVE
  "sequence_id": 5,
  "timestamp": 1234567900,
  "body": {
    "x": 7,
    "y": 7
  }
}

// 后端响应
{
  "type": 21,             // GAME_MOVE_RESULT
  "sequence_id": 5,
  "timestamp": 1234567901,
  "body": {
    "success": true,
    "state": {
      "board": [0,0,0,...,1,0,0,...],
      "current_player": 2,
      "move_count": 1
    }
  }
}
```

---

## 数据库设计

### ER图

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│    user     │         │ user_stats  │         │game_record  │
├─────────────┤         ├─────────────┤         ├─────────────┤
│ id (PK)     │────┐    │ user_id (PK)│    ┌────│ id (PK)     │
│ username    │    │    │ total_games │    │    │ room_id     │
│ password    │    │    │ wins        │    │    │ black_id    │
│ nickname    │    │    │ losses      │    │    │ white_id    │
│ email       │    │    │ draws       │    │    │ winner_id   │
│ avatar      │    │    │ max_rating  │    │    │ end_reason  │
│ rating      │    │    │ max_streak  │    │    │ moves       │
│ level       │    │    └─────────────┘    │    │ created_at  │
│ status      │    │           ▲          │    └─────────────┘
│ last_online │    │           │          │           ▲
│ created_at  │    │           │          │           │
└─────────────┘    │           │          │           │
       ▲           │           │          │           │
       │           │           └──────────┘           │
       │           │                                      │
┌─────────────┐   │           ┌─────────────┐           │
│   friend    │   │           │chat_message │           │
├─────────────┤   │           ├─────────────┤           │
│ id (PK)     │   │           │ id (PK)     │           │
│ user_id     │───┘           │ sender_id   │           │
│ friend_id   │───┐           │ receiver_id │           │
│ status      │   │           │ content     │           │
│ created_at  │   │           │ room_id     │           │
└─────────────┘   │           │ created_at  │           │
                  │           └─────────────┘           │
                  │                                      │
                  │           ┌─────────────┐           │
                  │           │observer_reco│           │
                  │           ├─────────────┤           │
                  └───────────│ id (PK)     │           │
                              │ room_id     │           │
                              │ user_id     │           │
                              │ join_time   │           │
                              └─────────────┘           │
                                                        │
                         用户与对局关系 ────────────────┘
```

### 数据表详解

#### 1. user - 用户表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 用户ID（主键） |
| username | VARCHAR(32) | 用户名（唯一） |
| password | VARCHAR(128) | BCrypt加密密码 |
| nickname | VARCHAR(32) | 昵称 |
| email | VARCHAR(64) | 邮箱 |
| avatar | VARCHAR(255) | 头像URL |
| rating | INT | ELO积分（默认1200） |
| level | INT | 等级（默认1） |
| exp | INT | 经验值 |
| status | TINYINT | 状态：0=离线,1=在线,2=游戏中,3=匹配中 |
| last_online | DATETIME | 最后在线时间 |
| created_at | DATETIME | 创建时间 |

**索引**：
- PRIMARY KEY (id)
- UNIQUE KEY uk_username (username)
- KEY idx_rating (rating)
- KEY idx_last_online (last_online)

#### 2. user_stats - 用户统计表

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | BIGINT | 用户ID（主键、外键） |
| total_games | INT | 总对局数 |
| wins | INT | 胜场 |
| losses | INT | 负场 |
| draws | INT | 平场 |
| max_rating | INT | 最高积分 |
| current_streak | INT | 当前连胜/连负 |
| max_streak | INT | 最高连胜 |
| total_moves | INT | 总落子数 |
| avg_moves_per_game | DECIMAL | 平均每局落子数 |
| fastest_win | INT | 最快获胜（秒） |

**外键**：
- FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE

#### 3. game_record - 对局记录表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 对局ID（主键） |
| room_id | VARCHAR(64) | 房间ID（唯一） |
| black_player_id | BIGINT | 黑方玩家ID |
| white_player_id | BIGINT | 白方玩家ID |
| winner_id | BIGINT | 获胜者ID |
| win_color | TINYINT | 获胜方颜色：1=黑,2=白 |
| end_reason | TINYINT | 结束原因：0=胜利,1=失败,2=平局,3=认输,4=超时 |
| move_count | INT | 总落子数 |
| duration | INT | 对局时长（秒） |
| black_rating_before | INT | 黑方变化前积分 |
| black_rating_after | INT | 黑方变化后积分 |
| black_rating_change | INT | 黑方积分变化 |
| white_rating_before | INT | 白方变化前积分 |
| white_rating_after | INT | 白方变化后积分 |
| white_rating_change | INT | 白方积分变化 |
| board_state | TEXT | 最终棋盘状态（压缩） |
| moves | TEXT | 所有落子记录（JSON数组） |
| created_at | DATETIME | 创建时间 |

**外键**：
- FOREIGN KEY (black_player_id) REFERENCES user(id)
- FOREIGN KEY (white_player_id) REFERENCES user(id)

**索引**：
- UNIQUE KEY uk_room_id (room_id)
- KEY idx_black_player (black_player_id)
- KEY idx_white_player (white_player_id)
- KEY idx_created_at (created_at)

#### 4. friend - 好友关系表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 关系ID（主键） |
| user_id | BIGINT | 用户ID |
| friend_id | BIGINT | 好友ID |
| status | TINYINT | 状态：0=待确认,1=已确认 |
| request_message | VARCHAR(255) | 申请消息 |
| created_at | DATETIME | 创建时间 |

**外键**：
- FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
- FOREIGN KEY (friend_id) REFERENCES user(id) ON DELETE CASCADE

**约束**：
- UNIQUE KEY uk_user_friend (user_id, friend_id)
- CHECK (user_id != friend_id) - 不能添加自己为好友

#### 5. chat_message - 聊天消息表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 消息ID（主键） |
| sender_id | BIGINT | 发送者ID |
| receiver_id | BIGINT | 接收者ID（NULL为公屏） |
| room_id | VARCHAR(64) | 房间ID（公屏聊天） |
| content | VARCHAR(500) | 消息内容 |
| message_type | TINYINT | 消息类型：0=普通,1=系统 |
| is_read | TINYINT | 是否已读：0=未读,1=已读 |
| created_at | DATETIME | 创建时间 |

**外键**：
- FOREIGN KEY (sender_id) REFERENCES user(id) ON DELETE CASCADE
- FOREIGN KEY (receiver_id) REFERENCES user(id) ON DELETE CASCADE

**索引**：
- KEY idx_sender (sender_id)
- KEY idx_receiver (receiver_id)
- KEY idx_room (room_id)
- KEY idx_created_at (created_at)

#### 6. observer_record - 观战记录表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 记录ID（主键） |
| room_id | VARCHAR(64) | 房间ID |
| user_id | BIGINT | 观战用户ID |
| join_time | DATETIME | 加入时间 |
| leave_time | DATETIME | 离开时间 |
| duration | INT | 观战时长（秒） |

**外键**：
- FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE

#### 7. rating_leaderboard - 排行榜视图

```sql
CREATE OR REPLACE VIEW rating_leaderboard AS
SELECT
  u.id, u.username, u.nickname, u.avatar,
  u.rating, u.level,
  s.total_games, s.wins,
  CASE WHEN s.total_games > 0
       THEN ROUND(s.wins * 100.0 / s.total_games, 2)
       ELSE 0
  END AS win_rate
FROM user u
LEFT JOIN user_stats s ON u.id = s.user_id
ORDER BY u.rating DESC
LIMIT 100;
```

### 数据访问层（Mapper）

| Mapper | 功能 | 方法 |
|--------|------|------|
| UserMapper | 用户操作 | findByUsername, insert, update, findById |
| UserStatsMapper | 统计操作 | findByUserId, insert, update |
| GameRecordMapper | 对局记录 | insert, findByRoomId, findByPlayerId |
| FriendMapper | 好友操作 | insert, delete, findByUserId |
| ChatMessageMapper | 聊天消息 | insert, findByRoomId, findUnread |

---

## 消息协议

### 消息类型枚举

#### 认证相关 (1-3)
| 类型值 | 名称 | 方向 | 说明 |
|--------|------|------|------|
| 1 | AUTH_LOGIN | C→S | 用户登录 |
| 2 | AUTH_REGISTER | C→S | 用户注册 |
| 3 | AUTH_RESPONSE | S→C | 认证响应 |

#### 匹配相关 (10-14)
| 类型值 | 名称 | 方向 | 说明 |
|--------|------|------|------|
| 10 | MATCH_START | C→S | 开始匹配 |
| 11 | MATCH_CANCEL | C→S | 取消匹配 |
| 12 | MATCH_SUCCESS | S→C | 匹配成功 |
| 13 | MATCH_FAILED | S→C | 匹配失败 |
| 14 | MATCH_TIMEOUT | S→C | 匹配超时 |

#### 房间相关 (15-17)
| 类型值 | 名称 | 方向 | 说明 |
|--------|------|------|------|
| 15 | CREATE_ROOM | C→S | 创建房间 |
| 16 | JOIN_ROOM | C→S | 加入房间 |
| 17 | ROOM_JOINED | S→C | 玩家加入通知 |

#### 游戏相关 (20-28)
| 类型值 | 名称 | 方向 | 说明 |
|--------|------|------|------|
| 20 | GAME_MOVE | C→S | 落子 |
| 21 | GAME_MOVE_RESULT | S→C | 落子结果 |
| 22 | GAME_STATE | S→C | 游戏状态 |
| 23 | GAME_OVER | S→C | 游戏结束 |
| 24 | GAME_RESIGN | C→S | 认输 |
| 25 | GAME_RECONNECT | C→S | 重连 |
| 26 | GAME_UNDO_REQUEST | C→S | 悔棋请求 |
| 27 | GAME_UNDO_RESPONSE | C→S | 悔棋响应 |
| 28 | GAME_UNDO_NOTIFY | S→C | 悔棋通知 |

#### 观战相关 (30-33)
| 类型值 | 名称 | 方向 | 说明 |
|--------|------|------|------|
| 30 | OBSERVER_LIST | S→C | 观战者列表 |
| 31 | OBSERVER_JOIN | C→S | 加入观战 |
| 32 | OBSERVER_LEAVE | C→S | 离开观战 |
| 33 | OBSERVER_COUNT | S→C | 观战人数 |

#### 聊天相关 (40-42)
| 类型值 | 名称 | 方向 | 说明 |
|--------|------|------|------|
| 40 | CHAT_SEND | C→S | 发送消息 |
| 41 | CHAT_RECEIVE | S→C | 接收消息 |
| 42 | CHAT_SYSTEM | S→C | 系统消息 |

#### 好友相关 (50-56)
| 类型值 | 名称 | 方向 | 说明 |
|--------|------|------|------|
| 50 | FRIEND_REQUEST | C→S | 好友请求 |
| 51 | FRIEND_ACCEPT | C→S | 接受好友 |
| 52 | FRIEND_REJECT | C→S | 拒绝好友 |
| 53 | FRIEND_REMOVE | C→S | 删除好友 |
| 54 | FRIEND_LIST | S→C | 好友列表 |
| 55 | FRIEND_ONLINE | S→C | 好友上线 |
| 56 | FRIEND_OFFLINE | S→C | 好友离线 |

#### 用户相关 (60-61)
| 类型值 | 名称 | 方向 | 说明 |
|--------|------|------|------|
| 60 | USER_INFO | C↔S | 用户信息 |
| 61 | USER_STATS | S→C | 用户统计 |

#### 回放相关 (70-71)
| 类型值 | 名称 | 方向 | 说明 |
|--------|------|------|------|
| 70 | GAME_REPLAY_REQUEST | C→S | 回放请求 |
| 71 | GAME_REPLAY_DATA | S→C | 回放数据 |

### 消息结构

#### 通用消息包
```protobuf
message Packet {
  MessageType type = 1;       // 消息类型
  int64 sequence_id = 2;      // 序列号
  int64 timestamp = 3;        // 时间戳
  bytes body = 4;             // 消息体
}
```

#### JSON格式
```json
{
  "type": 1,              // 消息类型
  "sequence_id": 1,       // 序列号
  "timestamp": 1234567890, // 时间戳
  "body": {               // 消息体（可选）
    // 具体消息内容
  }
}
```

### 核心消息详解

#### 1. 登录请求 (AUTH_LOGIN)
```json
{
  "type": 1,
  "sequence_id": 1,
  "timestamp": 1234567890,
  "body": {
    "username": "player1",
    "password": "hashed_password"
  }
}
```

#### 2. 匹配请求 (MATCH_START)
```json
{
  "type": 10,
  "sequence_id": 2,
  "timestamp": 1234567900,
  "body": {
    "mode": "ranked"     // "casual" 或 "ranked"
  }
}
```

#### 3. 匹配成功 (MATCH_SUCCESS)
```json
{
  "type": 12,
  "sequence_id": 0,
  "timestamp": 1234567910,
  "room_id": "ABC123",
  "my_color": 1,         // 1=黑, 2=白
  "is_first": true,
  "opponent": {
    "user_id": "789",
    "username": "opponent2",
    "nickname": "对手二号",
    "rating": 1250
  }
}
```

#### 4. 游戏落子 (GAME_MOVE)
```json
{
  "type": 20,
  "sequence_id": 3,
  "timestamp": 1234568000,
  "body": {
    "x": 7,
    "y": 7,
    "color": 1
  }
}
```

#### 5. 游戏状态 (GAME_STATE)
```json
{
  "type": 22,
  "sequence_id": 0,
  "timestamp": 1234568010,
  "room_id": "ABC123",
  "my_color": 1,
  "current_player": 2,
  "board": [0,0,0,1,0,0,...],  // 225个元素
  "move_count": 15,
  "game_state": "PLAYING"
}
```

#### 6. 游戏结束 (GAME_OVER)
```json
{
  "type": 23,
  "sequence_id": 0,
  "timestamp": 1234568500,
  "body": {
    "reason": 0,          // 0=胜利, 1=失败, 2=平局, 3=认输, 4=超时
    "winner_id": "123",
    "is_winner": true,
    "is_casual": false,
    "rating_change": 16
  }
}
```

---

## 部署方案

### 开发环境

```bash
# 1. 启动MySQL
docker run -d --name mysql-gobang \
  -e MYSQL_ROOT_PASSWORD=password \
  -e MYSQL_DATABASE=gobang \
  -p 3306:3306 \
  mysql:8.0

# 2. 启动Redis
docker run -d --name redis-gobang \
  -p 6379:6379 \
  redis:latest

# 3. 编译项目
mvn clean package

# 4. 启动服务器
java -jar target/gobang-server-1.0.0.jar
```

### 生产环境（Docker Compose）

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    container_name: gobang-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_PASSWORD}
      MYSQL_DATABASE: gobang
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql

  redis:
    image: redis:latest
    container_name: gobang-redis
    command: redis-server --requirepass ${REDIS_PASSWORD}
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

  server:
    image: gobang-server:latest
    container_name: gobang-server
    environment:
      JWT_SECRET: ${JWT_SECRET}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      REDIS_PASSWORD: ${REDIS_PASSWORD}
    ports:
      - "9091:9091"
    depends_on:
      - mysql
      - redis

  nginx:
    image: nginx:alpine
    container_name: gobang-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
      - ./nginx/ssl:/etc/nginx/ssl
    depends_on:
      - server

volumes:
  mysql-data:
  redis-data:
```

### Nginx配置

```nginx
events {
    worker_connections 1024;
}

http {
    upstream websocket {
        server localhost:9091;
    }

    server {
        listen 80;
        server_name your-domain.com;

        # HTTP重定向到HTTPS
        return 301 https://$server_name$request_uri;
    }

    server {
        listen 443 ssl http2;
        server_name your-domain.com;

        ssl_certificate /etc/nginx/ssl/cert.pem;
        ssl_certificate_key /etc/nginx/ssl/key.pem;

        # WebSocket代理
        location /ws {
            proxy_pass http://websocket;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_read_timeout 300s;
            proxy_send_timeout 300s;
        }

        # 静态文件代理
        location / {
            proxy_pass http://websocket;
            proxy_set_header Host $host;
        }
    }
}
```

### 环境变量配置

```bash
# .env 文件
MYSQL_PASSWORD=your_secure_password
REDIS_PASSWORD=your_redis_password
JWT_SECRET=your-256-bit-secret-key-change-in-production
```

---

## 附录

### 端口使用说明

| 端口 | 协议 | 说明 |
|------|------|------|
| 9091 | HTTP/WS | 主服务端口 |
| 3306 | TCP | MySQL数据库 |
| 6379 | TCP | Redis缓存 |
| 80 | HTTP | Nginx HTTP |
| 443 | HTTPS | Nginx HTTPS |

### 目录结构说明

```
wuziqi/
├── src/main/
│   ├── java/              # Java源代码
│   │   └── com/gobang/
│   │       ├── core/      # 核心业务逻辑
│   │       ├── model/     # 数据模型
│   │       ├── service/   # 业务服务
│   │       ├── util/      # 工具类
│   │       ├── GobangServer.java
│   ├── proto/             # Protobuf协议定义
│   └── resources/
│       ├── application.yml # 应用配置
│       ├── schema.sql     # 数据库结构
│       └── static/        # 前端静态文件
├── target/                # 编译输出
├── logs/                  # 日志文件
├── nginx/                 # Nginx配置
├── Dockerfile             # Docker镜像
├── docker-compose.yml     # Docker编排
└── pom.xml               # Maven配置
```

### 性能指标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 并发连接 | 1000+ | 单机WebSocket连接数 |
| 消息延迟 | <50ms | 端到端消息延迟 |
| 匹配时间 | <30s | 平均匹配等待时间 |
| 内存占用 | <500MB | 运行时内存 |
| CPU占用 | <30% | 正常负载下 |

---

*文档版本: 1.0.0*
*最后更新: 2026-03-15*
