# 五子棋游戏 - 数据库存储使用指南

## 📊 概述

本项目已完整实现对局数据、用户信息和积分数据的数据库存储功能。所有游戏相关数据都会自动保存到MySQL数据库中。

## 🗄️ 数据库表结构

### 1. 用户表 (user)

存储用户基本信息和积分数据。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 用户ID（主键） |
| username | VARCHAR(50) | 用户名（唯一） |
| password | VARCHAR(255) | 密码（BCrypt加密） |
| nickname | VARCHAR(50) | 昵称 |
| email | VARCHAR(100) | 邮箱 |
| avatar | VARCHAR(255) | 头像URL |
| rating | INT | ELO积分 |
| level | INT | 等级 |
| exp | INT | 经验值 |
| status | TINYINT | 状态（0=离线,1=在线,2=游戏中,3=匹配中） |
| last_online | DATETIME | 最后在线时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 2. 用户统计表 (user_stats)

存储用户的对局统计数据。

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | BIGINT | 用户ID（主键） |
| total_games | INT | 总对局数 |
| wins | INT | 胜场 |
| losses | INT | 负场 |
| draws | INT | 平场 |
| max_rating | INT | 最高积分 |
| current_streak | INT | 当前连胜/连负 |
| max_streak | INT | 最高连胜 |
| total_moves | INT | 总落子数 |
| avg_moves_per_game | DECIMAL(10,2) | 平均每局落子数 |
| fastest_win | INT | 最快获胜（秒） |

### 3. 对局记录表 (game_record)

存储每场对局的完整数据。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 对局ID（主键） |
| room_id | VARCHAR(64) | 房间ID（唯一） |
| black_player_id | BIGINT | 黑方玩家ID |
| white_player_id | BIGINT | 白方玩家ID |
| winner_id | BIGINT | 获胜者ID |
| win_color | TINYINT | 获胜方颜色（1=黑,2=白） |
| end_reason | TINYINT | 结束原因（0=胜利,1=失败,2=平局,3=认输,4=超时） |
| move_count | INT | 总落子数 |
| duration | INT | 对局时长（秒） |
| black_rating_before | INT | 黑方赛前积分 |
| black_rating_after | INT | 黑方赛后积分 |
| black_rating_change | INT | 黑方积分变化 |
| white_rating_before | INT | 白方赛前积分 |
| white_rating_after | INT | 白方赛后积分 |
| white_rating_change | INT | 白方积分变化 |
| board_state | TEXT | 最终棋盘状态 |
| moves | TEXT | 落子记录（JSON） |
| created_at | DATETIME | 对局时间 |

### 4. 好友表 (friend)

存储好友关系。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 关系ID（主键） |
| user_id | BIGINT | 用户ID |
| friend_id | BIGINT | 好友ID |
| status | TINYINT | 状态（0=待确认,1=已确认） |
| request_message | VARCHAR(255) | 申请消息 |
| created_at | DATETIME | 创建时间 |

### 5. 聊天消息表 (chat_message)

存储聊天记录。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 消息ID（主键） |
| sender_id | BIGINT | 发送者ID |
| receiver_id | BIGINT | 接收者ID（私聊，NULL为公聊） |
| room_id | VARCHAR(64) | 房间ID（公屏聊天） |
| content | VARCHAR(500) | 消息内容 |
| message_type | TINYINT | 消息类型（0=普通,1=系统） |
| is_read | TINYINT | 是否已读 |
| created_at | DATETIME | 创建时间 |

## 🔄 自动数据存储

### 对局数据自动保存

当游戏结束时，系统会自动执行以下操作：

```java
// GameService.java - handleGameOver() 方法
private void handleGameOver(GameRoom room, Long winnerId, int endReason) {
    // 1. 计算积分变化
    int[] ratingChanges = calculateRatingChanges(...);

    // 2. 更新用户积分
    userService.updateUserRating(userId, newRating, exp);

    // 3. 保存对局记录到数据库
    saveGameRecord(room, winnerId, endReason, ratingChanges);

    // 4. 更新用户统计
    updateUserStats(...);
}
```

### 用户统计自动更新

每次对局结束后，系统会自动更新：

- **对局统计**：总场数、胜场、负场、平场
- **积分统计**：当前积分、最高积分
- **连胜统计**：当前连胜、最高连胜
- **其他统计**：总落子数、平均每局落子数

## 📈 积分系统 (ELO)

项目使用ELO积分系统计算玩家积分变化：

```java
// ELO计算公式
E = 1 / (1 + 10^((对手积分 - 我的积分) / 400))

// 积分变化
新积分 = 旧积分 + K × (实际得分 - 预期得分)

// 其中 K = 32（标准值）
```

### 积分变化规则

| 结果 | 积分变化范围 | 经验值 |
|------|------------|--------|
| 胜利 | +16 ~ +32 | +10 |
| 失败 | -16 ~ -32 | +2 |
| 平局 | 0 ~ ±5 | +5 |

