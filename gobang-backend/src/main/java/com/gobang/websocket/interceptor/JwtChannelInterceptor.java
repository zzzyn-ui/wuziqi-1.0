package com.gobang.websocket.interceptor;

import com.gobang.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * JWT 通道拦截器
 * 用于在 WebSocket 连接时验证 JWT Token
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JwtChannelInterceptor.class);

    private final JwtUtil jwtUtil;

    public JwtChannelInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // 只在 CONNECT 命令时验证 JWT 并存储用户信息到 session
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            logger.info("========== CONNECT 鉴权 ==========");
            logger.info("提取的Token: {}", token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null");

            if (token != null && jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);
                logger.info("WebSocket Token 有效: userId={}, username={}", userId, username);

                // 将用户信息存储到 session attributes 中，供后续消息使用
                accessor.getSessionAttributes().put("userId", userId);
                accessor.getSessionAttributes().put("username", username);
                logger.info("用户信息已存储到 session: userId={}, username={}", userId, username);
            } else {
                logger.warn("WebSocket Token 无效");
            }
        } else {
            // 对于其他命令（SEND, SUBSCRIBE 等），从 session 获取用户信息
            Long userId = (Long) accessor.getSessionAttributes().get("userId");
            String username = (String) accessor.getSessionAttributes().get("username");

            if (userId != null) {
                logger.debug("从 session 获取用户: userId={}, username={}, command={}",
                    userId, username, accessor.getCommand());
            } else {
                logger.warn("Session 中没有用户信息: command={}", accessor.getCommand());
            }
        }

        return message;
    }

    /**
     * 从 STOMP 头中提取 JWT Token
     */
    private String extractToken(StompHeaderAccessor accessor) {
        // 尝试从 Authorization 头获取
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 尝试从 token 头获取（备用方案）
        String tokenHeader = accessor.getFirstNativeHeader("token");
        if (tokenHeader != null) {
            return tokenHeader;
        }

        // 尝试从查询参数获取
        String tokenQuery = accessor.getFirstNativeHeader("X-Auth-Token");
        if (tokenQuery != null) {
            return tokenQuery;
        }

        return null;
    }

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        // 消息发送后的处理
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        // 消息发送完成后的处理
    }
}
