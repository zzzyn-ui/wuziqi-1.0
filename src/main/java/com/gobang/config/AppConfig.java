package com.gobang.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * 应用配置类
 *
 * 从 application.yml 加载配置
 */
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    private final JwtConfig jwt;
    private final GameConfig game;
    private final WebSocketConfig webSocket;

    /**
     * 加载配置（静态工厂方法）
     */
    public static AppConfig load() {
        return new AppConfig();
    }

    public AppConfig() {
        Yaml yaml = new Yaml();
        InputStream inputStream = AppConfig.class.getClassLoader().getResourceAsStream("application.yml");
        Map<String, Object> config = yaml.load(inputStream);

        this.jwt = new JwtConfig(config);
        this.game = new GameConfig(config);
        this.webSocket = new WebSocketConfig(config);

        logger.info("AppConfig loaded: game.boardSize={}, game.matchTimeout={}s, ws.path={}",
                game.getBoardSize(), game.getMatchTimeout(), webSocket.getPath());
    }

    /**
     * 获取 JWT 配置
     */
    public JwtConfig getJwt() {
        return jwt;
    }

    /**
     * 获取游戏配置
     */
    public GameConfig getGame() {
        return game;
    }

    /**
     * 获取匹配配置
     */
    public MatchConfig getMatch() {
        Yaml yaml = new Yaml();
        InputStream inputStream = AppConfig.class.getClassLoader().getResourceAsStream("application.yml");
        Map<String, Object> config = yaml.load(inputStream);
        return new MatchConfig(config);
    }

    /**
     * 获取 WebSocket 配置
     */
    public WebSocketConfig getWebSocket() {
        return webSocket;
    }

    /**
     * 获取字符串配置值
     */
    public String getString(String key, String defaultValue) {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = AppConfig.class.getClassLoader().getResourceAsStream("application.yml");
            Map<String, Object> config = yaml.load(inputStream);

            String[] keys = key.split("\\.");
            Object current = config;

            for (String k : keys) {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(k);
                } else {
                    return defaultValue;
                }
            }

            return current != null ? current.toString() : defaultValue;
        } catch (Exception e) {
            logger.warn("Failed to read config key: {}", key, e);
            return defaultValue;
        }
    }

    /**
     * 获取整数配置值
     */
    public int getInt(String key, int defaultValue) {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = AppConfig.class.getClassLoader().getResourceAsStream("application.yml");
            Map<String, Object> config = yaml.load(inputStream);

            String[] keys = key.split("\\.");
            Object current = config;

            for (String k : keys) {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(k);
                } else {
                    return defaultValue;
                }
            }

            if (current instanceof Number) {
                return ((Number) current).intValue();
            }
            return defaultValue;
        } catch (Exception e) {
            logger.warn("Failed to read config key: {}", key, e);
            return defaultValue;
        }
    }

    // ==================== 内部配置类 ====================

    /**
     * JWT 配置
     */
    public static class JwtConfig {
        private final String secret;
        private final long expiration;
        private final String issuer;

        public JwtConfig(Map<String, Object> config) {
            Map<String, Object> jwtConfig = (Map<String, Object>) config.get("jwt");

            // 优先从环境变量读取 JWT 密钥
            String envSecret = System.getenv("JWT_SECRET");
            String configSecret = (String) jwtConfig.get("secret");

            if (envSecret != null && !envSecret.isEmpty()) {
                this.secret = envSecret;
                logger.info("JWT secret loaded from environment variable");
            } else if (configSecret != null && !configSecret.isEmpty()) {
                if (configSecret.contains("change-this")) {
                    logger.warn("⚠️  WARNING: Using default JWT secret! Set JWT_SECRET in production!");
                }
                this.secret = configSecret;
            } else {
                throw new IllegalStateException("JWT secret required via JWT_SECRET or config file");
            }

            this.expiration = ((Number) jwtConfig.get("expiration")).longValue();
            this.issuer = (String) jwtConfig.get("issuer");
        }

        public String getSecret() { return secret; }
        public long getExpiration() { return expiration; }
        public String getIssuer() { return issuer; }
    }

    /**
     * 游戏配置
     */
    public static class GameConfig {
        private final int boardSize;
        private final int winCount;
        private final int matchTimeout;
        private final long roomExpireTime;
        private final long reconnectWindow;

        public GameConfig(Map<String, Object> config) {
            Map<String, Object> gameConfig = (Map<String, Object>) config.get("game");
            this.boardSize = ((Number) gameConfig.get("board-size")).intValue();
            this.winCount = ((Number) gameConfig.get("win-count")).intValue();
            this.matchTimeout = ((Number) gameConfig.get("match-timeout")).intValue();
            this.roomExpireTime = ((Number) gameConfig.get("room-expire-time")).longValue() * 1000;
            this.reconnectWindow = ((Number) gameConfig.get("reconnect-window")).longValue() * 1000;
        }

        public int getBoardSize() { return boardSize; }
        public int getWinCount() { return winCount; }
        public int getMatchTimeout() { return matchTimeout; }
        public long getRoomExpireTime() { return roomExpireTime; }
        public long getReconnectWindow() { return reconnectWindow; }
    }

    /**
     * WebSocket 配置
     */
    public static class WebSocketConfig {
        private final String path;
        private final int maxFrameSize;
        private final int maxConnections;
        private final boolean enableCompression;
        private final int pingInterval;

        public WebSocketConfig(Map<String, Object> config) {
            Map<String, Object> wsConfig = (Map<String, Object>) config.getOrDefault("websocket", new java.util.HashMap<>());
            this.path = (String) wsConfig.getOrDefault("path", "/ws");
            this.maxFrameSize = ((Number) wsConfig.getOrDefault("max-frame-size", 65536)).intValue();
            this.maxConnections = ((Number) wsConfig.getOrDefault("max-connections", 10000)).intValue();
            this.enableCompression = (Boolean) wsConfig.getOrDefault("enable-compression", true);
            this.pingInterval = ((Number) wsConfig.getOrDefault("ping-interval", 30)).intValue();
        }

        public String getPath() { return path; }
        public int getMaxFrameSize() { return maxFrameSize; }
        public int getMaxConnections() { return maxConnections; }
        public boolean isEnableCompression() { return enableCompression; }
        public int getPingInterval() { return pingInterval; }
    }
}
