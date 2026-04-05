-- 修复nickname字段：添加默认值
-- 如果user表已存在，执行此语句来修复

USE gobang;

-- 修改nickname字段，添加默认值
ALTER TABLE user MODIFY COLUMN nickname VARCHAR(32) NOT NULL DEFAULT '' COMMENT '昵称';

-- 验证修改
DESCRIBE user;
