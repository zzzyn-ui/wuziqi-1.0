# 五子棋在线对战服务器 - API 接口文档

## 基本信息

- **服务器地址**: `http://your-server:8083`
- **API 前缀**: `/api`
- **WebSocket**: `ws://your-server:8083/ws`
- **认证方式**: JWT Bearer Token
- **数据格式**: JSON

---

## 认证机制

### JWT Token 获取

登录成功后，服务器会返回 `token` 字段，后续请求需要在 HTTP Header 中携带：

```
Authorization: Bearer <token>
```

---

## REST API 接口

### 1. 认证模块 `/api/auth`

#### 1.1 用户注册

```http
POST /api/auth/register
Content-Type: application/json
```

**请求体：**
```json
{
  "username": "string",  // 用户名（必填，3-20字符）
  "password": "string",  // 密码（必填，6-20字符）
  "nickname": "string"   // 昵称（必填，1-20字符）
}
```

**响应：**
```json
{
  "success": true,
  "message": "注册成功",
  "user": {
    "id": 123456789,
    "username": "player1",
    "nickname": "棋手一号",
    "rating": 1200,
    "level": 1,
    "avatar": ""
  }
}
```

#### 1.2 用户登录

```http
POST /api/auth/login
Content-Type: application/json
```

**请求体：**
```json
{
  "username": "string",  // 用户名
  "password": "string"   // 密码
}
```

**响应：**
```json
{
  "success": true,
  "message": "登录成功",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 123456789,
    "username": "player1",
    "nickname": "棋手一号",
    "rating": 1200,
    "level": 1,
    "avatar": ""
  }
}
```

#### 1.3 用户登出

```http
POST /api/auth/logout
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "message": "登出成功"
}
```

---

### 2. 用户模块 `/api/user`

#### 2.1 获取用户信息

```http
GET /api/user
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "user": {
    "id": 123456789,
    "username": "player1",
    "nickname": "棋手一号",
    "rating": 1250,
    "level": 2,
    "avatar": "",
    "status": 1,
    "createdAt": "2024-01-01 12:00:00"
  },
  "stats": {
    "userId": 123456789,
    "totalGames": 50,
    "wins": 30,
    "losses": 18,
    "draws": 2,
    "winRate": 60.0
  }
}
```

#### 2.2 更新用户信息

```http
PUT /api/user
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体：**
```json
{
  "nickname": "新昵称",
  "avatar": "头像URL"
}
```

**响应：**
```json
{
  "success": true,
  "message": "更新成功",
  "user": { ... }
}
```

---

### 3. 游戏模块 `/api/game`

#### 3.1 检查未完成游戏

```http
GET /api/game/unfinished
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "data": {
    "has_unfinished": true,
    "room_id": "room_123",
    "game_state": "PLAYING"
  }
}
```

#### 3.2 认输

```http
POST /api/game/resign
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "message": "已认输"
}
```

---

### 4. 房间模块 `/api/rooms`

#### 4.1 获取所有房间

```http
GET /api/rooms
```

**响应：**
```json
{
  "success": true,
  "data": {
    "count": 5,
    "rooms": [
      {
        "room_id": "room_123",
        "game_state": "PLAYING",
        "move_count": 15,
        "observer_count": 3,
        "black_player": {
          "id": 123,
          "nickname": "黑方选手",
          "rating": 1300
        },
        "white_player": {
          "id": 456,
          "nickname": "白方选手",
          "rating": 1250
        }
      }
    ]
  }
}
```

#### 4.2 获取进行中的房间

```http
GET /api/rooms/playing
```

**响应：** 同 4.1，但只返回游戏状态为 `PLAYING` 的房间

#### 4.3 获取房间观战者

```http
GET /api/rooms/{roomId}/observers
```

**响应：**
```json
{
  "success": true,
  "data": {
    "count": 2,
    "observers": [
      {
        "user_id": 789,
        "username": "observer1",
        "nickname": "观战者一号"
      }
    ]
  }
}
```

---

### 5. 好友模块 `/api/friends`

#### 5.1 获取好友列表

```http
GET /api/friends
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "friends": [
    {
      "id": 456,
      "username": "friend1",
      "nickname": "好友一号",
      "avatar": "",
      "rating": 1280,
      "level": 3,
      "status": 1,
      "isOnline": true
    }
  ]
}
```

#### 5.2 获取好友请求

```http
GET /api/friends/requests
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "requests": [
    {
      "id": 1,
      "senderId": 789,
      "senderName": "申请人",
      "message": "一起下棋吧",
      "createdAt": "2024-01-01 12:00:00"
    }
  ]
}
```

#### 5.3 发送好友请求

```http
POST /api/friends/request
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体：**
```json
{
  "username": "string",     // 对方用户名（与 target_id 二选一）
  "target_id": 123,         // 对方用户ID（与 username 二选一）
  "message": "string"       // 请求消息（可选）
}
```

