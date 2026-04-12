-- 好友系统表结构
-- 在现有数据库中执行

-- 1. 好友关系表
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

    UNIQUE KEY `uk_friendship` (`user_id`, `friend_id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_friend_status` (`friend_id`, `status`),
    KEY `idx_group` (`user_id`, `group_id`),

    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`friend_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';

-- 2. 好友分组表
CREATE TABLE IF NOT EXISTS `friend_group` (
    `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '分组ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `group_name` VARCHAR(50) NOT NULL COMMENT '分组名称',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    KEY `idx_user` (`user_id`),

    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友分组表';

-- 插入默认分组
INSERT IGNORE INTO `friend_group` (`user_id`, `group_name`, `sort_order`)
SELECT `id`, '默认分组', 0 FROM `user`;

-- 索引优化建议（如果数据量大）
-- ALTER TABLE `friend` ADD INDEX `idx_created` (`created_at`);
