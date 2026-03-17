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
        logger.info("=== RedisUtil 初始化: host={}, port={}, database={}, timeout={} ===", host, port, database, timeout);

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(50);
        config.setMaxIdle(20);
        config.setMinIdle(5);
        config.setTestOnBorrow(false);  // 禁用 - 连接需要先认证
        config.setTestOnReturn(false);
        config.setTestWhileIdle(false);  // 禁用 - 空闲测试也需要认证
        config.setMinEvictableIdleTimeMillis(60000);
        config.setTimeBetweenEvictionRunsMillis(30000);
        config.setNumTestsPerEvictionRun(-1);

        // 使用最简单的构造函数，手动处理认证
        this.pool = new JedisPool(config, host, port, timeout);
    }

    /**
     * 获取Jedis实例
     */
    private Jedis getResource() {
        Jedis jedis = pool.getResource();
        // 手动认证和选择数据库
        if (password != null && !password.isEmpty()) {
            jedis.auth(password);
        }
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
}