**响应：**
```json
{
  "success": true,
  "message": "好友请求已发送"
}
```

#### 5.4 接受好友请求

```http
POST /api/friends/accept
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体：**
```json
{
  "requestId": 123
}
```

**响应：**
```json
{
  "success": true,
  "message": "已添加好友"
}
```

#### 5.5 拒绝好友请求

```http
POST /api/friends/reject
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体：**
```json
{
  "requestId": 123
}
```

**响应：**
```json
{
  "success": true,
  "message": "已拒绝好友请求"
}
```

#### 5.6 删除好友

```http
DELETE /api/friends/{friendId}
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "message": "已删除好友"
}
```

#### 5.7 发送私聊消息

```http
POST /api/friends/{friendId}/messages
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体：**
```json
{
  "content": "消息内容"
}
```

**响应：**
```json
{
  "success": true,
  "message": "发送成功",
  "data": {
    "message": {
      "id": 12345,
      "senderId": 123,
      "receiverId": 456,
      "content": "消息内容",
      "createdAt": "2024-01-01 12:00:00"
    }
  }
}
```

#### 5.8 获取私聊消息

```http
GET /api/friends/{friendId}/messages
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "data": {
    "messages": [
      {
        "id": 12345,
        "senderId": 123,
        "receiverId": 456,
        "content": "消息内容",
        "createdAt": "2024-01-01 12:00:00",
        "isRead": true,
        "senderName": "发送者昵称"
      }
    ]
  }
}
```

#### 5.9 获取未读消息

```http
GET /api/friends/messages/unread
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "data": {
    "unread_count": 5,
    "messages": [ ... ]
  }
}
```

---

### 6. 排行榜模块 `/api/leaderboard`

#### 6.1 获取排行榜

```http
GET /api/leaderboard?limit=50
```

**查询参数：**
- `limit`: 返回数量，默认50，最大100

**响应：**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "username": "top1",
      "nickname": "棋圣",
      "rating": 2000,
      "level": 10
    }
  ]
}
```

---

### 7. 对局记录模块 `/api/records`

#### 7.1 获取最近对局记录

```http
GET /api/records/recent?limit=20
```

**响应：**
```json
{
  "success": true,
  "records": [
    {
      "id": 1,
      "roomId": "room_123",
      "blackPlayerId": 123,
      "whitePlayerId": 456,
      "winnerId": 123,
      "winColor": "black",
      "endReason": "FIVE_IN_ROW",
      "moveCount": 25,
      "duration": 300,
      "gameMode": "RANKED",
      "createdAt": "2024-01-01 12:00:00",
      "blackPlayer": { ... },
      "whitePlayer": { ... }
    }
  ]
}
```

#### 7.2 获取我的对局记录

```http
GET /api/records/my?limit=100
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "records": [ ... ],
  "total": 50
}
```

每条记录额外包含：
- `result`: "win" | "lose" | "draw"
- `myColor`: "black" | "white"
- `opponent`: 对手信息

#### 7.3 获取单条对局详情

```http
GET /api/records/{recordId}
```

**响应：**
```json
{
  "success": true,
  "record": {
    "id": 1,
    "roomId": "room_123",
    "moves": [
      { "x": 7, "y": 7, "color": 1 },
      { "x": 8, "y": 8, "color": 2 }
    ],
    ...
  }
}
```

#### 7.4 获取复盘数据

```http
GET /api/records/replay/{roomId}
```

**响应：**
```json
{
  "success": true,
  "data": {
    "roomId": "room_123",
    "blackPlayerId": 123,
    "whitePlayerId": 456,
    "winnerId": 123,
    "winColor": "black",
    "endReason": "FIVE_IN_ROW",
    "moveCount": 25,
    "duration": 300,
    "gameMode": "RANKED",
    "moves": [ ... ],
    "players": {
      "black": { ... },
      "white": { ... }
    },
    "winner": {
      "id": 123,
      "color": "black",
      "nickname": "黑方胜"
    }
  }
}
```

---

### 8. 残局模块 `/api/puzzles`

#### 8.1 获取残局列表

```http
GET /api/puzzles?difficulty=easy
```

**查询参数：**
- `difficulty`: 难度筛选，可选值：`easy`、`medium`、`hard`

**响应：**
```json
{
  "success": true,
  "data": {
    "puzzles": [
      {
        "id": 1,
        "title": "入门残局1",
        "description": "黑先胜",
        "difficulty": "easy",
        "optimalMoves": 3
      }
    ],
    "count": 10
  }
}
```

#### 8.2 获取残局详情

```http
GET /api/puzzles/{puzzleId}
```

**响应：**
```json
{
  "success": true,
  "data": {
    "puzzle": { ... },
    "solution": [[7,7], [8,8]],
    "hintMoves": [[7,7]],
    "pieceCount": { "black": 5, "white": 4 },
    "boardValid": true
  }
}
```

#### 8.3 获取我的残局记录

