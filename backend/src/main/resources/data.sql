-- 五子棋游戏初始化数据

-- 测试用户（密码都是 123456）
INSERT INTO `user` (`username`, `password`, `nickname`, `rating`, `level`) VALUES
('testuser', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYzW5qHlKim', '测试用户', 1200, 1),
('player1', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYzW5qHlKim', '玩家一号', 1250, 2),
('player2', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYzW5qHlKim', '玩家二号', 1180, 1)
ON DUPLICATE KEY UPDATE `nickname`=VALUES(`nickname`);

-- 初始化用户统计
INSERT INTO `user_stats` (`user_id`)
SELECT id FROM `user`
ON DUPLICATE KEY UPDATE `user_id`=`user_id`;
