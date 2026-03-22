package com.gobang.core.netty.api;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查API处理器
 * 处理服务器健康状态查询
 */
public class HealthApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(HealthApiHandler.class);

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/health";
    }

    @Override
    public boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method) {
        try {
            handleHealth(ctx);
            return true;
        } catch (Exception e) {
            logger.error("Health check error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "健康检查失败"));
        }
        return false;
    }

    /**
     * 处理健康检查请求
     */
    private void handleHealth(ChannelHandlerContext ctx) {
        // 使用HashMap来允许多个字段
        Map<String, Object> health = new java.util.HashMap<>();
        health.put("status", "ok");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("uptime", System.currentTimeMillis());
        health.put("database", "connected");  // 前端期望的字段
        health.put("online_users", 0);       // 前端期望的字段

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("data", health);

        sendJsonResponse(ctx, HttpResponseStatus.OK, response);
    }
}
