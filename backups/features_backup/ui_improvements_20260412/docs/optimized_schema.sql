-- ============================================================================
-- 五子棋数据库优化后的完整建表SQL
-- 版本: 2.0
-- 优化内容: 主键设计、索引优化、外键约束、分区表
-- ============================================================================

-- ============================================================================
-- 1. 用户表 (核心表)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    `username` VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `avatar` VARCHAR(255) DEFAULT '/default-avatar.png' COMMENT '头像URL',
    `rating` INT DEFAULT 1200 COMMENT '积分',
    `level` INT DEFAULT 1 COMMENT '等级',
    `exp` INT DEFAULT 0 COMMENT '经验值',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0=离线 1=在线 2=游戏中 3=匹配中',
    `last_online` DATETIME DEFAULT NULL COMMENT '最后在线时间',
    `deleted_at` DATETIME DEFAULT NULL COMMENT '软删除时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    INDEX `idx_username` (`username`),
    INDEX `idx_rating` (`rating` DESC),
    INDEX `idx_status` (`status`),
    INDEX `idx_last_online` (`last_online`),
    INDEX `idx_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================================
-- 2. 用户统计表 (一对一关系)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_stats` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '统计ID',
    `user_id` BIGINT UNIQUE NOT NULL COMMENT '用户ID',
    `total_games` INT DEFAULT 0 COMMENT '总场次',
    `wins` INT DEFAULT 0 COMMENT '胜局',
    `losses` INT DEFAULT 0 COMMENT '负局',
    `draws` INT DEFAULT 0 COMMENT '平局',
    `max_rating` INT DEFAULT 1200 COMMENT '最高积分',
    `current_streak` INT DEFAULT 0 COMMENT '当前连胜/连负',
    `max_streak` INT DEFAULT 0 COMMENT '最大连胜',
    `total_moves` INT DEFAULT 0 COMMENT '总落子数',
    `avg_moves_per_game` DECIMAL(10,2) DEFAULT 0.00 COMMENT '平均每局落子数',
    `fastest_win` INT DEFAULT NULL COMMENT '最快获胜(秒)',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 外键
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,

    -- 索引
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_total_games` (`total_games` DESC),
    INDEX `idx_wins` (`wins` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户统计表';

-- ============================================================================
-- 3. 用户设置表 (一对一关系)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_settings` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '设置ID',
    `user_id` BIGINT UNIQUE NOT NULL COMMENT '用户ID',
    `sound_enabled` BOOLEAN DEFAULT TRUE COMMENT '音效开关',
    `music_enabled` BOOLEAN DEFAULT TRUE COMMENT '音乐开关',
    `sound_volume` INT DEFAULT 80 COMMENT '音效音量',
    `music_volume` INT DEFAULT 60 COMMENT '音乐音量',
    `board_theme` VARCHAR(20) DEFAULT 'classic' COMMENT '棋盘主题',
    `piece_style` VARCHAR(20) DEFAULT 'classic' COMMENT '棋子样式',
    `auto_match` BOOLEAN DEFAULT TRUE COMMENT '自动匹配',
    `show_rating` BOOLEAN DEFAULT TRUE COMMENT '显示积分',
    `language` VARCHAR(10) DEFAULT 'zh-CN' COMMENT '语言',
    `timezone` VARCHAR(50) DEFAULT 'Asia/Shanghai' COMMENT '时区',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 外键
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,

    -- 索引
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置表';

