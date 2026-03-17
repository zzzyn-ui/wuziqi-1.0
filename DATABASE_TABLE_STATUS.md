# 📊 数据库表状态分析

## 🗄️ 表结构总览

项目共有 **10 个数据表**：

### 核心业务表 (6个)

| 表名 | 说明 | 初始状态 |
|------|------|---------|
| `user` | 用户信息 | ✅ 有测试数据 |
| `user_stats` | 用户统计 | ✅ 自动创建 |
| `game_record` | 对局记录 | 📭 空 (游戏后填充) |
| `friend` | 好友关系 | 📭 空 (添加好友后填充) |
| `chat_message` | 聊天消息 | 📭 空 (发送消息后填充) |
| `observer_record` | 观战记录 | 📭 空 (观战后填充) |

### 新增功能表 (4个)

| 表名 | 说明 | 初始状态 |
|------|------|---------|
| `user_settings` | 用户设置 | 📭 空 (首次访问创建) |
| `user_activity_log` | 活动日志 | 📭 空 (活动时记录) |
| `game_favorite` | 对局收藏 | 📭 空 (收藏后填充) |
| `game_invitation` | 游戏邀请 | 📭 空 (邀请后填充) |

---

## 📭 空表说明

### 1. game_record (对局记录表)

**状态**: 空表
**何时有数据**: 游戏对局结束后自动保存

**填充方式**:
```java
// GameService.handleGameOver() 自动调用
saveGameRecord(room, winnerId, endReason, ratingChanges);
```

**查看数据**:
```sql
SELECT COUNT(*) FROM game_record;
SELECT * FROM game_record ORDER BY created_at DESC LIMIT 10;
```

### 2. friend (好友关系表)

**状态**: 空表
**何时有数据**: 用户添加好友时产生

**填充方式**:
- 用户发送好友请求 → INSERT friend (status=0)
- 对方接受请求 → UPDATE friend (status=1)
- 对方拒绝 → DELETE friend

**查看数据**:
```sql
SELECT COUNT(*) FROM friend;
SELECT * FROM friend WHERE status = 1;  -- 已确认的好友关系
```

### 3. chat_message (聊天消息表)

**状态**: 空表
**何时有数据**: 用户发送聊天消息

**填充方式**:
```java
// ChatService 发送消息时自动保存
chatMessageMapper.insert(message);
```

**查看数据**:
```sql
SELECT COUNT(*) FROM chat_message;
SELECT * FROM chat_message ORDER BY created_at DESC LIMIT 20;
```

### 4. observer_record (观战记录表)

**状态**: 空表
**何时有数据**: 用户进入观战模式

**填充方式**:
```java
// ObserverManager 记录观战信息
observerMapper.insert(observerRecord);
```

### 5. user_settings (用户设置表)

**状态**: 空表
**何时有数据**: 用户首次访问设置页面

**填充方式**:
```java
// UserSettingsService 首次访问时自动创建默认设置
UserSettings settings = new UserSettings(userId);
settingsMapper.insert(settings);
```

**测试填充**:
```sql
-- 为用户1创建默认设置
INSERT INTO user_settings (user_id) VALUES (1);
```

### 6. user_activity_log (活动日志表)

**状态**: 空表
**何时有数据**: 用户登录、游戏等活动

**填充方式**:
```java
// ActivityLogService 记录活动
logService.logLogin(userId, ipAddress, userAgent);
logService.logGameEnd(userId, roomId, true);
```

**查看数据**:
```sql
SELECT COUNT(*) FROM user_activity_log;
SELECT * FROM user_activity_log ORDER BY created_at DESC LIMIT 20;
```

### 7. game_favorite (对局收藏表)

**状态**: 空表
**何时有数据**: 用户收藏对局

**填充方式**:
```javascript
// 前端调用API
api.addFavorite(gameRecordId, '精彩对局', '战术');
```

**查看数据**:
```sql
SELECT COUNT(*) FROM game_favorite;
SELECT * FROM game_favorite WHERE user_id = 1;
```

### 8. game_invitation (游戏邀请表)

**状态**: 空表
**何时有数据**: 用户发送游戏邀请

**填充方式**:
```javascript
// 前端调用API
api.sendInvitation(friendId, 'casual');
```

**查看数据**:
```sql
SELECT COUNT(*) FROM game_invitation;
SELECT * FROM game_invitation WHERE status = 'pending';
```

---

## 🔍 快速检查数据库状态

### 方式一：使用命令行工具

```bash
# 检查所有表状态
mvn compile exec:java -Dexec.mainClass="com.gobang.GobangServer" --check-db

# 只显示空表
mvn compile exec:java -Dexec.mainClass="com.gobang.GobangServer" --empty-tables

# 查看数据填充建议
mvn compile exec:java -Dexec.mainClass="com.gobang.GobangServer" --data-suggestions
```

### 方式二：使用CLI工具

