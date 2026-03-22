package com.gobang.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Set;

/**
 * Redis操作封装类
 */
public class RedisUtil {

    private static final Logger logger = LoggerFactory.getLogger(RedisUtil.class);

    private final JedisPool pool;
    private final int database;
    private final String host;
    private final int port;
    private final String password;

    public RedisUtil(String host, int port, String password, int database, int timeout) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.password = password;
        logger.info("=== RedisUtil 初始化: host={}, port={}, database={}, timeout={}, password={} ===",
            host, port, database, timeout, password != null && !password.isEmpty() ? "***" : "(none)");

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(50);
        config.setMaxIdle(20);
        config.setMinIdle(5);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(false);
        config.setTestWhileIdle(true);
        config.setMinEvictableIdleTimeMillis(60000);
        config.setTimeBetweenEvictionRunsMillis(30000);
        config.setNumTestsPerEvictionRun(-1);

        // 使用带密码的构造函数，让连接池自动处理认证
        if (password != null && !password.isEmpty()) {
            this.pool = new JedisPool(config, host, port, timeout, password);
        } else {
            this.pool = new JedisPool(config, host, port, timeout);
        }
    }

    /**
     * 获取Jedis实例
     */
    private Jedis getResource() {
        Jedis jedis = pool.getResource();
        // 只需要选择数据库，认证已由连接池自动处理
        if (database != 0) {
            jedis.select(database);
        }
        return jedis;
    }

    /**
     * 设置字符串值
     */
    public void set(String key, String value) {
        try (Jedis jedis = getResource()) {
            jedis.set(key, value);
        } catch (Exception e) {
            logger.error("Redis set error: key={}", key, e);
        }
    }

    /**
     * 设置字符串值(带过期时间)
     */
    public void setex(String key, int seconds, String value) {
        try (Jedis jedis = getResource()) {
            jedis.setex(key, seconds, value);
        } catch (Exception e) {
            logger.error("Redis setex error: key={}", key, e);
        }
    }

    /**
     * 获取字符串值
     */
    public String get(String key) {
        try (Jedis jedis = getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            logger.error("Redis get error: key={}", key, e);
            return null;
        }
    }

    /**
     * 测试 Redis 连接（抛出异常以便调用者检测连接状态）
     */
    public void testConnection() throws Exception {
        try (Jedis jedis = pool.getResource()) {
            // 认证
            if (password != null && !password.isEmpty()) {
                jedis.auth(password);
            }
            if (database != 0) {
                jedis.select(database);
            }
            // 执行 ping 命令测试连接
            String pong = jedis.ping();
            if (!"PONG".equals(pong)) {
                throw new RuntimeException("Redis ping 失败: " + pong);
            }
        }
    }

    /**
     * 删除键
     */
    public void del(String key) {
        try (Jedis jedis = getResource()) {
            jedis.del(key);
        } catch (Exception e) {
            logger.error("Redis del error: key={}", key, e);
        }
    }

    /**
     * 删除多个键
     */
    public void del(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        try (Jedis jedis = getResource()) {
            jedis.del(keys);
        } catch (Exception e) {
            logger.error("Redis del error: keys={}", java.util.Arrays.toString(keys), e);
        }
    }

    /**
     * 查找匹配的键
     */
    public Set<String> keys(String pattern) {
        try (Jedis jedis = getResource()) {
            return jedis.keys(pattern);
        } catch (Exception e) {
            logger.error("Redis keys error: pattern={}", pattern, e);
            return null;
        }
    }

    /**
     * 检查键是否存在
     */
    public boolean exists(String key) {
        try (Jedis jedis = getResource()) {
            return jedis.exists(key);
        } catch (Exception e) {
            logger.error("Redis exists error: key={}", key, e);
            return false;
        }
    }

    /**
     * 设置过期时间
     */
    public void expire(String key, int seconds) {
        try (Jedis jedis = getResource()) {
            jedis.expire(key, seconds);
        } catch (Exception e) {
            logger.error("Redis expire error: key={}", key, e);
        }
    }

    /**
     * 获取剩余过期时间
     */
    public long ttl(String key) {
        try (Jedis jedis = getResource()) {
            return jedis.ttl(key);
        } catch (Exception e) {
            logger.error("Redis ttl error: key={}", key, e);
            return -1;
        }
    }

    /**
     * 哈希表设置字段
     */
    public void hset(String key, String field, String value) {
        try (Jedis jedis = getResource()) {
            jedis.hset(key, field, value);
        } catch (Exception e) {
            logger.error("Redis hset error: key={}, field={}", key, field, e);
        }
    }

    /**
     * 哈希表获取字段
     */
    public String hget(String key, String field) {
        try (Jedis jedis = getResource()) {
            return jedis.hget(key, field);
        } catch (Exception e) {
            logger.error("Redis hget error: key={}, field={}", key, field, e);
            return null;
        }
    }

    /**
     * 哈希表获取所有字段
     */
    public java.util.Map<String, String> hgetAll(String key) {
        try (Jedis jedis = getResource()) {
            return jedis.hgetAll(key);
        } catch (Exception e) {
            logger.error("Redis hgetAll error: key={}", key, e);
            return null;
        }
    }

    /**
     * 哈希表删除字段
     */
    public void hdel(String key, String... fields) {
        try (Jedis jedis = getResource()) {
            jedis.hdel(key, fields);
        } catch (Exception e) {
            logger.error("Redis hdel error: key={}", key, e);
        }
    }

    /**
     * 集合添加成员
     */
    public void sadd(String key, String... members) {
        Jedis jedis = null;
        try {
            jedis = getResource();
            long result = jedis.sadd(key, members);
            logger.info("Redis sadd: key={}, members={}, result={}", key, java.util.Arrays.toString(members), result);
        } catch (Exception e) {
            logger.error("Redis sadd error: key={}", key, e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    /**
     * 集合移除成员
     */
    public void srem(String key, String... members) {
        try (Jedis jedis = getResource()) {
            jedis.srem(key, members);
        } catch (Exception e) {
            logger.error("Redis srem error: key={}", key, e);
        }
    }

    /**
     * 集合获取所有成员
     */
    public Set<String> smembers(String key) {
        try (Jedis jedis = getResource()) {
            return jedis.smembers(key);
        } catch (Exception e) {
            logger.error("Redis smembers error: key={}", key, e);
            return null;
        }
    }

    /**
     * 集合成员数量
     */
    public long scard(String key) {
        try (Jedis jedis = getResource()) {
            return jedis.scard(key);
        } catch (Exception e) {
            logger.error("Redis scard error: key={}", key, e);
            return 0;
        }
    }

    /**
     * 自增
     */
    public long incr(String key) {
        try (Jedis jedis = getResource()) {
            return jedis.incr(key);
        } catch (Exception e) {
            logger.error("Redis incr error: key={}", key, e);
            return 0;
        }
    }

    /**
     * 自增指定值
     */
    public long incrBy(String key, long value) {
        try (Jedis jedis = getResource()) {
            return jedis.incrBy(key, value);
        } catch (Exception e) {
            logger.error("Redis incrBy error: key={}", key, e);
            return 0;
        }
    }

    /**
     * 关闭连接池
     */
    public void close() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    /**
     * 保存游戏状态到Redis
     */
    public void saveGameState(String roomId, String gameStateJson, int expireSeconds) {
        String key = "game:state:" + roomId;
        setex(key, expireSeconds, gameStateJson);
        logger.info("保存游戏状态到Redis: roomId={}, expireSeconds={}", roomId, expireSeconds);
    }

    /**
     * 从Redis加载游戏状态
     */
    public String loadGameState(String roomId) {
        String key = "game:state:" + roomId;
        String state = get(key);
        logger.info("从Redis加载游戏状态: roomId={}, found={}", roomId, state != null);
        return state;
    }

    /**
     * 删除游戏状态
     */
    public void deleteGameState(String roomId) {
        String key = "game:state:" + roomId;
        del(key);
        logger.info("删除游戏状态: roomId={}", roomId);
    }

    /**
     * 保存用户到房间的映射（用于重连）
     */
    public void saveUserRoomMapping(Long userId, String roomId, int expireSeconds) {
        String key = "user:room:" + userId;
        setex(key, expireSeconds, roomId);
        logger.info("保存用户房间映射: userId={}, roomId={}, expireSeconds={}", userId, roomId, expireSeconds);
    }

    /**
     * 获取用户的房间ID
     */
    public String getUserRoomId(Long userId) {
        String key = "user:room:" + userId;
        String roomId = get(key);
        return roomId;
    }

    /**
     * 删除用户房间映射
     */
    public void deleteUserRoomMapping(Long userId) {
        String key = "user:room:" + userId;
        del(key);
        logger.info("删除用户房间映射: userId={}", userId);
    }

    /**
     * 保存用户最后活动时间（用于超时检测）
     */
    public void saveUserLastActivity(String roomId, Long userId, long timestamp) {
        String key = "game:activity:" + roomId + ":" + userId;
        setex(key, 180, String.valueOf(timestamp)); // 3分钟过期
        logger.info("保存用户活动时间: roomId={}, userId={}, timestamp={}", roomId, userId, timestamp);
    }

    /**
     * 获取用户最后活动时间
     */
    public Long getUserLastActivity(String roomId, Long userId) {
        String key = "game:activity:" + roomId + ":" + userId;
        String timestamp = get(key);
        if (timestamp != null) {
            try {
                return Long.parseLong(timestamp);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 检查用户是否超时（超过指定时间未活动）
     */
    public boolean isUserTimeout(String roomId, Long userId, int timeoutSeconds) {
        Long lastActivity = getUserLastActivity(roomId, userId);
        if (lastActivity == null) {
            return false; // 没有活动记录，不算超时
        }
        long elapsed = (System.currentTimeMillis() - lastActivity) / 1000;
        return elapsed > timeoutSeconds;
    }
}
