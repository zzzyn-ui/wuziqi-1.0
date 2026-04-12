package com.gobang.config;

import com.gobang.core.match.SpringMatchMaker;
import com.gobang.model.dto.FriendWebSocketDto;
import com.gobang.model.entity.User;
import com.gobang.service.FriendService;
import com.gobang.service.GameService;
import com.gobang.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 事件监听器
 * 处理连接断开等事件，并广播好友状态变化
 */
@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final SpringMatchMaker matchMaker;
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;
    private final FriendService friendService;

    public WebSocketEventListener(SpringMatchMaker matchMaker, GameService gameService,
                                   SimpMessagingTemplate messagingTemplate,
                                   UserService userService, FriendService friendService) {
        this.matchMaker = matchMaker;
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
        this.userService = userService;
        this.friendService = friendService;
        logger.info("📡 WebSocket 事件监听器初始化");
    }

    /**
     * 处理 WebSocket 连接成功事件
     * 更新用户在线状态并通知好友
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // 安全地获取 session attributes
        Map<?, ?> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) {
            logger.debug("Session attributes 为 null，跳过处理");
            return;
        }

        Object userIdObj = sessionAttributes.get("userId");
        if (userIdObj != null) {
            Long userId = parseUserId(userIdObj);
            if (userId != null) {
                User user = userService.getUserById(userId);
                if (user != null) {
                    // 更新用户状态为在线
                    user.setStatus(1);
                    userService.updateUserStatus(userId, 1);

                    logger.info("🟢 用户 {} 上线，通知好友", userId);

                    // 通知所有好友用户上线
                    notifyFriendsUserOnline(userId, user);
                }
            }
        }
    }

    /**
     * 处理 WebSocket 断开连接事件
     * 当用户断开连接时，从匹配队列中移除该用户，并通知房间内的对手和好友
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // 安全地获取 session attributes
        Map<?, ?> sessionAttributes = headerAccessor.getSessionAttributes();
        String sessionId = headerAccessor.getSessionId();

        Object userIdObj = null;
        if (sessionAttributes != null) {
            userIdObj = sessionAttributes.get("userId");
        }

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
                logger.info("🔌 用户 {} 断开连接 (sessionId: {}), 清理状态", userId, sessionId);

                User user = userService.getUserById(userId);
                if (user != null) {
                    // 更新用户最后在线时间
                    user.setStatus(0);
                    userService.updateLastOnline(userId);

                    // 通知所有好友用户下线
                    notifyFriendsUserOffline(userId, user);
                }

                // 从所有匹配队列中移除该用户
                matchMaker.cancelMatchAll(userId);

                // 通知房间内的对手玩家已离开
                try {
                    gameService.handlePlayerLeave(userId);
                } catch (Exception e) {
                    logger.error("通知对手玩家离开时出错", e);
                }
            }
        } else {
            logger.debug("🔌 会话断开 (sessionId: {}), 无用户信息", sessionId);
        }
    }

    /**
     * 通知好友用户上线
     */
    private void notifyFriendsUserOnline(Long userId, User user) {
        try {
            List<Map<String, Object>> friends = friendService.getFriendList(userId);
            for (Map<String, Object> friendData : friends) {
                Long friendId = getLongFromObject(friendData.get("id"));
                if (friendId != null) {
                    // 检查好友是否在线
                    User friend = userService.getUserById(friendId);
                    if (friend != null && friend.isOnline()) {
                        FriendWebSocketDto notification = FriendWebSocketDto.online(
                                userId,
                                user.getUsername(),
                                user.getNickname()
                        );

                        messagingTemplate.convertAndSendToUser(
                                String.valueOf(friendId),
                                "/queue/friend/status",
                                notification
                        );

                        logger.debug("已通知好友 {} 用户 {} 上线", friendId, userId);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("通知好友用户上线失败: userId={}", userId, e);
        }
    }

    /**
     * 通知好友用户下线
     */
    private void notifyFriendsUserOffline(Long userId, User user) {
        try {
            List<Map<String, Object>> friends = friendService.getFriendList(userId);
            for (Map<String, Object> friendData : friends) {
                Long friendId = getLongFromObject(friendData.get("id"));
                if (friendId != null) {
                    // 检查好友是否在线
                    User friend = userService.getUserById(friendId);
                    if (friend != null && friend.isOnline()) {
                        FriendWebSocketDto notification = FriendWebSocketDto.offline(
                                userId,
                                user.getUsername(),
                                user.getNickname()
                        );

                        messagingTemplate.convertAndSendToUser(
                                String.valueOf(friendId),
                                "/queue/friend/status",
                                notification
                        );

                        logger.debug("已通知好友 {} 用户 {} 下线", friendId, userId);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("通知好友用户下线失败: userId={}", userId, e);
        }
    }

    /**
     * 从对象中安全地获取Long值
     */
    private Long getLongFromObject(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析用户ID
     */
    private Long parseUserId(Object userIdObj) {
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        } else if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();
        } else {
            try {
                return Long.parseLong(userIdObj.toString());
            } catch (NumberFormatException e) {
                logger.warn("无法解析 userId: {}", userIdObj);
                return null;
            }
        }
    }
}