```bash
# 启动数据库查询工具
mvn compile exec:java -Dexec.mainClass="com.gobang.GobangServer" --cli

# 然后选择菜单项 6 (检查表状态)
# 或选择菜单项 7 (查看数据填充建议)
```

### 方式三：直接SQL查询

```sql
-- 检查各表数据量
SELECT 'user' AS table_name, COUNT(*) AS count FROM user
UNION ALL
SELECT 'user_stats', COUNT(*) FROM user_stats
UNION ALL
SELECT 'game_record', COUNT(*) FROM game_record
UNION ALL
SELECT 'friend', COUNT(*) FROM friend
UNION ALL
SELECT 'chat_message', COUNT(*) FROM chat_message
UNION ALL
SELECT 'observer_record', COUNT(*) FROM observer_record
UNION ALL
SELECT 'user_settings', COUNT(*) FROM user_settings
UNION ALL
SELECT 'user_activity_log', COUNT(*) FROM user_activity_log
UNION ALL
SELECT 'game_favorite', COUNT(*) FROM game_favorite
UNION ALL
SELECT 'game_invitation', COUNT(*) FROM game_invitation
ORDER BY count DESC;
```

---

## 💡 数据填充优先级建议

### 高优先级 (核心功能)

1. **user_settings** - 影响用户体验
   - 首次访问设置页面时自动创建
   - 或预先为所有用户创建默认设置

2. **user_activity_log** - 重要，便于审计
   - 用户登录、游戏等活动时自动记录

### 中优先级 (增强功能)

3. **game_record** - 游戏核心
   - 进行游戏对局后自动保存

4. **game_invitation** - 社交功能
   - 用户发送邀请时产生

### 低优先级 (辅助功能)

5. **chat_message** - 聊天功能
6. **friend** - 好友系统
7. **observer_record** - 观战功能
8. **game_favorite** - 收藏功能

---

## 🚀 快速填充测试数据

### 创建测试用户设置

```sql
-- 为所有用户创建默认设置
INSERT INTO user_settings (user_id)
SELECT id FROM user
ON DUPLICATE KEY UPDATE user_id = user_id;
```

### 创建测试活动日志

```sql
-- 模拟用户登录
INSERT INTO user_activity_log (user_id, activity_type, ip_address, created_at)
SELECT id, 'login', '127.0.0.1', NOW() FROM user
WHERE id <= 3;  -- 为前3个用户创建登录记录
```

### 创建测试对局记录

```sql
-- 模拟游戏对局
INSERT INTO game_record (
    room_id, black_player_id, white_player_id, winner_id, win_color,
    end_reason, move_count, duration,
    black_rating_before, black_rating_after, black_rating_change,
    white_rating_before, white_rating_after, white_rating_change,
    moves, created_at
) VALUES (
    'test_room_001', 1, 2, 1, 1,  -- 用户1击败用户2
    0, 45, 180,                 -- 正常胜利，45手，3分钟
    1200, 1216, 16,              -- 黑方积分+16
    1180, 1164, -16,             -- 白方积分-16
    '[[7,7],[7,8],[8,7]]',     -- 测试落子记录
    NOW()
);
```

### 创建测试收藏

```sql
-- 模拟收藏对局
INSERT INTO game_favorite (user_id, game_record_id, note, tags, is_public, created_at)
VALUES (1, 1, '精彩的对局！', '战术,中盘', 1, NOW());
```

---

## 📈 数据增长预期

运行游戏服务器后，数据量会按以下方式增长：

| 表名 | 数据增长方式 | 预期增长速度 |
|------|-------------|-------------|
| `user` | 用户注册 | 慢 |
| `user_stats` | 注册/游戏时创建 | 慢 |
| `game_record` | 每局游戏结束 | 中 |
| `friend` | 好友操作 | 慢 |
| `chat_message` | 聊天消息 | 快 |
| `observer_record` | 观战记录 | 慢 |
| `user_settings` | 首次访问设置 | 慢 |
| `user_activity_log` | 用户活动 | 快 |
| `game_favorite` | 用户收藏 | 慢 |
| `game_invitation` | 游戏邀请 | 中 |

---

## 🎯 总结

### 有初始数据的表 (2个)
- ✅ `user` - 有测试用户
- ✅ `user_stats` - 自动为用户创建

### 空表 (8个)
- 📭 `game_record` - 游戏后自动填充
- 📭 `friend` - 好友功能填充
- 📭 `chat_message` - 聊天功能填充
- 📭 `observer_record` - 观战功能填充
- 📭 `user_settings` - 首次访问创建
- 📭 `user_activity_log` - 活动时记录
- 📭 `game_favorite` - 收藏时创建
- 📭 `game_invitation` - 邀请时创建

### 如何检查

```bash
# 编译项目
mvn clean compile

# 检查数据库状态
mvn exec:java -Dexec.mainClass="com.gobang.GobangServer" --check-db

# 查看空表列表
mvn exec:java -Dexec.mainClass="com.gobang.GobangServer" --empty-tables
```

所有空表都是**正常**的，会在使用应用时自动填充数据！
