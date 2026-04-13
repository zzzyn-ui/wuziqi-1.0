# 五子棋在线对战系统 - 代码结构详解

## 📁 项目根目录结构

```
D:\wuziqi/
├── backend/                    # 后端源代码（Java + Spring Boot）
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                   # 前端源代码（Vue 3 + TypeScript）
│   ├── src/
│   ├── package.json
│   └── vite.config.ts
├── docs/                       # 项目文档
├── scripts/                    # 部署脚本
├── docker-compose.yml          # Docker容器编排配置
├── .env.example                # 环境变量示例
├── .gitignore                  # Git忽略文件配置
├── .dockerignore               # Docker忽略文件配置
└── README.md                   # 项目说明文档
```

---

## 🔧 后端代码结构 (backend/src/)

### 主程序入口
```
backend/src/main/java/com/gobang/
└── GobangApplication.java      # Spring Boot应用启动类
    # 作用：程序的入口点，启动Spring Boot应用，初始化所有组件
```

### 配置类 (config/)
```
config/
├── AppConfig.java              # 应用配置
    # 作用：全局应用配置，包括常量定义、跨域设置等

├── SecurityConfig.java         # 安全配置
    # 作用：配置Spring Security，设置密码加密、安全规则、JWT认证

├── WebSocketConfig.java        # WebSocket配置
    # 作用：配置WebSocket/STOMP，设置消息代理、端点、拦截器

├── WebSocketEventListener.java # WebSocket事件监听器
    # 作用：监听WebSocket连接/断开事件，记录用户在线状态

├── DatabaseConfig.java         # 数据库配置
    # 作用：配置数据源、MyBatis Plus、数据库连接池

├── JacksonConfig.java          # JSON序列化配置
    # 作用：配置JSON序列化规则，处理日期时间格式

├── WebConfig.java              # Web配置
    # 作用：配置CORS跨域、拦截器、静态资源处理

├── MatchConfig.java            # 匹配系统配置
    # 作用：配置匹配参数（等待时间、积分范围等）

└── RedisConfig.java            # Redis缓存配置
    # 作用：配置Redis连接、缓存策略
```

### 控制器 (controller/)
```
controller/
├── AuthController.java         # 认证控制器
    # 作用：处理用户注册、登录、登出，JWT Token生成

├── WebSocketController.java    # WebSocket消息控制器
    # 作用：处理所有WebSocket消息（游戏操作、匹配、房间管理等）

├── FriendController.java       # 好友系统控制器
    # 作用：处理好友添加、删除、查询等HTTP API

├── ObserverController.java     # 观战系统控制器
    # 作用：处理观战相关HTTP API（房间列表、加入/离开观战）

├── PuzzleController.java       # 棋谱控制器
    # 作用：处理棋谱查询、解答、统计

├── RankController.java         # 排行榜控制器
    # 作用：处理排行榜数据查询（不同模式、不同时间段）

├── RecordController.java       # 对局记录控制器
    # 作用：处理用户对局历史查询、分页、筛选

├── GameRecordController.java   # 游戏记录控制器
    # 作用：处理游戏记录的保存和查询

├── UserStatsController.java    # 用户统计控制器
    # 作用：处理用户战绩统计（胜率、场次等）

└── CleanupController.java      # 系统清理控制器
    # 作用：定期清理过期数据、日志、临时文件
```

