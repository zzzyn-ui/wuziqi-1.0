package com.gobang.core.netty.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.model.entity.User;
import com.gobang.service.AuthService;
import com.gobang.service.UserService;

import java.util.Map;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * 认证API处理器
 * 处理登录、注册、登出
 */
public class AuthApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthApiHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthService authService;
    private final UserService userService;

    public AuthApiHandler(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/auth";
    }

    @Override
    public boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method) {
        try {
            switch (path) {
                case "/api/auth/login":
                    if ("POST".equals(method)) {
                        handleLogin(ctx, request);
                        return true;
                    }
                    break;
                case "/api/auth/register":
                    if ("POST".equals(method)) {
                        handleRegister(ctx, request);
                        return true;
                    }
                    break;
                case "/api/auth/logout":
                    if ("POST".equals(method)) {
                        handleLogout(ctx, request);
                        return true;
                    }
                    break;
            }
        } catch (Exception e) {
            logger.error("Auth API error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "服务器错误"));
        }
        return false;
    }

    /**
     * 处理登录请求
     */
    private void handleLogin(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String content = request.content().toString(StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, String> data = objectMapper.readValue(content, Map.class);

            String username = data.get("username");
            String password = data.get("password");

            logger.info("Login attempt: username={}", username);

            User user = authService.login(username, password);
            if (user != null) {
                String token = authService.generateToken(user.getId());

                // 隐藏密码
                user.setPassword(null);

                sendJsonResponse(ctx, HttpResponseStatus.OK, Map.of(
                    "success", true,
                    "message", "登录成功",
                    "token", token,
                    "user", user
                ));
                logger.info("User {} logged in successfully", username);
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED, Map.of(
                    "success", false,
                    "message", "用户名或密码错误"
                ));
            }
        } catch (Exception e) {
            logger.error("Login error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "登录失败"));
        }
    }

    /**
     * 处理注册请求
     */
    private void handleRegister(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String content = request.content().toString(StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, String> data = objectMapper.readValue(content, Map.class);

            String username = data.get("username");
            String password = data.get("password");
            String nickname = data.get("nickname");

            logger.info("Register attempt: username={}, nickname={}", username, nickname);

            // 检查用户名是否已存在
            if (userService.getUserByUsername(username) != null) {
                sendJsonResponse(ctx, HttpResponseStatus.CONFLICT, Map.of(
                    "success", false,
                    "message", "用户名已存在"
                ));
                return;
            }

            // 创建用户
            User user = authService.register(username, password, nickname);
            if (user != null) {
                sendJsonResponse(ctx, HttpResponseStatus.OK, Map.of(
                    "success", true,
                    "message", "注册成功",
                    "user", user
                ));
                logger.info("New user registered: {}", username);
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    Map.of("success", false, "message", "注册失败"));
            }
        } catch (Exception e) {
            logger.error("Register error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "注册失败"));
        }
    }

    /**
     * 处理登出请求
     */
    private void handleLogout(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String token = extractTokenFromRequest(request);
            if (token != null) {
                Long userId = authService.validateToken(token);
                if (userId != null) {
                    authService.logout(userId);
                    logger.info("User {} logged out", userId);
                }
            }

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                Map.of("success", true, "message", "登出成功"));
        } catch (Exception e) {
            logger.error("Logout error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "登出失败"));
        }
    }
}
