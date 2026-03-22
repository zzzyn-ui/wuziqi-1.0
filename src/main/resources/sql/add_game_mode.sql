-- 为game_record表添加game_mode列
ALTER TABLE game_record ADD COLUMN game_mode VARCHAR(20) DEFAULT 'pvp_online' COMMENT '游戏模式: pve(人机), pvp_online(在线对战), pvp_local(本地对战)';

-- 更新现有记录的game_mode（假设旧记录都是在线对战）
UPDATE game_record SET game_mode = 'pvp_online' WHERE game_mode IS NULL;
