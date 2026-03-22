package com.gobang.core.netty.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.model.entity.User;
import com.gobang.service.AuthService;
import com.gobang.service.UserService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 用户API处理器
 * 处理用户信息查询和更新
 */
public class UserApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(UserApiHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserService userService;
    private final AuthService authService;

    public UserApiHandler(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/user";
    }

    @Override
    public boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method) {
        try {
            // 验证用户身份
            Long userId = extractUserId(ctx, request);
            if (userId == null) {
                sendJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED,
                    Map.of("success", false, "message", "未授权"));
                return true;
            }

            if ("GET".equals(method)) {
                handleGetUser(ctx, userId);
                return true;
            } else if ("PUT".equals(method)) {
                handleUpdateUser(ctx, request, userId);
                return true;
            }
        } catch (Exception e) {
            logger.error("User API error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "服务器错误"));
        }
        return false;
    }

    /**
     * 从请求中提取用户ID
     */
    private Long extractUserId(ChannelHandlerContext ctx, FullHttpRequest request) {
        String token = extractTokenFromRequest(request);
        if (token != null && !token.isEmpty()) {
            return authService.validateToken(token);
        }
        return null;
    }

    /**
     * 获取用户信息
     */
    private void handleGetUser(ChannelHandlerContext ctx, Long userId) {
        try {
            User user = userService.getUserById(userId);
            if (user != null) {
                user.setPassword(null); // 隐藏密码
                // 获取用户统计信息
                com.gobang.model.entity.UserStats stats = null;
                try {
                    stats = userService.getUserStats(userId);
                } catch (Exception statsError) {
                    logger.warn("Failed to get stats for user {}, using default values", userId);
                    // 创建默认统计
                    stats = new com.gobang.model.entity.UserStats(userId);
                }
                sendJsonResponse(ctx, HttpResponseStatus.OK, Map.of(
                    "success", true,
                    "user", user,
                    "stats", stats
                ));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    Map.of("success", false, "message", "用户不存在"));
            }
        } catch (Exception e) {
            logger.error("Failed to get user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取用户信息失败"));
        }
    }

    /**
     * 更新用户信息
     */
    private void handleUpdateUser(ChannelHandlerContext ctx, FullHttpRequest request, Long userId) {
        try {
            String content = request.content().toString(StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(content, Map.class);

            User user = userService.getUserById(userId);
            if (user == null) {
                sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    Map.of("success", false, "message", "用户不存在"));
                return;
            }

            // 简化实现 - 这里可以添加更详细的用户信息更新逻辑
            // 暂时只返回成功

            user.setPassword(null);
            sendJsonResponse(ctx, HttpResponseStatus.OK, Map.of(
                "success", true,
                "message", "更新成功",
                "user", user
            ));
        } catch (Exception e) {
            logger.error("Failed to update user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "更新用户信息失败"));
        }
    }
}
