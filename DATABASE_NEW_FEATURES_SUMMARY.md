# 🎉 新增数据库功能总结

## 📋 新增内容

### 1. 新增数据库表

| 表名 | 说明 | 优先级 |
|------|------|--------|
| `user_settings` | 用户个性化设置 | 🔴 高 |
| `user_activity_log` | 用户活动日志 | 🔴 高 |
| `game_favorite` | 对局收藏 | 🟡 中 |
| `game_invitation` | 游戏邀请 | 🔴 高 |

### 2. 新增实体类 (Entity)

| 类 | 文件 | 说明 |
|---|------|------|
| `UserSettings` | `model/entity/UserSettings.java` | 用户设置 |
| `UserActivityLog` | `model/entity/UserActivityLog.java` | 活动日志 |
| `GameFavorite` | `model/entity/GameFavorite.java` | 对局收藏 |
| `GameInvitation` | `model/entity/GameInvitation.java` | 游戏邀请 |

### 3. 新增Mapper接口

| Mapper | 文件 | 主要方法 |
|--------|------|----------|
| `UserSettingsMapper` | `mapper/UserSettingsMapper.java` | 获取/更新用户设置 |
| `UserActivityLogMapper` | `mapper/UserActivityLogMapper.java` | 记录/查询活动日志 |
| `GameFavoriteMapper` | `mapper/GameFavoriteMapper.java` | 收藏管理 |
| `GameInvitationMapper` | `mapper/GameInvitationMapper.java` | 邀请管理 |

### 4. 新增服务类 (Service)

| Service | 文件 | 功能 |
|---------|------|------|
| `UserSettingsService` | `service/UserSettingsService.java` | 用户设置管理 |
| `ActivityLogService` | `service/ActivityLogService.java` | 活动日志记录 |
| `GameFavoriteService` | `service/GameFavoriteService.java` | 对局收藏管理 |
| `GameInvitationService` | `service/GameInvitationService.java` | 游戏邀请管理 |

---

## 📖 详细功能说明

### 1. 用户设置 (UserSettings)

**功能**: 保存用户的个性化设置

**字段**:
- 音效开关/音量
- 音乐开关/音量
- 棋盘主题
- 棋子样式
- 语言设置
- 时区设置
- 其他界面偏好

**使用示例**:
```java
// 获取服务
UserSettingsService settingsService = new UserSettingsService(sqlSessionFactory);

// 获取用户设置
UserSettings settings = settingsService.getUserSettings(userId);

// 更新设置
Map<String, Object> updates = new HashMap<>();
updates.put("soundEnabled", false);
updates.put("boardTheme", "wood");
settingsService.updateUserSettings(userId, updates);

// 获取单个设置
Boolean soundEnabled = settingsService.getSetting(userId, "soundEnabled", Boolean.class);

// 重置为默认值
settingsService.resetToDefault(userId);
```

**前端API示例**:
```javascript
// 获取设置
GET /api/settings

// 更新设置
POST /api/settings
{
  "soundEnabled": false,
  "boardTheme": "wood"
}
```

---

### 2. 用户活动日志 (UserActivityLog)

**功能**: 记录用户的所有活动，用于安全审计和数据分析

**活动类型**:
- `login` - 用户登录
- `logout` - 用户登出
- `register` - 用户注册
- `match_start` - 开始匹配
- `match_success` - 匹配成功
- `game_start` - 游戏开始
- `game_end` - 游戏结束
- `resign` - 认输

**使用示例**:
```java
ActivityLogService logService = new ActivityLogService(sqlSessionFactory);

// 记录登录
logService.logLogin(userId, ipAddress, userAgent);

// 记录游戏开始
logService.logGameStart(userId, roomId);

// 记录游戏结束
logService.logGameEnd(userId, roomId, true);  // true=胜利

// 获取最近活动
List<UserActivityLog> activities = logService.getUserRecentActivities(userId, 20);

// 获取最后登录时间
UserActivityLog lastLogin = logService.getLastLogin(userId);

// 清理旧日志
int deleted = logService.cleanOldLogs(90);  // 保留90天
```

**前端API示例**:
```javascript
// 获取活动历史
GET /api/activity/history?userId=1&limit=20
```

---

### 3. 对局收藏 (GameFavorite)

**功能**: 用户可以收藏精彩对局，添加备注和标签

**字段**:
- 对局记录ID
- 收藏备注
- 标签（多个用逗号分隔）
- 是否公开