### 数据访问层 (mapper/)
```
mapper/
├── UserMapper.java             # 用户数据访问
    # 作用：用户的增删改查、认证信息查询

├── FriendMapper.java           # 好友关系数据访问
    # 作用：好友关系的查询、添加、删除

├── FriendGroupMapper.java      # 好友分组数据访问
    # 作用：好友分组的查询、管理

├── ChatMessageMapper.java      # 聊天消息数据访问
    # 作用：聊天消息的保存、查询、已读状态更新

├── GameRecordMapper.java       # 游戏记录数据访问
    # 作用：游戏记录的保存、查询

├── GameInvitationMapper.java   # 游戏邀请数据访问
    # 作用：游戏邀请的查询、状态更新

├── GameFavoriteMapper.java     # 收藏游戏数据访问
    # 作用：用户收藏的游戏记录查询

├── PuzzleMapper.java           # 棋谱数据访问
    # 作用：棋谱的查询、分类

├── PuzzleRecordMapper.java     # 棋谱解答记录数据访问
    # 作用：棋谱解答历史的保存和查询

├── PuzzleStatsMapper.java      # 棋谱统计数据访问
    # 作用：棋谱统计数据的查询

├── UserStatsMapper.java        # 用户统计数据访问
    # 作用：用户战绩统计的查询和更新

├── UserSettingsMapper.java     # 用户设置数据访问
    # 作用：用户个人设置的查询和更新

└── UserActivityLogMapper.java  # 用户活动日志数据访问
    # 作用：用户登录、游戏等活动的记录
```

### 实体类 (model/entity/)
```
model/entity/
├── User.java                   # 用户实体
    # 作用：用户表数据模型，包含用户名、密码、邮箱等

├── Friend.java                 # 好友关系实体
    # 作用：好友关系表数据模型

├── FriendGroup.java            # 好友分组实体
    # 作用：好友分组表数据模型

├── ChatMessage.java            # 聊天消息实体
    # 作用：聊天消息表数据模型

├── GameRecord.java             # 游戏记录实体
    # 作用：游戏记录表数据模型，包含棋谱、胜负等信息

├── GameInvitation.java         # 游戏邀请实体
    # 作用：游戏邀请表数据模型

├── GameFavorite.java           # 收藏游戏实体
    # 作用：用户收藏的游戏记录数据模型

├── Puzzle.java                 # 棋谱实体
    # 作用：棋谱表数据模型

├── PuzzleRecord.java           # 棋谱解答记录实体
    # 作用：棋谱解答记录表数据模型

├── UserStats.java              # 用户统计实体
    # 作用：用户战绩统计表数据模型

├── UserSettings.java           # 用户设置实体
    # 作用：用户个人设置表数据模型

└── UserActivityLog.java        # 用户活动日志实体
    # 作用：用户活动日志表数据模型
```

### 数据传输对象 (model/dto/)
```
model/dto/
├── LoginDto.java               # 登录请求数据传输对象
    # 作用：接收用户登录请求（用户名、密码）

├── RegisterDto.java            # 注册请求数据传输对象
    # 作用：接收用户注册请求（用户名、密码、邮箱）

├── CreateRoomDto.java          # 创建房间请求数据传输对象
    # 作用：接收创建房间请求（房间名、密码、模式）

├── JoinRoomDto.java            # 加入房间请求数据传输对象
    # 作用：接收加入房间请求（房间ID、密码）

├── GameMoveDto.java            # 游戏移动数据传输对象
    # 作用：传输游戏落子信息（位置、玩家）

├── FriendWebSocketDto.java     # 好友WebSocket消息传输对象
    # 作用：传输好友系统相关WebSocket消息

├── ObserverRoomDto.java        # 观战房间数据传输对象
    # 作用：传输可观战的房间信息

└── UserStatsDTO.java           # 用户统计数据传输对象
    # 作用：传输用户统计数据
```

### 枚举类 (model/enums/)
```
model/enums/
└── GameMode.java               # 游戏模式枚举
    # 作用：定义游戏模式（人机、匹配、房间等）
```

