package com.gobang.websocket.interceptor;

import com.gobang.util.JwtUtil;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
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

        logger.debug("收到WebSocket消息: command={}, sessionId={}, destination={}",
                accessor.getCommand(), accessor.getSessionId(), accessor.getDestination());

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            logger.info("WebSocket CONNECT 请求: token={}",
                    token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null");

            if (token != null && jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);

                if (userId != null) {
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            userId.toString(),
                            null,
                            java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                    authentication.setDetails(username);

                    accessor.setUser(authentication);
                    // 将用户信息存储到 session attributes 中，供后续消息使用
                    accessor.getSessionAttributes().put("userId", userId);
                    accessor.getSessionAttributes().put("username", username);
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    logger.info("✅ WebSocket 连接认证成功: userId={}, username={}", userId, username);

                    // 更新用户在线状态
                    updateUserStatus(userId, 1);
                } else {
                    logger.warn("⚠️ Token有效但无法提取用户ID");
                }
            } else {
                logger.warn("⚠️ 未提供Token或Token无效，拒绝连接");
                // 拒绝未认证的连接
                return null;
            }
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            // 用户断开连接时更新状态
            Principal user = accessor.getUser();
            if (user != null) {
                try {
                    Long userId = Long.parseLong(user.getName());
                    updateUserStatus(userId, 0);
                    logger.info("用户 {} 断开WebSocket连接", userId);
                } catch (Exception e) {
                    logger.warn("更新离线状态失败: {}", e.getMessage());
                }
            }
        } else {
            // 对于其他命令（SEND, SUBSCRIBE等），确保用户Principal存在
            if (accessor.getUser() == null) {
                // 尝试从 session attributes 获取用户信息
                Object userIdObj = accessor.getSessionAttributes().get("userId");
                if (userIdObj != null) {
                    Long userId = (Long) userIdObj;
                    String username = (String) accessor.getSessionAttributes().get("username");

                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            userId.toString(),
                            null,
                            java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                    authentication.setDetails(username);
                    accessor.setUser(authentication);

                    logger.debug("从session恢复用户Principal: userId={}", userId);
                } else {
                    logger.warn("⚠️ 无法获取用户信息，可能未连接");
                    // 不拒绝消息，允许继续处理（某些订阅可能在连接前）
                }
            }
        }

        return message;
    }

    /**
     * 更新用户在线状态
     */
    private void updateUserStatus(Long userId, int status) {
        // TODO: 实现更新用户状态
        // 可以通过注入UserService来更新
        logger.debug("更新用户 {} 状态为: {}", userId, status == 1 ? "在线" : "离线");
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

        return null;
    }
}
