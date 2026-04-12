# 五子棋在线对战系统 - 项目结构

本文档说明五子棋在线对战系统的目录结构和组织方式。

---

## 总体架构

```
五子棋在线对战系统
│
├── 后端 (Spring Boot 3 + Java 17)
│   ├── REST API
│   ├── WebSocket (STOMP)
│   └── 业务逻辑
│
├── 前端 (Vue 3 + TypeScript)
│   ├── 页面组件
│   ├── API 层
│   └── 状态管理
│
├── 数据层
│   ├── MySQL 8.0
│   └── Redis 7.0
│
└── 基础设施
    ├── Docker
    └── Nginx
```

---

## 项目目录结构

```
D:\wuziqi/
│
├── src/                          # 后端源代码 (Java + Spring Boot)
│   └── main/
│       ├── java/com/gobang/
│       │   ├── GobangApplication.java      # Spring Boot 启动类
│       │   ├── config/                    # 配置类 (8个文件)
│       │   ├── controller/                # 控制器 (10个文件)
│       │   ├── service/                   # 服务层接口
│       │   ├── service/impl/              # 服务层实现
│       │   ├── mapper/                    # 数据访问层 (12个文件)
│       │   ├── model/                     # 数据模型
│       │   │   ├── entity/                # 实体类 (11个文件)
│       │   │   ├── dto/                   # 数据传输对象 (8个文件)
│       │   │   └── enums/                 # 枚举类
│       │   ├── core/                      # 核心逻辑
│       │   │   ├── game/                  # 游戏逻辑
│       │   │   ├── match/                 # 匹配系统
│       │   │   ├── room/                  # 房间管理
│       │   │   ├── rating/                # 积分计算
│       │   │   └── security/              # 安全组件
│       │   ├── websocket/                 # WebSocket 拦截器
│       │   └── util/                      # 工具类 (9个文件)
│       └── resources/
│           ├── application.yml            # Spring Boot 配置
│           ├── logback.xml                # 日志配置
│           └── db/migration/              # 数据库迁移脚本
│
├── gobang-frontend/                # 前端源代码 (Vue 3 + TypeScript)
│   ├── src/
│   │   ├── main.ts                       # 应用入口
│   │   ├── App.vue                       # 根组件
│   │   ├── views/                        # 页面组件 (18个文件)
│   │   ├── components/                   # 组件
│   │   │   └── shared/                   # 共享组件 (8个文件)
│   │   ├── api/                          # API 层
│   │   ├── store/                        # 状态管理
│   │   ├── router/                       # 路由配置
│   │   ├── types/                        # TypeScript 类型
│   │   └── composables/                  # 组合式函数
│   ├── public/                           # 静态资源
│   ├── package.json                      # npm 配置
│   ├── vite.config.ts                    # Vite 配置
│   └── tsconfig.json                     # TypeScript 配置
│
├── docs/                                 # 项目文档
│   ├── API.md                            # API 接口文档
│   ├── CODE_STRUCTURE.md                 # 代码结构详解
│   ├── QUICKSTART.md                     # 快速开始指南
│   ├── USER_GUIDE.md                     # 用户使用指南
│   ├── CHANGELOG.md                      # 更新日志
│   └── ...
│
├── backups/                              # 代码备份
│   ├── BACKUP_INDEX.md                   # 备份索引
│   └── features_backup/                  # 功能模块备份
│
├── logs/                                 # 运行日志
│
├── pom.xml                               # Maven 项目配置
├── docker-compose.yml                    # Docker 编排配置
├── Dockerfile                            # Docker 镜像配置
├── .env.example                          # 环境变量示例
├── .gitignore                            # Git 忽略配置
├── README.md                             # 项目说明
└── CLEANUP_REPORT.md                     # 代码清理报告
```

---

## 后端详细结构

### 配置类 (config/)

| 文件 | 说明 |
|------|------|
| `SecurityConfig.java` | Spring Security 配置，JWT 认证 |
| `WebSocketConfig.java` | WebSocket + STOMP 配置 |
| `DatabaseConfig.java` | 数据源配置，连接池 |
| `WebConfig.java` | CORS 配置，拦截器 |
| `JacksonConfig.java` | JSON 序列化配置 |
| `AppConfig.java` | 应用配置 |
| `MatchConfig.java` | 匹配系统配置 |
| `RedisConfig.java` | Redis 缓存配置 |
| `WebSocketEventListener.java` | WebSocket 事件监听 |

### 控制器 (controller/)

| 文件 | 说明 |
|------|------|
| `AuthController.java` | 登录、注册、登出 |
| `WebSocketController.java` | 处理所有 WebSocket 消息 |
| `FriendController.java` | 好友系统 API |
| `ObserverController.java` | 观战系统 API |
| `PuzzleController.java` | 棋谱系统 API |
| `RankController.java` | 排行榜 API |
| `RecordController.java` | 对局记录 API |
| `GameRecordController.java` | 游戏记录 API |
| `UserStatsController.java` | 用户统计 API |
| `CleanupController.java` | 系统清理 API |

### 数据访问层 (mapper/)

| 文件 | 说明 |
|------|------|
| `UserMapper.java` | 用户 CRUD |
| `FriendMapper.java` | 好友关系 CRUD |
| `FriendGroupMapper.java` | 好友分组 CRUD |
| `ChatMessageMapper.java` | 聊天消息 CRUD |
| `GameRecordMapper.java` | 对局记录 CRUD |
| `GameInvitationMapper.java` | 游戏邀请 CRUD |
| `GameFavoriteMapper.java` | 收藏记录 CRUD |
| `PuzzleMapper.java` | 棋谱 CRUD |
| `PuzzleRecordMapper.java` | 棋谱记录 CRUD |
| `PuzzleStatsMapper.java` | 棋谱统计 CRUD |
| `UserStatsMapper.java` | 用户统计 CRUD |
| `UserSettingsMapper.java` | 用户设置 CRUD |
| `UserActivityLogMapper.java` | 活动日志 CRUD |