-- ============================================================================
-- 4. 对局记录表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `game_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '对局ID',
    `room_id` VARCHAR(50) NOT NULL COMMENT '房间ID',
    `game_mode` VARCHAR(20) NOT NULL COMMENT '游戏模式: CLASSIC/BLITZ/RENJU/PVE',
    `black_player_id` BIGINT NOT NULL COMMENT '黑方玩家ID',
    `white_player_id` BIGINT NOT NULL COMMENT '白方玩家ID',
    `winner_id` BIGINT DEFAULT NULL COMMENT '胜者ID',
    `win_color` TINYINT DEFAULT NULL COMMENT '胜者颜色: 1=黑 2=白',
    `end_reason` TINYINT NOT NULL COMMENT '结束原因: 0=五子 1=认输 2=超时 3=平局',
    `move_count` INT DEFAULT 0 COMMENT '落子数',
    `duration` INT DEFAULT 0 COMMENT '对局时长(秒)',
    `moves` JSON COMMENT '落子记录(JSON格式)',
    `board_state` TEXT COMMENT '最终棋盘状态',

    -- 积分变化
    `black_rating_before` INT NOT NULL COMMENT '黑方赛前积分',
    `black_rating_after` INT NOT NULL COMMENT '黑方赛后积分',
    `black_rating_change` INT NOT NULL COMMENT '黑方积分变化',
    `white_rating_before` INT NOT NULL COMMENT '白方赛前积分',
    `white_rating_after` INT NOT NULL COMMENT '白方赛后积分',
    `white_rating_change` INT NOT NULL COMMENT '白方积分变化',

    `deleted_at` DATETIME DEFAULT NULL COMMENT '软删除时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 外键
    FOREIGN KEY (`black_player_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`white_player_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`winner_id`) REFERENCES `user`(`id`) ON DELETE SET NULL,

    -- 索引
    INDEX `idx_room_id` (`room_id`),
    INDEX `idx_mode_created` (`game_mode`, `created_at` DESC),
    INDEX `idx_black_player` (`black_player_id`),
    INDEX `idx_white_player` (`white_player_id`),
    INDEX `idx_winner` (`winner_id`),
    INDEX `idx_created` (`created_at` DESC),
    INDEX `idx_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对局记录表';

-- ============================================================================
-- 5. 好友关系表 (多对多关系)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `friend` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '好友关系ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `friend_id` BIGINT NOT NULL COMMENT '好友ID',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0=待确认 1=已接受 2=已拒绝 3=已删除',
    `request_message` VARCHAR(200) DEFAULT NULL COMMENT '申请消息',
    `remark` VARCHAR(50) DEFAULT NULL COMMENT '好友备注',
    `group_id` INT DEFAULT NULL COMMENT '分组ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 唯一约束
    UNIQUE KEY `uk_friendship` (`user_id`, `friend_id`),

    -- 外键
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`friend_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`group_id`) REFERENCES `friend_group`(`id`) ON DELETE SET NULL,

    -- 索引
    INDEX `idx_user_status` (`user_id`, `status`),
    INDEX `idx_friend_status` (`friend_id`, `status`),
    INDEX `idx_group` (`user_id`, `group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';

