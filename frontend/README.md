# 五子棋在线对战系统 - 前端

<div align="center">

![Vue 3](https://img.shields.io/badge/Vue-3.4.21-brightgreen)
![TypeScript](https://img.shields.io/badge/TypeScript-5.3.3-blue)
![Vite](https://img.shields.io/badge/Vite-5.1.6-purple)
![Element Plus](https://img.shields.io/badge/Element%20Plus-2.6.3-blue)

基于 Vue 3 + TypeScript + Vite 的五子棋对战前端应用

[开发指南](../docs/CONTRIBUTING.md) • [组件文档](PAGE_API_GUIDE.md) • [样式指南](STYLE_GUIDE.md)

</div>

---

## 项目简介

这是五子棋在线对战系统的前端部分，采用 Vue 3 组合式 API、TypeScript 和 Vite 构建。提供完整的用户界面，包括游戏对弈、好友系统、聊天、排行榜等功能。

### 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.4.21 | 渐进式 JavaScript 框架 |
| TypeScript | 5.3.3 | JavaScript 的超集 |
| Vite | 5.1.6 | 下一代前端构建工具 |
| Element Plus | 2.6.3 | Vue 3 组件库 |
| Pinia | 2.1.7 | Vue 3 状态管理 |
| Vue Router | 4.3.0 | Vue 3 路由管理 |
| Axios | 1.6.7 | HTTP 客户端 |
| SockJS | - | WebSocket 降级方案 |
| @stomp/stompjs | - | STOMP 消息协议客户端 |

---

## 快速开始

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev

# 前端将在 http://localhost:5173 启动
```

### 构建生产版本

```bash
npm run build

# 构建产物将输出到 dist/ 目录
```

### 预览生产构建

```bash
npm run preview
```

---

## 项目结构

```
gobang-frontend/
├── public/                         # 静态资源
│   └── favicon.ico                 # 网站图标
├── src/
│   ├── main.ts                     # 应用入口
│   ├── App.vue                     # 根组件
│   ├── views/                      # 页面组件 (18)
│   │   ├── LoginView.vue           # 登录页面
│   │   ├── RegisterView.vue        # 注册页面
│   │   ├── HomeView.vue            # 主页（排行榜、记录、好友、帮助）
│   │   ├── MatchView.vue           # 快速匹配页面
│   │   ├── RoomView.vue            # 房间大厅页面
│   │   ├── GameView.vue            # 游戏对弈页面
│   │   ├── PVEView.vue             # 人机对战页面
│   │   ├── ObserverView.vue        # 观战页面
│   │   ├── ProfileView.vue         # 个人资料页面
│   │   ├── FriendsView.vue         # 好友页面
│   │   ├── RankView.vue            # 排行榜页面
│   │   ├── RecordsView.vue         # 对局记录页面
│   │   ├── RecordView.vue          # 对局详情页面
│   │   ├── ReplayView.vue          # 回放页面
│   │   ├── PuzzleView.vue          # 棋谱页面
│   │   ├── SettingsView.vue        # 设置页面
│   │   ├── HelpView.vue            # 帮助页面
│   │   └── NotFoundView.vue        # 404 页面
│   ├── components/                 # 组件
│   │   └── shared/                 # 共享组件 (8)
│   │       ├── GameBoard.vue       # 棋盘组件
│   │       ├── ActionButton.vue    # 操作按钮
│   │       ├── ContentCard.vue     # 内容卡片
│   │       ├── EmptyState.vue      # 空状态
│   │       ├── LoadingState.vue    # 加载状态
│   │       ├── ListItem.vue        # 列表项
│   │       ├── PageHeader.vue      # 页面头部
│   │       ├── TabPane.vue         # 标签面板
│   │       └── index.ts            # 组件导出
│   ├── api/                        # API 层
│   │   ├── http.ts                 # HTTP 封装（axios）
│   │   ├── websocket.ts            # WebSocket 封装
│   │   ├── services.ts             # API 服务定义
│   │   └── index.ts                # API 统一导出
│   ├── store/                      # 状态管理
│   │   ├── index.ts                # Store 配置
│   │   └── modules/                # 状态模块
│   │       ├── user.ts             # 用户状态
│   │       ├── game.ts             # 游戏状态
│   │       └── room.ts             # 房间状态
│   ├── router/                     # 路由
│   │   └── index.ts                # 路由配置
│   ├── types/                      # TypeScript 类型
│   │   ├── common.ts               # 通用类型
│   │   ├── user.ts                 # 用户类型
│   │   ├── game.ts                 # 游戏类型
│   │   ├── global.d.ts             # 全局类型声明
│   │   └── index.ts                # 类型导出
│   ├── composables/                # 组合式函数
│   │   └── usePageTheme.ts         # 页面主题
│   └── assets/                     # 资源文件
├── index.html                      # HTML 入口
├── vite.config.ts                  # Vite 配置
├── tsconfig.json                   # TypeScript 配置
├── package.json                    # npm 配置
└── README.md                       # 项目说明
```

---

## 核心功能

### 1. 认证系统

- 用户登录
- 用户注册
- JWT Token 管理
- 自动登录

### 2. 游戏模式

| 模式 | 页面 | 说明 |
|------|------|------|
| 🤖 人机对战 | PVEView.vue | 与 AI 对弈 |
| ⚡ 快速匹配 | MatchView.vue | 自动匹配对手 |
| 🏠 房间对战 | RoomView.vue + GameView.vue | 创建/加入房间 |
| 👀 观战模式 | ObserverView.vue | 观看对局 |

### 3. 社交功能

| 功能 | 页面 | 说明 |
|------|------|------|
| 👥 好友系统 | FriendsView.vue | 添加/管理好友 |
| 💬 实时聊天 | FriendsView.vue | 好友一对一聊天 |
| 📨 游戏邀请 | FriendsView.vue | 邀请好友对战 |

### 4. 数据展示

| 功能 | 页面 | 说明 |
|------|------|------|
| 🏆 排行榜 | RankView.vue | 多维度排名 |
| 📜 对局记录 | RecordsView.vue | 历史对局 |
| 🎭 个人资料 | ProfileView.vue | 用户信息和统计 |

### 5. 其他功能

| 功能 | 页面 | 说明 |
|------|------|------|
| 🧩 残局挑战 | PuzzleView.vue | 棋谱解答 |
| ⚙️ 系统设置 | SettingsView.vue | 偏好设置 |
| ❓ 帮助中心 | HelpView.vue | 使用说明 |

---

## API 层说明

### HTTP API (api/http.ts)

封装了 Axios 客户端，提供：

- 请求/响应拦截器
- 自动添加 JWT Token
- 统一错误处理
- 请求重试机制

```typescript
import { get, post } from '@/api/http'

// 示例：获取用户信息
const userInfo = await get('/api/user/info')

// 示例：登录
const result = await post('/api/auth/login', { username, password })
```

### WebSocket API (api/websocket.ts)

封装了 STOMP 客户端，提供：

- 自动连接管理
- 自动重连机制
- 订阅管理
- 消息发送

```typescript
import { wsClient } from '@/api/websocket'

// 连接 WebSocket
await wsClient.connect(token)

// 订阅游戏状态
wsClient.subscribeGameState(roomId, (data) => {
  console.log('游戏状态更新:', data)
})

// 发送落子消息
wsClient.sendMove({ x: 7, y: 8 })
```

---

## 状态管理

### 用户状态 (store/modules/user.ts)

```typescript
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()

// 登录
await userStore.login(username, password)

// 获取用户信息
const userInfo = userStore.userInfo

// 登出
userStore.logout()
```

### 游戏状态 (store/modules/game.ts)

```typescript
import { useGameStore } from '@/store/modules/game'

const gameStore = useGameStore()

// 更新游戏状态
gameStore.updateGameState(gameState)

// 重置游戏
gameStore.resetGame()
```

---

## 路由配置

```typescript
// 主要路由
/                    # 重定向到 /home
/login              # 登录页
/register           # 注册页
/home               # 主页
/home?panel=rank    # 排行榜
/home?panel=records # 对局记录
/home?panel=friends # 好友系统
/home?panel=help    # 帮助中心
/match              # 快速匹配
/room               # 房间大厅
/game/:roomId       # 游戏页面
/pve                # 人机对战
/observer/:roomId   # 观战
/profile            # 个人资料
/puzzle             # 残局挑战
/settings           # 设置
```

---

## 组件使用

### GameBoard 组件

可复用的棋盘组件：

```vue
<template>
  <GameBoard
    :board="boardState"
    :last-move="lastMove"
    :winning-line="winningLine"
    :disabled="isGameEnded"
    @cell-click="handleCellClick"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import GameBoard from '@/components/shared/GameBoard.vue'

const boardState = ref<Array<Array<number>>>([])
const lastMove = ref({ x: -1, y: -1 })
const winningLine = ref<Array<{x: number, y: number}>>([])
const isGameEnded = ref(false)

function handleCellClick(x: number, y: number) {
  console.log(`点击位置: ${x}, ${y}`)
}
</script>
```

---

## 环境变量

创建 `.env.development` 文件：

```env
# API 地址
VITE_API_BASE_URL=http://localhost:8080

# WebSocket 地址
VITE_WS_BASE_URL=ws://localhost:8080
```

创建 `.env.production` 文件：

```env
# 生产环境 API 地址
VITE_API_BASE_URL=https://api.example.com

# 生产环境 WebSocket 地址
VITE_WS_BASE_URL=wss://api.example.com
```

---

## 开发规范

### 命名规范

- **组件文件**: PascalCase，如 `GameBoard.vue`
- **工具函数**: camelCase，如 `formatDate()`
- **常量**: UPPER_SNAKE_CASE，如 `MAX_PLAYERS`
- **接口/类型**: PascalCase，如 `UserInfo`

### 组件规范

- 使用 `<script setup>` 语法
- 使用 TypeScript 定义 Props 和 Emits
- 组件命名应该具有描述性

```vue
<script setup lang="ts">
import { ref, computed } from 'vue'

interface Props {
  title: string
  count?: number
}

const props = withDefaults(defineProps<Props>(), {
  count: 0
})

const emit = defineEmits<{
  (e: 'update', value: number): void
}>()
</script>
```

### 样式规范

- 使用 Element Plus 组件库
- 遵循统一的设计规范
- 详见 [样式指南](STYLE_GUIDE.md)

---

## 调试技巧

### 1. Vue DevTools

安装 [Vue DevTools](https://devtools.vuejs.org/) 浏览器扩展：

- 查看组件树
- 查看 Pinia 状态
- 查看路由信息
- 性能分析

### 2. 网络请求调试

```typescript
// 在 api/http.ts 中开启调试
const isDev = import.meta.env.DEV

if (isDev) {
  console.log('[Request]', config)
  console.log('[Response]', response)
}
```

### 3. WebSocket 调试

```typescript
// 在 api/websocket.ts 中开启调试
debug: (str: string) => {
  console.log('[WebSocket STOMP]', str)
}
```

---

## 构建和部署

### 构建生产版本

```bash
npm run build

# 输出到 dist/ 目录
```

### Docker 部署

```dockerfile
# 多阶段构建
FROM node:18 AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

---

## 常见问题

### Q: npm install 失败

```bash
# 清除缓存重试
npm cache clean --force
rm -rf node_modules package-lock.json
npm install
```

### Q: 端口被占用

```bash
# 修改 vite.config.ts 中的端口
server: {
  port: 5174  # 改为其他端口
}
```

### Q: WebSocket 连接失败

1. 检查后端是否启动
2. 检查防火墙设置
3. 检查 WebSocket URL 配置

---

## 文档导航

| 文档 | 说明 |
|------|------|
| [PAGE_API_GUIDE.md](PAGE_API_GUIDE.md) | 页面 API 使用指南 |
| [STYLE_GUIDE.md](STYLE_GUIDE.md) | 样式规范指南 |
| [../docs/CODE_STRUCTURE.md](../docs/CODE_STRUCTURE.md) | 代码结构详解 |
| [../docs/API.md](../docs/API.md) | 后端 API 文档 |

---

## 更新日志

查看 [../docs/CHANGELOG.md](../docs/CHANGELOG.md)

---

## 许可证

[MIT License](../LICENSE)

---

<div align="center">

**祝你开发愉快！** 🚀

Made with ❤️ by gobang team

</div>
