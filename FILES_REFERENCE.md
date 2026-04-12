# 五子棋在线对战系统 - 文件功能详解

## 📚 目录
- [后端文件说明](#后端文件说明)
- [前端文件说明](#前端文件说明)
- [配置文件说明](#配置文件说明)

---

## 🔧 后端文件说明

### 主程序
| 文件 | 功能 |
|------|------|
| `GobangApplication.java` | **Spring Boot 主启动类** - 应用程序入口，初始化Spring容器 |

### 配置类 (config/)
| 文件 | 功能 |
|------|------|
| `AppConfig.java` | **应用配置** - 全局应用配置和Bean定义 |
| `DatabaseConfig.java` | **数据库配置** - 数据源、连接池配置 |
| `JacksonConfig.java` | **JSON序列化配置** - 日期格式、空值处理等 |
| `MatchConfig.java` | **匹配配置** - 匹配超时、积分差异等参数 |
| `RedisConfig.java` | **Redis配置** - 缓存连接配置 |
| `SecurityConfig.java` | **安全配置** - JWT认证、CORS、权限控制 |
| `WebConfig.java` | **Web配置** - 跨域、拦截器配置 |
| `WebSocketConfig.java` | **WebSocket配置** - STOMP端点、消息代理配置 |
| `WebSocketEventListener.java` | **WebSocket事件监听** - 连接/断开事件处理，用户在线状态 |

### 控制器 (controller/)
| 文件 | 功能 |
|------|------|
| `AuthController.java` | **认证控制器** - 登录、注册、登出、Token刷新 |
| `FriendController.java` | **好友控制器** - 添加/删除好友、好友分组、好友申请 |
| `GameRecordController.java` | **对局记录控制器** - 查询历史对局、对局详情 |
| `ObserverController.java` | **观战控制器** - 加入/离开观战、观战房间列表 |
| `PuzzleController.java` | **棋谱控制器** - 棋谱列表、棋谱详情、通关记录 |
| `RankController.java` | **排行榜控制器** - 积分排行榜、胜率排行榜 |
| `RecordController.java` | **对局记录控制器** - 用户对局历史、分页查询 |
| `UserStatsController.java` | **用户统计控制器** - 用户数据、战绩统计 |
| `WebSocketController.java` | **WebSocket控制器** - 处理所有WebSocket消息（落子、匹配、房间等） |
| `CleanupController.java` | **清理控制器** - 定期清理过期数据、日志 |

### 核心游戏逻辑 (core/)
#### 游戏逻辑 (core/game/)
| 文件 | 功能 |
|------|------|
| `Board.java` | **棋盘类** - 15×15棋盘数据结构、落子、状态检查 |
| `GameState.java` | **游戏状态类** - 游戏状态、玩家信息、轮次管理 |
| `WinChecker.java` | **胜负判断器** - 五子连珠检测算法 |
| `WinCheckerUtil.java` | **胜负判断工具** - 辅助方法集合 |

#### 匹配系统 (core/match/)
| 文件 | 功能 |
|------|------|
| `SpringMatchMaker.java` | **匹配器** - ELO积分匹配算法、匹配队列管理 |

#### 房间管理 (core/room/)
| 文件 | 功能 |
|------|------|
| `RoomManager.java` | **房间管理器** - 创建房间、加入房间、房间状态管理 |

#### 积分系统 (core/rating/)
| 文件 | 功能 |
|------|------|
| `ELOCalculator.java` | **ELO积分计算** - ELO等级分算法实现 |
| `RatingCalculator.java` | **积分计算接口** - 积分计算标准接口 |

#### 安全组件 (core/security/)
| 文件 | 功能 |
|------|------|
| `RateLimitManager.java` | **限流管理器** - 请求频率限制、防恶意请求 |

### 数据访问层 (mapper/)
| 文件 | 功能 |
|------|------|
| `UserMapper.java` | **用户数据访问** - 用户CRUD、认证信息查询 |
| `FriendMapper.java` | **好友数据访问** - 好友关系查询、添加、删除 |
| `FriendGroupMapper.java` | **好友分组访问** - 好友分组CRUD |
| `ChatMessageMapper.java` | **聊天消息访问** - 消息保存、查询历史、已读状态 |
| `GameRecordMapper.java` | **游戏记录访问** - 对局记录保存、查询 |
| `GameInvitationMapper.java` | **游戏邀请访问** - 邀请记录、状态更新 |
| `GameFavoriteMapper.java` | **收藏访问** - 收藏的游戏记录查询 |
| `PuzzleMapper.java` | **棋谱访问** - 棋谱查询、分类 |
| `PuzzleRecordMapper.java` | **棋谱记录访问** - 解答记录保存、查询 |
| `PuzzleStatsMapper.java` | **棋谱统计访问** - 棋谱统计数据查询 |
| `UserStatsMapper.java` | **用户统计访问** - 战绩统计查询、更新 |
| `UserSettingsMapper.java` | **用户设置访问** - 个人设置查询、更新 |
| `UserActivityLogMapper.java` | **活动日志访问** - 用户活动记录查询 |

### 服务层 (service/)
| 文件 | 功能 |
|------|------|
| `UserService.java` | **用户服务接口** - 用户相关业务逻辑 |
| `GameService.java` | **游戏服务接口** - 游戏核心逻辑接口 |
| `ChatService.java` | **聊天服务接口** - 聊天消息处理接口 |
| `FriendService.java` | **好友服务接口** - 好友业务接口 |
| `GameInvitationService.java` | **游戏邀请接口** - 游戏邀请业务接口 |
| `ObserverService.java` | **观战服务接口** - 观战业务接口 |
| `PuzzleService.java` | **棋谱服务接口** - 棋谱业务接口 |
| `RecordService.java` | **对局记录接口** - 对局记录查询接口 |

### 服务实现 (service/impl/)
| 文件 | 功能 |
|------|------|
| `UserServiceImpl.java` | **用户服务实现** - 用户CRUD、认证、统计 |
| `GameServiceImpl.java` | **游戏服务实现** - 落子、悔棋、认输、和棋 |
| `ChatServiceImpl.java` | **聊天服务实现** - 发送消息、查询历史、标记已读 |
| `FriendServiceImpl.java` | **好友服务实现** - 添加好友、好友分组、在线状态 |
| `GameInvitationServiceImpl.java` | **邀请服务实现** - 发送邀请、响应邀请、超时处理 |
| `ObserverServiceImpl.java` | **观战服务实现** - 加入观战、离开观战、房间列表 |
| `PuzzleServiceImpl.java` | **棋谱服务实现** - 棋谱查询、解答验证、统计 |
| `RecordServiceImpl.java` | **记录服务实现** - 对局记录查询、分页、筛选 |

### 数据模型 (model/)
#### 实体类 (model/entity/)
| 文件 | 功能 |
|------|------|
| `User.java` | **用户实体** - 用户账号、昵称、积分、等级 |
| `Friend.java` | **好友实体** - 好友关系、分组、状态 |
| `FriendGroup.java` | **好友分组实体** - 分组名称、排序 |
| `ChatMessage.java` | **聊天消息实体** - 消息内容、发送者、接收者、已读状态 |
| `GameRecord.java` | **游戏记录实体** - 对局数据、棋谱、胜负、模式 |
| `GameInvitation.java` | **游戏邀请实体** - 邀请信息、状态、时间戳 |
| `GameFavorite.java` | **收藏实体** - 收藏的游戏记录、收藏时间 |
| `Puzzle.java` | **棋谱实体** - 棋谱数据、难度、解答 |
| `PuzzleRecord.java` | **棋谱记录实体** - 解答记录、星级、用时 |
| `UserStats.java` | **用户统计实体** - 总场次、胜率、积分变化 |
| `UserSettings.java` | **用户设置实体** - 音效、主题、通知设置 |
| `UserActivityLog.java` | **活动日志实体** - 登录、游戏、聊天记录 |

#### 数据传输对象 (model/dto/)
| 文件 | 功能 |
|------|------|
| `LoginDto.java` | **登录请求** - 用户名、密码 |
| `RegisterDto.java` | **注册请求** - 用户名、密码、昵称、邮箱 |
| `CreateRoomDto.java` | **创建房间请求** - 房间名、密码、模式 |
| `JoinRoomDto.java` | **加入房间请求** - 房间ID、密码 |
| `GameMoveDto.java` | **游戏落子请求** - X坐标、Y坐标 |
| `FriendWebSocketDto.java` | **好友消息** - 好友事件类型、数据 |
| `ObserverRoomDto.java` | **观战房间信息** - 房间详情、玩家信息 |
| `UserStatsDTO.java` | **用户统计数据** - 胜率、场次、积分 |

### WebSocket 拦截器 (websocket/interceptor/)
| 文件 | 功能 |
|------|------|
| `JwtChannelInterceptor.java` | **JWT通道拦截器** - WebSocket连接时验证JWT Token，设置用户Principal |

### 工具类 (util/)
| 文件 | 功能 |
|------|------|
| `JwtUtil.java` | **JWT工具** - 生成Token、验证Token、解析Token |
| `PasswordValidator.java` | **密码验证** - 密码强度验证（长度、复杂度） |
| `ContentFilter.java` | **内容过滤** - 过滤敏感词、检查合规性 |
| `SensitiveWordFilter.java` | **敏感词过滤** - 检测和替换敏感词 |
| `RateLimiter.java` | **限流工具** - 请求限流算法实现 |
| `IdGenerator.java` | **ID生成器** - 生成唯一ID标识 |
| `SecureRandomUtil.java` | **安全随机** - 生成安全的随机数 |
| `DatabaseInitUtil.java` | **数据库初始化** - 初始化表结构、插入测试数据 |
| `DatabaseStatusChecker.java` | **数据库状态检查** - 检查数据库连接状态 |

---

## 🎨 前端文件说明

### 主程序
| 文件 | 功能 |
|------|------|
| `main.ts` | **应用入口** - 创建Vue应用、注册插件、挂载应用 |
| `App.vue` | **根组件** - 应用根组件、全局布局 |

### 页面组件 (views/)
| 文件 | 功能 |
|------|------|
| `LoginView.vue` | **登录页面** - 用户名密码登录、自动登录 |
| `RegisterView.vue` | **注册页面** - 用户注册表单、密码强度验证 |
| `HomeView.vue` | **主页** - 导航中心、排行榜、记录、好友、帮助面板 |
| `MatchView.vue` | **匹配页面** - 快速匹配、匹配进度、取消匹配 |
| `RoomView.vue` | **房间大厅** - 房间列表、创建房间、加入房间 |
| `GameView.vue` | **游戏页面** - 棋盘显示、落子、游戏控制按钮 |
| `PVEView.vue` | **人机对战** - AI对弈、难度选择 |
| `ObserverView.vue` | **观战页面** - 观看对局、观战者列表 |
| `ProfileView.vue` | **个人资料** - 用户信息、编辑资料、战绩展示 |
| `FriendsView.vue` | **好友页面** - 好友列表、添加好友、实时聊天 |
| `RankView.vue` | **排行榜** - 多维度排名（积分、胜率等） |
| `RecordsView.vue` | **对局记录** - 历史对局列表、筛选分页 |
| `RecordView.vue` | **对局详情** - 单局游戏详情、棋谱回放 |
| `ReplayView.vue` | **回放页面** - 棋谱完整回放、控制播放 |
| `PuzzleView.vue` | **棋谱页面** - 残局挑战、解答验证 |
| `SettingsView.vue` | **设置页面** - 系统设置、音效、主题 |
| `HelpView.vue` | **帮助中心** - 游戏规则、FAQ |
| `NotFoundView.vue` | **404页面** - 页面未找到提示 |

### 共享组件 (components/shared/)
| 文件 | 功能 |
|------|------|
| `GameBoard.vue` | **棋盘组件** - 可复用的棋盘组件、显示棋子 |
| `ActionButton.vue` | **操作按钮** - 统一的操作按钮样式 |
| `ContentCard.vue` | **内容卡片** - 卡片容器组件 |
| `EmptyState.vue` | **空状态** - 暂无数据提示组件 |
| `LoadingState.vue` | **加载状态** - 加载动画组件 |
| `ListItem.vue` | **列表项** - 统一的列表项样式 |
| `PageHeader.vue` | **页面头部** - 页面标题和导航 |
| `TabPane.vue` | **标签面板** - 标签切换面板 |

### API 层 (api/)
| 文件 | 功能 |
|------|------|
| `http.ts` | **HTTP客户端** - Axios封装、拦截器、错误处理 |
| `websocket.ts` | **WebSocket客户端** - STOMP封装、连接管理、订阅管理 |
| `services.ts` | **API服务** - 所有HTTP API接口定义 |
| `index.ts` | **API导出** - 统一导出API和服务 |

### 状态管理 (store/)
| 文件 | 功能 |
|------|------|
| `index.ts` | **Store配置** - Pinia store实例创建 |
| `modules/user.ts` | **用户状态** - 登录状态、用户信息、权限 |
| `modules/game.ts` | **游戏状态** - 棋盘数据、游戏状态、玩家信息 |
| `modules/room.ts` | **房间状态** - 房间信息、玩家列表、观战者 |

### 路由 (router/)
| 文件 | 功能 |
|------|------|
| `index.ts` | **路由配置** - 路由定义、导航守卫、权限控制 |

### 类型定义 (types/)
| 文件 | 功能 |
|------|------|
| `common.ts` | **通用类型** - 通用数据结构定义 |
| `user.ts` | **用户类型** - 用户相关类型定义 |
| `game.ts` | **游戏类型** - 游戏相关类型定义 |
| `global.d.ts` | **全局类型** - 全局类型声明、扩展 |
| `index.ts` | **类型导出** - 统一导出所有类型 |

### 组合式函数 (composables/)
| 文件 | 功能 |
|------|------|
| `usePageTheme.ts` | **页面主题** - 主题切换、样式管理 |

---

## ⚙️ 配置文件说明

### 根目录配置

| 文件 | 功能 |
|------|------|
| `pom.xml` | **Maven配置** - 项目依赖、构建插件、版本管理 |
| `docker-compose.yml` | **Docker编排** - 多容器配置（后端、前端、数据库、Redis） |
| `Dockerfile` | **Docker镜像** - 后端Docker镜像构建配置 |
| `.env.example` | **环境变量示例** - 环境变量配置模板 |
| `.gitignore` | **Git忽略** - Git版本控制忽略的文件和目录 |
| `.dockerignore` | **Docker忽略** - Docker构建时排除的文件 |

### 前端配置

| 文件 | 功能 |
|------|------|
| `package.json` | **npm配置** - 项目依赖、脚本命令 |
| `vite.config.ts` | **Vite配置** - 构建工具配置、代理设置 |
| `tsconfig.json` | **TypeScript配置** - TypeScript编译选项 |
| `tsconfig.app.json` | **应用TS配置** - 应用代码的TS配置 |
| `tsconfig.node.json` | **Node TS配置** - 构建脚本的TS配置 |
| `index.html` | **HTML入口** - 页面HTML结构、应用挂载点 |

### 后端资源 (resources/)

| 文件 | 功能 |
|------|------|
| `application.yml` | **Spring Boot配置** - 数据源、Redis、JWT、服务器端口 |
| `logback.xml` | **日志配置** - 日志级别、输出格式、文件路径 |

---

## 📖 文档说明

### 项目文档
| 文件 | 功能 |
|------|------|
| `README.md` | **项目说明** - 项目介绍、特性、快速开始 |
| `PROJECT_STRUCTURE.md` | **项目结构** - 目录结构说明 |
| `FILES_REFERENCE.md` | **文件参考** - 文件功能说明（本文档） |
| `CLEANUP_REPORT.md` | **清理报告** - 代码清理记录 |

### 技术文档 (docs/)
| 文件 | 功能 |
|------|------|
| `API.md` | **API文档** - REST API和WebSocket API接口说明 |
| `CODE_STRUCTURE.md` | **代码结构详解** - 每个文件的作用说明 |
| `QUICKSTART.md` | **快速开始** - 5分钟启动指南 |
| `QUICK_DEPLOY.md` | **快速部署** - 生产环境部署指南 |
| `USER_GUIDE.md` | **用户指南** - 面向玩家的使用说明 |
| `CHANGELOG.md` | **更新日志** - 版本更新记录 |
| `CONTRIBUTING.md` | **贡献指南** - 开发者贡献代码指南 |
| `TROUBLESHOOTING.md` | **故障排查** - 常见问题和解决方案 |
| `ECS_DEPLOYMENT_GUIDE.md` | **ECS部署** - 阿里云ECS部署详细步骤 |
| `PERFORMANCE_TEST_REPORT.md` | **性能测试** - 性能测试报告 |

### 前端文档
| 文件 | 功能 |
|------|------|
| `PAGE_API_GUIDE.md` | **页面API** - 页面组件API使用说明 |
| `STYLE_GUIDE.md` | **样式指南** - 样式规范和设计指南 |

---

## 🔍 快速查找

### 后端文件查找

| 查找内容 | 位置 |
|---------|------|
| 配置类 | `src/main/java/com/gobang/config/` |
| 控制器 | `src/main/java/com/gobang/controller/` |
| 服务接口 | `src/main/java/com/gobang/service/` |
| 服务实现 | `src/main/java/com/gobang/service/impl/` |
| 数据访问 | `src/main/java/com/gobang/mapper/` |
| 实体类 | `src/main/java/com/gobang/model/entity/` |
| DTO | `src/main/java/com/gobang/model/dto/` |
| 游戏逻辑 | `src/main/java/com/gobang/core/game/` |
| 匹配系统 | `src/main/java/com/gobang/core/match/` |
| 房间管理 | `src/main/java/com/gobang/core/room/` |
| 工具类 | `src/main/java/com/gobang/util/` |

### 前端文件查找

| 查找内容 | 位置 |
|---------|------|
| 页面组件 | `gobang-frontend/src/views/` |
| 共享组件 | `gobang-frontend/src/components/shared/` |
| API层 | `gobang-frontend/src/api/` |
| 状态管理 | `gobang-frontend/src/store/modules/` |
| 路由 | `gobang-frontend/src/router/` |
| 类型定义 | `gobang-frontend/src/types/` |

---

<div align="center">

**了解每个文件的作用是高效开发的关键！** 🎯

</div>
