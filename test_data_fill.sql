-- ============================================
-- 测试数据填充脚本
-- 用于为空表创建测试数据
-- ============================================

-- 注意：执行此脚本前请确保数据库已初始化
-- 使用方法：mysql -u root -p gobang < test_data_fill.sql

-- ============================================
-- 1. 用户设置 - 为所有用户创建默认设置
-- ============================================
INSERT INTO user_settings (user_id)
SELECT id FROM user
ON DUPLICATE KEY UPDATE user_id = user_id;

-- 验证
SELECT 'user_settings' AS table_name, COUNT(*) AS record_count FROM user_settings;


-- ============================================
-- 2. 活动日志 - 模拟用户登录活动
-- ============================================
INSERT INTO user_activity_log (user_id, activity_type, ip_address, created_at)
SELECT
    id AS user_id,
    'login' AS activity_type,
    '127.0.0.1' AS ip_address,
    NOW() AS created_at
FROM user
WHERE id <= 3
ON DUPLICATE KEY UPDATE created_at = created_at;

-- 验证
SELECT 'user_activity_log' AS table_name, COUNT(*) AS record_count FROM user_activity_log;


-- ============================================
-- 3. 对局记录 - 创建测试对局
-- ============================================
INSERT INTO game_record (
    room_id,
    black_player_id,
    white_player_id,
    winner_id,
    win_color,
    end_reason,
    move_count,
    duration,
    black_rating_before,
    black_rating_after,
    black_rating_change,
    white_rating_before,
    white_rating_after,
    white_rating_change,
    moves,
    created_at
) VALUES
(
    'test_room_001',  -- 房间ID
    1,                 -- 黑方玩家ID
    2,                 -- 白方玩家ID
    1,                 -- 获胜者ID (黑方胜)
    1,                 -- 获胜方颜色 (1=黑, 2=白)
    0,                 -- 结束原因 (0=胜利)
    45,                -- 落子数
    180,               -- 对局时长(秒)
    1200,              -- 黑方赛前积分
    1216,              -- 黑方赛后积分
    16,                -- 黑方积分变化
    1180,              -- 白方赛前积分
    1164,              -- 白方赛后积分
    -16,               -- 白方积分变化
    '[[7,7],[7,8],[8,7],[8,8],[9,7],[9,8],[9,9]]', -- 落子记录
    NOW()              -- 对局时间
);

-- 验证
SELECT 'game_record' AS table_name, COUNT(*) AS record_count FROM game_record;


-- ============================================
-- 4. 对局收藏 - 模拟用户收藏对局
-- ============================================
INSERT INTO game_favorite (user_id, game_record_id, note, tags, is_public, created_at)
VALUES
(
    1,                 -- 用户ID
    1,                 -- 对局记录ID (test_room_001的ID需要查询获得)
    '精彩的残局！',    -- 备注
    '残局,战术',      -- 标签
    1,                 -- 公开
    NOW()              -- 收藏时间
);

-- 验证
SELECT 'game_favorite' AS table_name, COUNT(*) AS record_count FROM game_favorite;


-- ============================================
-- 5. 游戏邀请 - 模拟邀请记录
-- ============================================
INSERT INTO game_invitation (
    inviter_id,
    invitee_id,
    invitation_type,
    status,
    expires_at,
    created_at
) VALUES
(
    2,                 -- 邀请人ID
    3,                 -- 被邀请人ID
    'casual',          -- 邀请类型
    'pending',         -- 状态
    DATE_ADD(NOW(), INTERVAL 5 MINUTE),  -- 过期时间
    NOW()              -- 创建时间
);

-- 验证
SELECT 'game_invitation' AS table_name, COUNT(*) AS record_count FROM game_invitation;


-- ============================================
-- 数据填充总结
-- ============================================
SELECT
    'user' AS table_name,
    (SELECT COUNT(*) FROM `user`) AS record_count
UNION ALL
SELECT
    'user_stats',
    (SELECT COUNT(*) FROM user_stats)
UNION ALL
SELECT
    'game_record',
    (SELECT COUNT(*) FROM game_record)
UNION ALL
SELECT
    'user_settings',
    (SELECT COUNT(*) FROM user_settings)
UNION ALL
SELECT
    'user_activity_log',
    (SELECT COUNT(*) FROM user_activity_log)
UNION ALL
SELECT
    'game_favorite',
    (SELECT COUNT(*) FROM game_favorite)
UNION ALL
SELECT
    'game_invitation',
    (SELECT COUNT(*) FROM game_invitation)
ORDER BY record_count DESC;


-- ============================================
-- 提示信息
-- ============================================
SELECT '============================================' AS info
UNION ALL
SELECT '测试数据填充完成！' AS info
UNION ALL
SELECT '' AS info
UNION ALL
SELECT '以下表仍为空（需要运行应用后填充）：' AS info
UNION ALL
SELECT '• friend - 好友关系（需要添加好友）' AS info
UNION ALL
SELECT '• chat_message - 聊天消息（需要发送消息）' AS info
UNION ALL
SELECT '• observer_record - 观战记录（需要观战）' AS info
UNION ALL
SELECT '============================================' AS info;
