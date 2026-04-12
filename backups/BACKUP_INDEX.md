# 五子棋游戏系统备份索引

## 备份日期
2026-04-10

## 备份内容

### 1. 观战系统 (observer-system)
查看详情：[观战系统 README](./observer-system/README.md)

**后端文件：**
- `src/main/java/com/gobang/controller/ObserverController.java`
- `src/main/java/com/gobang/service/ObserverService.java`
- `src/main/java/com/gobang/service/impl/ObserverServiceImpl.java`
- `src/main/java/com/gobang/model/dto/ObserverRoomDto.java`

**前端文件：**
- `src/views/ObserverView.vue`

### 2. 好友系统 (friend-system)
查看详情：[好友系统 README](./friend-system/README.md)

**后端文件：**
- `src/main/java/com/gobang/controller/FriendController.java`
- `src/main/java/com/gobang/service/FriendService.java`
- `src/main/java/com/gobang/service/impl/FriendServiceImpl.java`
- `src/main/java/com/gobang/service/ChatService.java`
- `src/main/java/com/gobang/service/impl/ChatServiceImpl.java`
- `src/main/java/com/gobang/mapper/FriendMapper.java`
- `src/main/java/com/gobang/mapper/FriendGroupMapper.java`
- `src/main/java/com/gobang/mapper/ChatMessageMapper.java`
- `src/main/java/com/gobang/model/entity/Friend.java`
- `src/main/java/com/gobang/model/entity/FriendGroup.java`
- `src/main/java/com/gobang/model/entity/ChatMessage.java`
- `src/main/java/com/gobang/model/dto/FriendWebSocketDto.java`

**前端文件：**
- `src/views/FriendsView.vue`

## 恢复方法

### 从备份恢复文件
```bash
# 恢复观战系统
cp -r backups/observer-system/backend/* src/main/java/com/gobang/
cp -r backups/observer-system/frontend/* gobang-frontend/src/

# 恢复好友系统
cp -r backups/friend-system/backend/* src/main/java/com/gobang/
cp -r backups/friend-system/frontend/* gobang-frontend/src/
```

## 系统架构

```
五子棋在线对战系统
├── 后端 (Spring Boot)
│   ├── 对战系统
│   ├── 匹配系统
│   ├── 观战系统 ⭐
│   ├── 好友系统 ⭐
│   └── 聊天系统 ⭐
└── 前端 (Vue 3 + TypeScript)
    ├── 游戏界面
    ├── 匹配大厅
    ├── 观战界面 ⭐
    └── 好友管理 ⭐
```

## WebSocket 端点

### 观战系统
- 客户端发送：`/app/observer/join`, `/app/observer/leave`, `/app/observer/rooms`
- 服务器推送：`/user/queue/observer/response`, `/user/queue/observer/rooms`, `/topic/room/{roomId}/observer`

### 好友系统
- HTTP API：`/api/friend/*`
- 客户端发送：`/app/friend/*`, `/app/chat/*`
- 服务器推送：`/user/queue/friend/*`, `/user/queue/chat/private`

## 数据库表

### 观战系统
- 使用现有游戏房间表，观战者信息存储在内存中

### 好友系统
- `friend` - 好友关系表
- `friend_group` - 好友分组表
- `chat_message` - 聊天消息表

## 技术栈

### 后端
- Spring Boot 3.x
- Spring WebSocket + STOMP
- MyBatis-Plus
- MySQL

### 前端
- Vue 3 (Composition API)
- TypeScript
- Element Plus
- @stomp/stompjs
- SockJS

## 开发者注意事项

1. **Principal 传播**：WebSocket 消息需要在 JwtChannelInterceptor 中正确传递 Principal
2. **全局订阅**：私聊消息需要在用户登录后立即订阅，而不是在打开聊天对话框时订阅
3. **消息格式**：所有 WebSocket 消息应包含 `type` 字段来标识消息类型
4. **错误处理**：使用 `sendErrorToUser` 方法向前端发送错误消息
5. **在线状态**：用户连接/断开 WebSocket 时自动更新在线状态

## 版本历史
- 2026-04-12 16:15: 完整系统备份
  - 修复聊天历史加载功能（改为topic-based方式）
  - 删除页脚统计信息（在线玩家、今日对局）
  - 删除排行榜在线状态显示
  - 备份位置：`backup_20260412_161553/`
- 2026-04-10: 初始备份，包含观战系统和好友系统完整代码