### 核心逻辑 (core/)

#### 游戏逻辑 (game/)
| 文件 | 说明 |
|------|------|
| `Board.java` | 15×15 棋盘，落子，状态管理 |
| `GameState.java` | 游戏状态，玩家，轮次 |
| `WinChecker.java` | 五子连珠判定 |
| `WinCheckerUtil.java` | 胜负判定工具 |

#### 匹配系统 (match/)
| 文件 | 说明 |
|------|------|
| `SpringMatchMaker.java` | ELO 积分匹配器 |

#### 房间管理 (room/)
| 文件 | 说明 |
|------|------|
| `RoomManager.java` | 房间创建、加入、离开、状态管理 |

#### 积分系统 (rating/)
| 文件 | 说明 |
|------|------|
| `ELOCalculator.java` | ELO 等级分计算 |
| `RatingCalculator.java` | 积分计算接口 |

---

## 前端详细结构

### 页面组件 (views/)

| 文件 | 说明 |
|------|------|
| `LoginView.vue` | 登录页面 |
| `RegisterView.vue` | 注册页面 |
| `HomeView.vue` | 主页（排行榜、记录、好友、帮助） |
| `MatchView.vue` | 快速匹配页面 |
| `RoomView.vue` | 房间大厅 |
| `GameView.vue` | 游戏对弈页面 |
| `PVEView.vue` | 人机对战页面 |
| `ObserverView.vue` | 观战页面 |
| `ProfileView.vue` | 个人资料 |
| `FriendsView.vue` | 好友系统 |
| `RankView.vue` | 排行榜 |
| `RecordsView.vue` | 对局记录列表 |
| `RecordView.vue` | 对局详情 |
| `ReplayView.vue` | 棋谱回放 |
| `PuzzleView.vue` | 残局挑战 |
| `SettingsView.vue` | 系统设置 |
| `HelpView.vue` | 帮助中心 |
| `NotFoundView.vue` | 404 页面 |

### 共享组件 (components/shared/)

| 文件 | 说明 |
|------|------|
| `GameBoard.vue` | 棋盘组件（可复用） |
| `ActionButton.vue` | 操作按钮 |
| `ContentCard.vue` | 内容卡片 |
| `EmptyState.vue` | 空状态提示 |
| `LoadingState.vue` | 加载动画 |
| `ListItem.vue` | 列表项 |
| `PageHeader.vue` | 页面头部 |
| `TabPane.vue` | 标签面板 |

### API 层 (api/)

| 文件 | 说明 |
|------|------|
| `http.ts` | HTTP 客户端（Axios 封装） |
| `websocket.ts` | WebSocket 客户端（STOMP 封装） |
| `services.ts` | API 服务定义 |
| `index.ts` | API 统一导出 |

### 状态管理 (store/modules/)

| 文件 | 说明 |
|------|------|
| `user.ts` | 用户状态（登录、用户信息） |
| `game.ts` | 游戏状态（棋盘、轮次） |
| `room.ts` | 房间状态（房间信息、玩家列表） |

---

## 数据流向

### 用户登录流程

```
LoginView.vue (输入用户名密码)
    ↓ POST /api/auth/login
AuthController.java (接收请求)
    ↓ 调用
UserServiceImpl.java (验证用户)
    ↓ 查询
UserMapper.java (数据库)
    ↓ 返回用户信息
JwtUtil.java (生成 Token)
    ↓ 返回 Token
前端存储 Token (user store)
    ↓ 后续请求携带 Token
```

### 游戏对弈流程

```
GameView.vue (玩家落子)
    ↓ WebSocket 发送
WebSocketController.java (接收消息)
    ↓ 调用
GameServiceImpl.java (处理落子)
    ↓ 更新
Board.java (棋盘状态)
    ↓ 检查
WinChecker.java (判断胜负)
    ↓ 广播
WebSocket (发送游戏状态)
    ↓ 接收
前端订阅者 (更新棋盘显示)
```

---

## 配置文件

### 后端配置 (application.yml)

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/gobang
    username: root
    password: your_password

  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: your-secret-key
  expiration: 604800  # 7天
```

### 前端配置 (vite.config.ts)

```typescript
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
})
```

---

## 相关文档

| 文档 | 说明 |
|------|------|
| [CODE_STRUCTURE.md](docs/CODE_STRUCTURE.md) | 详细的代码结构说明 |
| [API.md](docs/API.md) | API 接口文档 |
| [QUICKSTART.md](docs/QUICKSTART.md) | 快速开始指南 |
| [CONTRIBUTING.md](docs/CONTRIBUTING.md) | 贡献指南 |

---

## 快速导航

### 查找后端代码

- **配置** → `src/main/java/com/gobang/config/`
- **控制器** → `src/main/java/com/gobang/controller/`
- **服务** → `src/main/java/com/gobang/service/`
- **数据访问** → `src/main/java/com/gobang/mapper/`
- **实体** → `src/main/java/com/gobang/model/entity/`

### 查找前端代码

- **页面** → `gobang-frontend/src/views/`
- **组件** → `gobang-frontend/src/components/shared/`
- **API** → `gobang-frontend/src/api/`
- **状态** → `gobang-frontend/src/store/modules/`
- **类型** → `gobang-frontend/src/types/`

---

<div align="center">

**熟悉项目结构是高效开发的基础！** 🎯

</div>
