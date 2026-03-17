# ✅ 数据库存储功能完成总结

## 🎯 完成的工作

### 1. 数据库结构 ✅

项目已包含完整的数据库表结构：

- **user** - 用户信息表（用户名、积分、等级等）
- **user_stats** - 用户统计表（胜率、连胜、总局数等）
- **game_record** - 对局记录表（完整对局数据）
- **friend** - 好友关系表
- **chat_message** - 聊天记录表

所有表结构定义在：
- `src/main/resources/schema.sql`
- `init-database.sql`

### 2. 数据访问层 ✅

MyBatis Mapper 接口：

| Mapper | 功能 | 文件 |
|--------|------|------|
| UserMapper | 用户CRUD、排行榜 | `UserMapper.java` |
| UserStatsMapper | 用户统计更新 | `UserStatsMapper.java` |
| GameRecordMapper | 对局记录查询 | `GameRecordMapper.java` |
| FriendMapper | 好友管理 | `FriendMapper.java` |
| ChatMessageMapper | 聊天记录 | `ChatMessageMapper.java` |

### 3. 业务服务层 ✅

| Service | 功能 | 状态 |
|---------|------|------|
| UserService | 用户管理、积分更新 | ✅ 已完善 |
| GameService | 游戏逻辑、**自动保存对局** | ✅ 已完善 |
| GameRecordService | **对局记录管理** | ✅ 新增 |
| FriendService | 好友管理 | ✅ 已有 |
| ChatService | 聊天管理 | ✅ 已有 |

### 4. 自动保存机制 ✅

**游戏结束自动保存流程：**

```
GameService.handleGameOver()
├── 计算积分变化 (ELO算法)
├── 更新用户积分 (UserService.updateUserRating)
├── 保存对局记录 (saveGameRecord → GameRecordMapper.insert)
├── 更新用户统计 (updateUserStats → UserStatsMapper.update)
└── 发送游戏结束消息给客户端
```

**保存的数据包括：**
- 完整的棋盘状态
- 所有落子记录（JSON格式）
- 双方积分变化
- 对局时长、落子数
- 结束原因（胜利/平局/认输/超时）

### 5. 工具类 ✅

新增的工具类：

| 类 | 功能 | 文件 |
|---|------|------|
| **DatabaseUtil** | 数据库查询工具 | `DatabaseUtil.java` |
| **DatabaseCLI** | 命令行查询界面 | `DatabaseCLI.java` |

**DatabaseUtil 方法：**
```java
dbUtil.checkConnection()                    // 检查连接
dbUtil.getStats()                           // 获取统计
dbUtil.printStats()                         // 打印统计
dbUtil.printLeaderboard(10)                 // 打印排行榜
dbUtil.printUserInfo(userId)                 // 打印用户信息
dbUtil.printUserHistory(userId, 20)          // 打印对局历史
dbUtil.printGameDetail(roomId)               // 打印对局详情
```

### 6. 文档 ✅

| 文档 | 说明 |
|------|------|
| `DATABASE_STORAGE_GUIDE.md` | **完整数据库使用指南** |
| `DATABASE_QUICK_REF.md` | **快速参考手册** |

### 7. 初始化脚本 ✅

| 脚本 | 平台 | 用途 |
|------|------|------|
| `init-db.sh` | Linux/Mac | 数据库初始化 |
| `init-db.bat` | Windows | 数据库初始化 |

### 8. 命令行工具 ✅

启动CLI工具：
```bash
# 编译项目
mvn clean compile

# 启动数据库查询工具
mvn exec:java -Dexec.mainClass="com.gobang.GobangServer" --cli

# 或使用编译后的jar
java -jar gobang-server.jar --cli
```

## 📊 数据存储详情

### 对局记录保存示例

当一局游戏结束时，系统会自动：

```java
// GameService.saveGameRecord() 调用流程
GameRecord record = new GameRecord();
record.setRoomId("room_12345");
record.setBlackPlayerId(1L);
record.setWhitePlayerId(2L);
record.setWinnerId(1L);              // 黑方获胜
record.setWinColor(1);                // 黑方胜
record.setEndReason(0);               // 正常胜利
record.setMoveCount(45);              // 共45手
record.setDuration(180);              // 3分钟
record.setBlackRatingBefore(1200);
record.setBlackRatingAfter(1216);
record.setBlackRatingChange(16);
record.setWhiteRatingBefore(1180);
record.setWhiteRatingAfter(1164);
record.setWhiteRatingChange(-16);
record.setBoardState("0,1,0,0...");    // 棋盘状态
record.setMoves("[[7,7],[7,8]...]"); // 落子记录

// 保存到数据库
gameRecordMapper.insert(record);
```

### 积分计算 (ELO)

