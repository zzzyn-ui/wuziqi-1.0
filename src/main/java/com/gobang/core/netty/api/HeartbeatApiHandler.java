package com.gobang.core.netty.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.core.room.RoomManager;
import com.gobang.service.AuthService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 心跳API处理器
 * 处理客户端心跳请求
 */
public class HeartbeatApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatApiHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoomManager roomManager;
    private final AuthService authService;

    public HeartbeatApiHandler(RoomManager roomManager, AuthService authService) {
        this.roomManager = roomManager;
        this.authService = authService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/heartbeat";
    }

    @Override
    public boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method) {
        try {
            if ("POST".equals(method)) {
                handleHeartbeat(ctx, request);
                return true;
            }
        } catch (Exception e) {
            logger.error("Heartbeat API error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "心跳处理失败"));
        }
        return false;
    }

    private void handleHeartbeat(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            // 从URL参数中获取token
            String token = extractTokenFromRequest(request);
            Long userId = null;

            if (token != null && !token.isEmpty()) {
                userId = authService.validateToken(token);
            }

            if (userId != null) {
                logger.debug("💓 Heartbeat from user: {}", userId);
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "message", "pong", "userId", userId));
            } else {
                // 即使没有token也返回成功（允许匿名心跳）
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "message", "pong"));
            }
        } catch (Exception e) {
            logger.error("Heartbeat error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "心跳处理失败"));
        }
    }
}