### 核心游戏逻辑 (core/)
```
core/
├── game/
│   ├── Board.java              # 棋盘类
│   # 作用：管理棋盘状态、落子、判断胜负
│
│   ├── GameState.java          # 游戏状态类
│   # 作用：管理游戏状态（玩家、当前轮次、游戏结果）
│
│   ├── WinChecker.java         # 胜负判定类
│   # 作用：检查五子连珠，判断游戏胜负
│
│   └── WinCheckerUtil.java     # 胜负判定工具类
│       # 作用：提供胜负判定的辅助方法
│
├── match/
│   └── SpringMatchMaker.java   # 匹配器
│       # 作用：实现玩家匹配逻辑，根据积分和模式匹配对手
│
├── room/
│   └── RoomManager.java        # 房间管理器
│       # 作用：管理游戏房间（创建、加入、离开、状态管理）
│
├── rating/
│   ├── ELOCalculator.java      # ELO积分计算器
│   # 作用：计算游戏后的积分变化（ELO等级分系统）
│
│   └── RatingCalculator.java   # 积分计算器
│       # 作用：积分计算的通用接口
│
└── security/
    └── RateLimitManager.java   # 限流管理器
        # 作用：防止恶意请求，限制请求频率
```

### 服务层 (service/ & service/impl/)
```
service/
├── UserService.java            # 用户服务接口
    # 作用：定义用户相关操作（查询、更新、统计）
    #
    └── impl/
        └── UserServiceImpl.java # 用户服务实现
            # 作用：实现用户服务接口的所有方法

├── ChatService.java            # 聊天服务接口
    # 作用：定义聊天相关操作（发送、查询历史、标记已读）
    #
    └── impl/
        └── ChatServiceImpl.java # 聊天服务实现
            # 作用：实现聊天服务接口，处理消息发送和查询

├── FriendService.java          # 好友服务接口
    # 作用：定义好友相关操作（添加、删除、查询）
    #
    └── impl/
        └── FriendServiceImpl.java # 好友服务实现
            # 作用：实现好友服务接口的所有方法

├── GameInvitationService.java  # 游戏邀请服务接口
    # 作用：定义游戏邀请操作（发送、响应、超时处理）
    #
    └── impl/
        └── GameInvitationServiceImpl.java # 游戏邀请服务实现
            # 作用：实现游戏邀请服务接口的所有方法

├── ObserverService.java        # 观战服务接口
    # 作用：定义观战相关操作（加入、离开、获取房间列表）
    #
    └── impl/
        └── ObserverServiceImpl.java # 观战服务实现
            # 作用：实现观战服务接口的所有方法

├── PuzzleService.java          # 棋谱服务接口
    # 作用：定义棋谱相关操作（查询、解答、统计）
    #
    └── impl/
        └── PuzzleServiceImpl.java # 棋谱服务实现
            # 作用：实现棋谱服务接口的所有方法

├── RecordService.java          # 对局记录服务接口
    # 作用：定义对局记录查询操作
    #
    └── impl/
        └── RecordServiceImpl.java # 对局记录服务实现
            # 作用：实现对局记录查询的所有方法

├── GameService.java            # 游戏服务接口
    # 作用：定义游戏核心操作（落子、悔棋、认输等）
    #
    └── impl/
        └── GameServiceImpl.java # 游戏服务实现
            # 作用：实现游戏核心操作的所有方法
```

### 工具类 (util/)
```
util/
├── JwtUtil.java                # JWT工具类
    # 作用：生成和验证JWT Token

├── PasswordValidator.java      # 密码验证工具
    # 作用：验证密码强度（长度、复杂度）

├── ContentFilter.java          # 内容过滤工具
    # 作用：过滤敏感词、检查内容合规性

├── SensitiveWordFilter.java    # 敏感词过滤工具
    # 作用：检测和替换敏感词

├── RateLimiter.java            # 限流工具
    # 作用：实现请求限流算法

├── IdGenerator.java            # ID生成器
    # 作用：生成唯一的ID标识

├── SecureRandomUtil.java       # 安全随机工具
    # 作用：生成安全的随机数

├── DatabaseInitUtil.java       # 数据库初始化工具
    # 作用：初始化数据库表、插入测试数据

└── DatabaseStatusChecker.java  # 数据库状态检查工具
    # 作用：检查数据库连接状态
```

