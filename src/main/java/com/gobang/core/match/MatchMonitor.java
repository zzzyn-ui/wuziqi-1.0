package com.gobang.core.match;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 匹配系统监控
 *
 * 监控指标：
 * - 当前队列大小
 * - 平均等待时间
 * - 匹配成功率
 * - 每秒匹配数
 * - 分段分布
 *
 * @author Gobang Team
 */
public class MatchMonitor {

    private static final Logger logger = LoggerFactory.getLogger(MatchMonitor.class);

    // 监控数据
    private final AtomicLong totalEnqueue = new AtomicLong(0);
    private final AtomicLong totalDequeue = new AtomicLong(0);
    private final AtomicLong totalMatches = new AtomicLong(0);
    private final AtomicLong totalTimeouts = new AtomicLong(0);

    // 时间窗口统计（最近60秒）
    private final WindowStats[] windows = new WindowStats[60];
    private int currentWindow = 0;

    // 监控调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "match-monitor");
        t.setDaemon(true);
        return t;
    });

    // 监控的匹配器
    private LockFreeMatcher matcher;

    public MatchMonitor() {
        // 初始化时间窗口
        for (int i = 0; i < windows.length; i++) {
            windows[i] = new WindowStats();
        }

        // 启动监控任务
        startMonitoring();
    }

    public void setMatcher(LockFreeMatcher matcher) {
        this.matcher = matcher;
    }

    // ==================== 事件记录 ====================

    public void recordEnqueue() {
        totalEnqueue.incrementAndGet();
        windows[currentWindow].enqueueCount.incrementAndGet();
    }

    public void recordDequeue() {
        totalDequeue.incrementAndGet();
    }

    public void recordMatch() {
        totalMatches.incrementAndGet();
        windows[currentWindow].matchCount.incrementAndGet();
    }

    public void recordTimeout() {
        totalTimeouts.incrementAndGet();
        windows[currentWindow].timeoutCount.incrementAndGet();
    }

    // ==================== 监控任务 ====================

    private void startMonitoring() {
        // 每秒滚动窗口
        scheduler.scheduleAtFixedRate(() -> {
            currentWindow = (currentWindow + 1) % windows.length;
            windows[currentWindow].reset();
        }, 1, 1, TimeUnit.SECONDS);

        // 每10秒打印统计
        scheduler.scheduleAtFixedRate(() -> {
            logStats();
        }, 10, 10, TimeUnit.SECONDS);

        // 每分钟输出详细报告
        scheduler.scheduleAtFixedRate(() -> {
            logDetailedReport();
        }, 60, 60, TimeUnit.SECONDS);
    }

    // ==================== 统计计算 ====================

    /**
     * 获取当前队列大小
     */
    public int getCurrentQueueSize() {
        return matcher != null ? matcher.getQueueSize() : 0;
    }

    /**
     * 获取最近60秒的匹配数
     */
    public long getMatchesLastMinute() {
        long sum = 0;
        for (WindowStats window : windows) {
            sum += window.matchCount.get();
        }
        return sum;
    }

    /**
     * 获取最近60秒的超时数
     */
    public long getTimeoutsLastMinute() {
        long sum = 0;
        for (WindowStats window : windows) {
            sum += window.timeoutCount.get();
        }
        return sum;
    }

    /**
     * 获取每秒匹配数（最近10秒平均）
     */
    public double getMatchesPerSecond() {
        long sum = 0;
        int count = 0;
        for (int i = 0; i < 10; i++) {
            int idx = (currentWindow - i + windows.length) % windows.length;
            sum += windows[idx].matchCount.get();
            if (windows[idx].matchCount.get() > 0) {
                count++;
            }
        }
        return count > 0 ? (double) sum / count : 0;
    }

    /**
     * 获取匹配成功率
     */
    public double getSuccessRate() {
        long total = totalEnqueue.get();
        if (total == 0) {
            return 0;
        }
        return (double) totalMatches.get() / total * 100;
    }

    /**
     * 获取超时率
     */
    public double getTimeoutRate() {
        long total = totalEnqueue.get();
        if (total == 0) {
            return 0;
        }
        return (double) totalTimeouts.get() / total * 100;
    }

    // ==================== 日志输出 ====================

    private void logStats() {
        int queueSize = getCurrentQueueSize();
        long matchesLastMin = getMatchesLastMinute();
        long timeoutsLastMin = getTimeoutsLastMinute();
        double mps = getMatchesPerSecond();

        logger.info("MatchMonitor - Queue: {}, Matches/min: {}, Timeouts/min: {}, MPS: {:.2f}",
                queueSize, matchesLastMin, timeoutsLastMin, mps);
    }

    private void logDetailedReport() {
        LockFreeMatcher.MatchStats stats = matcher != null ? matcher.getStats() : null;

        logger.info("==================== MatchMonitor Report ====================");
        logger.info("Total Enqueue:    {}", totalEnqueue.get());
        logger.info("Total Dequeue:    {}", totalDequeue.get());
        logger.info("Total Matches:    {}", totalMatches.get());
        logger.info("Total Timeouts:   {}", totalTimeouts.get());
        logger.info("Success Rate:     {:.2f}%", getSuccessRate());
        logger.info("Timeout Rate:     {:.2f}%", getTimeoutRate());
        logger.info("Matches/min:      {}", getMatchesLastMinute());
        logger.info("Current Queue:    {}", getCurrentQueueSize());

        if (stats != null) {
            logger.info("Segment Distribution:");
            int[] segmentSizes = stats.getSegmentSizes();
            for (int i = 0; i < segmentSizes.length && i < 20; i++) {
                if (segmentSizes[i] > 0) {
                    logger.info("  Segment {} ({}-{}): {} players",
                            i, i * 200, (i + 1) * 200 - 1, segmentSizes[i]);
                }
            }
        }

        logger.info("============================================================");
    }

    // ==================== 内部类 ====================

    /**
     * 时间窗口统计
     */
    private static class WindowStats {
        private final AtomicLong enqueueCount = new AtomicLong(0);
        private final AtomicLong matchCount = new AtomicLong(0);
        private final AtomicLong timeoutCount = new AtomicLong(0);

        public void reset() {
            enqueueCount.set(0);
            matchCount.set(0);
            timeoutCount.set(0);
        }
    }

    /**
     * 关闭监控
     */
    public void shutdown() {
        scheduler.shutdown();
        logDetailedReport();
    }
}
