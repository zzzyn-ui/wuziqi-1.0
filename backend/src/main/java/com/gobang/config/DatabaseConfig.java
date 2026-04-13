package com.gobang.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * 数据库配置类
 * 支持从环境变量覆盖配置：
 * - DB_URL: 数据库连接URL
 * - DB_USERNAME: 数据库用户名
 * - DB_PASSWORD: 数据库密码
 */
public class DatabaseConfig {

    private final String driver;
    private final String url;
    private final String username;
    private final String password;
    private final int poolSize;
    private final long connectionTimeout;
    private final long idleTimeout;
    private final long maxLifetime;

    public DatabaseConfig(Map<String, Object> config) {
        Map<String, Object> dbConfig = (Map<String, Object>) config.get("database");

        this.driver = (String) dbConfig.get("driver");

        // 优先从环境变量读取数据库配置
        String envUrl = System.getenv("DB_URL");
        String envUsername = System.getenv("DB_USERNAME");
        String envPassword = System.getenv("DB_PASSWORD");

        this.url = (envUrl != null && !envUrl.isEmpty()) ? envUrl : (String) dbConfig.get("url");
        this.username = (envUsername != null && !envUsername.isEmpty()) ? envUsername : (String) dbConfig.get("username");
        this.password = (envPassword != null && !envPassword.isEmpty()) ? envPassword : (String) dbConfig.get("password");

        this.poolSize = ((Number) dbConfig.get("pool-size")).intValue();
        this.connectionTimeout = ((Number) dbConfig.get("connection-timeout")).longValue();
        this.idleTimeout = ((Number) dbConfig.get("idle-timeout")).longValue();
        this.maxLifetime = ((Number) dbConfig.get("max-lifetime")).longValue();
    }

    public static DatabaseConfig load() {
        Yaml yaml = new Yaml();
        InputStream inputStream = DatabaseConfig.class.getClassLoader().getResourceAsStream("application.yml");
        Map<String, Object> config = yaml.load(inputStream);
        return new DatabaseConfig(config);
    }

    // Getters
    public String getDriver() {
        return driver;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public long getMaxLifetime() {
        return maxLifetime;
    }
}