```java
// ELO计算示例
黑方积分: 1200, 白方积分: 1180

// 预期得分
黑方预期 = 1 / (1 + 10^((1180-1200)/400)) ≈ 0.53
白方预期 = 1 / (1 + 10^((1200-1180)/400)) ≈ 0.47

// 实际得分
黑方胜: 实际=1, 白方=0

// 积分变化
黑方 = 32 × (1 - 0.53) = +16
白方 = 32 × (0 - 0.47) = -16

// 新积分
黑方: 1200 + 16 = 1216 (+10经验)
白方: 1180 - 16 = 1164 (+2经验)
```

## 🚀 使用方法

### 1. 初始化数据库

```bash
# 方式一：使用脚本
./init-db.sh root your_password

# 方式二：手动执行
mysql -u root -p < init-database.sql
```

### 2. 配置数据库连接

编辑 `src/main/resources/application.yml`:
```yaml
database:
  url: jdbc:mysql://localhost:3306/gobang?useSSL=false&serverTimezone=UTC
  username: root
  password: your_password
```

### 3. 启动服务器

```bash
mvn clean compile exec:java -Dexec.mainClass="com.gobang.GobangServer"
```

### 4. 查询数据库

**方式一：使用CLI工具**
```bash
java -jar gobang-server.jar --cli
```

**方式二：使用Java代码**
```java
DatabaseUtil dbUtil = new DatabaseUtil(sqlSessionFactory);
dbUtil.printStats();           // 打印统计
dbUtil.printLeaderboard(10);   // 打印排行榜
dbUtil.printUserInfo(1L);       // 打印用户信息
```

**方式三：直接SQL查询**
```sql
-- 查看用户统计
SELECT u.username, u.rating, s.total_games, s.wins
FROM user u
LEFT JOIN user_stats s ON u.id = s.user_id
ORDER BY u.rating DESC;
```

## 📝 数据查询示例

### 查询用户对局历史

```java
UserService userService = ...;
List<GameRecord> history = recordService.getUserGameHistory(userId, 20);
```

### 查询排行榜

```java
List<User> leaderboard = userService.getLeaderboard(100);
```

### 查询用户统计

```java
UserStats stats = userService.getUserStats(userId);
System.out.println("胜率: " + stats.getWinRate() + "%");
System.out.println("最高连胜: " + stats.getMaxStreak());
```

## ⚠️ 注意事项

1. **游客用户不保存** - userId < 0 的游客数据不保存到数据库
2. **休闲模式不计分** - 休闲模式对局记录保存但不影响积分
3. **并发处理** - 使用MyBatis的SqlSession确保事务安全
4. **数据备份** - 建议每天备份数据库

## 📚 相关文件清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `src/main/java/com/gobang/service/GameRecordService.java` | 对局记录服务 |
| `src/main/java/com/gobang/util/DatabaseUtil.java` | 数据库工具类 |
| `src/main/java/com/gobang/cli/DatabaseCLI.java` | 命令行查询工具 |
| `src/main/resources/static/js/config.js` | 前端配置管理 |
| `DATABASE_STORAGE_GUIDE.md` | 完整使用指南 |
| `DATABASE_QUICK_REF.md` | 快速参考手册 |
| `init-db.sh` | Linux/Mac初始化脚本 |
| `init-db.bat` | Windows初始化脚本 |

### 更新的文件

| 文件 | 更新内容 |
|------|---------|
| `README.md` | 添加数据库功能说明 |
| `src/main/resources/application.yml` | 添加WebSocket配置 |
| `src/main/java/com/gobang/config/AppConfig.java` | 添加WebSocketConfig |
| `src/main/java/com/gobang/GobangServer.java` | 添加CLI工具支持 |
| `src/main/java/com/gobang/service/GameService.java` | 已有自动保存功能 |

## ✅ 功能验证清单

- [x] 数据库表结构完整
- [x] MyBatis Mapper 接口完整
- [x] 对局记录自动保存
- [x] 用户积分自动更新
- [x] 用户统计自动更新
- [x] 排行榜查询功能
- [x] 对局历史查询功能
- [x] 数据库初始化脚本
- [x] 命令行查询工具
- [x] 完整文档说明

## 🎉 总结

所有对局数据、用户信息和积分信息都会**自动保存**到MySQL数据库中！

游戏结束后无需任何手动操作，系统会自动：
1. 保存完整对局记录（棋盘、落子、积分变化）
2. 更新双方玩家积分
3. 更新用户统计数据

详细使用说明请参考：
- 📖 [DATABASE_STORAGE_GUIDE.md](DATABASE_STORAGE_GUIDE.md) - 完整指南
- 📋 [DATABASE_QUICK_REF.md](DATABASE_QUICK_REF.md) - 快速参考