### WebSocket拦截器 (websocket/interceptor/)
```
websocket/interceptor/
└── JwtChannelInterceptor.java  # JWT通道拦截器
    # 作用：拦截WebSocket连接，验证JWT Token，设置用户Principal
```

### 资源文件 (resources/)
```
resources/
├── application.yml             # Spring Boot配置文件
    # 作用：配置数据库连接、服务器端口、日志级别等

└── logback.xml                 # 日志配置文件
    # 作用：配置日志输出格式、文件路径、日志级别
```

---

## 🎨 前端代码结构 (frontend/)

### 主程序入口
```
frontend/
├── index.html                  # HTML入口文件
    # 作用：页面的HTML结构，挂载Vue应用

├── vite.config.ts              # Vite配置文件
    # 作用：配置Vite构建工具（别名、代理、构建选项）

├── package.json                # npm包管理配置
    # 作用：定义项目依赖、脚本命令

├── tsconfig.json               # TypeScript配置
    # 作用：配置TypeScript编译选项

├── tsconfig.app.json           # 应用TypeScript配置
    # 作用：配置应用的TypeScript选项

└── tsconfig.node.json          # Node环境TypeScript配置
    # 作用：配置构建脚本和Node环境的TypeScript选项
```

### 源代码 (src/)
```
src/
├── main.ts                     # 应用入口
    # 作用：创建Vue应用，注册插件，挂载应用

├── App.vue                     # 根组件
    # 作用：应用的根组件，包含全局布局和路由视图
```

#### 页面组件 (views/)
```
views/
├── LoginView.vue               # 登录页面
    # 作用：用户登录界面，包含用户名密码输入

├── RegisterView.vue            # 注册页面
    # 作用：用户注册界面，包含注册表单

├── HomeView.vue                # 主页面
    # 作用：应用主页面，包含导航和多个功能面板
    # 包含：排行榜、对局记录、好友系统、帮助中心

├── MatchView.vue               # 匹配页面
    # 作用：快速匹配界面，显示匹配状态和进度

├── RoomView.vue                # 房间页面
    # 作用：房间大厅，显示公开房间列表，支持创建/加入房间

├── GameView.vue                # 游戏页面
    # 作用：对弈界面，显示棋盘、棋子、控制按钮

├── PVEView.vue                 # 人机对战页面
    # 作用：与AI对弈的界面

├── ObserverView.vue            # 观战页面
    # 作用：观战界面，实时显示其他玩家的对局

├── ProfileView.vue             # 个人资料页面
    # 作用：查看和编辑个人资料、战绩统计

├── FriendsView.vue             # 好友页面
    # 作用：好友列表、添加好友、聊天界面

├── RankView.vue                # 排行榜页面
    # 作用：显示玩家积分排名

├── RecordsView.vue             # 对局记录页面
    # 作用：查看个人对局历史

├── RecordView.vue              # 对局详情页面
    # 作用：查看单局游戏详细信息和棋谱回放

├── ReplayView.vue              # 回放页面
    # 作用：棋谱回放界面

├── PuzzleView.vue              # 棋谱页面
    # 作用：棋谱练习和解答界面

├── SettingsView.vue            # 设置页面
    # 作用：用户设置界面（音效、主题等）

├── HelpView.vue                # 帮助页面
    # 作用：显示游戏帮助和FAQ

└── NotFoundView.vue            # 404页面
    # 作用：页面未找到时的提示页面
```

#### 共享组件 (components/shared/)
```
components/shared/
├── GameBoard.vue               # 棋盘组件
    # 作用：可复用的棋盘组件，显示棋子和落子位置

├── ActionButton.vue            # 操作按钮组件
    # 作用：统一的操作按钮样式

├── ContentCard.vue             # 内容卡片组件
    # 作用：卡片容器，用于展示内容

├── EmptyState.vue              # 空状态组件
    # 作用：显示暂无数据的提示

├── LoadingState.vue            # 加载状态组件
    # 作用：显示加载动画

├── ListItem.vue                # 列表项组件
    # 作用：统一的列表项样式

├── PageHeader.vue              # 页面头部组件
    # 作用：页面标题和导航

├── TabPane.vue                 # 标签面板组件
    # 作用：标签切换面板

└── index.ts                    # 组件导出文件
    # 作用：统一导出所有共享组件
```

