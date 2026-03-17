package com.gobang.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.util.RedisUtil;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 匹配队列服务 - 使用Redis存储匹配中的用户
 * 支持分布式匹配，多服务器可以共享匹配队列
 */
public class MatchQueueService {

    private static final Logger logger = LoggerFactory.getLogger(MatchQueueService.class);

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    // Redis键前缀（使用不同的键名避免Redis问题）
    private static final String CASUAL_QUEUE_KEY = "match:q:c";  // 休闲模式队列
    private static final String RANKED_QUEUE_KEY = "match:q:r";  // 竞技模式队列
    private static final String PLAYER_PREFIX = "match:player:";
    private static final String QUEUE_LOCK_KEY = "match:lock";

    // 过期时间（秒）- 5分钟未匹配则自动清除
    private static final int PLAYER_EXPIRE_TIME = 300;

    public MatchQueueService(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 添加玩家到匹配队列
     * @param userId 用户ID
     * @param username 用户名
     * @param nickname 昵称
     * @param rating 积分
     * @param channel 通道（用于本地通信，不存储到Redis）
     * @param mode 游戏模式（casual/ranked）
     * @return 是否成功加入
     */
    public boolean addToQueue(Long userId, String username, String nickname, Integer rating, Channel channel, String mode) {
        try {
            logger.info("=== addToQueue 开始: userId={}, mode='{}' ===", userId, mode);

            // 先移除可能存在的旧记录
            boolean removed = removeFromQueue(userId);
            logger.info("removeFromQueue 返回: {}", removed);

            // 创建玩家信息
            MatchPlayer player = new MatchPlayer(userId, username, nickname, rating, channel, mode);
            logger.info("创建MatchPlayer: mode='{}', player.mode='{}'", mode, player.getMode());

            // 存储玩家信息到Redis（JSON格式）
            String playerKey = PLAYER_PREFIX + userId;
            String playerJson = objectMapper.writeValueAsString(player);
            logger.info("存储玩家数据到 Redis: key={}, json={}", playerKey, playerJson);
            redisUtil.setex(playerKey, PLAYER_EXPIRE_TIME, playerJson);

            // 添加到对应模式的匹配队列
            String queueKey = "casual".equals(mode) ? CASUAL_QUEUE_KEY : RANKED_QUEUE_KEY;
            logger.info("模式 '{}' 对应的队列键: {}", mode, queueKey);
            logger.info("尝试添加用户 {} 到队列: {}", userId, queueKey);
            redisUtil.sadd(queueKey, String.valueOf(userId));
            logger.info("sadd 调用完成");

            // 验证是否添加成功
            long count = redisUtil.scard(queueKey);
            logger.info("队列 {} 当前成员数: {}", queueKey, count);

            // 同时检查另一个队列
            String otherQueueKey = "casual".equals(mode) ? RANKED_QUEUE_KEY : CASUAL_QUEUE_KEY;
            long otherCount = redisUtil.scard(otherQueueKey);
            logger.info("另一个队列 {} 当前成员数: {}", otherQueueKey, otherCount);

            logger.info("玩家 {} ({}) 已加入 {} 匹配队列", userId, nickname, mode);
            return true;

        } catch (Exception e) {
            logger.error("添加玩家到匹配队列失败: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 从匹配队列移除玩家
     * @param userId 用户ID
     * @return 是否成功移除
     */
    public boolean removeFromQueue(Long userId) {
        try {
            // 获取玩家信息
            String playerKey = PLAYER_PREFIX + userId;
            String playerJson = redisUtil.get(playerKey);

            if (playerJson != null) {
                MatchPlayer player = objectMapper.readValue(playerJson, MatchPlayer.class);
                String mode = player.getMode();

                // 从对应模式的队列中移除
                String queueKey = "casual".equals(mode) ? CASUAL_QUEUE_KEY : RANKED_QUEUE_KEY;
                redisUtil.srem(queueKey, String.valueOf(userId));

                // 删除玩家信息
                redisUtil.del(playerKey);

                logger.info("玩家 {} 已从 {} 匹配队列移除", userId, mode);
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("从匹配队列移除玩家失败: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 获取队列中的所有玩家
     * @param mode 游戏模式
     * @param localChannels 本地通道映射（用于更新Channel引用）
     * @return 玩家列表
     */
    public List<MatchPlayer> getPlayersInQueue(String mode, Map<Long, Channel> localChannels) {
        return getPlayersInQueue(mode, localChannels, false);
    }

    /**
     * 获取队列中的所有玩家
     * @param mode 游戏模式
     * @param localChannels 本地通道映射（用于更新Channel引用）
     * @param removeInactive 是否移除不活跃的玩家（广播时设为false）
     * @return 玩家列表
     */
    public List<MatchPlayer> getPlayersInQueue(String mode, Map<Long, Channel> localChannels, boolean removeInactive) {
        try {
            String queueKey = "casual".equals(mode) ? CASUAL_QUEUE_KEY : RANKED_QUEUE_KEY;
            Set<String> userIds = redisUtil.smembers(queueKey);

            if (userIds == null || userIds.isEmpty()) {
                return new ArrayList<>();
            }

            List<MatchPlayer> players = new ArrayList<>();
            for (String userIdStr : userIds) {
                try {
                    Long userId = Long.parseLong(userIdStr);
                    String playerKey = PLAYER_PREFIX + userId;
                    String playerJson = redisUtil.get(playerKey);

                    if (playerJson != null) {
                        MatchPlayer player = objectMapper.readValue(playerJson, MatchPlayer.class);

                        // 更新Channel引用（使用本地通道）
                        if (localChannels.containsKey(userId)) {
                            player = player.withChannel(localChannels.get(userId));
                        }

                        // 检查Channel是否活跃
                        if (!removeInactive || (player.getChannel() != null && player.getChannel().isActive())) {
                            players.add(player);
                        } else if (removeInactive) {
                            // Channel不活跃，从队列中移除
                            removeFromQueue(userId);
                        }
                    } else {
                        // 玩家信息不存在，从队列中移除
                        redisUtil.srem(queueKey, userIdStr);
                    }

                } catch (Exception e) {
                    logger.error("解析玩家信息失败: userId={}", userIdStr, e);
                    redisUtil.srem(queueKey, userIdStr);
                }
            }

            logger.debug("{} 模式队列中有 {} 个活跃玩家", mode, players.size());
            return players;

        } catch (Exception e) {
            logger.error("获取匹配队列失败: mode={}", mode, e);
            return new ArrayList<>();
        }
    }

    /**
     * 休闲模式匹配 - 简单的两两配对
     * @param localChannels 本地通道映射
     * @return 匹配成功的玩家对列表
     */
    public List<MatchPair> matchCasual(Map<Long, Channel> localChannels) {
        List<MatchPlayer> players = getPlayersInQueue("casual", localChannels);

        if (players.size() < 2) {
            return new ArrayList<>();
        }

        List<MatchPair> pairs = new ArrayList<>();
        List<MatchPlayer> matched = new ArrayList<>();

        // 简单的两两配对
        for (int i = 0; i < players.size() - 1; i += 2) {
            MatchPlayer p1 = players.get(i);
            MatchPlayer p2 = players.get(i + 1);

            if (p1 != null && p2 != null && !matched.contains(p1) && !matched.contains(p2)) {
                pairs.add(new MatchPair(p1, p2));
                matched.add(p1);
                matched.add(p2);

                // 从队列中移除
                removeFromQueue(p1.getUserId());
                removeFromQueue(p2.getUserId());

                logger.info("休闲模式匹配成功: {} ({}) vs {} ({})",
                    p1.getUserId(), p1.getNickname(),
                    p2.getUserId(), p2.getNickname());
            }
        }

        return pairs;
    }

    /**
     * 竞技模式匹配 - 根据积分配对
     * @param localChannels 本地通道映射
     * @param ratingDiff 允许的积分差异
     * @return 匹配成功的玩家对列表
     */
    public List<MatchPair> matchRanked(Map<Long, Channel> localChannels, int ratingDiff) {
        List<MatchPlayer> players = getPlayersInQueue("ranked", localChannels);

        if (players.size() < 2) {
            return new ArrayList<>();
        }

        // 按积分排序
        players.sort(Comparator.comparingInt(MatchPlayer::getRating));

        List<MatchPair> pairs = new ArrayList<>();
        Set<MatchPlayer> matched = new HashSet<>();

        for (int i = 0; i < players.size(); i++) {
            MatchPlayer p1 = players.get(i);
            if (matched.contains(p1) || !p1.isActive()) {
                continue;
            }

            for (int j = i + 1; j < players.size(); j++) {
                MatchPlayer p2 = players.get(j);
                if (matched.contains(p2) || !p2.isActive()) {
                    continue;
                }

                // 检查积分差异
                int diff = Math.abs(p1.getRating() - p2.getRating());

                if (diff <= ratingDiff) {
                    pairs.add(new MatchPair(p1, p2));
                    matched.add(p1);
                    matched.add(p2);

                    // 从队列中移除
                    removeFromQueue(p1.getUserId());
                    removeFromQueue(p2.getUserId());

                    logger.info("竞技模式匹配成功: {} ({积分:{}}) vs {} ({积分:{}}), 积分差:{}",
                        p1.getUserId(), p1.getRating(),
                        p2.getUserId(), p2.getRating(), diff);

                    break;
                }
            }
        }

        return pairs;
    }

    /**
     * 获取队列统计信息
     * @return 统计信息
     */
    public QueueStats getQueueStats() {
        try {
            long casualCount = redisUtil.scard(CASUAL_QUEUE_KEY);
            long rankedCount = redisUtil.scard(RANKED_QUEUE_KEY);

            return new QueueStats((int) casualCount, (int) rankedCount);

        } catch (Exception e) {
            logger.error("获取队列统计失败", e);
            return new QueueStats(0, 0);
        }
    }

    /**
     * 清理过期的玩家信息
     */
    public void cleanupExpiredPlayers() {
        // 这个方法可以定期调用，清理过期的玩家
        // Redis会自动删除过期的key，所以这里主要是清理队列集合中的残留引用
        try {
            String[] queueKeys = {CASUAL_QUEUE_KEY, RANKED_QUEUE_KEY};

            for (String queueKey : queueKeys) {
                Set<String> userIds = redisUtil.smembers(queueKey);
                if (userIds != null && !userIds.isEmpty()) {
                    for (String userIdStr : userIds) {
                        String playerKey = PLAYER_PREFIX + userIdStr;
                        if (!redisUtil.exists(playerKey)) {
                            // 玩家信息已过期，从队列中移除引用
                            redisUtil.srem(queueKey, userIdStr);
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("清理过期玩家失败", e);
        }
    }

    // ==================== 内部类 ====================

    /**
     * 匹配玩家信息
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchPlayer {
        private final Long userId;
        private final String username;
        private final String nickname;
        private final Integer rating;
        @JsonIgnore  // JSON序列化时忽略Channel对象
        private final transient Channel channel; // 不序列化到Redis
        private final String mode;
        private final long enqueueTime;

        public MatchPlayer(Long userId, String username, String nickname, Integer rating, Channel channel, String mode) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.rating = rating;
            this.channel = channel;
            this.mode = mode;
            this.enqueueTime = System.currentTimeMillis();
        }

        // 用于从Redis反序列化后创建新的对象（channel为null）
        @JsonCreator
        private MatchPlayer(
                @JsonProperty("userId") Long userId,
                @JsonProperty("username") String username,
                @JsonProperty("nickname") String nickname,
                @JsonProperty("rating") Integer rating,
                @JsonProperty("mode") String mode,
                @JsonProperty("enqueueTime") long enqueueTime) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.rating = rating;
            this.channel = null;
            this.mode = mode;
            this.enqueueTime = enqueueTime;
        }

        public MatchPlayer withChannel(Channel channel) {
            return new MatchPlayer(userId, username, nickname, rating, channel, mode);
        }

        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getNickname() { return nickname; }
        public Integer getRating() { return rating; }
        public Channel getChannel() { return channel; }
        public String getMode() { return mode; }
        public long getEnqueueTime() { return enqueueTime; }
        public long getWaitSeconds() { return (System.currentTimeMillis() - enqueueTime) / 1000; }
        public boolean isActive() {
            return channel != null && channel.isActive();
        }
    }

    /**
     * 匹配成功的玩家对
     */
    public static class MatchPair {
        private final MatchPlayer player1;
        private final MatchPlayer player2;

        public MatchPair(MatchPlayer player1, MatchPlayer player2) {
            this.player1 = player1;
            this.player2 = player2;
        }

        public MatchPlayer getPlayer1() { return player1; }
        public MatchPlayer getPlayer2() { return player2; }
    }

    /**
     * 队列统计信息
     */
    public static class QueueStats {
        private final int casualCount;
        private final int rankedCount;

        public QueueStats(int casualCount, int rankedCount) {
            this.casualCount = casualCount;
            this.rankedCount = rankedCount;
        }

        public int getCasualCount() { return casualCount; }
        public int getRankedCount() { return rankedCount; }
    }
}
