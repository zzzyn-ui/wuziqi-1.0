-- ============================================
-- 五子棋游戏数据库初始化脚本
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS gobang
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;

USE gobang;

-- ============================================
-- 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS user (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(32) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(128) NOT NULL COMMENT '密码(BCrypt加密)',
    nickname VARCHAR(32) NOT NULL COMMENT '昵称',
    email VARCHAR(64) DEFAULT NULL COMMENT '邮箱',
    avatar VARCHAR(255) DEFAULT '/default-avatar.png' COMMENT '头像URL',
    rating INT NOT NULL DEFAULT 1200 COMMENT 'ELO积分',
    level INT NOT NULL DEFAULT 1 COMMENT '等级',
    exp INT NOT NULL DEFAULT 0 COMMENT '经验值',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_online DATETIME DEFAULT NULL COMMENT '最后在线时间',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0=离线,1=在线,2=游戏中,3=匹配中',

    INDEX idx_rating (rating),
    INDEX idx_last_online (last_online)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 用户统计表
-- ============================================
CREATE TABLE IF NOT EXISTS user_stats (
    user_id BIGINT PRIMARY KEY COMMENT '用户ID',
    total_games INT DEFAULT 0 COMMENT '总对局数',
    wins INT DEFAULT 0 COMMENT '胜场',
    losses INT DEFAULT 0 COMMENT '负场',
    draws INT DEFAULT 0 COMMENT '平场',
    max_rating INT DEFAULT 1200 COMMENT '最高积分',
    current_streak INT DEFAULT 0 COMMENT '当前连胜/连负',
    max_streak INT DEFAULT 0 COMMENT '最高连胜',
    total_moves INT DEFAULT 0 COMMENT '总落子数',
    avg_moves_per_game DECIMAL(10,2) DEFAULT 0 COMMENT '平均每局落子数',
    fastest_win INT DEFAULT NULL COMMENT '最快获胜(秒)',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户统计表';

-- ============================================
-- 对局记录表
-- ============================================
CREATE TABLE IF NOT EXISTS game_record (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '对局ID',
    room_id VARCHAR(64) NOT NULL UNIQUE COMMENT '房间ID',
    black_player_id BIGINT UNSIGNED NOT NULL COMMENT '黑方玩家ID',
    white_player_id BIGINT UNSIGNED NOT NULL COMMENT '白方玩家ID',
    winner_id BIGINT UNSIGNED DEFAULT NULL COMMENT '获胜者ID',
    win_color TINYINT DEFAULT NULL COMMENT '获胜方颜色:1=黑,2=白',
    end_reason TINYINT NOT NULL COMMENT '结束原因:0=胜利,1=失败,2=平局,3=认输,4=超时',
    move_count INT NOT NULL DEFAULT 0 COMMENT '总落子数',
    duration INT NOT NULL DEFAULT 0 COMMENT '对局时长(秒)',
    black_rating_before INT NOT NULL COMMENT '黑方积分变化前',
    black_rating_after INT NOT NULL COMMENT '黑方积分变化后',
    black_rating_change INT NOT NULL COMMENT '黑方积分变化',
    white_rating_before INT NOT NULL COMMENT '白方积分变化前',
    white_rating_after INT NOT NULL COMMENT '白方积分变化后',
    white_rating_change INT NOT NULL COMMENT '白方积分变化',
    board_state TEXT COMMENT '最终棋盘状态(压缩)',
    moves TEXT COMMENT '所有落子记录(JSON数组)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_black_player (black_player_id),
    INDEX idx_white_player (white_player_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对局记录表';

-- ============================================
-- 好友表
-- ============================================
CREATE TABLE IF NOT EXISTS friend (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '关系ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    friend_id BIGINT UNSIGNED NOT NULL COMMENT '好友ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态:0=待确认,1=已确认',
    request_message VARCHAR(255) DEFAULT NULL COMMENT '申请消息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_user_friend (user_id, friend_id),
    INDEX idx_friend_id (friend_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友表';

-- ============================================
-- 聊天消息表
-- ============================================
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '消息ID',
    sender_id BIGINT UNSIGNED NOT NULL COMMENT '发送者ID',
    receiver_id BIGINT UNSIGNED DEFAULT NULL COMMENT '接收者ID(私聊),NULL为公屏',
    room_id VARCHAR(64) DEFAULT NULL COMMENT '房间ID(公屏聊天)',
    content VARCHAR(500) NOT NULL COMMENT '消息内容',
    message_type TINYINT NOT NULL DEFAULT 0 COMMENT '消息类型:0=普通,1=系统',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读:0=未读,1=已读',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_sender (sender_id),
    INDEX idx_receiver (receiver_id),
    INDEX idx_room (room_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- ============================================
-- 初始化测试数据
-- ============================================

-- 创建测试用户
INSERT INTO user (username, password, nickname, rating) VALUES
('test1', '$2a$10$test1', '测试玩家1', 1200),
('test2', '$2a$10$test2', '测试玩家2', 1200),
('test3', '$2a$10$test3', '测试玩家3', 1300),
('test4', '$2a$10$test4', '测试玩家4', 1100)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

-- ============================================
-- 观战记录表
-- ============================================
CREATE TABLE IF NOT EXISTS observer_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    room_id VARCHAR(64) NOT NULL COMMENT '房间ID',
    user_id BIGINT NOT NULL COMMENT '观战用户ID',
    join_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    leave_time DATETIME DEFAULT NULL COMMENT '离开时间',
    duration INT DEFAULT NULL COMMENT '观战时长(秒)',

    INDEX idx_room_id (room_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='观战记录表';

-- ============================================
-- 用户设置表
-- ============================================
CREATE TABLE IF NOT EXISTS user_settings (
    user_id BIGINT PRIMARY KEY COMMENT '用户ID',
    sound_enabled TINYINT DEFAULT 1 COMMENT '是否启用音效',
    music_enabled TINYINT DEFAULT 1 COMMENT '是否启用音乐',
    sound_volume INT DEFAULT 80 COMMENT '音效音量(0-100)',
    music_volume INT DEFAULT 60 COMMENT '音乐音量(0-100)',
    board_theme VARCHAR(50) DEFAULT 'classic' COMMENT '棋盘主题',
    piece_style VARCHAR(50) DEFAULT 'classic' COMMENT '棋子样式',
    auto_match TINYINT DEFAULT 1 COMMENT '是否自动匹配',
    show_rating TINYINT DEFAULT 1 COMMENT '是否显示积分',
    language VARCHAR(10) DEFAULT 'zh-CN' COMMENT '语言设置',
    timezone VARCHAR(50) DEFAULT 'Asia/Shanghai' COMMENT '时区',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户设置表';

-- ============================================
-- 用户活动日志表
-- ============================================
CREATE TABLE IF NOT EXISTS user_activity_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    activity_type VARCHAR(50) NOT NULL COMMENT '活动类型',
    ip_address VARCHAR(45) COMMENT 'IP地址',
    user_agent VARCHAR(500) COMMENT '浏览器/客户端信息',
    activity_data TEXT COMMENT '活动附加数据(JSON)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '活动时间',

    INDEX idx_user_id (user_id),
    INDEX idx_activity_type (activity_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户活动日志表';

-- ============================================
-- 对局收藏表
-- ============================================
CREATE TABLE IF NOT EXISTS game_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收藏ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    game_record_id BIGINT NOT NULL COMMENT '对局记录ID',
    note VARCHAR(500) COMMENT '收藏备注',
    tags VARCHAR(200) COMMENT '标签(逗号分隔)',
    is_public TINYINT DEFAULT 1 COMMENT '是否公开',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',

    UNIQUE KEY uk_user_record (user_id, game_record_id),
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY (game_record_id) REFERENCES game_record(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对局收藏表';

-- ============================================
-- 游戏邀请表
-- ============================================
CREATE TABLE IF NOT EXISTS game_invitation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '邀请ID',
    inviter_id BIGINT NOT NULL COMMENT '邀请人ID',
    invitee_id BIGINT NOT NULL COMMENT '被邀请人ID',
    invitation_type VARCHAR(20) NOT NULL COMMENT '邀请类型(casual/ranked)',
    room_id VARCHAR(64) COMMENT '房间ID(接受后创建)',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态(pending/accepted/rejected/cancelled)',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    responded_at DATETIME COMMENT '响应时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '邀请时间',

    INDEX idx_inviter_id (inviter_id),
    INDEX idx_invitee_id (invitee_id),
    INDEX idx_status (status),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏邀请表';

-- ============================================
-- 初始化测试数据
-- ============================================

-- 创建测试用户
INSERT INTO user (username, password, nickname, rating) VALUES
('test1', '$2a$10$test1', '测试玩家1', 1200),
('test2', '$2a$10$test2', '测试玩家2', 1200),
('test3', '$2a$10$test3', '测试玩家3', 1300),
('test4', '$2a$10$test4', '测试玩家4', 1100)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

-- 初始化用户统计
INSERT INTO user_stats (user_id, total_games, wins, losses, draws, max_rating, current_streak, max_streak, total_moves, avg_moves_per_game)
SELECT id, 0, 0, 0, 0, 1200, 0, 0, 0, 0 FROM user
ON DUPLICATE KEY UPDATE user_id = user_id;
