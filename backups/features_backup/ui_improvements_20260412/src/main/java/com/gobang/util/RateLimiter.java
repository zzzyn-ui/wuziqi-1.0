package com.gobang.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简单的令牌桶限流器
 */
public class RateLimiter {

    private final long capacity;          // 桶容量
    private final long refillTokens;      // 每次补充的令牌数
    private final long refillMillis;      // 补充间隔（毫秒）

    private final ConcurrentHashMap<Long, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(long capacity, long refillTokens, long refillMillis) {
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillMillis = refillMillis;
    }

    /**
     * 尝试获取令牌
     * @param key 用户ID
     * @return 是否获取成功
     */
    public boolean tryAcquire(Long key) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket());
        return bucket.tryAcquire();
    }

    /**
     * 重置指定用户的令牌桶
     */
    public void reset(Long key) {
        buckets.remove(key);
    }

    /**
     * 清理所有令牌桶
     */
    public void clear() {
        buckets.clear();
    }

    /**
     * 令牌桶
     */
    private class TokenBucket {
        private final AtomicLong tokens;
        private volatile long lastRefillTime;

        TokenBucket() {
            this.tokens = new AtomicLong(capacity);
            this.lastRefillTime = System.currentTimeMillis();
        }

        boolean tryAcquire() {
            refill();
            while (true) {
                long current = tokens.get();
                if (current <= 0) {
                    return false;
                }
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }

        void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;

            if (elapsed >= refillMillis) {
                long periods = elapsed / refillMillis;
                long toAdd = Math.min(periods * refillTokens, capacity);

                if (toAdd > 0) {
                    tokens.updateAndGet(current -> Math.min(current + toAdd, capacity));
                    lastRefillTime = now;
                }
            }
        }
    }

    /**
     * 创建限流器实例
     */
    public static class Builder {
        private long capacity = 100;
        private long refillTokens = 10;
        private long refillMillis = 1000;

        public Builder capacity(long capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder refillTokens(long refillTokens) {
            this.refillTokens = refillTokens;
            return this;
        }

        public Builder refillMillis(long refillMillis) {
            this.refillMillis = refillMillis;
            return this;
        }

        public RateLimiter build() {
            return new RateLimiter(capacity, refillTokens, refillMillis);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
