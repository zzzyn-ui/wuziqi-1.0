package com.gobang.core.netty.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.model.entity.GameFavorite;
import com.gobang.service.AuthService;
import com.gobang.service.GameFavoriteService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 对局收藏API处理器
 * 处理对局收藏操作
 */
public class FavoritesApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(FavoritesApiHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameFavoriteService gameFavoriteService;
    private final AuthService authService;

    public FavoritesApiHandler(GameFavoriteService gameFavoriteService, AuthService authService) {
        this.gameFavoriteService = gameFavoriteService;
        this.authService = authService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/favorites";
    }

    @Override
    public boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method) {
        try {
            Long userId = extractUserId(ctx, request);
            if (userId == null) {
                sendJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED,
                    Map.of("success", false, "message", "未授权"));
                return true;
            }

            if ("GET".equals(method)) {
                if (path.equals("/api/favorites")) {
                    handleGetFavorites(ctx, userId);
                    return true;
                }
            } else if ("POST".equals(method)) {
                if (path.equals("/api/favorites")) {
                    handleAddFavorite(ctx, request, userId);
                    return true;
                }
            } else if ("DELETE".equals(method)) {
                if (path.startsWith("/api/favorites/")) {
                    handleRemoveFavorite(ctx, path, userId);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("Favorites API error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "服务器错误"));
        }
        return false;
    }

    private Long extractUserId(ChannelHandlerContext ctx, FullHttpRequest request) {
        String token = extractTokenFromRequest(request);
        if (token != null && !token.isEmpty()) {
            return authService.validateToken(token);
        }
        return null;
    }

    private void handleGetFavorites(ChannelHandlerContext ctx, Long userId) {
        try {
            List<GameFavorite> favorites = gameFavoriteService.getUserFavorites(userId);
            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "favorites", favorites));
        } catch (Exception e) {
            logger.error("Failed to get favorites for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取收藏失败"));
        }
    }

    private void handleAddFavorite(ChannelHandlerContext ctx, FullHttpRequest request, Long userId) {
        try {
            String content = request.content().toString(StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(content, Map.class);

            Number gameIdNum = (Number) data.get("gameRecordId");
            if (gameIdNum == null) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "缺少对局ID"));
                return;
            }

            Long gameRecordId = gameIdNum.longValue();
            String note = (String) data.getOrDefault("note", "");
            String tags = (String) data.getOrDefault("tags", "");

            boolean success = gameFavoriteService.addFavorite(userId, gameRecordId, note, tags);

            if (success) {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "message", "已添加收藏"));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Map.of("success", false, "message", "添加收藏失败"));
            }
        } catch (Exception e) {
            logger.error("Failed to add favorite for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "添加收藏失败"));
        }
    }

    private void handleRemoveFavorite(ChannelHandlerContext ctx, String path, Long userId) {
        try {
            String[] parts = path.split("/");
            if (parts.length < 4) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "无效的请求"));
                return;
            }

            Long gameRecordId = Long.parseLong(parts[3]);
            boolean success = gameFavoriteService.removeFavorite(userId, gameRecordId);

            if (success) {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "message", "已取消收藏"));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Map.of("success", false, "message", "取消收藏失败"));
            }
        } catch (Exception e) {
            logger.error("Failed to remove favorite for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "取消收藏失败"));
        }
    }
}
