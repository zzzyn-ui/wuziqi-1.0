package com.gobang.util;

/**
 * 雪花算法ID生成器
 * 生成64位唯一ID
 */
public class IdGenerator {

    // 起始时间戳 (2024-01-01 00:00:00)
    private static final long EPOCH = 1704067200000L;

    // 各部分位数
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    // 各部分最大值
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    // 各部分偏移量
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long datacenterId;
    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * 构造函数
     *
     * @param datacenterId 数据中心ID (0-31)
     * @param workerId     工作机器ID (0-31)
     */
    public IdGenerator(long datacenterId, long workerId) {
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(
                    String.format("datacenter Id can't be greater than %d or less than 0", MAX_DATACENTER_ID));
        }
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("worker Id can't be greater than %d or less than 0", MAX_WORKER_ID));
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    /**
     * 生成下一个ID
     *
     * @return 64位唯一ID
     */
    public synchronized long nextId() {
        long timestamp = timeGen();

        // 时钟回拨检查
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                try {
                    // 等待2ms
                    Thread.sleep(2L);
                    timestamp = timeGen();
                    if (timestamp < lastTimestamp) {
                        throw new RuntimeException("Clock moved backwards. Refusing to generate id");
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException("Clock moved backwards. Refusing to generate id");
                }
            } else {
                throw new RuntimeException("Clock moved backwards. Refusing to generate id");
            }
        }

        // 同一毫秒内
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 序列号溢出，等待下一毫秒
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 组装ID
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 等待下一毫秒
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳
     */
    private long timeGen() {
        return System.currentTimeMillis();
    }

    /**
     * 解析ID获取时间戳
     */
    public static long parseTimestamp(long id) {
        return ((id >> TIMESTAMP_SHIFT) & ~(-1L << 41)) + EPOCH;
    }

    /**
     * 解析ID获取数据中心ID
     */
    public static long parseDatacenterId(long id) {
        return (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
    }

    /**
     * 解析ID获取工作机器ID
     */
    public static long parseWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    /**
     * 解析ID获取序列号
     */
    public static long parseSequence(long id) {
        return id & MAX_SEQUENCE;
    }
}