```http
GET /api/puzzles/my?difficulty=easy
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "data": {
    "records": [ ... ],
    "count": 5,
    "stats": {
      "total": 10,
      "completed": 5,
      "bestMoves": 3
    }
  }
}
```

#### 8.4 记录尝试

```http
POST /api/puzzles/{puzzleId}/attempt
Authorization: Bearer <token>
```

**响应：**
```json
{
  "success": true,
  "message": "记录成功"
}
```

#### 8.5 记录完成

```http
POST /api/puzzles/{puzzleId}/complete
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体：**
```json
{
  "moves": 5,
  "time": 120,
  "solutionPath": [[7,7], [8,8]]
}
```

**响应：**
```json
{
  "success": true,
  "message": "恭喜通关！",
  "data": {
    "stars": 3,
    "optimalMoves": 3,
    "isNewRecord": true
  }
}
```

#### 8.6 获取残局排行榜

```http
GET /api/puzzles/stats/leaderboard
```

**响应：**
```json
{
  "success": true,
  "data": {
    "leaderboard": [ ... ],
    "count": 50
  }
}
```

#### 8.7 获取残局统计

```http
GET /api/puzzles/stats/summary
```

**响应：**
```json
{
  "success": true,
  "data": {
    "difficultyCounts": {
      "easy": 10,
      "medium": 15,
      "hard": 5
    },
    "totalPuzzles": 30
  }
}
```

---

### 9. 其他接口

#### 9.1 健康检查

```http
GET /api/health
```

**响应：**
```json
{
  "success": true,
  "data": {
    "status": "ok"
  }
}
```

#### 9.2 心跳

```http
GET /api/heartbeat
```

**响应：**
```json
{
  "success": true,
  "timestamp": 1704110400000
}
```

#### 9.3 统计数据

```http
GET /api/stats
```

**响应：**
```json
{
  "success": true,
  "data": {
    "onlineUsers": 100,
    "playingRooms": 20,
    "totalUsers": 10000
  }
}
```

---

## WebSocket 消息协议

### 连接

```
ws://your-server:8083/ws
```

连接成功后，需要先发送登录消息进行认证。

### 消息格式

```json
{
  "type": "消息类型",
  "data": { ... }
}
```

### 消息类型

#### 客户端发送

| 类型 | 说明 | data 字段 |
|------|------|-----------|
| `login` | 登录认证 | `{ "token": "jwt_token" }` |
| `match_join` | 加入匹配队列 | `{ "mode": "ranked" }` |
| `match_cancel` | 取消匹配 | `{}` |
| `move` | 落子 | `{ "x": 7, "y": 7 }` |
| `resign` | 认输 | `{}` |
| `chat_send` | 发送聊天 | `{ "content": "消息", "type": "public" }` |
| `observer_join` | 加入观战 | `{ "roomId": "room_id" }` |
| `observer_leave` | 离开观战 | `{}` |

#### 服务器推送

| 类型 | 说明 | data 字段 |
|------|------|-----------|
| `login_success` | 登录成功 | `{ "user": {...} }` |
| `match_found` | 匹配成功 | `{ "roomId": "...", "opponent": {...} }` |
| `game_start` | 游戏开始 | `{ "roomId": "...", "yourColor": "black" }` |
| `move` | 对手落子 | `{ "x": 7, "y": 7, "color": 2 }` |
| `game_over` | 游戏结束 | `{ "winner": "black", "reason": "..." }` |
| `chat_message` | 聊天消息 | `{ "sender": "...", "content": "..." }` |
| `error` | 错误消息 | `{ "message": "错误描述" }` |

---

## 错误码

| HTTP 状态码 | 说明 |
|------------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未授权（token 无效或过期） |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 409 | 资源冲突（如用户名已存在） |
| 500 | 服务器内部错误 |

---

## 使用示例

### JavaScript (浏览器)

```javascript
// 登录
const loginResponse = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'player1', password: 'password' })
});
const { token } = await loginResponse.json();

// 获取用户信息
const userResponse = await fetch('/api/user', {
  headers: { 'Authorization': `Bearer ${token}` }
});
const userData = await userResponse.json();

// WebSocket 连接
const ws = new WebSocket('ws://localhost:8083/ws');
ws.onopen = () => {
  ws.send(JSON.stringify({
    type: 'login',
    data: { token }
  }));
};
ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  console.log('收到消息:', message);
};
```

### Python

```python
import requests

# 登录
response = requests.post('http://localhost:8083/api/auth/login', json={
    'username': 'player1',
    'password': 'password'
})
token = response.json()['token']

# 获取用户信息
headers = {'Authorization': f'Bearer {token}'}
response = requests.get('http://localhost:8083/api/user', headers=headers)
print(response.json())
```

---

## 更新日志

- **v1.0.0** (2024-01-01): 初始版本，基础 API 接口
- **v1.1.0** (2024-02-01): 新增残局模块
- **v1.2.0** (2024-03-01): 新增好友私聊功能
