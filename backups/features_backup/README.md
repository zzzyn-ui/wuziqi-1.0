# 功能备份说明

## 备份时间
- 2026-04-07: 初始功能备份
- 2026-04-12: UI改进完整备份

## 备份内容

### 5. UI改进完整备份 (ui_improvements_20260412/)
**备份时间:** 2026-04-12 16:15:53

**完整系统备份，包含所有源代码和配置文件**

**修改内容:**
- 修复聊天历史加载功能（改为topic-based方式）
  - 后端: `WebSocketController.java` - 使用 `/topic/chat/history/{userId}` 代替 `convertAndSendToUser`
  - 前端: `HomeView.vue`, `websocket.ts` - 订阅改为 topic-based 方式
- 删除页脚统计信息
  - `HomeView.vue` - 移除"在线玩家"和"今日对局"显示
  - 清理相关变量 `onlineCount` 和 `totalGames`
- 删除排行榜在线状态
  - `HomeView.vue` - 移除排行榜项中的"在线"标签
  - 删除 `.online-tag` CSS样式
  - 移除模拟数据中的 `online` 属性

**备份文件:**
- `src/` - 完整后端源代码
- `gobang-frontend/` - 完整前端源代码
- `docs/` - 项目文档
- `pom.xml`, `docker-compose.yml`, `Dockerfile`, `.env.example` - 配置文件

查看详情：[BACKUP_INFO.md](./ui_improvements_20260412/BACKUP_INFO.md)

---

### 1. 个人资料功能 (profile/)
- `ProfileView.vue` - 个人资料页面
- `UserService.java` - 用户服务接口
- `UserServiceImpl.java` - 用户服务实现
- `UserStatsController.java` - 用户统计控制器
- `user.ts` - 用户状态管理
- `services.ts` - API服务

### 2. 人机对战功能 (pve/)
- `PVEView.vue` - 人机对战页面

### 3. 快速匹配功能 (match/)
- `MatchView.vue` - 快速匹配页面
- `SpringMatchMaker.java` - 匹配器

### 4. 房间对战功能 (room/)
**前端文件 (frontend/):**
- `GameView.vue` - 游戏页面
- `RoomView.vue` - 房间页面
- `ObserverView.vue` - 观战页面

**后端文件 (backend/):**
- `WebSocketController.java` - WebSocket控制器（房间创建、加入、游戏操作）
- `GameService.java` - 游戏服务接口
- `GameServiceImpl.java` - 游戏服务实现
- `ObserverService.java` - 观战服务接口
- `ObserverServiceImpl.java` - 观战服务实现

**核心文件 (core/):**
- `Board.java` - 棋盘类
- `GameState.java` - 游戏状态类
- `WinChecker.java` - 胜负判定类
- `RoomManager.java` - 房间管理器

## 功能说明

### 个人资料功能
- 查看用户信息
- 编辑个人资料
- 查看战绩统计
- 用户设置管理

### 人机对战功能
- 简单AI模式
- 中等AI模式
- 困难AI模式
- 实时对弈

### 快速匹配功能
- 休闲模式匹配
- 竞技模式匹配
- 实时匹配状态
- 匹配成功自动进入游戏

### 房间对战功能
- 创建房间（可选择密码保护）
- 加入房间（公开或私有）
- 实时对战
- 悔棋功能
- 和棋功能
- 认输功能
- 观战模式
- 房间列表显示
- 再来一局（双方准备后重新开始）
- 积分计算（竞技模式）
- 游戏结果结算

## 恢复方法
将对应文件复制回原目录即可
