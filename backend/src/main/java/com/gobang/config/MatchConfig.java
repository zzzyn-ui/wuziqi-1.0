package com.gobang.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * 匹配系统配置
 *
 * 配置说明：
 * - ratingDiff: 初始积分差异范围（随等待时间动态扩大）
 * - maxQueueTime: 最大等待时间（秒）
 * - checkInterval: 匹配检查间隔（毫秒）
 * - testMode: 测试模式（单人可与机器人匹配）
 * - testMatchDelay: 测试模式下自动匹配延迟（毫秒）
 * - enableTimeExpansion: 是否启用时间膨胀算法
 * - expansionRate: 时间膨胀速率（每秒扩大多少积分范围）
 * - maxExpansionRating: 时间膨胀最大积分范围
 *
 * @author Gobang Team
 */
public class MatchConfig {

    /**
     * 初始积分差异范围
     */
    private final int ratingDiff;

    /**
     * 最大等待时间（秒）
     */
    private final int maxQueueTime;

    /**
     * 匹配检查间隔（毫秒）
     */
    private final int checkInterval;

    /**
     * 测试模式开关
     */
    private final boolean testMode;

    /**
     * 测试模式下自动匹配延迟（毫秒）
     */
    private final int testMatchDelay;

    /**
     * 是否启用时间膨胀算法
     * 等待时间越长，匹配范围越大
     */
    private final boolean enableTimeExpansion;

    /**
     * 时间膨胀速率
     * 每等待1秒，积分范围扩大多少
     */
    private final int expansionRate;

    /**
     * 时间膨胀最大积分范围
     */
    private final int maxExpansionRating;

    /**
     * 是否启用分段匹配
     * 按积分分段，减少匹配计算量
     */
    private final boolean enableSegmentedMatch;

    /**
     * 积分段大小
     * 每段包含的积分范围
     */
    private final int segmentSize;

    /**
     * 从 YAML 配置创建 MatchConfig
     */
    @SuppressWarnings("unchecked")
    public MatchConfig(Map<String, Object> config) {
        Map<String, Object> matchConfig = (Map<String, Object>) config.get("match");

        this.ratingDiff = getIntValue(matchConfig, "rating-diff", 100);
        this.maxQueueTime = getIntValue(matchConfig, "max-queue-time", 300);
        this.checkInterval = getIntValue(matchConfig, "check-interval", 100);
        this.testMode = getBooleanValue(matchConfig, "test-mode", false);
        this.testMatchDelay = getIntValue(matchConfig, "test-match-delay", 3000);
        this.enableTimeExpansion = getBooleanValue(matchConfig, "enable-time-expansion", true);
        this.expansionRate = getIntValue(matchConfig, "expansion-rate", 20);
        this.maxExpansionRating = getIntValue(matchConfig, "max-expansion-rating", 500);
        this.enableSegmentedMatch = getBooleanValue(matchConfig, "enable-segmented-match", true);
        this.segmentSize = getIntValue(matchConfig, "segment-size", 200);
    }

    private int getIntValue(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private boolean getBooleanValue(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    // Getters
    public int getRatingDiff() { return ratingDiff; }

    public int getMaxQueueTime() { return maxQueueTime; }

    public int getCheckInterval() { return checkInterval; }

    public boolean isTestMode() { return testMode; }

    public int getTestMatchDelay() { return testMatchDelay; }

    public boolean isEnableTimeExpansion() { return enableTimeExpansion; }

    public int getExpansionRate() { return expansionRate; }

    public int getMaxExpansionRating() { return maxExpansionRating; }

    public boolean isEnableSegmentedMatch() { return enableSegmentedMatch; }

    public int getSegmentSize() { return segmentSize; }

    /**
     * 计算时间膨胀后的积分范围
     */
    public int calculateExpandedRatingDiff(long waitSeconds) {
        if (!enableTimeExpansion) {
            return ratingDiff;
        }
        int expansion = (int) (waitSeconds * expansionRate);
        int expanded = ratingDiff + expansion;
        return Math.min(expanded, maxExpansionRating);
    }

    @Override
    public String toString() {
        return "MatchConfig{" +
                "ratingDiff=" + ratingDiff +
                ", maxQueueTime=" + maxQueueTime +
                ", checkInterval=" + checkInterval +
                ", testMode=" + testMode +
                ", testMatchDelay=" + testMatchDelay +
                ", enableTimeExpansion=" + enableTimeExpansion +
                ", expansionRate=" + expansionRate +
                ", maxExpansionRating=" + maxExpansionRating +
                ", enableSegmentedMatch=" + enableSegmentedMatch +
                ", segmentSize=" + segmentSize +
                '}';
    }
}
