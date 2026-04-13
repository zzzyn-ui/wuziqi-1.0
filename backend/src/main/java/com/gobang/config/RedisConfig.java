package com.gobang.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * Redis配置类
 * 支持从环境变量覆盖配置：
 * - REDIS_HOST: Redis主机地址
 * - REDIS_PORT: Redis端口
 * - REDIS_PASSWORD: Redis密码
 */
public class RedisConfig {

    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final int timeout;
    private final PoolConfig pool;

    public RedisConfig(Map<String, Object> config) {
        Map<String, Object> redisConfig = (Map<String, Object>) config.get("redis");

        // 优先从环境变量读取Redis配置
        String envHost = System.getenv("REDIS_HOST");
        String envPort = System.getenv("REDIS_PORT");
        String envPassword = System.getenv("REDIS_PASSWORD");

        this.host = (envHost != null && !envHost.isEmpty()) ? envHost : (String) redisConfig.get("host");
        this.port = (envPort != null && !envPort.isEmpty()) ? Integer.parseInt(envPort) : ((Number) redisConfig.get("port")).intValue();
        this.password = (envPassword != null && !envPassword.isEmpty()) ? envPassword : (String) redisConfig.get("password");
        this.database = ((Number) redisConfig.get("database")).intValue();
        this.timeout = ((Number) redisConfig.get("timeout")).intValue();

        Map<String, Object> poolConfig = (Map<String, Object>) redisConfig.get("pool");
        this.pool = new PoolConfig(poolConfig);
    }

    public static RedisConfig load() {
        Yaml yaml = new Yaml();
        InputStream inputStream = RedisConfig.class.getClassLoader().getResourceAsStream("application.yml");
        Map<String, Object> config = yaml.load(inputStream);
        return new RedisConfig(config);
    }

    // Getters
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPassword() {
        return password;
    }

    public int getDatabase() {
        return database;
    }

    public int getTimeout() {
        return timeout;
    }

    public PoolConfig getPool() {
        return pool;
    }

    public static class PoolConfig {
        private final int maxActive;
        private final int maxIdle;
        private final int minIdle;
        private final int maxWait;

        public PoolConfig(Map<String, Object> config) {
            this.maxActive = ((Number) config.get("max-active")).intValue();
            this.maxIdle = ((Number) config.get("max-idle")).intValue();
            this.minIdle = ((Number) config.get("min-idle")).intValue();
            this.maxWait = ((Number) config.get("max-wait")).intValue();
        }

        public int getMaxActive() {
            return maxActive;
        }

        public int getMaxIdle() {
            return maxIdle;
        }

        public int getMinIdle() {
            return minIdle;
        }

        public int getMaxWait() {
            return maxWait;
        }
    }
}
