package com.gobang.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.service.ActivityLogService;
import com.gobang.service.GameFavoriteService;
import com.gobang.service.GameInvitationService;
import com.gobang.service.UserSettingsService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Netty HTTP API处理器
 * 处理RESTful API请求
 */
public class ApiHttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(ApiHttpRequestHandler.class);

    private final ObjectMapper objectMapper;
    private final UserSettingsService settingsService;
    private final ActivityLogService logService;
    private final GameFavoriteService favoriteService;
    private final GameInvitationService invitationService;
    private final com.gobang.service.FriendService friendService;
    private final com.gobang.service.UserService userService;
    private final com.gobang.service.AuthService authService;
    private final com.gobang.util.JwtUtil jwtUtil;

    public ApiHttpRequestHandler(UserSettingsService settingsService,
                                  ActivityLogService logService,
                                  GameFavoriteService favoriteService,
                                  GameInvitationService invitationService,
                                  com.gobang.service.FriendService friendService,
                                  com.gobang.service.UserService userService,
                                  com.gobang.service.AuthService authService,
                                  com.gobang.util.JwtUtil jwtUtil) {
        this.objectMapper = new ObjectMapper();
        this.settingsService = settingsService;
        this.logService = logService;
        this.favoriteService = favoriteService;
        this.invitationService = invitationService;
        this.friendService = friendService;
        this.userService = userService;
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        try {
            String uri = request.uri();
            HttpMethod method = request.method();

            // 只处理 /api 开头的请求
            if (!uri.startsWith("/api")) {
                ctx.fireChannelRead(request.retain());
                return;
            }

            logger.info("API请求: {} {}", method, uri);

            // 处理CORS预检请求
            if (method.equals(HttpMethod.OPTIONS)) {
                sendCorsResponse(ctx);
                return;
            }

            // 路由分发
            Object result = null;
            int statusCode = 200;
            String errorMessage = null;

            try {
                if (uri.startsWith("/api/settings")) {
                    result = handleSettings(uri, method, request);
                } else if (uri.startsWith("/api/activity")) {
                    result = handleActivity(uri, method, request);
                } else if (uri.startsWith("/api/favorites")) {
                    result = handleFavorites(uri, method, request);
                } else if (uri.startsWith("/api/invitations")) {
                    result = handleInvitations(uri, method, request);
                } else if (uri.startsWith("/api/friends")) {
                    result = handleFriends(uri, method, request);
                } else {
                    statusCode = 404;
                    errorMessage = "API endpoint not found";
                }
            } catch (ApiException e) {
                statusCode = e.getStatusCode();
                errorMessage = e.getMessage();
            } catch (Exception e) {
                logger.error("API处理错误", e);
                statusCode = 500;
                errorMessage = "Internal server error";
            }

            sendJsonResponse(ctx, statusCode, result, errorMessage);

        } finally {
            request.release();
        }
    }

    // ==================== 路由处理 ====================

    private Object handleSettings(String uri, HttpMethod method, FullHttpRequest request) {
        Long userId = getUserIdFromToken(request);

        if (method.equals(HttpMethod.GET) && "/api/settings".equals(uri)) {
            return settingsService.getSettingsAsMap(userId);
        }

        if (method.equals(HttpMethod.PUT) && "/api/settings".equals(uri)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = parseJsonBody(request);
            settingsService.updateUserSettings(userId, body);
            return Map.of("message", "设置已更新");
        }

        throw new ApiException(404, "Not found");
    }

    private Object handleActivity(String uri, HttpMethod method, FullHttpRequest request) {
        Long userId = getUserIdFromToken(request);

        if (method.equals(HttpMethod.GET) && uri.startsWith("/api/activity/history")) {
            int limit = getQueryParam(uri, "limit", 20);
            return logService.getUserRecentActivities(userId, limit);
        }

        throw new ApiException(404, "Not found");
    }

    private Object handleFavorites(String uri, HttpMethod method, FullHttpRequest request) {
        Long userId = getUserIdFromToken(request);

        // GET /api/favorites - 获取收藏列表
        if (method.equals(HttpMethod.GET) && "/api/favorites".equals(uri)) {
            return favoriteService.getUserFavorites(userId);
        }

        // POST /api/favorites - 添加收藏
        if (method.equals(HttpMethod.POST) && "/api/favorites".equals(uri)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = parseJsonBody(request);
            Long gameRecordId = Long.valueOf(body.get("gameRecordId").toString());
            String note = (String) body.get("note");
            String tags = (String) body.get("tags");

            boolean success = favoriteService.addFavorite(userId, gameRecordId, note, tags);
            if (!success) {
                throw new ApiException(400, "收藏失败");
            }
            return Map.of("message", "收藏成功");
        }

        // DELETE /api/favorites/{id} - 取消收藏
        if (method.equals(HttpMethod.DELETE) && uri.matches("/api/favorites/\\d+")) {
            Long gameRecordId = extractId(uri);
            boolean success = favoriteService.removeFavorite(userId, gameRecordId);
            if (!success) {
                throw new ApiException(404, "收藏不存在");
            }
            return Map.of("message", "已取消收藏");
        }

        // GET /api/favorites/check/{id} - 检查是否收藏
        if (method.equals(HttpMethod.GET) && uri.matches("/api/favorites/check/\\d+")) {
            Long gameRecordId = extractId(uri);
            boolean favorited = favoriteService.isFavorited(userId, gameRecordId);
            return Map.of("favorited", favorited);
        }

        throw new ApiException(404, "Not found");
    }

    private Object handleInvitations(String uri, HttpMethod method, FullHttpRequest request) {
        Long userId = getUserIdFromToken(request);

        // GET /api/invitations/pending - 获取待处理邀请
        if (method.equals(HttpMethod.GET) && "/api/invitations/pending".equals(uri)) {
            return invitationService.getPendingInvitations(userId);
        }

        // GET /api/invitations/sent - 获取发送的邀请
        if (method.equals(HttpMethod.GET) && "/api/invitations/sent".equals(uri)) {
            int limit = getQueryParam(uri, "limit", 20);
            return invitationService.getSentInvitations(userId, limit);
        }

        // POST /api/invitations - 发送邀请
        if (method.equals(HttpMethod.POST) && "/api/invitations".equals(uri)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = parseJsonBody(request);
            Long inviteeId = Long.valueOf(body.get("inviteeId").toString());
            String invitationType = (String) body.get("invitationType");

            Long invitationId = invitationService.sendInvitation(userId, inviteeId, invitationType);
            if (invitationId == null) {
                throw new ApiException(400, "发送邀请失败");
            }
            return Map.of("message", "邀请已发送", "invitationId", invitationId);
        }

        // POST /api/invitations/{id}/accept - 接受邀请
        if (method.equals(HttpMethod.POST) && uri.matches("/api/invitations/\\d+/accept")) {
            Long invitationId = extractId(uri);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = parseJsonBody(request);
            String roomId = (String) body.get("roomId");

            boolean success = invitationService.acceptInvitation(invitationId, roomId);
            if (!success) {
                throw new ApiException(400, "接受邀请失败");
            }
            return Map.of("message", "已接受邀请");
        }

        // POST /api/invitations/{id}/reject - 拒绝邀请
        if (method.equals(HttpMethod.POST) && uri.matches("/api/invitations/\\d+/reject")) {
            Long invitationId = extractId(uri);
            boolean success = invitationService.rejectInvitation(invitationId);
            if (!success) {
                throw new ApiException(400, "拒绝邀请失败");
            }
            return Map.of("message", "已拒绝邀请");
        }

        // DELETE /api/invitations/{id} - 取消邀请
        if (method.equals(HttpMethod.DELETE) && uri.matches("/api/invitations/\\d+")) {
            Long invitationId = extractId(uri);
            boolean success = invitationService.cancelInvitation(invitationId);
            if (!success) {
                throw new ApiException(400, "取消邀请失败");
            }
            return Map.of("message", "已取消邀请");
        }

        throw new ApiException(404, "Not found");
    }

    private Object handleFriends(String uri, HttpMethod method, FullHttpRequest request) {
        Long userId = getUserIdFromToken(request);

        // GET /api/friends/requests - 获取待处理的好友请求
        if (method.equals(HttpMethod.GET) && "/api/friends/requests".equals(uri)) {
            List<com.gobang.model.entity.Friend> requests = friendService.getPendingRequests(userId);
            // 转换为包含发送者信息的列表
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (com.gobang.model.entity.Friend req : requests) {
                Map<String, Object> reqInfo = new java.util.HashMap<>();
                reqInfo.put("id", req.getId());
                reqInfo.put("user_id", req.getUserId());
                reqInfo.put("request_message", req.getRequestMessage());
                reqInfo.put("created_at", req.getCreatedAt() != null ? req.getCreatedAt().toString() : null);

                // 获取发送者信息
                com.gobang.model.entity.User sender = userService.getUserById(req.getUserId());
                if (sender != null) {
                    reqInfo.put("username", sender.getUsername());
                    reqInfo.put("nickname", sender.getNickname());
                }
                result.add(reqInfo);
            }
            return result;
        }

        // GET /api/friends/requests/pending - 获取待处理的好友请求（备用路径）
        if (method.equals(HttpMethod.GET) && "/api/friends/requests/pending".equals(uri)) {
            List<com.gobang.model.entity.Friend> requests = friendService.getPendingRequests(userId);
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
            return result;
        }

        // GET /api/friends/list - 获取好友列表
        if (method.equals(HttpMethod.GET) && "/api/friends/list".equals(uri)) {
            List<com.gobang.model.entity.User> friends = friendService.getFriendList(userId);
            return Map.of("friends", friends);
        }

        // POST /api/friends/request - 发送好友请求
        if (method.equals(HttpMethod.POST) && "/api/friends/request".equals(uri)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = parseJsonBody(request);
            String targetIdStr = (String) body.get("target_id");
            String message = (String) body.get("message");

            if (targetIdStr == null || targetIdStr.isEmpty()) {
                throw new ApiException(400, "target_id is required");
            }

            // 解析target_id - 可以是用户名或ID
            Long targetId;
            try {
                targetId = Long.parseLong(targetIdStr);
            } catch (NumberFormatException e) {
                // 如果不是数字，尝试通过用户名查找
                com.gobang.model.entity.User targetUser = userService.getUserByUsername(targetIdStr);
                if (targetUser == null) {
                    throw new ApiException(404, "User not found");
                }
                targetId = targetUser.getId();
            }

            boolean success = friendService.sendRequest(userId, targetId, message != null ? message : "想加你好友");
            if (!success) {
                throw new ApiException(400, "Failed to send friend request");
            }
            return Map.of("message", "好友请求已发送", "success", true);
        }

        // POST /api/friends/accept - 接受好友请求
        if (method.equals(HttpMethod.POST) && "/api/friends/accept".equals(uri)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = parseJsonBody(request);
            Long requestId = Long.valueOf(body.get("request_id").toString());

            boolean success = friendService.acceptRequest(requestId);
            if (!success) {
                throw new ApiException(400, "Failed to accept friend request");
            }
            return Map.of("message", "已接受好友请求", "success", true);
        }

        // POST /api/friends/reject - 拒绝好友请求
        if (method.equals(HttpMethod.POST) && "/api/friends/reject".equals(uri)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = parseJsonBody(request);
            Long requestId = Long.valueOf(body.get("request_id").toString());

            boolean success = friendService.rejectRequest(requestId);
            if (!success) {
                throw new ApiException(400, "Failed to reject friend request");
            }
            return Map.of("message", "已拒绝好友请求", "success", true);
        }

        // DELETE /api/friends/{id} - 删除好友
        if (method.equals(HttpMethod.DELETE) && uri.matches("/api/friends/\\d+")) {
            Long friendId = extractId(uri);
            boolean success = friendService.removeFriend(userId, friendId);
            if (!success) {
                throw new ApiException(400, "Failed to remove friend");
            }
            return Map.of("message", "已删除好友", "success", true);
        }

        throw new ApiException(404, "Not found");
    }

    // ==================== 辅助方法 ====================

    private Long getUserIdFromToken(FullHttpRequest request) {
        // 首先尝试从URL参数获取token
        String uri = request.uri();
        String token = null;

        if (uri.contains("?token=")) {
            try {
                int tokenStart = uri.indexOf("?token=") + 7;
                int tokenEnd = uri.indexOf("&", tokenStart);
                if (tokenEnd == -1) {
                    token = uri.substring(tokenStart);
                } else {
                    token = uri.substring(tokenStart, tokenEnd);
                }
                logger.debug("Token from URL parameter: {}", token != null ? "present (length=" + token.length() + ")" : "null");
            } catch (Exception e) {
                logger.error("Failed to extract token from URL", e);
            }
        }

        // 如果URL中没有token，尝试从Authorization header获取
        if (token == null || token.isEmpty()) {
            String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                logger.debug("Token from Authorization header: present (length={})", token.length());
            }
        }

        if (token == null || token.isEmpty()) {
            logger.warn("No token found in request");
            throw new ApiException(401, "Unauthorized: No token provided");
        }

        // 使用authService验证token
        Long userId = authService.validateToken(token);
        if (userId == null) {
            logger.warn("Invalid token provided");
            throw new ApiException(401, "Unauthorized: Invalid token");
        }

        logger.debug("User authenticated: userId={}", userId);
        return userId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonBody(FullHttpRequest request) {
        try {
            ByteBuf content = request.content();
            String json = content.toString(StandardCharsets.UTF_8);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new ApiException(400, "Invalid JSON");
        }
    }

    private Long extractId(String uri) {
        String[] parts = uri.split("/");
        try {
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Invalid ID");
        }
    }

    private int getQueryParam(String uri, String key, int defaultValue) {
        try {
            if (uri.contains(key + "=")) {
                String value = uri.substring(uri.indexOf(key + "=") + key.length() + 1);
                if (value.contains("&")) {
                    value = value.substring(0, value.indexOf("&"));
                }
                return Integer.parseInt(value);
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return defaultValue;
    }

    // ==================== 响应发送 ====================

    private void sendJsonResponse(ChannelHandlerContext ctx, int statusCode, Object data, String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", statusCode < 400);

        if (data != null) {
            response.put("data", data);
        }

        if (errorMessage != null) {
            response.put("message", errorMessage);
        }

        try {
            String json = objectMapper.writeValueAsString(response);
            ByteBuf content = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);

            FullHttpResponse httpResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(statusCode),
                content
            );

            httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
            httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            httpResponse.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            httpResponse.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
            httpResponse.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization");

            ctx.writeAndFlush(httpResponse);
        } catch (Exception e) {
            logger.error("发送响应失败", e);
        }
    }

    private void sendCorsResponse(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK
        );

        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);

        ctx.writeAndFlush(response);
    }

    // ==================== 异常类 ====================

    private static class ApiException extends RuntimeException {
        private final int statusCode;

        public ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