**使用示例**:
```java
GameFavoriteService favoriteService = new GameFavoriteService(sqlSessionFactory);

// 添加收藏
favoriteService.addFavorite(userId, gameRecordId, "精彩的对局", "战术,中盘");

// 取消收藏
favoriteService.removeFavorite(userId, gameRecordId);

// 获取用户收藏列表
List<GameFavorite> favorites = favoriteService.getUserFavorites(userId);

// 检查是否已收藏
boolean isFavorited = favoriteService.isFavorited(userId, gameRecordId);

// 更新收藏备注
favoriteService.updateFavorite(userId, gameRecordId, "新的备注", "战术,残局", true);

// 获取收藏数量
int count = favoriteService.getFavoriteCount(userId);
```

**前端API示例**:
```javascript
// 添加收藏
POST /api/favorites
{
  "gameRecordId": 123,
  "note": "精彩的对局",
  "tags": "战术,中盘"
}

// 获取收藏列表
GET /api/favorites

// 取消收藏
DELETE /api/favorites/{gameRecordId}

// 检查是否收藏
GET /api/favorites/check/{gameRecordId}
```

---

### 4. 游戏邀请 (GameInvitation)

**功能**: 好友间的游戏邀请系统

**状态**:
- `pending` - 待处理
- `accepted` - 已接受
- `rejected` - 已拒绝
- `timeout` - 已过期
- `cancelled` - 已取消

**使用示例**:
```java
GameInvitationService invitationService = new GameInvitationService(sqlSessionFactory);

// 发送邀请
Long invitationId = invitationService.sendInvitation(inviterId, inviteeId, "casual");

// 接受邀请
boolean accepted = invitationService.acceptInvitation(invitationId, roomId);

// 拒绝邀请
boolean rejected = invitationService.rejectInvitation(invitationId);

// 取消邀请
boolean cancelled = invitationService.cancelInvitation(invitationId);

// 获取待处理邀请
List<GameInvitation> pending = invitationService.getPendingInvitations(userId);

// 获取发送的邀请
List<GameInvitation> sent = invitationService.getSentInvitations(userId, 20);

// 清理过期邀请
int cleaned = invitationService.cleanExpiredInvitations();
```

**前端API示例**:
```javascript
// 发送邀请
POST /api/invitations
{
  "inviteeId": 123,
  "invitationType": "casual"
}

// 获取待处理邀请
GET /api/invitations/pending

// 接受邀请
POST /api/invitations/{id}/accept
{
  "roomId": "room_123"
}

// 拒绝邀请
POST /api/invitations/{id}/reject

// 取消邀请
DELETE /api/invitations/{id}
```

---

## 🗄️ 数据库初始化

### 方式一：更新现有数据库

```sql
-- 执行以下SQL添加新表
ALTER DATABASE gobang CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建新表（见 schema.sql 文件）
```

### 方式二：重新初始化

```bash
# 使用初始化脚本
./init-db.sh root your_password
```

---

## 🔧 配置更新

已在 `GobangServer.java` 中注册新的Mapper：

```java
configuration.addMapper(com.gobang.mapper.UserSettingsMapper.class);
configuration.addMapper(com.gobang.mapper.UserActivityLogMapper.class);
configuration.addMapper(com.gobang.mapper.GameFavoriteMapper.class);
configuration.addMapper(com.gobang.mapper.GameInvitationMapper.class);
```

---

## 📝 使用建议

### 1. 用户设置
- 用户首次登录时自动创建默认设置
- 前端在用户登录后立即获取设置
- 设置变更实时保存到数据库

### 2. 活动日志
- 所有关键操作都应记录日志
- 定期清理旧日志（建议保留90天）
- 可用于分析用户行为和安全审计

### 3. 对局收藏
- 公开的收藏可以被其他用户查看
- 支持按标签筛选收藏
- 可用于构建"精彩对局"功能

### 4. 游戏邀请
- 邀请有效期5分钟
- 定期清理过期邀请
- 同一对用户只能有一个待处理邀请

---

## 📚 相关文档

- [DATABASE_ENHANCEMENTS.md](DATABASE_ENHANCEMENTS.md) - 完整增强建议
- [DATABASE_STORAGE_GUIDE.md](DATABASE_STORAGE_GUIDE.md) - 数据库使用指南
- [DATABASE_QUICK_REF.md](DATABASE_QUICK_REF.md) - 快速参考

---

## ✅ 检查清单

- [x] 创建实体类
- [x] 创建Mapper接口
- [x] 创建服务类
- [x] 更新schema.sql
- [x] 注册新Mapper
- [ ] 创建API接口
- [ ] 前端集成
- [ ] 单元测试
- [ ] 集成测试

---

## 🚀 下一步

1. **创建API Controller** - 对外暴露RESTful API
2. **前端集成** - 在前端页面中调用新功能
3. **单元测试** - 测试各个服务方法
4. **集成测试** - 测试完整功能流程

需要我继续创建API接口吗？
