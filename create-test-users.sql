-- ============================================
-- 创建测试用户
-- 密码统一为: 123456
-- ============================================

USE gobang;

-- 删除旧的测试用户（如果存在）
DELETE FROM user WHERE username IN ('admin', 'test', 'player1', 'player2');

-- 插入测试用户
-- 密码 "123456" 的 BCrypt 哈希值: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
INSERT INTO user (username, password, nickname, rating, level, exp, status) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '管理员', 1500, 10, 5000, 1),
('test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '测试用户', 1200, 5, 1000, 1),
('player1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '高手玩家1', 1400, 8, 3000, 1),
('player2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '新手玩家2', 1000, 2, 200, 1);

-- 初始化用户统计数据
INSERT INTO user_stats (user_id, total_games, wins, losses, max_rating)
SELECT id, 0, 0, 0, rating FROM user
WHERE username IN ('admin', 'test', 'player1', 'player2')
ON DUPLICATE KEY UPDATE user_id = user_id;

-- 验证插入结果
SELECT id, username, nickname, rating, level, status, created_at
FROM user
WHERE username IN ('admin', 'test', 'player1', 'player2')
ORDER BY rating DESC;