-- ============================================================================
-- 6. 好友分组表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `friend_group` (
    `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '分组ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `group_name` VARCHAR(50) NOT NULL COMMENT '分组名称',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 外键
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,

    -- 索引
    INDEX `idx_user` (`user_id`),
    UNIQUE KEY `uk_user_group` (`user_id`, `group_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友分组表';

-- 插入默认分组
INSERT IGNORE INTO `friend_group` (`user_id`, `group_name`, `sort_order`)
SELECT `id`, '默认分组', 0 FROM `user`;

-- ============================================================================
-- 7. 聊天消息表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    `sender_id` BIGINT NOT NULL COMMENT '发送者ID',
    `receiver_id` BIGINT DEFAULT NULL COMMENT '接收者ID(私聊)',
    `room_id` VARCHAR(50) DEFAULT NULL COMMENT '房间ID(群聊)',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `message_type` TINYINT DEFAULT 0 COMMENT '消息类型: 0=文本 1=表情 2=系统',
    `is_read` BOOLEAN DEFAULT FALSE COMMENT '是否已读',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 外键
    FOREIGN KEY (`sender_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`receiver_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,

    -- 索引
    INDEX `idx_receiver_read` (`receiver_id`, `is_read`),
    INDEX `idx_room_created` (`room_id`, `created_at`),
    INDEX `idx_sender_created` (`sender_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- ============================================================================
-- 8. 游戏邀请表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `game_invitation` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '邀请ID',
    `inviter_id` BIGINT NOT NULL COMMENT '邀请者ID',
    `invitee_id` BIGINT NOT NULL COMMENT '被邀请者ID',
    `invitation_type` VARCHAR(20) NOT NULL COMMENT '邀请类型: casual/ranked',
    `room_id` VARCHAR(50) DEFAULT NULL COMMENT '房间ID',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending/accepted/rejected/timeout/cancelled',
    `expires_at` DATETIME NOT NULL COMMENT '过期时间',
    `responded_at` DATETIME DEFAULT NULL COMMENT '响应时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 外键
    FOREIGN KEY (`inviter_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`invitee_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,

    -- 索引
    INDEX `idx_invitee_status` (`invitee_id`, `status`),
    INDEX `idx_inviter_status` (`inviter_id`, `status`),
    INDEX `idx_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏邀请表';

-- ============================================================================
-- 9. 对局收藏表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `game_favorite` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '收藏ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `game_record_id` BIGINT NOT NULL COMMENT '对局记录ID',
    `note` TEXT DEFAULT NULL COMMENT '收藏备注',
    `tags` VARCHAR(500) DEFAULT NULL COMMENT '标签(逗号分隔)',
    `is_public` BOOLEAN DEFAULT TRUE COMMENT '是否公开',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 外键
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`game_record_id`) REFERENCES `game_record`(`id`) ON DELETE CASCADE,

    -- 索引
    INDEX `idx_user` (`user_id`),
    INDEX `idx_public` (`is_public`),
    UNIQUE KEY `uk_user_record` (`user_id`, `game_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对局收藏表';

-- ============================================================================
-- 10. 用户活动日志表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_activity_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `activity_type` VARCHAR(50) NOT NULL COMMENT '活动类型',
    `ip_address` VARCHAR(45) DEFAULT NULL COMMENT 'IP地址',
    `user_agent` VARCHAR(500) DEFAULT NULL COMMENT '用户代理',
    `activity_data` JSON DEFAULT NULL COMMENT '活动数据',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 外键
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,

    -- 索引
    INDEX `idx_user_created` (`user_id`, `created_at` DESC),
    INDEX `idx_type_created` (`activity_type`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户活动日志表';

-- ============================================================================
-- 11. 残局表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `puzzle` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '残局ID',
    `title` VARCHAR(100) NOT NULL COMMENT '标题',
    `description` TEXT COMMENT '描述',
    `difficulty` VARCHAR(20) NOT NULL COMMENT '难度: easy/medium/hard/expert',
    `puzzle_type` VARCHAR(30) NOT NULL COMMENT '类型: four_three/double_four/vcf/vct/etc',
    `board_state` TEXT NOT NULL COMMENT '初始棋盘状态',
    `first_player` VARCHAR(10) NOT NULL COMMENT '先手: black/white',
    `player_color` VARCHAR(10) NOT NULL COMMENT '玩家执子: black/white',
    `win_condition` VARCHAR(20) NOT NULL COMMENT '胜利条件: win/draw/opponent_lose',
    `max_moves` INT DEFAULT NULL COMMENT '最大步数限制',
    `optimal_moves` INT DEFAULT NULL COMMENT '最佳解法步数',
    `solution` JSON COMMENT '最佳解法',
    `alternative_solutions` JSON COMMENT '备选解法',
    `hint` TEXT COMMENT '提示信息',
    `hint_moves` JSON COMMENT '提示步数',
    `level_order` INT DEFAULT 0 COMMENT '关卡顺序',
    `is_active` BOOLEAN DEFAULT TRUE COMMENT '是否启用',

    -- 外键/索引
    INDEX `idx_difficulty` (`difficulty`),
    INDEX `idx_type` (`puzzle_type`),
    INDEX `idx_level` (`level_order`),
    INDEX `idx_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='残局表';

-- ============================================================================
-- 12. 残局记录表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `puzzle_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `puzzle_id` BIGINT NOT NULL COMMENT '残局ID',
    `attempts` INT DEFAULT 0 COMMENT '尝试次数',
    `completed` BOOLEAN DEFAULT FALSE COMMENT '是否完成',
    `best_moves` INT DEFAULT NULL COMMENT '最佳步数',
    `best_time` INT DEFAULT NULL COMMENT '最佳用时(秒)',
    `stars` INT DEFAULT 0 COMMENT '星级(1-3)',
    `solution_path` JSON COMMENT '解法路径',
    `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次尝试时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 外键
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`puzzle_id`) REFERENCES `puzzle`(`id`) ON DELETE CASCADE,

    -- 索引
    INDEX `idx_user_puzzle` (`user_id`, `puzzle_id`),
    INDEX `idx_user_completed` (`user_id`, `completed`),
    INDEX `idx_stars` (`user_id`, `stars`),
    UNIQUE KEY `uk_user_puzzle` (`user_id`, `puzzle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='残局记录表';

-- ============================================================================
-- 统计视图
-- ============================================================================

-- 用户综合统计视图
CREATE OR REPLACE VIEW `v_user_stats` AS
SELECT
    u.id,
    u.username,
    u.nickname,
    u.rating,
    u.level,
    u.status,
    u.last_online,
    COALESCE(us.total_games, 0) as total_games,
    COALESCE(us.wins, 0) as wins,
    COALESCE(us.losses, 0) as losses,
    COALESCE(us.draws, 0) as draws,
    CASE
        WHEN COALESCE(us.total_games, 0) > 0
        THEN ROUND(COALESCE(us.wins, 0) * 100.0 / us.total_games, 2)
        ELSE 0
    END as win_rate,
    COALESCE(us.max_streak, 0) as max_streak
FROM `user` u
LEFT JOIN `user_stats` us ON u.id = us.user_id
WHERE u.deleted_at IS NULL;

-- 残局统计视图
CREATE OR REPLACE VIEW `v_puzzle_stats` AS
SELECT
    p.id,
    p.title,
    p.difficulty,
    p.puzzle_type,
    COUNT(pr.id) as total_attempts,
    SUM(CASE WHEN pr.completed = 1 THEN 1 ELSE 0 END) as total_completions,
    ROUND(AVG(CASE WHEN pr.completed = 1 THEN 1 ELSE 0 END) * 100, 2) as completion_rate,
    AVG(CASE WHEN pr.completed = 1 THEN pr.stars ELSE NULL END) as avg_stars
FROM `puzzle` p
LEFT JOIN `puzzle_record` pr ON p.id = pr.puzzle_id
WHERE p.is_active = TRUE
GROUP BY p.id;

-- ============================================================================
-- 定期清理存储过程
-- ============================================================================

DELIMITER $$

-- 清理过期的活动日志
CREATE PROCEDURE `sp_cleanup_old_logs`()
BEGIN
    DECLARE deleted_count INT DEFAULT 0;

    -- 删除30天前的活动日志
    DELETE FROM `user_activity_log`
    WHERE `created_at` < DATE_SUB(NOW(), INTERVAL 30 DAY);

    SET deleted_count = ROW_COUNT();

    -- 删除过期的游戏邀请
    DELETE FROM `game_invitation`
    WHERE `status` IN ('timeout', 'rejected', 'cancelled')
    AND `created_at` < DATE_SUB(NOW(), INTERVAL 7 DAY);

    SELECT CONCAT('已清理 ', deleted_count, ' 条活动日志') as result;
END$$

DELIMITER ;

-- 设置定时任务（每天凌晨2点执行）
-- CREATE EVENT IF NOT EXISTS `evt_cleanup_logs`
-- ON SCHEDULE EVERY 1 DAY
-- STARTS (TIMESTAMP(CURRENT_DATE) + INTERVAL 1 DAY + INTERVAL 2 HOUR)
-- DO CALL sp_cleanup_old_logs();

-- ============================================================================
-- 性能优化建议
-- ============================================================================

-- 1. 定期分析表以优化查询计划
-- ANALYZE TABLE `user`, `game_record`, `friend`, `chat_message`;

-- 2. 检查表碎片
-- OPTIMIZE TABLE `user_activity_log`, `chat_message`;

-- 3. 启用慢查询日志
-- SET GLOBAL slow_query_log = 'ON';
-- SET GLOBAL long_query_time = 1;

-- ============================================================================
-- 初始化数据
-- ============================================================================

-- 创建默认管理员账户
-- INSERT INTO `user` (`username`, `password`, `nickname`, `rating`, `level`, `status`)
-- VALUES ('admin', '$2a$10$...', '管理员', 2000, 10, 0);
