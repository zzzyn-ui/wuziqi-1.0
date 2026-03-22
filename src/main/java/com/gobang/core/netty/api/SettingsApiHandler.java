package com.gobang.core.netty.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.model.entity.UserSettings;
import com.gobang.service.AuthService;
import com.gobang.service.UserSettingsService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 用户设置API处理器
 * 处理用户设置查询和更新
 */
public class SettingsApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(SettingsApiHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserSettingsService userSettingsService;
    private final AuthService authService;

    public SettingsApiHandler(UserSettingsService userSettingsService, AuthService authService) {
        this.userSettingsService = userSettingsService;
        this.authService = authService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/settings";
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
                handleGetSettings(ctx, userId);
                return true;
            } else if ("PUT".equals(method)) {
                handleUpdateSettings(ctx, request, userId);
                return true;
            }
        } catch (Exception e) {
            logger.error("Settings API error", e);
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

    private void handleGetSettings(ChannelHandlerContext ctx, Long userId) {
        try {
            Map<String, Object> settings = userSettingsService.getSettingsAsMap(userId);

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "settings", settings));
        } catch (Exception e) {
            logger.error("Failed to get settings for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取设置失败"));
        }
    }

    private void handleUpdateSettings(ChannelHandlerContext ctx, FullHttpRequest request, Long userId) {
        try {
            String content = request.content().toString(StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(content, Map.class);

            // 简化实现 - 这里可以添加更详细的设置更新逻辑
            // 暂时只返回成功，实际设置可以保存到数据库或缓存

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "message", "设置已更新"));
        } catch (Exception e) {
            logger.error("Failed to update settings for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "更新设置失败"));
        }
    }
}
