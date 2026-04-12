-- ============================================================================
-- 五子棋数据库优化SQL - 添加索引和优化
-- 执行前请先备份数据库！
-- ============================================================================

USE `gobang`;

-- 1. 修复 user_stats 表主键
-- 添加独立主键ID
ALTER TABLE `user_stats`
ADD COLUMN `id` BIGINT AUTO_INCREMENT PRIMARY KEY FIRST;

-- 添加唯一约束
ALTER TABLE `user_stats`
ADD UNIQUE KEY `uk_user_id` (`user_id`);

-- 2. 创建 user_settings 表（如果不存在）
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

    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置表';

-- 3. 添加关键索引

-- user 表索引
CREATE INDEX IF NOT EXISTS `idx_status` ON `user`(`status`);
CREATE INDEX IF NOT EXISTS `idx_last_online` ON `user`(`last_online`);

-- game_record 表索引
CREATE INDEX IF NOT EXISTS `idx_mode_created` ON `game_record`(`game_mode`, `created_at` DESC);
CREATE INDEX IF NOT EXISTS `idx_black_player` ON `game_record`(`black_player_id`);
CREATE INDEX IF NOT EXISTS `idx_white_player` ON `game_record`(`white_player_id`);
CREATE INDEX IF NOT EXISTS `idx_winner` ON `game_record`(`winner_id`);
CREATE INDEX IF NOT EXISTS `idx_created` ON `game_record`(`created_at` DESC);

-- friend 表索引（如果表已存在）
-- CREATE INDEX IF NOT EXISTS `idx_user_status` ON `friend`(`user_id`, `status`);
-- CREATE INDEX IF NOT EXISTS `idx_friend_status` ON `friend`(`friend_id`, `status`);

-- chat_message 表索引（如果表已存在）
-- CREATE INDEX IF NOT EXISTS `idx_receiver_read` ON `chat_message`(`receiver_id`, `is_read`);
-- CREATE INDEX IF NOT EXISTS `idx_room_created` ON `chat_message`(`room_id`, `created_at`);

-- 4. 为现有用户创建默认设置
INSERT IGNORE INTO `user_settings` (`user_id`, `sound_enabled`, `music_enabled`, `sound_volume`, `music_volume`, `board_theme`, `piece_style`, `auto_match`, `show_rating`, `language`, `timezone`)
SELECT `id`, TRUE, TRUE, 80, 60, 'classic', 'classic', TRUE, TRUE, 'zh-CN', 'Asia/Shanghai'
FROM `user`
WHERE NOT EXISTS (
    SELECT 1 FROM `user_settings` WHERE `user_settings`.`user_id` = `user`.`id`
);

-- 5. 更新 user_stats 表结构
ALTER TABLE `user_stats`
MODIFY COLUMN `total_games` INT DEFAULT 0,
MODIFY COLUMN `wins` INT DEFAULT 0,
MODIFY COLUMN `losses` INT DEFAULT 0,
MODIFY COLUMN `draws` INT DEFAULT 0,
MODIFY COLUMN `max_rating` INT DEFAULT 1200,
MODIFY COLUMN `current_streak` INT DEFAULT 0,
MODIFY COLUMN `max_streak` INT DEFAULT 0,
MODIFY COLUMN `total_moves` INT DEFAULT 0,
MODIFY COLUMN `avg_moves_per_game` DECIMAL(10,2) DEFAULT 0.00;

-- 6. 创建用户统计视图（方便查询）
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
WHERE u.deleted_at IS NULL OR u.deleted_at IS NULL;

-- 完成
SELECT '数据库优化完成！' as result;
