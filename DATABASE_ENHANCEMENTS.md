# 📊 数据库增强建议

## 当前已有的表

✅ user - 用户信息
✅ user_stats - 用户统计
✅ game_record - 对局记录
✅ friend - 好友关系
✅ chat_message - 聊天记录
✅ observer_record - 观战记录

## 🆕 建议新增的表

### 1. 匹配记录表 (match_log)
**用途**: 记录玩家的匹配历史，用于分析匹配效率和玩家行为

```sql
CREATE TABLE IF NOT EXISTS `match_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `match_mode` VARCHAR(20) NOT NULL COMMENT '匹配模式(casual=休闲,ranked=竞技,bot=人机)',
  `rating_before` INT COMMENT '匹配前积分',
  `queue_time` INT NOT NULL COMMENT '排队时长(秒)',
  `is_success` TINYINT NOT NULL DEFAULT 0 COMMENT '是否匹配成功',
  `match_success_time` INT COMMENT '匹配成功用时(秒)',
  `room_id` VARCHAR(64) COMMENT '匹配到的房间ID',
  `opponent_id` BIGINT COMMENT '对手ID',
  `opponent_rating` INT COMMENT '对手积分',
  `cancel_reason` VARCHAR(50) COMMENT '取消原因(timeout/manual)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '匹配时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_match_mode` (`match_mode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='匹配记录表';