## 🛠️ 数据库初始化

### 1. 创建数据库

```bash
mysql -u root -p < init-database.sql
```

### 2. 配置数据库连接

编辑 `src/main/resources/application.yml`：

```yaml
database:
  driver: com.mysql.cj.jdbc.Driver
  url: jdbc:mysql://localhost:3306/gobang?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
  username: root
  password: your_password
  pool-size: 10
```

### 3. 启动服务器

```bash
mvn clean package
java -jar target/gobang-server.jar
```

## 📝 查询数据示例

### 获取用户对局历史

```java
// 通过 UserService
UserService userService = ...;
User user = userService.getUserById(userId);

// 通过 GameRecordService
GameRecordService recordService = ...;
List<GameRecord> history = recordService.getUserGameHistory(userId, 20);
```

### 获取排行榜

```java
UserService userService = ...;
List<User> leaderboard = userService.getLeaderboard(100);
```

### 获取用户统计

```java
UserService userService = ...;
UserStats stats = userService.getUserStats(userId);

double winRate = stats.getWinRate();
int totalGames = stats.getTotalGames();
```

## 🔌 API 接口

### 获取对局记录

```
GET /api/games/record?roomId=xxx
```

**响应：**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "room_id": "room123",
    "black_player": {
      "user_id": "1",
      "nickname": "玩家1"
    },
    "white_player": {
      "user_id": "2",
      "nickname": "玩家2"
    },
    "winner_id": "1",
    "move_count": 45,
    "duration": 300,
    "moves": [[7,7], [7,8], [8,7]]
  }
}
```

### 获取用户历史

```
GET /api/games/history?userId=1&limit=20
```

### 获取排行榜

```
GET /api/leaderboard?limit=100
```

## 📊 数据库视图

### 排行榜视图 (rating_leaderboard)

```sql
CREATE OR REPLACE VIEW rating_leaderboard AS
SELECT
  u.id,
  u.username,
  u.nickname,
  u.avatar,
  u.rating,
  u.level,
  s.total_games,
  s.wins,
  CASE WHEN s.total_games > 0
    THEN ROUND(s.wins * 100.0 / s.total_games, 2)
    ELSE 0
  END AS win_rate
FROM user u
LEFT JOIN user_stats s ON u.id = s.user_id
ORDER BY u.rating DESC
LIMIT 100;
```

## 🔍 数据完整性

### 外键约束

- `user_stats.user_id` → `user.id`
- `game_record.black_player_id` → `user.id`
- `game_record.white_player_id` → `user.id`
- `friend.user_id` → `user.id`
- `friend.friend_id` → `user.id`

### 级联操作

- 用户删除时，相关统计数据、好友关系、聊天消息会自动删除
- 对局记录永久保留（不级联删除）

## 🚀 性能优化

### 索引

```sql
-- 用户表索引
CREATE INDEX idx_rating ON user(rating);
CREATE INDEX idx_status ON user(status);
CREATE INDEX idx_last_online ON user(last_online);

-- 对局记录索引
CREATE INDEX idx_room_id ON game_record(room_id);
CREATE INDEX idx_black_player ON game_record(black_player_id);
CREATE INDEX idx_white_player ON game_record(white_player_id);
CREATE INDEX idx_created_at ON game_record(created_at);
```

### 分页查询

```java
// Mapper方法
@Select("SELECT * FROM game_record " +
        "WHERE black_player_id = #{userId} OR white_player_id = #{userId} " +
        "ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
List<GameRecord> findByUserIdPaged(
    @Param("userId") Long userId,
    @Param("offset") int offset,
    @Param("limit") int limit
);
```

## 📁 相关文件

| 文件 | 说明 |
|------|------|
| `init-database.sql` | 数据库初始化脚本 |
| `src/main/resources/schema.sql` | 表结构定义 |
| `UserMapper.java` | 用户数据访问 |
| `GameRecordMapper.java` | 对局记录访问 |
| `UserStatsMapper.java` | 用户统计访问 |
| `UserService.java` | 用户业务逻辑 |
| `GameRecordService.java` | 对局记录业务逻辑 |
| `GameService.java` | 游戏核心逻辑 |

## 💡 使用建议

1. **定期备份**：建议每天备份数据库
2. **数据归档**：定期将旧对局数据归档到历史表
3. **监控慢查询**：使用 EXPLAIN 分析慢查询
4. **连接池配置**：根据并发量调整数据库连接池大小
5. **Redis缓存**：热点数据使用Redis缓存

## ⚠️ 注意事项

1. 游客用户（userId < 0）的数据不会保存到数据库
2. 休闲模式的对局不计入积分变化
3. 对局记录中的棋盘状态和落子记录可能很大，注意存储空间
4. 删除用户前应考虑是否保留对局记录
