# 📚 RESTful API 接口文档

## 基本信息

- **Base URL**: `http://localhost:9090/api`
- **认证方式**: Bearer Token (JWT)
- **响应格式**: JSON

## 通用响应格式

### 成功响应
```json
{
  "success": true,
  "data": { ... }
}
```

### 错误响应
```json
{
  "success": false,
  "message": "错误描述"
}
```

---

## 1. 用户设置 API

### 1.1 获取用户设置

```http
GET /api/settings
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "soundEnabled": true,
    "musicEnabled": true,
    "soundVolume": 80,
    "musicVolume": 60,
    "boardTheme": "classic",
    "pieceStyle": "classic",
    "autoMatch": true,
    "showRating": true,
    "language": "zh-CN",
    "timezone": "Asia/Shanghai"
  }
}
```

### 1.2 更新用户设置

```http
PUT /api/settings
Content-Type: application/json

{
  "soundEnabled": false,
  "boardTheme": "modern"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "设置已更新"
}
```

---

## 2. 活动日志 API

### 2.1 获取活动历史

```http
GET /api/activity/history?limit=20
```

**参数**:
- `limit` (可选): 返回数量，默认20

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "userId": 1,
      "activityType": "game_end",
      "activityData": {
        "roomId": "room_123",
        "isWin": true
      },
      "createdAt": "2024-03-16T10:30:00"
    }
  ]
}
```

---

## 3. 对局收藏 API

### 3.1 获取收藏列表

```http
GET /api/favorites
```

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "userId": 1,
      "gameRecordId": 123,
      "note": "精彩的对局",
      "tags": "战术,中盘",
      "isPublic": true,
      "createdAt": "2024-03-16T10:00:00"
    }
  ]
}
```

### 3.2 添加收藏

```http
POST /api/favorites
Content-Type: application/json

{
  "gameRecordId": 123,
  "note": "精彩的对局",
  "tags": "战术,中盘"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "收藏成功"
}
```

### 3.3 取消收藏

```http
DELETE /api/favorites/{gameRecordId}
```

**响应示例**:
```json
{
  "success": true,
  "message": "已取消收藏"
}
```

### 3.4 检查是否收藏

```http
GET /api/favorites/check/{gameRecordId}
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "favorited": true
  }
}
```

### 3.5 更新收藏备注

```http
PUT /api/favorites/{gameRecordId}
Content-Type: application/json

{
  "note": "新的备注",
  "tags": "战术,残局"
}
```

---

## 4. 游戏邀请 API

### 4.1 获取待处理邀请

```http
GET /api/invitations/pending
```

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "inviterId": 2,
      "invitationType": "casual",
      "status": "pending",
      "expiresAt": "2024-03-16T11:00:00",
      "createdAt": "2024-03-16T10:55:00"
    }
  ]
}
```

### 4.2 获取发送的邀请

```http
GET /api/invitations/sent?limit=20
```

**参数**:
- `limit` (可选): 返回数量，默认20

### 4.3 发送邀请

```http
POST /api/invitations
Content-Type: application/json

{
  "inviteeId": 2,
  "invitationType": "casual"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "邀请已发送",
  "data": {
    "invitationId": 1
  }
}
```

### 4.4 接受邀请

```http
POST /api/invitations/{invitationId}/accept
Content-Type: application/json

{
  "roomId": "room_12345"
}
```

**响应示例**:
```json
{
  "success": true,
  "message": "已接受邀请"
}
```

### 4.5 拒绝邀请

```http
POST /api/invitations/{invitationId}/reject
```

**响应示例**:
```json
{
  "success": true,
  "message": "已拒绝邀请"
}
```

### 4.6 取消邀请

```http
DELETE /api/invitations/{invitationId}
```

**响应示例**:
```json
{
  "success": true,
  "message": "已取消邀请"
}
```

---

## 5. 用户统计 API

### 5.1 获取用户统计

```http
GET /api/user/stats
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "totalGames": 100,
    "wins": 60,
    "losses": 35,
    "draws": 5,
    "maxRating": 1350,
    "currentStreak": 3,
    "maxStreak": 10
  }
}
```

---

## 错误码说明

| HTTP状态码 | 说明 |
|----------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 前端调用示例

### 使用 api-client.js

```javascript
// 获取用户设置
const settings = await api.getSettings();
console.log(settings.data);

// 更新设置
await api.updateSettings({ soundEnabled: false });

// 获取收藏列表
const favorites = await api.getFavorites();

// 添加收藏
await api.addFavorite(123, '精彩对局', '战术');

// 取消收藏
await api.removeFavorite(123);

// 发送邀请
await api.sendInvitation(2, 'casual');

// 接受邀请
await api.acceptInvitation(1, 'room_12345');
```

### 使用原生 fetch

```javascript
// 获取设置
const response = await fetch('/api/settings', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});
const data = await response.json();
```

---

## 认证说明

### 获取Token

Token在用户登录后自动保存到 `localStorage`:
```javascript
const token = localStorage.getItem('authToken');
```

### 在请求中使用

```javascript
fetch('/api/settings', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
});
```

---

## WebSocket 集成

虽然这些API使用HTTP协议，但游戏通信仍然使用WebSocket。API主要用于：

1. **配置管理** - 获取和保存用户设置
2. **数据查询** - 查询历史记录、收藏列表
3. **社交功能** - 好友邀请、收藏管理
4. **统计分析** - 用户活动日志

实时游戏通信（落子、匹配等）仍然通过WebSocket进行。

---

## 测试工具

### 使用 curl

```bash
# 获取设置
curl -H "Authorization: Bearer YOUR_TOKEN" \
     http://localhost:9090/api/settings

# 更新设置
curl -X PUT \
     -H "Authorization: Bearer YOUR_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"soundEnabled":false}' \
     http://localhost:9090/api/settings

# 添加收藏
curl -X POST \
     -H "Authorization: Bearer YOUR_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"gameRecordId":123,"note":"精彩"}' \
     http://localhost:9090/api/favorites
```

### 使用浏览器开发者工具

1. 打开Chrome DevTools (F12)
2. 切换到Console标签
3. 执行以下代码:

```javascript
// 测试API
api.getSettings().then(console.log);
api.getFavorites().then(console.log);
```

---

## 注意事项

1. **CORS**: API已配置CORS，允许跨域请求
2. **认证**: 大部分API需要登录认证
3. **频率限制**: 建议客户端实现请求频率限制
4. **错误处理**: 始终检查 `success` 字段
5. **数据验证**: 前端应验证用户输入

---

## 更新日志

### v1.0.0 (2024-03-16)
- ✅ 用户设置API
- ✅ 活动日志API
- ✅ 对局收藏API
- ✅ 游戏邀请API
- ✅ 用户统计API
