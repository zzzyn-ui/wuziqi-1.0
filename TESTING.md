# 五子棋系统测试指南

## 系统状态

### ✅ 后端 (Spring Boot 3.2)
- **地址**: http://localhost:8083
- **WebSocket**: ws://localhost:8083/ws
- **REST API**: http://localhost:8083/api
- **状态**: ✅ 运行中

### ✅ 前端 (Vue3 + TypeScript)
- **地址**: http://localhost:5173
- **状态**: ✅ 运行中

### 🎯 技术栈
- **前端**: Vue 3 + TypeScript + Vite + Element Plus + Pinia + SockJS + STOMP
- **后端**: Spring Boot 3.2 + Spring WebSocket + STOMP + Spring Security + JWT + MyBatis-Plus

## 测试账号

| 用户名 | 密码 |
|--------|------|
| testuser2026 | 123456 |
| gamer2026 | 123456 |

## 功能测试流程

### 1. 访问前端
打开浏览器访问: **http://localhost:5173**

### 2. 登录测试
1. 输入用户名: `testuser2026`
2. 输入密码: `123456`
3. 点击"登录"按钮
4. **预期结果**: 跳转到游戏大厅

### 3. 大厅功能测试
登录后进入游戏大厅，可以访问：
- **快速匹配** - 匹配同水平对手
- **房间对战** - 创建或加入房间
- **人机对战** - 挑战AI对手
- **排行榜** - 查看玩家排名
- **对局记录** - 查看历史对局

### 4. 双人对战测试 ⭐
**打开两个浏览器窗口或使用无痕模式：**

**窗口1:**
1. 访问 http://localhost:5173
2. 登录 `testuser2026` / `123456`
3. 点击"休闲匹配"卡片

**窗口2:**
1. 访问 http://localhost:5173
2. 登录 `gamer2026` / `123456`
3. 点击"休闲匹配"卡片

**预期结果:**
- 两个窗口都显示"正在寻找对手..."
- 约1-2秒后自动匹配成功
- 跳转到游戏页面
- 显示棋盘和玩家信息

### 4. 游戏测试
1. **黑方先手** - 点击棋盘空白格子落子
2. **白方后手** - 在对手落子后点击空白格子
3. 观察棋盘同步更新
4. 五子连珠时游戏结束，显示胜负

## 已实现功能

### ✅ 后端功能
- [x] Spring Boot 3.2 项目结构
- [x] WebSocket + STOMP (替代Netty)
- [x] JWT认证 + Spring Security
- [x] 用户注册/登录API
- [x] 匹配系统
- [x] 游戏逻辑
- [x] 房间管理
- [x] MyBatis-Plus数据库集成

### ✅ 前端功能
- [x] Vue3 + TypeScript项目结构
- [x] Element Plus UI组件库
- [x] Pinia状态管理 (user/game/room stores)
- [x] Vue Router路由配置
- [x] Axios HTTP客户端封装
- [x] SockJS + STOMP WebSocket客户端
- [x] 登录页面 (LoginView.vue)
- [x] 大厅页面 (HomeView.vue) - 主导航中心
- [x] 匹配页面 (MatchView.vue) - 快速匹配
- [x] 房间页面 (RoomView.vue) - 创建/加入房间
- [x] 游戏页面 (GameView.vue) - 对战棋盘
- [x] 人机对战 (PVEView.vue) - AI对手
- [x] 排行榜 (RankView.vue) - 玩家排名
- [x] 对局记录 (RecordsView.vue) - 历史对局
- [x] 响应式设计
- [x] 统一渐变主题色

