package com.gobang.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流器测试类
 */
@DisplayName("限流器测试")
class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // 创建一个容量为10，每秒补充5个令牌的限流器
        rateLimiter = RateLimiter.builder()
                .capacity(10)
                .refillTokens(5)
                .refillMillis(1000)
                .build();
    }

    @Test
    @DisplayName("初始状态下应该能够获取令牌")
    void shouldAcquireTokenInitially() {
        assertTrue(rateLimiter.tryAcquire(1L), "初始状态下应该能够获取令牌");
    }

    @Test
    @DisplayName("应该能够连续获取多个令牌")
    void shouldAcquireMultipleTokens() {
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.tryAcquire(1L), "应该能够获取第" + (i + 1) + "个令牌");
        }
    }

    @Test
    @DisplayName("超过容量后不应该能够获取令牌")
    void shouldNotAcquireWhenExhausted() {
        // 耗尽所有令牌
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryAcquire(1L);
        }
        assertFalse(rateLimiter.tryAcquire(1L), "超过容量后不应该能够获取令牌");
    }

    @Test
    @DisplayName("不同用户应该有独立的令牌桶")
    void shouldHaveIndependentBucketsForDifferentUsers() {
        // 用户1耗尽令牌
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryAcquire(1L);
        }
        assertFalse(rateLimiter.tryAcquire(1L), "用户1不应该能够获取更多令牌");

        // 用户2应该仍然能够获取令牌
        assertTrue(rateLimiter.tryAcquire(2L), "用户2应该能够获取令牌");
    }

    @Test
    @DisplayName("重置后应该能够重新获取令牌")
    void shouldAcquireAfterReset() {
        // 耗尽所有令牌
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryAcquire(1L);
        }
        assertFalse(rateLimiter.tryAcquire(1L), "耗尽后不应该能够获取令牌");

        // 重置
        rateLimiter.reset(1L);
        assertTrue(rateLimiter.tryAcquire(1L), "重置后应该能够获取令牌");
    }

    @Test
    @DisplayName("清空后所有用户都应该重新开始")
    void shouldRestartAfterClear() {
        // 用户1和用户2都获取一些令牌
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(1L);
            rateLimiter.tryAcquire(2L);
        }

        rateLimiter.clear();

        // 清空后两个用户都应该能够重新获取令牌
        assertTrue(rateLimiter.tryAcquire(1L), "清空后用户1应该能够获取令牌");
        assertTrue(rateLimiter.tryAcquire(2L), "清空后用户2应该能够获取令牌");
    }

    @Test
    @DisplayName("应该正确创建限流器")
    void shouldCreateRateLimiterWithBuilder() {
        RateLimiter limiter = RateLimiter.builder()
                .capacity(100)
                .refillTokens(10)
                .refillMillis(1000)
                .build();

        assertNotNull(limiter, "应该成功创建限流器");

        // 验证容量正确
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.tryAcquire(1L), "应该能够获取所有容量内的令牌");
        }
        assertFalse(limiter.tryAcquire(1L), "超过容量不应该能够获取令牌");
    }
}
