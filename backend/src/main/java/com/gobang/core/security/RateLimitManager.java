package com.gobang.core.security;

import com.gobang.util.RateLimiter;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流管理器
 * 管理所有业务场景的限流器
 */
public class RateLimitManager {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitManager.class);

    // Channel属性：客户端IP地址
    private static final AttributeKey<String> IP_ATTR = AttributeKey.valueOf("clientIp");

    // 限流器类型
    public enum LimitType {
        // 登录限流（按IP）
        LOGIN("login", 5, 60_000),        // 5次/分钟
        // 注册限流（按IP）
        REGISTER("register", 3, 300_000),  // 3次/5分钟
        // 聊天限流（按用户ID）
        CHAT("chat", 10, 10_000),          // 10条/10秒
        // 好友请求限流（按用户ID）
        FRIEND_REQUEST("friend_req", 3, 60_000),  // 3次/分钟
        // 匹配请求限流（按用户ID）
        MATCH("match", 2, 5_000),          // 2次/5秒
        // 游戏落子限流（按用户ID）
        GAME_MOVE("game_move", 3, 1_000);  // 3次/秒

        private final String name;
        private final int capacity;
        private final long refillMillis;

        LimitType(String name, int capacity, long refillMillis) {
            this.name = name;
            this.capacity = capacity;
            this.refillMillis = refillMillis;
        }
    }

    // 限流器容器
    private final Map<LimitType, RateLimiter> limiters = new ConcurrentHashMap<>();

    public RateLimitManager() {
        // 初始化所有限流器
        for (LimitType type : LimitType.values()) {
            RateLimiter limiter = RateLimiter.builder()
                    .capacity(type.capacity)
                    .refillTokens(type.capacity)  // 每次补充满容量
                    .refillMillis(type.refillMillis)
                    .build();
            limiters.put(type, limiter);
            logger.info("Initialized rate limiter: {} ({} per {}ms)",
                    type.name, type.capacity, type.refillMillis);
        }
    }

    /**
     * 检查是否允许通过（按IP限流）
     *
     * @param type     限流类型
     * @param channel  Netty Channel
     * @return 是否允许
     */
    public boolean tryAcquire(LimitType type, Channel channel) {
        String ip = getClientIp(channel);
        if (ip == null) {
            ip = "unknown";
        }
        return tryAcquire(type, ip.hashCode());
    }

    /**
     * 检查是否允许通过（按用户ID限流）
     *
     * @param type   限流类型
     * @param userId 用户ID
     * @return 是否允许
     */
    public boolean tryAcquire(LimitType type, Long userId) {
        if (userId == null) {
            // 未登录用户不允许通过需要认证的操作
            return false;
        }
        // 调用long版本的方法，避免递归
        return tryAcquire(type, userId.longValue());
    }

    /**
     * 检查是否允许通过（按自定义key限流）
     *
     * @param type 限流类型
     * @param key  限流key
     * @return 是否允许
     */
    public boolean tryAcquire(LimitType type, long key) {
        RateLimiter limiter = limiters.get(type);
        if (limiter == null) {
            return true;
        }

        boolean allowed = limiter.tryAcquire(key);

        if (!allowed) {
            logger.debug("Rate limit exceeded: type={}, key={}", type.name, key);
        }

        return allowed;
    }

    /**
     * 重置指定用户的限流
     *
     * @param type   限流类型
     * @param userId 用户ID
     */
    public void reset(LimitType type, Long userId) {
        if (userId == null) {
            return;
        }
        RateLimiter limiter = limiters.get(type);
        if (limiter != null) {
            limiter.reset(userId);
            logger.debug("Reset rate limit: type={}, userId={}", type.name, userId);
        }
    }

    /**
     * 获取限流器配置信息
     *
     * @param type 限流类型
     * @return [容量, 补充间隔(毫秒)]
     */
    public int[] getConfig(LimitType type) {
        return new int[]{type.capacity, (int) type.refillMillis};
    }

    /**
     * 获取剩余可执行次数
     *
     * @param type   限流类型
     * @param userId 用户ID
     * @return 剩余次数（-1表示未知）
     */
    public int getRemaining(LimitType type, Long userId) {
        if (userId == null) {
            return 0;
        }
        // 简化实现，返回容量值
        return type.capacity;
    }

    /**
     * 清理所有限流器
     */
    public void clear() {
        for (RateLimiter limiter : limiters.values()) {
            limiter.clear();
        }
        logger.info("All rate limiters cleared");
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(Channel channel) {
        String ip = channel.attr(IP_ATTR).get();
        if (ip == null) {
            // 从Channel的remoteAddress获取
            ip = channel.remoteAddress() != null
                    ? channel.remoteAddress().toString()
                    : "unknown";
        }
        return ip;
    }

    /**
     * 设置客户端IP地址（由HttpRequestHandler调用）
     */
    public static void setClientIp(Channel channel, String ip) {
        channel.attr(IP_ATTR).set(ip);
    }

    /**
     * 获取限流错误消息
     */
    public String getErrorMessage(LimitType type) {
        switch (type) {
            case LOGIN:
                return "登录请求过于频繁，请稍后再试";
            case REGISTER:
                return "注册请求过于频繁，请稍后再试";
            case CHAT:
                return "发送消息过于频繁，请稍后再试";
            case FRIEND_REQUEST:
                return "好友请求过于频繁，请稍后再试";
            case MATCH:
                return "匹配请求过于频繁，请稍后再试";
            case GAME_MOVE:
                return "落子过于频繁";
            default:
                return "操作过于频繁，请稍后再试";
        }
    }

    /**
     * 获取限流配置详情（用于监控）
     */
    public Map<String, String> getStats() {
        Map<String, String> stats = new ConcurrentHashMap<>();
        for (LimitType type : LimitType.values()) {
            int[] config = getConfig(type);
            stats.put(type.name(),
                    String.format("capacity=%d, refill=%dms",
                            config[0], config[1]));
        }
        return stats;
    }
}
