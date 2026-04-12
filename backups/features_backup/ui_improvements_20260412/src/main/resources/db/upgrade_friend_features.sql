-- 好友功能增强 SQL 脚本
-- 执行此脚本以添加好友备注、分组等功能

-- 1. 为 friend 表添加备注和分组字段
ALTER TABLE `friend`
ADD COLUMN `remark` VARCHAR(64) DEFAULT NULL COMMENT '好友备注' AFTER `request_message`,
ADD COLUMN `group_id` INT UNSIGNED DEFAULT NULL COMMENT '分组ID' AFTER `remark`,
ADD INDEX `idx_group_id` (`group_id`);

-- 2. 创建好友分组表
CREATE TABLE IF NOT EXISTS `friend_group` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分组ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `group_name` VARCHAR(32) NOT NULL COMMENT '分组名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序顺序',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_group_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友分组表';

-- 3. 创建表情包配置表
CREATE TABLE IF NOT EXISTS `emoji_config` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '表情ID',
  `emoji_code` VARCHAR(32) NOT NULL COMMENT '表情代码',
  `emoji_name` VARCHAR(32) NOT NULL COMMENT '表情名称',
  `emoji_url` VARCHAR(255) NOT NULL COMMENT '表情图片URL',
  `category` VARCHAR(20) NOT NULL DEFAULT 'default' COMMENT '分类:default=默认,love=喜爱,game=游戏,fun=搞笑',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序顺序',
  `is_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_emoji_code` (`emoji_code`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表情包配置表';

-- 4. 插入默认表情包数据
INSERT INTO `emoji_config` (`emoji_code`, `emoji_name`, `emoji_url`, `category`, `sort_order`) VALUES
-- 默认表情
('😀', '笑脸', 'emoji/grinning.svg', 'default', 1),
('😂', '笑哭', 'emoji/joy.svg', 'default', 2),
('😍', '爱心眼', 'emoji/heart_eyes.svg', 'default', 3),
('🤔', '思考', 'emoji/thinking.svg', 'default', 4),
('😎', '酷', 'emoji/sunglasses.svg', 'default', 5),
('🎉', '庆祝', 'emoji/party.svg', 'default', 6),
('👍', '赞', 'emoji/thumbsup.svg', 'default', 7),
('👎', '踩', 'emoji/thumbsdown.svg', 'default', 8),
('💪', '加油', 'emoji/muscle.svg', 'default', 9),
('🙏', '祈祷', 'emoji/pray.svg', 'default', 10),
-- 游戏相关
('♟️', '兵', 'emoji/pawn.svg', 'game', 1),
('🎯', '靶心', 'emoji/dart.svg', 'game', 2),
('🏆', '奖杯', 'emoji/trophy.svg', 'game', 3),
('⚡', '闪电', 'emoji/lightning.svg', 'game', 4),
('🔥', '火焰', 'emoji/fire.svg', 'game', 5),
('💯', '满分', 'emoji/100.svg', 'game', 6),
('🎮', '游戏手柄', 'emoji/game.svg', 'game', 7),
('👑', '皇冠', 'emoji/crown.svg', 'game', 8),
-- 喜爱相关
('❤️', '红心', 'emoji/heart.svg', 'love', 1),
('💕', '两心', 'emoji/two_hearts.svg', 'love', 2),
('💖', '闪光心', 'emoji/sparkling_heart.svg', 'love', 3),
('💗', '成长心', 'emoji/growing_heart.svg', 'love', 4),
('💘', '爱心箭', 'emoji/cupid.svg', 'love', 5),
('🤗', '拥抱', 'emoji/hugging.svg', 'love', 6),
-- 搞笑表情
('🤪', ' Crazy ', 'emoji/zany.svg', 'fun', 1),
('😜', '眨眼', 'emoji/wink.svg', 'fun', 2),
('🤣', '大笑', 'emoji/rolling.svg', 'fun', 3),
('😜', '调皮', 'emoji/stuck.svg', 'fun', 4),
('🤭', '捂嘴', 'emoji/giggle.svg', 'fun', 5);

-- 5. 为每个用户创建默认分组（我的好友、游戏好友、家人）
-- 这个需要在应用层面处理，或者通过触发器实现

-- 6. 优化 chat_message 表，添加已读状态字段
ALTER TABLE `chat_message`
MODIFY COLUMN `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读:0=未读,1=已读',
ADD COLUMN `deleted_by_sender` TINYINT NOT NULL DEFAULT 0 COMMENT '发送者是否删除:0=未删除,1=已删除' AFTER `is_read`,
ADD COLUMN `deleted_by_receiver` TINYINT NOT NULL DEFAULT 0 COMMENT '接收者是否删除:0=未删除,1=已删除' AFTER `deleted_by_sender`;

-- 7. 创建聊天会话表（用于显示会话列表）
CREATE TABLE IF NOT EXISTS `chat_conversation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `friend_id` BIGINT UNSIGNED NOT NULL COMMENT '好友ID',
  `last_message_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '最后一条消息ID',
  `last_message_time` DATETIME DEFAULT NULL COMMENT '最后消息时间',
  `unread_count` INT NOT NULL DEFAULT 0 COMMENT '未读消息数',
  `is_pinned` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶',
  `is_muted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否免打扰',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
  KEY `idx_last_message_time` (`last_message_time`),
  KEY `idx_unread_count` (`unread_count`),
  CONSTRAINT `fk_conv_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_conv_friend` FOREIGN KEY (`friend_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';