```

### 2. 用户活动日志表 (user_activity_log)
**用途**: 记录用户登录/登出/操作行为，用于安全审计和行为分析

```sql
CREATE TABLE IF NOT EXISTS `user_activity_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `activity_type` VARCHAR(50) NOT NULL COMMENT '活动类型(login/logout/match/resign等)',
  `ip_address` VARCHAR(45) COMMENT 'IP地址',
  `user_agent` VARCHAR(500) COMMENT '浏览器/客户端信息',
  `activity_data` JSON COMMENT '活动附加数据',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '活动时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_activity_type` (`activity_type`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户活动日志表';
```

### 3. 对局收藏表 (game_favorite)
**用途**: 用户收藏的精彩对局，方便后续复盘

```sql
CREATE TABLE IF NOT EXISTS `game_favorite` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `game_record_id` BIGINT UNSIGNED NOT NULL COMMENT '对局记录ID',
  `note` VARCHAR(500) COMMENT '收藏备注',
  `tags` VARCHAR(200) COMMENT '标签(逗号分隔)',
  `is_public` TINYINT NOT NULL DEFAULT 1 COMMENT '是否公开',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_record` (`user_id`, `game_record_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_favorite_game` FOREIGN KEY (`game_record_id`) REFERENCES `game_record` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对局收藏表';
```

### 4. 成就系统表 (achievement & user_achievement)
**用途**: 游戏成就系统，增加用户粘性

```sql
-- 成就定义表
CREATE TABLE IF NOT EXISTS `achievement` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '成就ID',
  `code` VARCHAR(50) NOT NULL UNIQUE COMMENT '成就代码',
  `name` VARCHAR(100) NOT NULL COMMENT '成就名称',
  `description` VARCHAR(500) COMMENT '成就描述',
  `icon` VARCHAR(255) COMMENT '成就图标',
  `category` VARCHAR(50) COMMENT '成就类别(wins/streak/rating等)',
  `requirement` JSON COMMENT '达成条件',
  `reward_exp` INT DEFAULT 0 COMMENT '奖励经验',
  `reward_title` VARCHAR(50) COMMENT '奖励称号',
  `sort_order` INT DEFAULT 0 COMMENT '排序',
  `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成就定义表';

-- 用户成就表
CREATE TABLE IF NOT EXISTS `user_achievement` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `achievement_id` BIGINT UNSIGNED NOT NULL COMMENT '成就ID',
  `progress` INT DEFAULT 0 COMMENT '进度',
  `is_completed` TINYINT NOT NULL DEFAULT 0 COMMENT '是否完成',
  `completed_at` DATETIME COMMENT '完成时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_achievement` (`user_id`, `achievement_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_achievement_id` (`achievement_id`),
  CONSTRAINT `fk_ua_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ua_achievement` FOREIGN KEY (`achievement_id`) REFERENCES `achievement` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户成就表';
```

### 5. 用户设置表 (user_settings)
**用途**: 保存用户的个性化设置

```sql
CREATE TABLE IF NOT EXISTS `user_settings` (
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `sound_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用音效',
  `music_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用音乐',
  `sound_volume` INT DEFAULT 80 COMMENT '音效音量(0-100)',
  `music_volume` INT DEFAULT 60 COMMENT '音乐音量(0-100)',
  `board_theme` VARCHAR(50) DEFAULT 'classic' COMMENT '棋盘主题',
  `piece_style` VARCHAR(50) DEFAULT 'classic' COMMENT '棋子样式',
  `auto_match` TINYINT DEFAULT 1 COMMENT '是否自动匹配',
  `show_rating` TINYINT DEFAULT 1 COMMENT '是否显示积分',
  `language` VARCHAR(10) DEFAULT 'zh-CN' COMMENT '语言设置',
  `timezone` VARCHAR(50) DEFAULT 'Asia/Shanghai' COMMENT '时区',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_settings_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置表';
```

### 6. 举报记录表 (report)
**用途**: 用户举报系统，管理违规行为

```sql
CREATE TABLE IF NOT EXISTS `report` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '举报ID',
  `reporter_id` BIGINT UNSIGNED NOT NULL COMMENT '举报人ID',
  `reported_user_id` BIGINT UNSIGNED COMMENT '被举报用户ID',
  `report_type` VARCHAR(50) NOT NULL COMMENT '举报类型(cheating/abuse/fake等)',
  `report_reason` VARCHAR(500) COMMENT '举报原因',
  `related_game_id` BIGINT COMMENT '相关对局ID',
  `evidence` JSON COMMENT '证据数据',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态(pending/processing/resolved/rejected)',
  `admin_note` VARCHAR(500) COMMENT '管理员备注',
  `processed_by` BIGINT COMMENT '处理人ID',
  `processed_at` DATETIME COMMENT '处理时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报时间',
  PRIMARY KEY (`id`),
  KEY `idx_reporter_id` (`reporter_id`),
  KEY `idx_reported_user_id` (`reported_user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报记录表';
```

### 7. 游戏邀请表 (game_invitation)
**用途**: 好友间的游戏邀请记录

```sql
CREATE TABLE IF NOT EXISTS `game_invitation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '邀请ID',
  `inviter_id` BIGINT UNSIGNED NOT NULL COMMENT '邀请人ID',
  `invitee_id` BIGINT UNSIGNED NOT NULL COMMENT '被邀请人ID',
  `invitation_type` VARCHAR(20) NOT NULL COMMENT '邀请类型(casual/ranked)',
  `room_id` VARCHAR(64) COMMENT '房间ID(接受后创建)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态(pending/accepted/rejected/timeout)',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `responded_at` DATETIME COMMENT '响应时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '邀请时间',
  PRIMARY KEY (`id`),
  KEY `idx_inviter_id` (`inviter_id`),
  KEY `idx_invitee_id` (`invitee_id`),
  KEY `idx_status` (`status`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏邀请表';
```

### 8. 排行榜快照表 (ranking_snapshot)
**用途**: 定期保存排行榜历史，用于分析和展示

```sql
CREATE TABLE IF NOT EXISTS `ranking_snapshot` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '快照ID',
  `snapshot_date` DATE NOT NULL COMMENT '快照日期',
  `snapshot_type` VARCHAR(20) NOT NULL COMMENT '快照类型(daily/weekly/monthly)',
  `rank_data` JSON NOT NULL COMMENT '排行榜数据(JSON数组)',
  `total_players` INT NOT NULL COMMENT '总玩家数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date_type` (`snapshot_date`, `snapshot_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排行榜快照表';
```

### 9. 用户签到表 (user_checkin)
**用途**: 每日签到奖励系统

```sql
CREATE TABLE IF NOT EXISTS `user_checkin` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '签到ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `checkin_date` DATE NOT NULL COMMENT '签到日期',
  `consecutive_days` INT DEFAULT 1 COMMENT '连续签到天数',
  `reward_exp` INT DEFAULT 0 COMMENT '奖励经验',
  `reward_item` VARCHAR(100) COMMENT '奖励物品',
  `ip_address` VARCHAR(45) COMMENT 'IP地址',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `checkin_date`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_checkin_date` (`checkin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户签到表';
```

### 10. 对局评论表 (game_comment)
**用途**: 用户对对局的评论和讨论

```sql
CREATE TABLE IF NOT EXISTS `game_comment` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `game_record_id` BIGINT UNSIGNED NOT NULL COMMENT '对局记录ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `parent_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '父评论ID(回复)',
  `content` VARCHAR(1000) NOT NULL COMMENT '评论内容',
  `likes` INT DEFAULT 0 COMMENT '点赞数',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_game_record_id` (`game_record_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对局评论表';
```

## 🎯 优先级建议

### 🔴 高优先级 (建议立即实现)

1. **user_settings** - 用户个性化设置
   - 提升用户体验
   - 实现简单
   - 用户需求强烈

2. **user_activity_log** - 用户活动日志
   - 安全审计需要
   - 帮助分析用户行为
   - 异常检测

3. **game_invitation** - 游戏邀请
   - 好友对战功能必需
   - 增强社交功能

### 🟡 中优先级 (可以考虑实现)

4. **game_favorite** - 对局收藏
   - 增加用户粘性
   - 方便复盘学习

5. **match_log** - 匹配记录
   - 优化匹配算法
   - 分析系统性能

6. **achievement** - 成就系统
   - 增加游戏趣味性
   - 提高用户留存

### 🟢 低优先级 (未来可扩展)

7. **report** - 举报系统
   - 需要管理后台支持
   - 社区成熟后再考虑

8. **ranking_snapshot** - 排行榜快照
   - 数据分析用途
   - 可通过日志分析替代

9. **user_checkin** - 签到系统
   - 运营活动相关
   - 可后续添加

10. **game_comment** - 对局评论
    - 需要内容审核
    - 社区功能扩展

## 📋 实现建议

### 第一阶段 (核心功能)

```sql
-- 创建优先级最高的表
CREATE TABLE user_settings (...);
CREATE TABLE user_activity_log (...);
CREATE TABLE game_invitation (...);
```

### 第二阶段 (增强功能)

```sql
CREATE TABLE game_favorite (...);
CREATE TABLE match_log (...);
CREATE TABLE achievement (...);
CREATE TABLE user_achievement (...);
```

### 第三阶段 (扩展功能)

```sql
CREATE TABLE report (...);
CREATE TABLE ranking_snapshot (...);
CREATE TABLE user_checkin (...);
CREATE TABLE game_comment (...);
```

## 🔧 相关文件

需要创建的文件：

```
src/main/java/com/gobang/
├── model/entity/
│   ├── UserSettings.java
│   ├── UserActivityLog.java
│   ├── GameFavorite.java
│   ├── GameInvitation.java
│   ├── Achievement.java
│   ├── UserAchievement.java
│   └── Report.java
├── mapper/
│   ├── UserSettingsMapper.java
│   ├── UserActivityLogMapper.java
│   ├── GameFavoriteMapper.java
│   └── GameInvitationMapper.java
└── service/
    ├── UserSettingsService.java
    ├── ActivityLogService.java
    └── GameFavoriteService.java
```

是否需要我创建这些表的实体类和Mapper？
