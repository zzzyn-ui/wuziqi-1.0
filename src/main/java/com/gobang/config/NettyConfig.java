package com.gobang.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * Netty配置类
 */
public class NettyConfig {

    private final int port;
    private final String host;
    private final int bossThreads;
    private final int workerThreads;
    private final int maxConnections;
    private final int readTimeout;
    private final int writeTimeout;
    private final int heartbeatInterval;

    public NettyConfig(Map<String, Object> config) {
        Map<String, Object> nettyConfig = (Map<String, Object>) config.get("netty");

        this.port = ((Number) nettyConfig.get("port")).intValue();
        this.host = (String) nettyConfig.getOrDefault("host", "0.0.0.0");
        this.bossThreads = ((Number) nettyConfig.get("boss-threads")).intValue();
        this.workerThreads = ((Number) nettyConfig.get("worker-threads")).intValue();
        this.maxConnections = ((Number) nettyConfig.get("max-connections")).intValue();
        this.readTimeout = ((Number) nettyConfig.get("read-timeout")).intValue();
        this.writeTimeout = ((Number) nettyConfig.get("write-timeout")).intValue();
        this.heartbeatInterval = ((Number) nettyConfig.get("heartbeat-interval")).intValue();
    }

    public static NettyConfig load() {
        Yaml yaml = new Yaml();
        InputStream inputStream = NettyConfig.class.getClassLoader().getResourceAsStream("application.yml");
        Map<String, Object> config = yaml.load(inputStream);
        return new NettyConfig(config);
    }

    // Getters
    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getWriteTimeout() {
        return writeTimeout;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }
}