#### API层 (api/)
```
api/
├── http.ts                     # HTTP请求封装
    # 作用：封装axios，处理请求/响应拦截器

├── websocket.ts                # WebSocket客户端封装
    # 作用：封装WebSocket连接、订阅、发送消息

├── services.ts                 # API服务定义
    # 作用：定义所有HTTP API接口

└── index.ts                    # API统一导出
    # 作用：导出所有API和服务
```

#### 状态管理 (store/)
```
store/
├── index.ts                    # Store配置
    # 作用：创建Pinia store实例

└── modules/
    ├── user.ts                 # 用户状态模块
    # 作用：管理用户登录状态、用户信息

    ├── game.ts                 # 游戏状态模块
    # 作用：管理游戏状态、棋盘数据

    └── room.ts                 # 房间状态模块
    # 作用：管理房间状态、房间信息
```

#### 路由 (router/)
```
router/
└── index.ts                    # 路由配置
    # 作用：定义所有路由规则和导航守卫
```

#### 类型定义 (types/)
```
types/
├── common.ts                   # 通用类型定义
    # 作用：定义通用数据结构

├── user.ts                     # 用户类型定义
    # 作用：定义用户相关的数据结构

├── game.ts                     # 游戏类型定义
    # 作用：定义游戏相关的数据结构

├── global.d.ts                 # 全局类型声明
    # 作用：声明全局类型和变量

└── index.ts                    # 类型统一导出
    # 作用：导出所有类型定义
```

#### 组合式函数 (composables/)
```
composables/
└── usePageTheme.ts             # 页面主题组合函数
    # 作用：管理页面主题切换和样式
```

#### 静态资源 (public/)
```
public/
├── favicon.ico                 # 网站图标
└── (其他静态文件)              # 图片、字体等
```

---

## 📚 文档目录 (docs/)

```
docs/
├── API.md                      # API接口文档
    # 作用：描述所有HTTP API和WebSocket接口

├── QUICKSTART.md               # 快速开始指南
    # 作用：快速启动项目的步骤说明

├── QUICK_DEPLOY.md             # 快速部署指南
    # 作用：快速部署到服务器的步骤

├── USER_GUIDE.md               # 用户使用指南
    # 作用：面向最终用户的功能说明

├── CHANGELOG.md                # 变更日志
    # 作用：记录项目版本更新历史

├── CONTRIBUTING.md             # 贡献指南
    # 作用：指导开发者如何贡献代码

├── TROUBLESHOOTING.md          # 故障排查指南
    # 作用：常见问题和解决方案

├── ECS_DEPLOYMENT_GUIDE.md     # ECS部署指南
    # 作用：阿里云ECS服务器部署详细步骤

├── PERFORMANCE_TEST_REPORT.md  # 性能测试报告
    # 作用：系统性能测试结果

├── database-analysis.md        # 数据库分析文档
    # 作用：数据库表结构和索引分析

└── migration-complete.md       # 迁移完成文档
    # 作用：记录代码迁移的完成情况
```

---

## 💾 备份目录 (backups/)

```
backups/
├── BACKUP_INDEX.md             # 备份索引文件
    # 作用：记录所有备份的说明和位置

└── features_backup/            # 功能模块备份
    ├── README.md               # 功能备份说明

    ├── ui_improvements_20260412/ # 最新UI改进完整备份
    │   └── BACKUP_INFO.md      # 备份详细信息

    ├── match/                  # 匹配系统备份
    ├── profile/                # 个人资料功能备份
    ├── pve/                    # 人机对战备份
    └── room/                   # 房间对战备份

├── friend-system/              # 好友系统详细备份
│   └── README.md

└── observer-system/            # 观战系统详细备份
    └── README.md
```

