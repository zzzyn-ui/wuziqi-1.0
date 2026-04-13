-- 五子棋游戏数据库表结构

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(32) NOT NULL COMMENT '用户名',
  `password` VARCHAR(128) NOT NULL COMMENT '密码(BCrypt加密)',
  `nickname` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '昵称',
  `email` VARCHAR(64) DEFAULT NULL COMMENT '邮箱',
  `avatar` VARCHAR(255) DEFAULT '/default-avatar.png' COMMENT '头像URL',
  `rating` INT NOT NULL DEFAULT 1200 COMMENT 'ELO积分',
  `level` INT NOT NULL DEFAULT 1 COMMENT '等级',
  `exp` INT NOT NULL DEFAULT 0 COMMENT '经验值',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_online` DATETIME DEFAULT NULL COMMENT '最后在线时间',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0=离线,1=在线,2=游戏中,3=匹配中',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_rating` (`rating`),
  KEY `idx_last_online` (`last_online`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户统计表
CREATE TABLE IF NOT EXISTS `user_stats` (
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `total_games` INT NOT NULL DEFAULT 0 COMMENT '总对局数',
  `wins` INT NOT NULL DEFAULT 0 COMMENT '胜场',
  `losses` INT NOT NULL DEFAULT 0 COMMENT '负场',
  `draws` INT NOT NULL DEFAULT 0 COMMENT '平场',
  `max_rating` INT NOT NULL DEFAULT 1200 COMMENT '最高积分',
  `current_streak` INT NOT NULL DEFAULT 0 COMMENT '当前连胜/连负',
  `max_streak` INT NOT NULL DEFAULT 0 COMMENT '最高连胜',
  `total_moves` INT NOT NULL DEFAULT 0 COMMENT '总落子数',
  `avg_moves_per_game` DECIMAL(10,2) DEFAULT 0 COMMENT '平均每局落子数',
  `fastest_win` INT DEFAULT NULL COMMENT '最快获胜(秒)',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_stats_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户统计表';

-- 对局记录表
CREATE TABLE IF NOT EXISTS `game_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '对局ID',
  `room_id` VARCHAR(64) NOT NULL COMMENT '房间ID',
  `black_player_id` BIGINT UNSIGNED NOT NULL COMMENT '黑方玩家ID',
  `white_player_id` BIGINT UNSIGNED NOT NULL COMMENT '白方玩家ID',
  `winner_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '获胜者ID',
  `win_color` TINYINT DEFAULT NULL COMMENT '获胜方颜色:1=黑,2=白',
  `end_reason` TINYINT NOT NULL COMMENT '结束原因:0=胜利,1=失败,2=平局,3=认输,4=超时',
  `move_count` INT NOT NULL DEFAULT 0 COMMENT '总落子数',
  `duration` INT NOT NULL DEFAULT 0 COMMENT '对局时长(秒)',
  `black_rating_before` INT NOT NULL COMMENT '黑方积分变化前',
  `black_rating_after` INT NOT NULL COMMENT '黑方积分变化后',
  `black_rating_change` INT NOT NULL COMMENT '黑方积分变化',
  `white_rating_before` INT NOT NULL COMMENT '白方积分变化前',
  `white_rating_after` INT NOT NULL COMMENT '白方积分变化后',
  `white_rating_change` INT NOT NULL COMMENT '白方积分变化',
  `board_state` TEXT COMMENT '最终棋盘状态(压缩)',
  `moves` TEXT COMMENT '所有落子记录(JSON数组)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_id` (`room_id`),
  KEY `idx_black_player` (`black_player_id`),
  KEY `idx_white_player` (`white_player_id`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `fk_record_black` FOREIGN KEY (`black_player_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_record_white` FOREIGN KEY (`white_player_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对局记录表';

-- 好友关系表
CREATE TABLE IF NOT EXISTS `friend` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `friend_id` BIGINT UNSIGNED NOT NULL COMMENT '好友ID',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0=待确认,1=已确认',
  `request_message` VARCHAR(255) DEFAULT NULL COMMENT '申请消息',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
  KEY `idx_friend_id` (`friend_id`),
  CONSTRAINT `fk_friend_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_friend_target` FOREIGN KEY (`friend_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CHECK (`user_id` != `friend_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';

-- 聊天消息表
CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `sender_id` BIGINT UNSIGNED NOT NULL COMMENT '发送者ID',
  `receiver_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '接收者ID(私聊),NULL为公屏',
  `room_id` VARCHAR(64) DEFAULT NULL COMMENT '房间ID(公屏聊天)',
  `content` VARCHAR(500) NOT NULL COMMENT '消息内容',
  `message_type` TINYINT NOT NULL DEFAULT 0 COMMENT '消息类型:0=普通,1=系统',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读:0=未读,1=已读',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sender` (`sender_id`),
  KEY `idx_receiver` (`receiver_id`),
  KEY `idx_room` (`room_id`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `fk_chat_sender` FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_chat_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- 观战记录表
CREATE TABLE IF NOT EXISTS `observer_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `room_id` VARCHAR(64) NOT NULL COMMENT '房间ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '观战用户ID',
  `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `leave_time` DATETIME DEFAULT NULL COMMENT '离开时间',
  `duration` INT DEFAULT NULL COMMENT '观战时长(秒)',
  PRIMARY KEY (`id`),
  KEY `idx_room` (`room_id`),
  KEY `idx_user` (`user_id`),
  CONSTRAINT `fk_observer_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='观战记录表';

-- 排行榜视图(每周更新)
CREATE OR REPLACE VIEW `rating_leaderboard` AS
SELECT
  u.id,
  u.username,
  u.nickname,
  u.avatar,
  u.rating,
  u.level,
  s.total_games,
  s.wins,
  CASE WHEN s.total_games > 0 THEN ROUND(s.wins * 100.0 / s.total_games, 2) ELSE 0 END AS win_rate
FROM `user` u
LEFT JOIN `user_stats` s ON u.id = s.user_id
ORDER BY u.rating DESC
LIMIT 100;

-- ============================================
-- 新增功能表 (2024年新增)
-- ============================================

-- 用户设置表
CREATE TABLE IF NOT EXISTS `user_settings` (
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `sound_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用音效',
  `music_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用音乐',
  `sound_volume` INT NOT NULL DEFAULT 80 COMMENT '音效音量(0-100)',
  `music_volume` INT NOT NULL DEFAULT 60 COMMENT '音乐音量(0-100)',
  `board_theme` VARCHAR(50) NOT NULL DEFAULT 'classic' COMMENT '棋盘主题',
  `piece_style` VARCHAR(50) NOT NULL DEFAULT 'classic' COMMENT '棋子样式',
  `auto_match` TINYINT NOT NULL DEFAULT 1 COMMENT '是否自动匹配',
  `show_rating` TINYINT NOT NULL DEFAULT 1 COMMENT '是否显示积分',
  `language` VARCHAR(10) NOT NULL DEFAULT 'zh-CN' COMMENT '语言设置',
  `timezone` VARCHAR(50) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '时区',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_settings_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置表';

-- 用户活动日志表
CREATE TABLE IF NOT EXISTS `user_activity_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `activity_type` VARCHAR(50) NOT NULL COMMENT '活动类型',
  `ip_address` VARCHAR(45) COMMENT 'IP地址',
  `user_agent` VARCHAR(500) COMMENT '浏览器/客户端信息',
  `activity_data` JSON COMMENT '活动附加数据',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '活动时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_activity_type` (`activity_type`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户活动日志表';

-- 对局收藏表
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

-- 游戏邀请表
CREATE TABLE IF NOT EXISTS `game_invitation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '邀请ID',
  `inviter_id` BIGINT UNSIGNED NOT NULL COMMENT '邀请人ID',
  `invitee_id` BIGINT UNSIGNED NOT NULL COMMENT '被邀请人ID',
  `invitation_type` VARCHAR(20) NOT NULL COMMENT '邀请类型',
  `room_id` VARCHAR(64) COMMENT '房间ID(接受后创建)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `responded_at` DATETIME COMMENT '响应时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '邀请时间',
  PRIMARY KEY (`id`),
  KEY `idx_inviter_id` (`inviter_id`),
  KEY `idx_invitee_id` (`invitee_id`),
  KEY `idx_status` (`status`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏邀请表';
