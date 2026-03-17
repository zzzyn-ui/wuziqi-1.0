# 📊 数据库存储快速参考

## 🚀 快速开始

### 1. 初始化数据库

```bash
# Linux/Mac
chmod +x init-db.sh
./init-db.sh root your_password

# Windows
init-db.bat root your_password
```

### 2. 配置数据库连接

编辑 `src/main/resources/application.yml`:

```yaml
database:
  url: jdbc:mysql://localhost:3306/gobang?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
  username: root
  password: your_password
```

### 3. 启动服务器

```bash
mvn clean compile exec:java -Dexec.mainClass="com.gobang.GobangServer"
```

## 📁 数据库表结构

| 表名 | 说明 | 主要字段 |
|------|------|----------|
| `user` | 用户信息 | id, username, nickname, rating, level, exp |
| `user_stats` | 用户统计 | user_id, total_games, wins, losses, win_rate |
| `game_record` | 对局记录 | room_id, black_player_id, white_player_id, winner_id, moves |
| `friend` | 好友关系 | user_id, friend_id, status |
| `chat_message` | 聊天记录 | sender_id, receiver_id, content |

## 🔄 自动保存机制

游戏结束后自动保存以下数据：

```java
// GameService.java - handleGameOver()
private void handleGameOver(GameRoom room, Long winnerId, int endReason) {
    // 1. 计算积分变化
    int[] ratingChanges = ELOCalculator.calculateRatingChange(...);

    // 2. 更新用户积分
    userService.updateUserRating(userId, newRating, exp);

    // 3. 保存对局记录
    saveGameRecord(room, winnerId, endReason, ratingChanges);

    // 4. 更新用户统计
    updateUserStats(...);
}
```

## 📈 积分计算

### ELO公式

```
预期得分 = 1 / (1 + 10^((对手积分 - 我的积分) / 400))
积分变化 = K × (实际得分 - 预期得分)
```

### 参数

| 参数 | 值 | 说明 |
|------|---|------|
| K | 32 | 标准K值 |
| 实际得分 | 1/0.5/0 | 胜/平/负 |
| 变化范围 | -32 ~ +32 | 单场最大变化 |

### 经验值

| 结果 | 经验值 |
|------|--------|
| 胜利 | +10 |
| 失败 | +2 |
| 平局 | +5 |

## 🔍 查询示例

### Java代码

```java
// 获取用户信息
UserService userService = ...;
User user = userService.getUserById(userId);

// 获取用户统计
UserStats stats = userService.getUserStats(userId);

// 获取对局历史
List<GameRecord> history = recordService.getUserGameHistory(userId, 20);

// 获取排行榜
List<User> leaderboard = userService.getLeaderboard(100);
```

### SQL查询

```sql
-- 获取用户信息
SELECT * FROM user WHERE id = 1;

-- 获取用户统计
SELECT * FROM user_stats WHERE user_id = 1;

-- 获取对局历史
SELECT * FROM game_record
WHERE black_player_id = 1 OR white_player_id = 1
ORDER BY created_at DESC LIMIT 20;

-- 获取排行榜
SELECT u.*, s.wins, s.total_games,
       ROUND(s.wins * 100.0 / s.total_games, 2) AS win_rate
FROM user u
LEFT JOIN user_stats s ON u.id = s.user_id
ORDER BY u.rating DESC
LIMIT 100;
```

## 🛠️ 工具类

### DatabaseUtil

```java
// 创建实例
DatabaseUtil dbUtil = new DatabaseUtil(sqlSessionFactory);

// 检查连接
boolean connected = dbUtil.checkConnection();

// 获取统计信息
DatabaseUtil.DatabaseStats stats = dbUtil.getStats();

// 打印统计信息
dbUtil.printStats();

// 打印排行榜
dbUtil.printLeaderboard(10);

// 打印用户信息
dbUtil.printUserInfo(userId);

// 打印用户历史
dbUtil.printUserHistory(userId, 20);

// 打印对局详情
dbUtil.printGameDetail(roomId);
```

### DatabaseCLI

```java
// 启动命令行查询工具
DatabaseCLI cli = new DatabaseCLI(sqlSessionFactory);
cli.run();
```

## 📊 数据完整性

### 外键关系

```
user_stats.user_id → user.id
game_record.black_player_id → user.id
game_record.white_player_id → user.id
friend.user_id → user.id
friend.friend_id → user.id
chat_message.sender_id → user.id
chat_message.receiver_id → user.id
```

### 级联规则

- 删除用户时，统计数据、好友关系、聊天消息会自动删除
- 对局记录永久保留（不级联删除）

## 🔧 故障排查

### 对局记录未保存

1. 检查数据库连接是否正常
2. 检查 `GameService.handleGameOver()` 是否被调用
3. 检查日志中的错误信息

### 积分未更新

1. 确认不是休闲模式（休闲模式不计分）
2. 确认用户已注册（游客不计分）
3. 检查 `UserService.updateUserRating()` 是否成功

### 统计数据不准确

1. 运行 `DatabaseUtil.printStats()` 检查数据
2. 手动执行SQL查询验证
3. 检查 `UserStatsMapper` 的更新逻辑

## 📝 最佳实践

1. **定期备份** - 每天备份数据库
2. **监控慢查询** - 使用 EXPLAIN 分析查询
3. **数据归档** - 定期将旧数据归档
4. **索引优化** - 为常用查询添加索引
5. **连接池配置** - 根据并发量调整连接池

## 📚 相关文档

- [DATABASE_STORAGE_GUIDE.md](DATABASE_STORAGE_GUIDE.md) - 完整使用指南
- [README.md](README.md) - 项目说明
- [SECURITY_FIXES_SUMMARY.md](SECURITY_FIXES_SUMMARY.md) - 安全修复总结