---

## 🔧 配置文件说明

### 根目录配置文件

```
├── pom.xml                     # Maven项目配置
    # 作用：定义Java项目依赖、构建配置、插件

├── docker-compose.yml          # Docker编排配置
    # 作用：定义多容器应用（后端、前端、数据库、Redis）

├── Dockerfile                  # Docker镜像配置
    # 作用：定义如何构建后端Docker镜像

├── .env.example                # 环境变量示例
    # 作用：示例环境变量配置文件（不包含敏感信息）

├── .gitignore                  # Git忽略配置
    # 作用：指定Git版本控制时忽略的文件和目录

├── .dockerignore               # Docker忽略配置
    # 作用：指定构建Docker镜像时排除的文件

├── README.md                   # 项目说明文档
    # 作用：项目的总体介绍和使用说明

├── FILES_REFERENCE.md          # 文件参考文档
    # 作用：重要文件和目录的参考说明

├── PROJECT_STRUCTURE.md        # 项目结构文档
    # 作用：项目的目录结构说明

└── CLEANUP_REPORT.md           # 代码清理报告
    # 作用：记录代码清理的详细内容
```

---

## 🎯 数据流向说明

### 用户登录流程
```
1. 用户在 LoginView.vue 输入用户名密码
2. 前端调用 api/services.ts 的登录API
3. 后端 AuthController.java 接收请求
4. UserServiceImpl.java 验证用户
5. JwtUtil.java 生成Token
6. Token返回前端，存储在 user store
```

### WebSocket连接流程
```
1. 应用启动时，main.ts 创建WebSocket连接
2. websocket.ts 连接后端 /ws 端点
3. JwtChannelInterceptor.java 验证Token
4. 连接成功，订阅相关频道
5. 接收和发送实时消息
```

### 游戏对弈流程
```
1. 用户选择游戏模式（PVE/PVP/房间）
2. 进入对应页面（PVEView/MatchView/RoomView）
3. 建立游戏会话（SpringMatchMaker匹配 或 RoomManager创建房间）
4. GameView.vue 显示棋盘
5. 用户落子，发送到 WebSocketController
6. GameService 处理落子，Board 更新状态
7. WinChecker 判断胜负
8. 通过WebSocket广播游戏状态
```

---

## 📊 关键技术栈

### 后端
- **Java 17** - 编程语言
- **Spring Boot 3.x** - 应用框架
- **Spring WebSocket + STOMP** - 实时通信
- **MyBatis Plus** - 数据库ORM
- **MySQL** - 关系型数据库
- **Redis** - 缓存数据库
- **JWT** - 身份认证
- **Maven** - 构建工具

### 前端
- **Vue 3** - 前端框架
- **TypeScript** - 类型系统
- **Vite** - 构建工具
- **Element Plus** - UI组件库
- **Pinia** - 状态管理
- **Vue Router** - 路由管理
- **Axios** - HTTP客户端
- **SockJS + @stomp/stompjs** - WebSocket客户端
- **npm** - 包管理器

---

## 🔐 安全机制

1. **JWT认证** - 所有API请求需要携带有效Token
2. **密码加密** - 使用BCrypt加密存储密码
3. **WebSocket拦截** - JwtChannelInterceptor验证连接
4. **内容过滤** - 敏感词过滤、内容审核
5. **限流保护** - RateLimiter防止恶意请求
6. **CORS配置** - WebConfig配置跨域访问

---

## 📝 代码规范

- **后端**: 遵循Java代码规范，使用Spring Boot最佳实践
- **前端**: 遵循Vue 3组合式API规范，使用TypeScript类型
- **命名**: 使用清晰的变量和函数命名
- **注释**: 关键逻辑添加中文注释说明
- **错误处理**: 统一的异常处理和错误响应

---

以上就是五子棋在线对战系统的完整代码结构说明。
每个文件和文件夹都有其特定的职责，共同构成了一个完整的在线对弈平台。
