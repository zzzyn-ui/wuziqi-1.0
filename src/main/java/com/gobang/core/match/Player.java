package com.gobang.core.match;

import io.netty.channel.Channel;

/**
 * 匹配队列中的玩家
 */
public class Player {

    private final Long userId;
    private final String username;
    private final String nickname;
    private final Integer rating;
    private final Channel channel;
    private final long enqueueTime;
    private final String mode;  // 游戏模式: "casual"=休闲, "ranked"=竞技

    public Player(Long userId, String username, String nickname, Integer rating, Channel channel, String mode) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.rating = rating;
        this.channel = channel;
        this.enqueueTime = System.currentTimeMillis();
        this.mode = mode != null ? mode : "casual";  // 默认休闲模式
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }

    public Integer getRating() {
        return rating;
    }

    public Channel getChannel() {
        return channel;
    }

    public long getEnqueueTime() {
        return enqueueTime;
    }

    public String getMode() {
        return mode;
    }

    /**
     * 检查是否超时
     */
    public boolean isTimeout(long maxQueueTime) {
        return System.currentTimeMillis() - enqueueTime > maxQueueTime;
    }

    /**
     * 检查通道是否活跃
     * 机器人玩家（userId < 0）始终被认为是活跃的
     */
    public boolean isActive() {
        // 机器人玩家始终活跃
        if (userId < 0) {
            return true;
        }
        return channel != null && channel.isActive();
    }

    /**
     * 检查是否可以匹配
     * 必须满足：
     * 1. 双方都活跃
     * 2. 积分差异在允许范围内
     * 3. 游戏模式相同（休闲只能匹配休闲，竞技只能匹配竞技）
     */
    public boolean canMatch(Player other, int maxRatingDiff) {
        if (!this.isActive() || !other.isActive()) {
            return false;
        }
        // 检查模式是否一致
        if (!this.mode.equals(other.mode)) {
            return false;
        }
        return Math.abs(this.rating - other.rating) <= maxRatingDiff;
    }
}
