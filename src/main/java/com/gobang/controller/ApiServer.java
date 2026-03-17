package com.gobang.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
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

    private HttpServer server;

    public ApiServer(int port,
                     UserService userService,
                     UserSettingsService settingsService,
                     ActivityLogService logService,
                     GameFavoriteService favoriteService,
                     GameInvitationService invitationService,
                     AuthService authService,
                     GameService gameService,
                     FriendService friendService) {
        this.port = port;
        this.userService = userService;
        this.settingsService = settingsService;
        this.logService = logService;
        this.favoriteService = favoriteService;
        this.invitationService = invitationService;
        this.authService = authService;
        this.gameService = gameService;
        this.friendService = friendService;
        this.objectMapper = new ObjectMapper();
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
        server.createContext("/api/cleanup", new CleanupHandler());
        server.createContext("/api/rank", new RankHandler());

        // 注册静态文件处理器（默认路径）
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        server.start();

        logger.info("API服务器已启动，监听端口: {}", port);
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

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("Game API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetUnfinishedGame(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            // 从token获取用户ID（简化处理，实际应该从JWT解析）
            Long userId = 1L;
            try {
                String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    // 从token解析userId（简化实现）
                    // 实际应该使用JwtUtil
                }
            } catch (Exception e) {
                // 忽略错误，使用默认userId
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

        private void sendCorsResponse(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
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

                if ("OPTIONS".equals(method)) {
                    sendCorsResponse(exchange);
                    return;
                }

                if ("GET".equals(method) && path.startsWith("/api/friends/requests")) {
                    handleGetPendingRequests(exchange);
                    return;
                }

                sendError(exchange, 404, "Not found");

            } catch (Exception e) {
                logger.error("Friends API error", e);
                sendError(exchange, 500, "Internal server error");
            }
        }

        private void handleGetPendingRequests(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            // TODO: 从token获取userId
            Long userId = 1L;

            var requests = friendService.getPendingRequests(userId);
            sendSuccess(exchange, Map.of("requests", requests));
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
            var result = Map.of(
                "success", true,
                "message", "清理成功"
            );
            sendSuccess(exchange, result);
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
}
