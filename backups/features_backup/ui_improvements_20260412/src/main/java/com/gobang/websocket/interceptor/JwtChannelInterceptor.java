package com.gobang.websocket.interceptor;

import com.gobang.mapper.UserMapper;
import com.gobang.model.entity.User;
import com.gobang.util.JwtUtil;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.LocalDateTime;

/**
 * JWT 通道拦截器
 * 用于在 WebSocket 连接时验证 JWT Token
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JwtChannelInterceptor.class);

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public JwtChannelInterceptor(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // 改为 info 级别以便追踪所有消息
        String destination = accessor.getDestination();
        logger.info("📨 [INTERCEPTOR] 收到WebSocket消息: command={}, sessionId={}, destination={}",
                accessor.getCommand(), accessor.getSessionId(), destination);

        // 特别追踪 /app/game/leave 消息
        if (destination != null && destination.equals("/app/game/leave")) {
            logger.info("🚪🚪🚪 [INTERCEPTOR] 检测到 /app/game/leave 消息！");
            logger.info("🚪 [INTERCEPTOR] 消息体: {}", message.getPayload());
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            logger.info("🔗 WebSocket CONNECT 请求: sessionId={}, token={}",
                    accessor.getSessionId(),
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

                    logger.info("✅ WebSocket 连接认证成功: userId={}, username={}, sessionId={}, principalName={}",
                            userId, username, accessor.getSessionId(), authentication.getName());

                    // 更新用户在线状态
                    updateUserStatus(userId, 1);

                    // 返回带有修改后accessor的新消息，确保Principal被正确存储
                    Message<?> newMessage = MessageBuilder.createMessage(
                        message.getPayload(),
                        accessor.getMessageHeaders()
                    );
                    return newMessage;
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
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            // 对于 SEND 命令，总是确保用户 Principal 存在
            Principal existingPrincipal = accessor.getUser();
            boolean needRestore = false;

            // 检查现有 Principal 是否有效
            if (existingPrincipal == null) {
                needRestore = true;
                logger.debug("SEND命令: Principal为null，需要恢复");
            } else {
                // 检查 Principal 的 name 是否有效
                String principalName = existingPrincipal.getName();
                if (principalName == null || principalName.isEmpty() || "anonymousUser".equals(principalName)) {
                    needRestore = true;
                    logger.debug("SEND命令: Principal无效 ({})，需要恢复", principalName);
                }
            }

            if (needRestore) {
                // 从 session attributes 获取用户信息
                Object userIdObj = accessor.getSessionAttributes().get("userId");
                if (userIdObj != null) {
                    Long userId = null;
                    if (userIdObj instanceof Long) {
                        userId = (Long) userIdObj;
                    } else if (userIdObj instanceof Integer) {
                        userId = ((Integer) userIdObj).longValue();
                    } else {
                        try {
                            userId = Long.parseLong(userIdObj.toString());
                        } catch (NumberFormatException e) {
                            logger.warn("无法解析 userId: {}", userIdObj);
                        }
                    }

                    if (userId != null) {
                        String username = (String) accessor.getSessionAttributes().get("username");

                        UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                userId.toString(),
                                null,
                                java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                        authentication.setDetails(username);
                        accessor.setUser(authentication);

                        logger.info("✅ SEND命令: 恢复用户Principal (userId={}, username={})", userId, username);

                        // 关键修复：返回带有修改后accessor的新消息
                        Message<?> newMessage = MessageBuilder.createMessage(
                            message.getPayload(),
                            accessor.getMessageHeaders()
                        );
                        return newMessage;
                    } else {
                        logger.warn("⚠️ SEND命令: 无法从session attributes获取有效userId，拒绝消息");
                        if (destination != null && destination.equals("/app/game/leave")) {
                            logger.error("🚪🚪🚪 [INTERCEPTOR] /app/game/leave 消息被拒绝：无法解析userId");
                        }
                        return null; // 拒绝消息
                    }
                } else {
                    logger.warn("⚠️ SEND命令: session attributes中没有userId，拒绝消息");
                    if (destination != null && destination.equals("/app/game/leave")) {
                        logger.error("🚪🚪🚪 [INTERCEPTOR] /app/game/leave 消息被拒绝：session attributes中没有userId");
                    }
                    return null; // 拒绝消息
                }
            }
        } else {
            // 对于其他命令（SUBSCRIBE等），确保用户Principal存在
            boolean accessorModified = false;
            if (accessor.getUser() == null) {
                // 尝试从 session attributes 获取用户信息
                Object userIdObj = accessor.getSessionAttributes().get("userId");
                if (userIdObj != null) {
                    Long userId = null;
                    if (userIdObj instanceof Long) {
                        userId = (Long) userIdObj;
                    } else if (userIdObj instanceof Integer) {
                        userId = ((Integer) userIdObj).longValue();
                    } else {
                        try {
                            userId = Long.parseLong(userIdObj.toString());
                        } catch (NumberFormatException e) {
                            logger.warn("无法解析 userId: {}", userIdObj);
                        }
                    }

                    if (userId != null) {
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
                        accessorModified = true;
                    }
                } else {
                    logger.debug("无法获取用户信息，可能未连接");
                    // 不拒绝消息，允许继续处理（某些订阅可能在连接前）
                }
            }

            // 如果修改了accessor，返回带有新头部的消息
            if (accessorModified) {
                Message<?> newMessage = MessageBuilder.createMessage(
                    message.getPayload(),
                    accessor.getMessageHeaders()
                );
                return newMessage;
            }
        }

        // 记录消息通过拦截器
        if (destination != null && destination.equals("/app/game/leave")) {
            logger.info("✅🚪 [INTERCEPTOR] /app/game/leave 消息通过拦截器，将发送到控制器");
        }

        return message;
    }

    /**
     * 更新用户在线状态
     */
    private void updateUserStatus(Long userId, int status) {
        try {
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setStatus(status);
                user.setLastOnline(LocalDateTime.now());
                userMapper.updateById(user);
                logger.info("✅ 更新用户 {} 状态为: {}", userId, status == 1 ? "在线" : "离线");
            } else {
                logger.warn("⚠️ 用户 {} 不存在，无法更新状态", userId);
            }
        } catch (Exception e) {
            logger.error("❌ 更新用户状态失败: userId={}, status={}", userId, status, e);
        }
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
