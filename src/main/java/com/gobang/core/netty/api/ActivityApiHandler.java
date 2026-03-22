package com.gobang.core.netty.api;

import com.gobang.model.entity.UserActivityLog;
import com.gobang.service.ActivityLogService;
import com.gobang.service.AuthService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 活动日志API处理器
 * 处理用户活动历史查询
 */
public class ActivityApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(ActivityApiHandler.class);
    private final ActivityLogService activityLogService;
    private final AuthService authService;

    public ActivityApiHandler(ActivityLogService activityLogService, AuthService authService) {
        this.activityLogService = activityLogService;
        this.authService = authService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/activity";
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
                if (path.equals("/api/activity/history")) {
                    handleGetActivityHistory(ctx, request, userId);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("Activity API error", e);
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

    private void handleGetActivityHistory(ChannelHandlerContext ctx, FullHttpRequest request, Long userId) {
        try {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            String limitStr = decoder.parameters().getOrDefault("limit", List.of("20")).get(0);
            int limit = Integer.parseInt(limitStr);

            List<UserActivityLog> activities = activityLogService.getUserRecentActivities(userId, limit);

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "activities", activities));
        } catch (Exception e) {
            logger.error("Failed to get activity history for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取活动历史失败"));
        }
    }
}
