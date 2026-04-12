# 好友系统备份

## 备份日期
2026-04-10

## 系统概述
好友系统允许用户添加好友、管理好友关系、进行私聊，以及邀请好友进行对局。

## 后端文件结构

### Controller
- `FriendController.java` - 好友功能控制器
  - GET `/api/friend/list` - 获取好友列表
  - POST `/api/friend/request` - 发送好友申请
  - GET `/api/friend/requests` - 获取待处理申请
  - POST `/api/friend/accept` - 接受好友申请
  - POST `/api/friend/reject` - 拒绝好友申请
  - DELETE `/api/friend/delete` - 删除好友
  - GET `/api/friend/online` - 获取在线好友

### Service
- `FriendService.java` - 好友服务接口
  - `sendFriendRequest(Long userId, Long friendId)` - 发送好友申请
  - `acceptFriendRequest(Long requestId)` - 接受好友申请
  - `rejectFriendRequest(Long requestId)` - 拒绝好友申请
  - `deleteFriend(Long userId, Long friendId)` - 删除好友
  - `getFriendList(Long userId)` - 获取好友列表
  - `areFriends(Long userId1, Long userId2)` - 检查是否为好友

- `FriendServiceImpl.java` - 好友服务实现
  - 好友关系管理
  - 好友申请处理
  - 在线状态管理

- `ChatService.java` - 聊天服务接口
  - `sendPrivateMessage(Long senderId, Long receiverId, String content)` - 发送私聊消息
  - `getPrivateChatHistory(Long userId, Long friendId, int limit)` - 获取聊天历史
  - `markMessagesAsRead(Long userId, Long friendId)` - 标记消息为已读
  - `getUnreadMessages(Long userId)` - 获取未读消息

- `ChatServiceImpl.java` - 聊天服务实现
  - 使用 WebSocket 发送实时消息
  - 消息持久化到数据库
  - 消息已读状态管理

### Mapper (MyBatis-Plus)
- `FriendMapper.java` - 好友表映射
- `FriendGroupMapper.java` - 好友分组表映射
- `ChatMessageMapper.java` - 聊天消息表映射

### Entity
- `Friend.java` - 好友关系实体
  - `userId` - 用户ID
  - `friendId` - 好友ID
  - `status` - 关系状态（0-待处理，1-已接受，2-已拒绝）
  - `createdAt` - 创建时间
  - `updatedAt` - 更新时间

- `FriendGroup.java` - 好友分组实体
  - `name` - 分组名称
  - `userId` - 所属用户ID
  - `description` - 分组描述

- `ChatMessage.java` - 聊天消息实体
  - `senderId` - 发送者ID
  - `receiverId` - 接收者ID
  - `content` - 消息内容
  - `messageType` - 消息类型（0-文字，1-图片等）
  - `isRead` - 是否已读
  - `createdAt` - 发送时间

### DTO
- `FriendWebSocketDto.java` - WebSocket 好友数据传输对象
  - `userId` - 用户ID
  - `username` - 用户名
  - `nickname` - 昵称
  - `online` - 在线状态
  - `inGame` - 是否在游戏中

## 前端文件结构

### Views
- `FriendsView.vue` - 好友管理页面
  - 好友列表展示
  - 好友申请处理
  - 添加好友功能
  - 好友在线状态显示
  - 私聊功能集成

## WebSocket 订阅

### 客户端订阅
- `/user/queue/friend/status` - 好友状态更新
- `/user/queue/friend/invitation` - 游戏邀请
- `/user/queue/friend/invitation/response` - 邀请响应
- `/user/queue/chat/private` - 私聊消息（全局订阅）

### 消息类型
好友状态：
- `FRIEND_ONLINE` - 好友上线
- `FRIEND_OFFLINE` - 好友离线
- `FRIEND_IN_GAME` - 好友进入游戏
- `FRIEND_OUT_GAME` - 好友退出游戏

游戏邀请：
- `GAME_INVITATION` - 收到游戏邀请
- `INVITATION_ACCEPTED` - 邀请被接受
- `INVITATION_REJECTED` - 邀请被拒绝
- `INVITATION_CANCELLED` - 邀请被取消

聊天消息：
- `PRIVATE_MESSAGE` - 私聊消息
- `CHAT_HISTORY` - 聊天历史

## 数据库表

### friend 表
```sql
CREATE TABLE friend (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  friend_id BIGINT NOT NULL,
  status INT DEFAULT 0 COMMENT '0-待处理,1-已接受,2-已拒绝',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_id (user_id),
  KEY idx_friend_id (friend_id),
  KEY idx_status (status)
);
```

### friend_group 表
```sql
CREATE TABLE friend_group (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  description VARCHAR(200),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_id (user_id)
);
```

### chat_message 表
```sql
CREATE TABLE chat_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  message_type INT DEFAULT 0 COMMENT '0-文字,1-图片',
  is_read BOOLEAN DEFAULT FALSE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_sender_receiver (sender_id, receiver_id),
  KEY idx_created_at (created_at),
  KEY idx_is_read (is_read)
);
```

## 核心功能

### 1. 好友管理
- 搜索并添加好友
- 处理好友申请（接受/拒绝）
- 查看好友列表
- 删除好友
- 好友分组管理

### 2. 实时状态
- 好友在线/离线状态
- 好友游戏状态
- 状态变化实时推送

### 3. 私聊功能
- 实时消息发送
- 聊天历史记录
- 消息已读状态
- 未读消息提醒

### 4. 游戏邀请
- 邀请好友对局
- 接受/拒绝邀请
- 邀请超时处理
- 邀请状态同步

## 依赖关系
- `UserMapper` - 用户数据访问
- `JwtChannelInterceptor` - WebSocket 认证
- `SimpMessagingTemplate` - WebSocket 消息发送
- `GameInvitationService` - 游戏邀请服务

## 特性说明
- 好友关系是单向的（user_id 指向 friend_id）
- 双方都需要添加对方才能建立完整的好友关系
- 聊天消息全局订阅，无论是否打开聊天对话框都能收到
- 消息通知使用 Element Plus 的 ElMessage 组件
