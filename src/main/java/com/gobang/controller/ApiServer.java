package com.gobang.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gobang.mapper.GameRecordMapper;
import com.gobang.service.ActivityLogService;
import com.gobang.service.GameFavoriteService;
import com.gobang.service.GameInvitationService;
import com.gobang.service.UserSettingsService;
import com.gobang.service.UserService;
import com.gobang.service.AuthService;
import com.gobang.service.GameService;
import com.gobang.service.FriendService;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RESTful API服务器
 * 提供HTTP接口访问游戏数据
 */
public class ApiServer {

    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);

    private final int port;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final UserSettingsService settingsService;
    private final ActivityLogService logService;
    private final GameFavoriteService favoriteService;
    private final GameInvitationService invitationService;
    private final AuthService authService;
    private final GameService gameService;
    private final FriendService friendService;
    private final com.gobang.core.room.RoomManager roomManager;
    private final com.gobang.core.social.FriendManager friendManager;
    private final org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory;

    // 简单的聊天消息存储（生产环境应使用数据库）
    private final List<Map<String, Object>> chatMessages = new java.util.concurrent.CopyOnWriteArrayList<>();

    // 用户在线状态跟踪（基于最后活跃时间）
    private final Map<Long, Long> userLastActive = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long ONLINE_TIMEOUT_MS = 60000; // 60秒无活动认为离线

    private HttpServer server;

    public ApiServer(int port,
                     UserService userService,
                     UserSettingsService settingsService,
                     ActivityLogService logService,
                     GameFavoriteService favoriteService,
                     GameInvitationService invitationService,
                     AuthService authService,
                     GameService gameService,
                     FriendService friendService,
                     com.gobang.core.room.RoomManager roomManager,
                     com.gobang.core.social.FriendManager friendManager,
                     org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory) {
        this.port = port;
        this.userService = userService;
        this.settingsService = settingsService;
        this.logService = logService;
        this.favoriteService = favoriteService;
        this.invitationService = invitationService;
        this.authService = authService;
        this.gameService = gameService;
        this.friendService = friendService;
        this.roomManager = roomManager;
        this.friendManager = friendManager;
        this.sqlSessionFactory = sqlSessionFactory;
        this.objectMapper = new ObjectMapper();
        // 注册JavaTimeModule以支持Java 8日期时间类型
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // 禁用将日期写为时间戳
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 启动API服务器
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // 注册API路由
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/auth/login", new AuthLoginHandler());
        server.createContext("/api/auth/register", new AuthRegisterHandler());
        server.createContext("/api/auth/logout", new AuthLogoutHandler());
        server.createContext("/api/settings", new SettingsHandler());
        server.createContext("/api/activity", new ActivityHandler());
        server.createContext("/api/favorites", new FavoritesHandler());
        server.createContext("/api/invitations", new InvitationsHandler());
        server.createContext("/api/user", new UserHandler());
        server.createContext("/api/game", new GameHandler());
        server.createContext("/api/friends", new FriendsHandler());
        server.createContext("/api/heartbeat", new HeartbeatHandler());
        server.createContext("/api/cleanup", new CleanupHandler());
        server.createContext("/api/rank", new RankHandler());
        server.createContext("/api/leaderboard", new RankHandler());  // 添加别名路由
        server.createContext("/api/rooms", new RoomsHandler());
        server.createContext("/api/stats", new StatsHandler());
        server.createContext("/api/records/recent", new RecentRecordsHandler());

        // 注册静态文件处理器（默认路径）
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        server.start();

        // 启动离线用户清理任务（每30秒检查一次）
        startOfflineUserCleanupTask();

        logger.info("API服务器已启动，监听端口: {}", port);
    }

    /**
     * 启动离线用户清理任务
     */
    private void startOfflineUserCleanupTask() {
        java.util.concurrent.ScheduledExecutorService scheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "offline-user-cleanup");
                t.setDaemon(true);
                return t;
            });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                java.util.List<Long> toRemove = new java.util.ArrayList<>();

                for (java.util.Map.Entry<Long, Long> entry : userLastActive.entrySet()) {
                    Long userId = entry.getKey();
                    Long lastActive = entry.getValue();

                    // 如果超过60秒没有活动，标记为离线
                    if (now - lastActive > ONLINE_TIMEOUT_MS) {
                        userService.updateUserStatus(userId, 0);
                        toRemove.add(userId);
                        logger.info("📴 用户 {} 超过{}秒无活动，设置为离线", userId, ONLINE_TIMEOUT_MS / 1000);
                    }
                }

                // 从内存中移除已离线的用户
                for (Long userId : toRemove) {
                    userLastActive.remove(userId);
                }
            } catch (Exception e) {
                logger.error("清理离线用户时出错", e);
            }
        }, 30, 30, java.util.concurrent.TimeUnit.SECONDS);

        logger.info("离线用户清理任务已启动 - 每30秒检查一次，超时时间: {}秒", ONLINE_TIMEOUT_MS / 1000);
    }

    /**
     * 停止API服务器
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("API服务器已停止");
        }
    }

    // ==================== 基础处理器 ====================

    /**
     * 发送JSON响应
     */
    private void sendJsonResponse(com.sun.net.httpserver.HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = objectMapper.writeValueAsString(data);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, jsonBytes.length);

        OutputStream os = exchange.getResponseBody();
        os.write(jsonBytes);
        os.close();
    }

    /**
     * 发送错误响应
     */
    private void sendError(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        sendJsonResponse(exchange, statusCode, error);
    }

    /**
     * 发送成功响应
     */
    private void sendSuccess(com.sun.net.httpserver.HttpExchange exchange, Object data) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        if (data != null) {
            response.put("data", data);
        }
        sendJsonResponse(exchange, 200, response);
    }

    /**
     * 从请求中读取JSON数据
     */
    private Map<String, Object> readJson(InputStream is) throws IOException {
        return objectMapper.readValue(is, Map.class);
    }

    /**
     * 从路径中提取ID
     */
    private Long extractId(String path) {
        try {
            String[] parts = path.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从token中提取用户ID（共享方法，供所有Handler使用）
     */
    protected Long extractUserIdFromToken(com.sun.net.httpserver.HttpExchange exchange, String query) {
        String token = null;

        // 首先尝试从URL参数获取token
        if (query != null && query.contains("token=")) {
            try {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("token=")) {
                        token = param.substring(6);
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to extract token from query", e);
            }
        }

        // 如果URL中没有token，尝试从Authorization header获取
        if (token == null || token.isEmpty()) {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }

        if (token == null || token.isEmpty()) {
            return null;
        }

        // 验证token并获取用户ID
        try {
            return authService.validateToken(token);
        } catch (Exception e) {
            logger.error("Failed to validate token", e);
            return null;
        }
    }

    // ==================== API处理器 ====================

    /**
     * 用户设置API处理器
     */
    class SettingsHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();
                String query = exchange.getRequestURI().getQuery();

                // 处理CORS预检请求
                if ("OPTIONS".equals(method)) {
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
                    exchange.sendResponseHeaders(200, -1);
                    exchange.getResponseBody().close();
                    return;
                }

                // GET /api/settings - 获取当前用户设置
                if ("GET".equals(method) && "/api/settings".equals(path)) {
                    handleGetSettings(exchange);
                    return;
                }

                // PUT /api/settings - 更新设置
                if ("PUT".equals(method) && "/api/settings".equals(path)) {
                    handleUpdateSettings(exchange);
                    return;
                }

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("Settings API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetSettings(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            // TODO: 从token获取userId
            Long userId = 1L; // 临时使用固定ID

            Map<String, Object> settings = settingsService.getSettingsAsMap(userId);
            sendSuccess(exchange, settings);
        }

        private void handleUpdateSettings(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            Long userId = 1L;

            @SuppressWarnings("unchecked")
            Map<String, Object> updates = readJson(exchange.getRequestBody());
            settingsService.updateUserSettings(userId, updates);

            sendSuccess(exchange, Map.of("message", "设置已更新"));
        }
    }

    /**
     * 活动日志API处理器
     */
    class ActivityHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                if ("OPTIONS".equals(method)) {
                    sendCorsResponse(exchange);
                    return;
                }

                // GET /api/activity/history - 获取活动历史
                if ("GET".equals(method) && path.startsWith("/api/activity/history")) {
                    handleGetHistory(exchange);
                    return;
                }

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("Activity API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetHistory(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            Long userId = 1L;
            int limit = 20;

            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("limit=")) {
                limit = Integer.parseInt(query.split("limit=")[1].split("&")[0]);
            }

            var activities = logService.getUserRecentActivities(userId, limit);
            sendSuccess(exchange, activities);
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 对局收藏API处理器
     */
    class FavoritesHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                if ("OPTIONS".equals(method)) {
                    sendCorsResponse(exchange);
                    return;
                }

                // GET /api/favorites - 获取收藏列表
                if ("GET".equals(method) && "/api/favorites".equals(path)) {
                    handleGetFavorites(exchange);
                    return;
                }

                // POST /api/favorites - 添加收藏
                if ("POST".equals(method) && "/api/favorites".equals(path)) {
                    handleAddFavorite(exchange);
                    return;
                }

                // DELETE /api/favorites/{id} - 取消收藏
                if ("DELETE".equals(method) && path.startsWith("/api/favorites/")) {
                    handleRemoveFavorite(exchange);
                    return;
                }

                // GET /api/favorites/check/{gameRecordId} - 检查是否收藏
                if ("GET".equals(method) && path.startsWith("/api/favorites/check/")) {
                    handleCheckFavorite(exchange);
                    return;
                }

                // PUT /api/favorites/{id} - 更新收藏备注
                if ("PUT".equals(method) && path.startsWith("/api/favorites/")) {
                    handleUpdateFavorite(exchange);
                    return;
                }

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("Favorites API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetFavorites(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            Long userId = 1L;
            var favorites = favoriteService.getUserFavorites(userId);
            sendSuccess(exchange, favorites);
        }

        private void handleAddFavorite(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            Long userId = 1L;

            @SuppressWarnings("unchecked")
            Map<String, Object> data = readJson(exchange.getRequestBody());

            Long gameRecordId = Long.valueOf(data.get("gameRecordId").toString());
            String note = (String) data.get("note");
            String tags = (String) data.get("tags");

            boolean success = favoriteService.addFavorite(userId, gameRecordId, note, tags);

            if (success) {
                sendSuccess(exchange, Map.of("message", "收藏成功"));
            } else {
                sendError(exchange, 400, "收藏失败（可能已收藏或对局不存在）");
            }
        }

        private void handleRemoveFavorite(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            Long userId = 1L;
            Long gameRecordId = extractId(exchange.getRequestURI().getPath());

            boolean success = favoriteService.removeFavorite(userId, gameRecordId);

            if (success) {
                sendSuccess(exchange, Map.of("message", "已取消收藏"));
            } else {
                sendError(exchange, 404, "收藏不存在");
            }
        }

        private void handleCheckFavorite(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            Long userId = 1L;
            String path = exchange.getRequestURI().getPath();
            Long gameRecordId = Long.parseLong(path.substring(path.lastIndexOf("/") + 1));

            boolean favorited = favoriteService.isFavorited(userId, gameRecordId);
            sendSuccess(exchange, Map.of("favorited", favorited));
        }

        private void handleUpdateFavorite(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            Long userId = 1L;
            String path = exchange.getRequestURI().getPath();
            Long gameRecordId = Long.parseLong(path.substring(path.lastIndexOf("/") + 1));

            @SuppressWarnings("unchecked")
            Map<String, Object> data = readJson(exchange.getRequestBody());

            String note = (String) data.get("note");
            String tags = (String) data.get("tags");

            boolean success = favoriteService.updateFavorite(userId, gameRecordId, note, tags, null);

            if (success) {
                sendSuccess(exchange, Map.of("message", "收藏已更新"));
            } else {
                sendError(exchange, 400, "更新收藏失败（收藏不存在）");
            }
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 游戏邀请API处理器
     */
    class InvitationsHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                if ("OPTIONS".equals(method)) {
                    sendCorsResponse(exchange);
                    return;
                }

                // GET /api/invitations/pending - 获取待处理邀请
                if ("GET".equals(method) && "/api/invitations/pending".equals(path)) {
                    handleGetPending(exchange);
                    return;
                }

                // GET /api/invitations/sent - 获取发送的邀请
                if ("GET".equals(method) && "/api/invitations/sent".equals(path)) {
                    handleGetSent(exchange);
                    return;
                }

                // POST /api/invitations - 发送邀请
                if ("POST".equals(method) && "/api/invitations".equals(path)) {
                    handleSendInvitation(exchange);
                    return;
                }

                // POST /api/invitations/{id}/accept - 接受邀请
                if ("POST".equals(method) && path.matches("/api/invitations/\\d+/accept")) {
                    handleAcceptInvitation(exchange);
                    return;
                }

                // POST /api/invitations/{id}/reject - 拒绝邀请
                if ("POST".equals(method) && path.matches("/api/invitations/\\d+/reject")) {
                    handleRejectInvitation(exchange);
                    return;
                }

                // DELETE /api/invitations/{id} - 取消邀请
                if ("DELETE".equals(method) && path.matches("/api/invitations/\\d+")) {
                    handleCancelInvitation(exchange);
                    return;
                }

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("Invitations API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetPending(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            Long userId = 1L;
            var invitations = invitationService.getPendingInvitations(userId);
            sendSuccess(exchange, invitations);
        }

        private void handleGetSent(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            Long userId = 1L;
            int limit = 20;

            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("limit=")) {
                limit = Integer.parseInt(query.split("limit=")[1].split("&")[0]);
            }

            var invitations = invitationService.getSentInvitations(userId, limit);
            sendSuccess(exchange, invitations);
        }

        private void handleSendInvitation(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            Long inviterId = 1L;

            @SuppressWarnings("unchecked")
            Map<String, Object> data = readJson(exchange.getRequestBody());

            Long inviteeId = Long.valueOf(data.get("inviteeId").toString());
            String invitationType = (String) data.get("invitationType");

            Long invitationId = invitationService.sendInvitation(inviterId, inviteeId, invitationType);

            if (invitationId != null) {
                sendSuccess(exchange, Map.of(
                    "message", "邀请已发送",
                    "invitationId", invitationId
                ));
            } else {
                sendError(exchange, 400, "发送邀请失败");
            }
        }

        private void handleAcceptInvitation(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            Long invitationId = Long.parseLong(path.split("/")[3]);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = readJson(exchange.getRequestBody());
            String roomId = (String) data.get("roomId");

            boolean success = invitationService.acceptInvitation(invitationId, roomId);

            if (success) {
                sendSuccess(exchange, Map.of("message", "已接受邀请"));
            } else {
                sendError(exchange, 400, "接受邀请失败");
            }
        }

        private void handleRejectInvitation(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            Long invitationId = Long.parseLong(path.split("/")[3]);

            boolean success = invitationService.rejectInvitation(invitationId);

            if (success) {
                sendSuccess(exchange, Map.of("message", "已拒绝邀请"));
            } else {
                sendError(exchange, 400, "拒绝邀请失败");
            }
        }

        private void handleCancelInvitation(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            Long invitationId = Long.parseLong(path.split("/")[3]);

            boolean success = invitationService.cancelInvitation(invitationId);

            if (success) {
                sendSuccess(exchange, Map.of("message", "已取消邀请"));
            } else {
                sendError(exchange, 400, "取消邀请失败");
            }
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 用户API处理器
     */
    class UserHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                if ("OPTIONS".equals(method)) {
                    sendCorsResponse(exchange);
                    return;
                }

                // GET /api/user/stats - 获取用户统计
                if ("GET".equals(method) && "/api/user/stats".equals(path)) {
                    handleGetStats(exchange);
                    return;
                }

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("User API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetStats(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            Long userId = 1L;
            var stats = userService.getUserStats(userId);
            sendSuccess(exchange, stats);
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    // ==================== 游戏API处理器 ====================

    /**
     * 游戏API处理器
     */
    class GameHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                // CORS预检请求
                if ("OPTIONS".equals(method)) {
                    sendCorsResponse(exchange);
                    return;
                }

                if ("GET".equals(method)) {
                    if ("/api/game/unfinished".equals(path)) {
                        handleGetUnfinishedGame(exchange);
                        return;
                    } else if ("/api/game/active".equals(path)) {
                        handleGetActiveGames(exchange);
                        return;
                    }
                }

                if ("POST".equals(method)) {
                    if ("/api/game/save-record".equals(path)) {
                        handleSaveGameRecord(exchange);
                        return;
                    }
                }

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("Game API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetUnfinishedGame(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            // 从查询参数获取用户ID
            Long userId = null;
            try {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("userId=")) {
                    String userIdStr = query.substring(query.indexOf("userId=") + 7);
                    // 如果有更多参数，只取userId的值
                    int ampIndex = userIdStr.indexOf('&');
                    if (ampIndex > 0) {
                        userIdStr = userIdStr.substring(0, ampIndex);
                    }
                    userId = Long.parseLong(userIdStr);
                }
            } catch (Exception e) {
                logger.error("解析userId失败", e);
            }

            if (userId == null) {
                sendError(exchange, 400, "Missing userId parameter");
                return;
            }

            var unfinishedGame = gameService.getUnfinishedGame(userId);
            if (unfinishedGame != null) {
                sendSuccess(exchange, unfinishedGame);
            } else {
                sendSuccess(exchange, Map.of("has_unfinished", false));
            }
        }

        private void handleGetActiveGames(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            var activeGames = gameService.getActiveGames();
            sendSuccess(exchange, activeGames);
        }

        private void handleSaveGameRecord(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = readJson(exchange.getRequestBody());

                // 获取参数（处理 userId 为 null 的情况）
                Long userId = null;
                if (data.get("userId") != null) {
                    userId = ((Number) data.get("userId")).longValue();
                }

                String gameMode = (String) data.get("gameMode"); // "pve" or "pvp_local"
                Integer winnerColor = (Integer) data.get("winnerColor"); // 1=黑胜, 2=白胜, 0=平局
                Integer moveCount = (Integer) data.get("moveCount");
                Integer duration = (Integer) data.get("duration");
                String boardState = (String) data.get("boardState");
                String moves = (String) data.get("moves");

                logger.info("📥 收到保存游戏记录请求 - userId: {}, gameMode: {}, winnerColor: {}",
                    userId, gameMode, winnerColor);

                // 如果没有登录，返回错误（本地PvP也需要登录才能保存统计）
                if (userId == null) {
                    logger.warn("⚠️ 用户未登录，无法保存游戏记录");
                    sendError(exchange, 401, "请先登录后再进行游戏，游戏记录需要登录后才能保存。");
                    return;
                }

                // 确保用户存在（如果不存在则创建）
                com.gobang.model.entity.User user = userService.getUserById(userId);
                if (user == null) {
                    // 使用MyBatis直接创建用户
                    try (org.apache.ibatis.session.SqlSession session = sqlSessionFactory.openSession(true)) {
                        com.gobang.mapper.UserMapper userMapper = session.getMapper(com.gobang.mapper.UserMapper.class);
                        com.gobang.model.entity.User newUser = new com.gobang.model.entity.User();
                        newUser.setId(userId);
                        newUser.setUsername("player_" + userId);
                        newUser.setNickname("玩家" + userId);
                        newUser.setPassword("temp");
                        newUser.setAvatar("/default-avatar.png");
                        newUser.setRating(1200);
                        newUser.setLevel(1);
                        newUser.setExp(0);
                        newUser.setStatus(0);
                        newUser.setCreatedAt(java.time.LocalDateTime.now());
                        try {
                            userMapper.insert(newUser);
                            logger.info("创建临时用户: {}", userId);
                        } catch (Exception e) {
                            // 用户可能已存在（用户名重复），重新获取
                            logger.warn("创建用户失败，可能已存在: {}", e.getMessage());
                            user = userService.getUserById(userId);
                            if (user == null) {
                                // 如果还是不存在，尝试用用户名获取
                                user = userService.getUserByUsername("player_" + userId);
                                if (user != null) {
                                    // 更新实际的用户ID
                                    userId = user.getId();
                                }
                            }
                        }
                    }
                }

                // 创建游戏记录
                com.gobang.model.entity.GameRecord record = new com.gobang.model.entity.GameRecord();
                record.setRoomId("client_" + System.currentTimeMillis());
                record.setBlackPlayerId(userId);
                // PvE和本地PvP都使用相同用户ID（因为AI不需要真实用户记录）
                record.setWhitePlayerId(userId);
                record.setWinnerId(userId); // 默认玩家获胜
                record.setWinColor(winnerColor == 0 ? null : winnerColor);
                record.setEndReason(winnerColor == 0 ? 2 : 0); // 0=正常胜负, 2=平局
                record.setMoveCount(moveCount);
                record.setDuration(duration);
                // 设置rating默认值（PvE和本地PvP不需要rating）
                record.setBlackRatingBefore(1200);
                record.setBlackRatingAfter(1200);
                record.setBlackRatingChange(0);
                record.setWhiteRatingBefore(1200);
                record.setWhiteRatingAfter(1200);
                record.setWhiteRatingChange(0);
                record.setBoardState(boardState);
                record.setMoves(moves);
                record.setGameMode(gameMode);
                record.setCreatedAt(java.time.LocalDateTime.now());

                // 保存到数据库
                boolean success = gameService.saveGameRecord(record);

                if (success) {
                    logger.info("✅ 游戏记录已保存 - userId: {}, gameMode: {}", userId, gameMode);
                    sendSuccess(exchange, Map.of("message", "游戏记录已保存"));
                } else {
                    logger.error("❌ 保存游戏记录失败 - userId: {}, gameMode: {}", userId, gameMode);
                    sendError(exchange, 500, "保存游戏记录失败");
                }
            } catch (Exception e) {
                logger.error("❌ Failed to save game record", e);
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 好友API处理器
     */
    class FriendsHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();
                String query = exchange.getRequestURI().getQuery();

                if ("OPTIONS".equals(method)) {
                    sendCorsResponse(exchange);
                    return;
                }

                // 获取userId从token
                Long userId = extractUserIdFromToken(exchange, query);
                if (userId == null) {
                    sendError(exchange, 401, "Unauthorized");
                    return;
                }

                // GET /api/friends/requests - 获取待处理的好友请求
                if ("GET".equals(method) && "/api/friends/requests".equals(path)) {
                    handleGetPendingRequests(exchange, userId);
                    return;
                }

                // GET /api/friends/requests/pending - 获取待处理的好友请求（备用路径）
                if ("GET".equals(method) && "/api/friends/requests/pending".equals(path)) {
                    handleGetPendingRequests(exchange, userId);
                    return;
                }

                // GET /api/friends/list - 获取好友列表
                if ("GET".equals(method) && "/api/friends/list".equals(path)) {
                    handleGetFriendsList(exchange, userId);
                    return;
                }

                // POST /api/friends/request - 发送好友请求
                if ("POST".equals(method) && "/api/friends/request".equals(path)) {
                    handleSendFriendRequest(exchange, userId);
                    return;
                }

                // POST /api/friends/accept - 接受好友请求
                if ("POST".equals(method) && "/api/friends/accept".equals(path)) {
                    handleAcceptFriendRequest(exchange, userId);
                    return;
                }

                // POST /api/friends/reject - 拒绝好友请求
                if ("POST".equals(method) && "/api/friends/reject".equals(path)) {
                    handleRejectFriendRequest(exchange, userId);
                    return;
                }

                // DELETE /api/friends/{id} - 删除好友
                if ("DELETE".equals(method) && path.matches("/api/friends/\\d+")) {
                    handleRemoveFriend(exchange, userId, path);
                    return;
                }

                // POST /api/friends/{id}/messages - 发送聊天消息
                if ("POST".equals(method) && path.matches("/api/friends/\\d+/messages")) {
                    handleSendMessage(exchange, userId, path);
                    return;
                }

                // GET /api/friends/{id}/messages - 获取聊天消息
                if ("GET".equals(method) && path.matches("/api/friends/\\d+/messages")) {
                    handleGetMessages(exchange, userId, path);
                    return;
                }

                // POST /api/heartbeat - 心跳API（维持在线状态）
                if ("POST".equals(method) && "/api/heartbeat".equals(path)) {
                    handleHeartbeat(exchange, userId);
                    return;
                }

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("Friends API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private Long getUserIdFromToken(com.sun.net.httpserver.HttpExchange exchange, String query) {
            String token = null;

            // 首先尝试从URL参数获取token
            if (query != null && query.contains("token=")) {
                try {
                    String[] params = query.split("&");
                    for (String param : params) {
                        if (param.startsWith("token=")) {
                            token = param.substring(6);
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to extract token from query", e);
                }
            }

            // 如果URL中没有token，尝试从Authorization header获取
            if (token == null || token.isEmpty()) {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token == null || token.isEmpty()) {
                logger.warn("No token found in request");
                return null;
            }

            // 使用authService验证token
            Long userId = authService.validateToken(token);
            if (userId == null) {
                logger.warn("Invalid token provided");
                return null;
            }

            return userId;
        }

        private void handleGetPendingRequests(com.sun.net.httpserver.HttpExchange exchange, Long userId) throws IOException {
            var requests = friendService.getPendingRequests(userId);
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (com.gobang.model.entity.Friend req : requests) {
                Map<String, Object> reqInfo = new java.util.HashMap<>();
                reqInfo.put("id", req.getId());
                reqInfo.put("user_id", req.getUserId());
                reqInfo.put("request_message", req.getRequestMessage());
                reqInfo.put("created_at", req.getCreatedAt() != null ? req.getCreatedAt().toString() : null);
                com.gobang.model.entity.User sender = userService.getUserById(req.getUserId());
                if (sender != null) {
                    reqInfo.put("username", sender.getUsername());
                    reqInfo.put("nickname", sender.getNickname());
                }
                result.add(reqInfo);
            }
            sendSuccess(exchange, result);
        }

        private void handleGetFriendsList(com.sun.net.httpserver.HttpExchange exchange, Long userId) throws IOException {
            List<com.gobang.model.entity.User> friends = friendService.getFriendList(userId);

            // 添加在线状态信息（基于最后活跃时间）
            List<Map<String, Object>> friendsWithStatus = new java.util.ArrayList<>();
            long now = System.currentTimeMillis();
            for (com.gobang.model.entity.User friend : friends) {
                Map<String, Object> friendData = new java.util.HashMap<>();
                friendData.put("id", friend.getId());
                friendData.put("username", friend.getUsername());
                friendData.put("nickname", friend.getNickname());
                friendData.put("rating", friend.getRating());
                friendData.put("level", friend.getLevel());
                friendData.put("avatar", friend.getAvatar());

                // 检查用户是否在线（60秒内有活动）
                Long lastActive = userLastActive.get(friend.getId());
                boolean isOnline = lastActive != null && (now - lastActive) < ONLINE_TIMEOUT_MS;
                friendData.put("online", isOnline);
                friendsWithStatus.add(friendData);
            }

            sendSuccess(exchange, Map.of("friends", friendsWithStatus));
        }

        private void handleSendFriendRequest(com.sun.net.httpserver.HttpExchange exchange, Long userId) throws IOException {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = readJson(exchange.getRequestBody());
            String targetIdStr = (String) data.get("target_id");
            String message = (String) data.get("message");

            logger.info("=== handleSendFriendRequest: userId={}, targetIdStr={}, message='{}' ===", userId, targetIdStr, message);

            if (targetIdStr == null || targetIdStr.isEmpty()) {
                logger.warn("target_id is empty");
                sendError(exchange, 400, "target_id is required");
                return;
            }

            // 解析target_id - 可以是用户名或ID
            Long targetId;
            try {
                targetId = Long.parseLong(targetIdStr);
                logger.info("Parsed targetId as number: {}", targetId);
            } catch (NumberFormatException e) {
                // 如果不是数字，尝试通过用户名查找
                logger.info("targetId is not a number, trying username lookup: {}", targetIdStr);
                com.gobang.model.entity.User targetUser = userService.getUserByUsername(targetIdStr);
                if (targetUser == null) {
                    logger.warn("User not found: {}", targetIdStr);
                    sendError(exchange, 404, "User not found");
                    return;
                }
                targetId = targetUser.getId();
                logger.info("Found user by username: {} -> {}", targetIdStr, targetId);
            }

            boolean success = friendService.sendRequest(userId, targetId, message != null ? message : "想加你好友");
            logger.info("friendService.sendRequest returned: {}", success);
            if (!success) {
                logger.warn("Failed to send friend request from {} to {}", userId, targetId);
                sendError(exchange, 400, "Failed to send friend request");
                return;
            }
            sendSuccess(exchange, Map.of("message", "好友请求已发送", "success", true));
        }

        private void handleAcceptFriendRequest(com.sun.net.httpserver.HttpExchange exchange, Long userId) throws IOException {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = readJson(exchange.getRequestBody());
            Long requestId = Long.valueOf(data.get("request_id").toString());

            boolean success = friendService.acceptRequest(requestId);
            if (!success) {
                sendError(exchange, 400, "Failed to accept friend request");
                return;
            }
            sendSuccess(exchange, Map.of("message", "已接受好友请求", "success", true));
        }

        private void handleRejectFriendRequest(com.sun.net.httpserver.HttpExchange exchange, Long userId) throws IOException {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = readJson(exchange.getRequestBody());
            Long requestId = Long.valueOf(data.get("request_id").toString());

            boolean success = friendService.rejectRequest(requestId);
            if (!success) {
                sendError(exchange, 400, "Failed to reject friend request");
                return;
            }
            sendSuccess(exchange, Map.of("message", "已拒绝好友请求", "success", true));
        }

        private void handleRemoveFriend(com.sun.net.httpserver.HttpExchange exchange, Long userId, String path) throws IOException {
            String[] parts = path.split("/");
            Long friendId = Long.parseLong(parts[parts.length - 1]);

            boolean success = friendService.removeFriend(userId, friendId);
            if (!success) {
                sendError(exchange, 400, "Failed to remove friend");
                return;
            }
            sendSuccess(exchange, Map.of("message", "已删除好友", "success", true));
        }

        private void handleSendMessage(com.sun.net.httpserver.HttpExchange exchange, Long userId, String path) throws IOException {
            // 从路径中提取friendId: /api/friends/{id}/messages
            String[] parts = path.split("/");
            Long friendId = Long.parseLong(parts[parts.length - 2]);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = readJson(exchange.getRequestBody());
            String content = (String) data.get("content");

            if (content == null || content.isEmpty()) {
                sendError(exchange, 400, "content is required");
                return;
            }

            // 检查是否是好友
            if (friendService.getFriendList(userId).stream().noneMatch(u -> u.getId().equals(friendId))) {
                sendError(exchange, 403, "Not friends with this user");
                return;
            }

            // 保存消息（使用更安全的ID生成方式）
            Map<String, Object> message = new java.util.HashMap<>();
            long messageId = System.currentTimeMillis() * 1000 + (long)(Math.random() * 1000);
            message.put("id", messageId);
            message.put("senderId", userId);
            message.put("receiverId", friendId);
            message.put("content", content);
            message.put("timestamp", System.currentTimeMillis());
            message.put("read", false);

            logger.info("=== SAVING MESSAGE === userId={} (sender) -> friendId={} (receiver), content='{}', messageId={}", userId, friendId, content, messageId);

            // 检查是否已有相同的消息（防重复）
            boolean isDuplicate = chatMessages.stream().anyMatch(m ->
                m.get("senderId").equals(userId) &&
                m.get("receiverId").equals(friendId) &&
                m.get("content").equals(content) &&
                (System.currentTimeMillis() - (Long) m.get("timestamp")) < 2000 // 2秒内的相同消息视为重复
            );

            if (isDuplicate) {
                logger.warn("=== DUPLICATE MESSAGE DETECTED === Ignoring duplicate message from {} to {}", userId, friendId);
                sendError(exchange, 400, "请勿频繁发送相同消息");
                return;
            }

            chatMessages.add(message);
            logger.info("=== TOTAL MESSAGES IN MEMORY === {}", chatMessages.size());

            // 清理旧消息（只保留最近100条）
            if (chatMessages.size() > 100) {
                chatMessages.subList(0, chatMessages.size() - 100).clear();
                logger.info("=== CLEARED OLD MESSAGES, REMAINING: {} ===", chatMessages.size());
            }

            // 尝试实时发送给在线用户
            if (friendManager.isUserOnline(friendId)) {
                io.netty.channel.Channel channel = friendManager.getChannel(friendId);
                if (channel != null && channel.isActive()) {
                    // 通过WebSocket发送（如果可用）
                    logger.info("Sending message to online user {} via WebSocket", friendId);
                }
            }

            sendSuccess(exchange, Map.of("message", "消息已发送", "success", true));
        }

        private void handleGetMessages(com.sun.net.httpserver.HttpExchange exchange, Long userId, String path) throws IOException {
            // 从路径中提取friendId: /api/friends/{id}/messages
            String[] parts = path.split("/");
            Long friendId = Long.parseLong(parts[parts.length - 2]);

            logger.info("=== GETTING MESSAGES === userId={} (requester) -> friendId={} (chat partner)", userId, friendId);

            // 获取这两个用户之间的消息
            List<Map<String, Object>> messages = chatMessages.stream()
                .filter(m -> {
                    Long sender = (Long) m.get("senderId");
                    Long receiver = (Long) m.get("receiverId");
                    boolean match = (sender.equals(userId) && receiver.equals(friendId)) ||
                                   (sender.equals(friendId) && receiver.equals(userId));
                    if (match) {
                        logger.info("  Matched message: senderId={} -> receiverId={}, content='{}'",
                            sender, receiver, m.get("content"));
                    }
                    return match;
                })
                .sorted((a, b) -> Long.compare((Long) a.get("timestamp"), (Long) b.get("timestamp")))
                .limit(50) // 限制最近50条消息
                .collect(java.util.stream.Collectors.toList());

            logger.info("=== RETURNING {} MESSAGES ===", messages.size());

            // 标记消息为已读
            messages.forEach(m -> {
                if (m.get("receiverId").equals(userId)) {
                    m.put("read", true);
                }
            });

            // 获取发送者信息
            for (Map<String, Object> msg : messages) {
                Long senderId = (Long) msg.get("senderId");
                com.gobang.model.entity.User sender = userService.getUserById(senderId);
                if (sender != null) {
                    msg.put("senderName", sender.getNickname() != null ? sender.getNickname() : sender.getUsername());
                }
            }

            sendSuccess(exchange, Map.of("messages", messages));
        }

        private void handleHeartbeat(com.sun.net.httpserver.HttpExchange exchange, Long userId) throws IOException {
            // 更新用户最后活跃时间
            userLastActive.put(userId, System.currentTimeMillis());
            sendSuccess(exchange, Map.of("status", "alive", "timestamp", System.currentTimeMillis()));
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 房间API处理器
     */
    class RoomsHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                if ("OPTIONS".equals(method)) {
                    sendCorsResponse(exchange);
                    return;
                }

                if ("GET".equals(method) && "/api/rooms/playing".equals(path)) {
                    handleGetPlayingRooms(exchange);
                    return;
                }

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("Rooms API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetPlayingRooms(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            var rooms = roomManager.getPlayingRooms();
            List<Map<String, Object>> roomList = new java.util.ArrayList<>();

            for (com.gobang.core.room.GameRoom room : rooms) {
                Map<String, Object> roomInfo = new java.util.HashMap<>();
                roomInfo.put("room_id", room.getRoomId());
                roomInfo.put("game_state", room.getGameState().name());
                roomInfo.put("move_count", room.getMoves().size());
                roomInfo.put("observer_count", room.getObserverCount());

                // 获取玩家信息
                Long blackPlayerId = room.getBlackPlayerId();
                Long whitePlayerId = room.getWhitePlayerId();

                if (blackPlayerId != null) {
                    com.gobang.model.entity.User blackPlayer = userService.getUserById(blackPlayerId);
                    if (blackPlayer != null) {
                        roomInfo.put("black_player", Map.of(
                            "id", blackPlayer.getId(),
                            "nickname", blackPlayer.getNickname(),
                            "rating", blackPlayer.getRating()
                        ));
                    }
                }

                if (whitePlayerId != null) {
                    com.gobang.model.entity.User whitePlayer = userService.getUserById(whitePlayerId);
                    if (whitePlayer != null) {
                        roomInfo.put("white_player", Map.of(
                            "id", whitePlayer.getId(),
                            "nickname", whitePlayer.getNickname(),
                            "rating", whitePlayer.getRating()
                        ));
                    }
                }

                roomList.add(roomInfo);
            }

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("count", roomList.size());
            result.put("rooms", roomList);
            sendSuccess(exchange, result);
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 静态文件处理器
     */
    class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // 根路径重定向到 index.html
            if ("/".equals(path)) {
                path = "/index.html";
            }

            // 尝试从 classpath 加载静态文件
            InputStream is = getClass().getResourceAsStream("/static" + path);

            if (is == null) {
                // 文件不存在，返回 404
                String response = "<h1>404 Not Found</h1>";
                exchange.sendResponseHeaders(404, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            // 确定内容类型
            String contentType = getContentType(path);
            exchange.getResponseHeaders().set("Content-Type", contentType);

            // 读取文件内容
            byte[] bytes = is.readAllBytes();
            is.close();

            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        }

        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=UTF-8";
            if (path.endsWith(".css")) return "text/css; charset=UTF-8";
            if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (path.endsWith(".json")) return "application/json; charset=UTF-8";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".gif")) return "image/gif";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }

    /**
     * 清理API处理器
     */
    /**
     * 心跳API处理器
     */
    class HeartbeatHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();
                String query = exchange.getRequestURI().getQuery();

                if ("OPTIONS".equals(method)) {
                    sendCorsResponse(exchange);
                    return;
                }

                // 只处理POST请求
                if ("POST".equals(method) && "/api/heartbeat".equals(path)) {
                    // 首先尝试从URL参数或header获取token
                    Long userId = extractUserIdFromToken(exchange, query);

                    // 如果没有获取到，尝试从请求体中获取token
                    if (userId == null) {
                        try {
                            InputStream is = exchange.getRequestBody();
                            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            if (body != null && !body.isEmpty()) {
                                // 解析JSON获取token
                                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                @SuppressWarnings("unchecked")
                                java.util.Map<String, String> jsonBody = mapper.readValue(body, java.util.Map.class);
                                String token = jsonBody.get("token");
                                if (token != null && !token.isEmpty()) {
                                    userId = authService.validateToken(token);
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to read token from request body", e);
                        }
                    }

                    if (userId == null) {
                        sendError(exchange, 401, "Unauthorized");
                        return;
                    }

                    // 更新用户最后活跃时间和在线状态
                    userLastActive.put(userId, System.currentTimeMillis());
                    userService.updateUserStatus(userId, 1); // 设置为在线状态
                    logger.info("💓 Heartbeat from user: {}", userId);
                    sendSuccess(exchange, Map.of("status", "alive", "timestamp", System.currentTimeMillis()));
                    return;
                }

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("Heartbeat API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private Long getUserIdFromTokenForHeartbeat(com.sun.net.httpserver.HttpExchange exchange, String query) {
            String token = null;

            // 首先尝试从URL参数获取token
            if (query != null && query.contains("token=")) {
                try {
                    String[] params = query.split("&");
                    for (String param : params) {
                        if (param.startsWith("token=")) {
                            token = param.substring(6);
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to extract token from query", e);
                }
            }

            // 如果URL中没有token，尝试从Authorization header获取
            if (token == null || token.isEmpty()) {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token == null || token.isEmpty()) {
                logger.warn("No token found in request");
                return null;
            }

            // 使用authService验证token
            Long userId = authService.validateToken(token);
            if (userId == null) {
                logger.warn("Invalid token provided");
                return null;
            }

            return userId;
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    class CleanupHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                if ("OPTIONS".equals(method)) {
                    sendCorsResponse(exchange);
                    return;
                }

                if ("GET".equals(method) && "/api/cleanup/status".equals(path)) {
                    handleGetStatus(exchange);
                    return;
                }

                if ("POST".equals(method) && "/api/cleanup/all".equals(path)) {
                    handleCleanupAll(exchange);
                    return;
                }

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("Cleanup API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetStatus(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            var result = Map.of(
                "success", true,
                "casualQueue", 0,
                "rankedQueue", 0,
                "playersInQueue", 0,
                "activeRooms", 0
            );
            sendSuccess(exchange, result);
        }

        private void handleCleanupAll(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                // 获取所有用户并设置为离线
                List<com.gobang.model.entity.User> users = userService.getLeaderboard(1000);
                int count = 0;
                for (com.gobang.model.entity.User user : users) {
                    userService.updateUserStatus(user.getId(), 0);
                    count++;
                }
                logger.info("🔄 已将 {} 个用户设置为离线", count);

                var result = Map.of(
                    "success", true,
                    "message", "已将 " + count + " 个用户设置为离线",
                    "count", count
                );
                sendSuccess(exchange, result);
            } catch (Exception e) {
                logger.error("重置用户状态失败", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 健康检查处理器
     */
    class HealthHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    sendCorsResponse(exchange);
                    return;
                }

                var result = Map.of(
                    "status", "ok",
                    "server", "gobang-server",
                    "websocket", "ws://localhost:8083/ws",
                    "api", "http://localhost:9090/api"
                );
                sendSuccess(exchange, result);
            } catch (Exception e) {
                logger.error("Health check error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 统计数据处理器
     */
    class StatsHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    sendCorsResponse(exchange);
                    return;
                }

                if ("GET".equals(exchange.getRequestMethod())) {
                    // 获取在线人数
                    int onlineCount = userService.getOnlineUserCount();

                    // 获取正在进行的游戏数
                    int playingGames = gameService.getActiveGames().size();

                    // 获取当前登录用户的ID（从token中）
                    String query = exchange.getRequestURI().getQuery();
                    Long currentUserId = extractUserIdFromToken(exchange, query);

                    if (currentUserId == null) {
                        // 未登录，返回0的统计（不影响任何用户）
                        logger.warn("未登录用户访问统计API，返回空数据");
                        Map<String, Object> emptyStats = new HashMap<>();
                        emptyStats.put("pve", Map.of("today_matches", 0, "win_rate", "0.0"));
                        emptyStats.put("pvp_online", Map.of("today_matches", 0, "win_rate", "0.0", "online_players", onlineCount));
                        emptyStats.put("pvp_local", Map.of("today_matches", 0, "win_rate", "0.0"));
                        emptyStats.put("online_players", onlineCount);
                        emptyStats.put("playing_games", playingGames);
                        emptyStats.put("today_matches", 0);
                        sendSuccess(exchange, emptyStats);
                        return;
                    }

                    logger.info("Stats API - 用户ID: {}", currentUserId);

                    // 获取当前用户今日各模式对战数（按用户统计）
                    int pveTodayMatches = gameService.getTodayMatchesCountByUserAndMode(currentUserId, "pve");
                    int pvpOnlineTodayMatches = gameService.getTodayMatchesCountByUserAndMode(currentUserId, "pvp_online");
                    int pvpLocalTodayMatches = gameService.getTodayMatchesCountByUserAndMode(currentUserId, "pvp_local");

                    // 获取当前用户今日各模式胜率
                    double pveWinRate = gameService.getTodayWinRateByMode("pve", currentUserId);
                    double pvpOnlineWinRate = gameService.getTodayWinRateByMode("pvp_online", currentUserId);
                    double pvpLocalWinRate = gameService.getTodayWinRateByMode("pvp_local", currentUserId);

                    logger.info("Win rates for user {}: pve={}, pvp_online={}, pvp_local={}",
                        currentUserId, pveWinRate, pvpOnlineWinRate, pvpLocalWinRate);

                    // 获取匹配队列等待人数
                    int waitingCount = 0;
                    int casualWaiting = 0;
                    int rankedWaiting = 0;
                    try {
                        if (gameService.getMatchQueueService() != null) {
                            var stats = gameService.getMatchQueueService().getQueueStats();
                            casualWaiting = stats.getCasualCount();
                            rankedWaiting = stats.getRankedCount();
                            waitingCount = casualWaiting + rankedWaiting;
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to get queue stats: {}", e.getMessage());
                    }

                    // 获取房间总数
                    int totalRooms = 0;
                    try {
                        totalRooms = roomManager.getAllRooms().size();
                    } catch (Exception e) {
                        logger.debug("Failed to get room count: {}", e.getMessage());
                    }

                    Map<String, Object> result = new HashMap<>();

                    // 人机对战统计
                    Map<String, Object> pveStats = new HashMap<>();
                    pveStats.put("today_matches", pveTodayMatches);
                    pveStats.put("win_rate", String.format("%.1f", pveWinRate));
                    result.put("pve", pveStats);

                    // 在线对战统计
                    Map<String, Object> pvpOnlineStats = new HashMap<>();
                    pvpOnlineStats.put("today_matches", pvpOnlineTodayMatches);
                    pvpOnlineStats.put("win_rate", String.format("%.1f", pvpOnlineWinRate));
                    pvpOnlineStats.put("online_players", onlineCount);
                    result.put("pvp_online", pvpOnlineStats);

                    // 本地对战统计
                    Map<String, Object> pvpLocalStats = new HashMap<>();
                    pvpLocalStats.put("today_matches", pvpLocalTodayMatches);
                    pvpLocalStats.put("win_rate", String.format("%.1f", pvpLocalWinRate));
                    result.put("pvp_local", pvpLocalStats);

                    // 通用统计（兼容旧版前端）
                    result.put("online_players", onlineCount);
                    result.put("playing_games", playingGames);
                    result.put("today_matches", pveTodayMatches + pvpOnlineTodayMatches + pvpLocalTodayMatches);
                    result.put("waiting_players", waitingCount);
                    result.put("casual_waiting", casualWaiting);
                    result.put("ranked_waiting", rankedWaiting);
                    result.put("total_rooms", totalRooms);
                    result.put("timestamp", System.currentTimeMillis());

                    sendSuccess(exchange, result);
                    return;
                }

                sendError(exchange, 405, "Method not allowed");
            } catch (Exception e) {
                logger.error("Stats error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 认证登录处理器
     */
    class AuthLoginHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    sendCorsResponse(exchange);
                    return;
                }

                if ("POST".equals(exchange.getRequestMethod())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = readJson(exchange.getRequestBody());
                    String username = (String) data.get("username");
                    String password = (String) data.get("password");

                    // 调用认证服务
                    var user = authService.login(username, password);
                    if (user != null) {
                        // 生成 JWT token
                        String token = authService.generateToken(user.getId());
                        Map<String, Object> result = new HashMap<>();
                        result.put("token", token);
                        result.put("user", Map.of(
                            "id", user.getId(),
                            "username", user.getUsername(),
                            "nickname", user.getNickname(),
                            "rating", user.getRating()
                        ));
                        sendSuccess(exchange, result);
                    } else {
                        sendError(exchange, 401, "用户名或密码错误");
                    }
                    return;
                }

                sendError(exchange, 405, "Method not allowed");
            } catch (Exception e) {
                logger.error("Auth login error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 认证注册处理器
     */
    class AuthRegisterHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    sendCorsResponse(exchange);
                    return;
                }

                if ("POST".equals(exchange.getRequestMethod())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = readJson(exchange.getRequestBody());
                    String username = (String) data.get("username");
                    String password = (String) data.get("password");
                    String nickname = data.get("nickname") != null ? (String) data.get("nickname") : username;

                    // 验证密码强度
                    String passwordError = com.gobang.util.PasswordValidator.validate(password);
                    if (passwordError != null) {
                        sendError(exchange, 400, passwordError);
                        return;
                    }

                    // 调用认证服务
                    var user = authService.register(username, password, nickname);
                    if (user != null) {
                        // 生成 JWT token
                        String token = authService.generateToken(user.getId());
                        Map<String, Object> result = new HashMap<>();
                        result.put("token", token);
                        result.put("user", Map.of(
                            "id", user.getId(),
                            "username", user.getUsername(),
                            "nickname", user.getNickname(),
                            "rating", user.getRating()
                        ));
                        sendSuccess(exchange, result);
                    } else {
                        sendError(exchange, 400, "注册失败（用户名可能已存在）");
                    }
                    return;
                }

                sendError(exchange, 405, "Method not allowed");
            } catch (Exception e) {
                logger.error("Auth register error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 认证登出处理器
     */
    class AuthLogoutHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    sendCorsResponse(exchange);
                    return;
                }

                if ("POST".equals(exchange.getRequestMethod())) {
                    // 获取用户ID并设置离线状态
                    String query = exchange.getRequestURI().getQuery();
                    Long userId = extractUserIdFromToken(exchange, query);

                    if (userId != null) {
                        authService.logout(userId);
                        logger.info("用户登出: {}", userId);
                    }

                    sendSuccess(exchange, Map.of("message", "登出成功"));
                    return;
                }

                sendError(exchange, 405, "Method not allowed");
            } catch (Exception e) {
                logger.error("Auth logout error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 排行榜处理器
     */
    class RankHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    sendCorsResponse(exchange);
                    return;
                }

                if ("GET".equals(exchange.getRequestMethod())) {
                    String query = exchange.getRequestURI().getQuery();
                    int limit = 100;
                    if (query != null && query.contains("limit=")) {
                        limit = Integer.parseInt(query.split("limit=")[1].split("&")[0]);
                    }

                    var leaderboard = userService.getLeaderboard(limit);
                    sendSuccess(exchange, leaderboard);
                    return;
                }

                sendError(exchange, 405, "Method not allowed");
            } catch (Exception e) {
                logger.error("Rank API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }

    /**
     * 最近游戏记录处理器
     */
    class RecentRecordsHandler implements HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            try {
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    sendCorsResponse(exchange);
                    return;
                }

                if ("GET".equals(exchange.getRequestMethod())) {
                    // 获取最近的游戏记录
                    var recentGames = gameService.getRecentGameRecords(10);
                    sendSuccess(exchange, recentGames);
                    return;
                }

                sendError(exchange, 405, "Method not allowed");
            } catch (Exception e) {
                logger.error("Recent records API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        }
    }
}