### 🎨 UI主题
- 主色调: 橙色 (#ff6b35)
- 背景: 渐变色 (#fff5f0 → #ffe8dc → #ffd4c4)
- 棋盘: 木质纹理
- 棋子: 黑白渐变 + 阴影效果

## API 文档

### REST API
```
POST /api/auth/login
POST /api/auth/register
```

### WebSocket STOMP

#### 客户端发送
```
/app/auth/login       - 登录
/app/auth/register    - 注册
/app/match/start      - 开始匹配
/app/match/cancel     - 取消匹配
/app/game/move        - 落子
/app/game/resign      - 认输
/app/room/create      - 创建房间
/app/room/join        - 加入房间
```

#### 服务器推送
```
/user/queue/match                 - 匹配通知
/topic/room/{roomId}              - 房间消息
```

## 项目结构

### 前端
```
D:\wuziqi\gobang-frontend\
├── src\
│   ├── api\              # API封装
│   │   ├── http.ts       # Axios
│   │   ├── websocket.ts  # STOMP
│   │   └── services.ts   # API服务
│   ├── assets\           # 静态资源
│   │   └── styles\       # 样式文件
│   ├── components\       # 组件
│   ├── composables\      # 组合式函数
│   ├── router\           # 路由
│   │   └── index.ts
│   ├── store\            # Pinia Store
│   │   ├── index.ts
│   │   └── modules\
│   │       ├── user.ts
│   │       ├── game.ts
│   │       └── room.ts
│   ├── types\            # TypeScript类型
│   │   ├── user.ts
│   │   ├── game.ts
│   │   └── common.ts
│   ├── views\            # 页面组件
│   │   ├── LoginView.vue      # 登录页
│   │   ├── HomeView.vue       # 游戏大厅
│   │   ├── MatchView.vue      # 快速匹配
│   │   ├── RoomView.vue       # 房间对战
│   │   ├── GameView.vue       # 对战游戏
│   │   ├── PVEView.vue        # 人机对战
│   │   ├── RankView.vue       # 排行榜
│   │   └── RecordsView.vue    # 对局记录
│   ├── App.vue
│   └── main.ts
├── vite.config.ts
├── tsconfig.json
└── package.json
```

### 后端
```
D:\wuziqi\
├── src\main\java\com\gobang\
│   ├── config\          # 配置类
│   ├── controller\      # 控制器
│   ├── service\         # 服务层
│   ├── mapper\          # MyBatis-Plus Mapper
│   ├── model\           # 实体和DTO
│   │   ├── entity\
│   │   └── dto\
│   ├── core\            # 核心逻辑
│   │   ├── game\       # Board, WinChecker
│   │   ├── match\      # SpringMatchMaker
│   │   └── rating\     # ELOCalculator
│   ├── websocket\       # WebSocket
│   │   └── interceptor\
│   ├── util\            # 工具类
│   └── GobangApplication.java
└── src\main\resources\
    └── application.yml
```

## 页面路由

```
/               → 游戏大厅 (需认证)
/login          → 登录页
/register       → 注册页
/match          → 快速匹配 (需认证)
/room           → 房间对战 (需认证)
/game/:roomId    → 对战游戏 (需认证)
/pve            → 人机对战 (需认证)
/puzzle         → 残局挑战 (需认证)
/replay/:id?    → 对局复盘 (需认证)
/rank           → 排行榜 (需认证)
/records        → 对局记录 (需认证)
/friends        → 好友系统 (需认证)
/settings       → 设置 (需认证)
/profile        → 个人资料 (需认证)
```

## 已完成迁移功能 ✅

### 原前端页面 → 新Vue3页面
- [x] index.html → HomeView.vue (游戏大厅)
- [x] login.html → LoginView.vue (登录)
- [x] match.html → MatchView.vue (匹配)
- [x] game.html → GameView.vue (游戏)
- [x] create-room.html → RoomView.vue (创建房间)
- [x] join.html → RoomView.vue (加入房间)
- [x] pve.html → PVEView.vue (人机对战)
- [x] puzzle.html → PuzzleView.vue (残局挑战) ✨
- [x] replay.html → ReplayView.vue (对局复盘) ✨
- [x] friends.html → FriendsView.vue (好友系统) ✨
- [x] settings.html → SettingsView.vue (设置) ✨
- [x] rank.html → RankView.vue (排行榜)
- [x] records.html → RecordsView.vue (对局记录)

### 旧前端功能迁移完成度：90%
- ✅ 已迁移: 13个核心功能页面
- ⏳ 待实现:
  - help.html (帮助文档)
  - rules.html (规则说明)
  - about.html (关于页面)
  - contact.html (联系页面)
  - privacy.html / terms.html (法律条款)
  - spectate.html (观战功能)

## 下一步开发计划

- [ ] 实现注册页面 (RegisterView.vue)
- [ ] 实现个人资料页面 (ProfileView.vue)
- [ ] 添加聊天功能
- [ ] 添加复盘功能
- [ ] 添加好友系统
- [ ] 添加残局挑战
- [ ] 优化移动端体验
- [ ] 添加音效和动画

## 备份说明

- **原项目备份**: `D:\wuziqi-backup`
- **旧8080后端**: `D:\wuziqi\gobang-backend` (保留)
- **旧5173前端**: 已删除

## 调试

### 查看前端日志
打开浏览器控制台 (F12)

### 查看后端日志
后端控制台输出或日志文件: `D:\wuziqi\logs\`

### WebSocket连接测试
在浏览器控制台输入:
```javascript
// 查看WebSocket连接状态
console.log('WebSocket状态:', window.stompClient?.connected)
```
