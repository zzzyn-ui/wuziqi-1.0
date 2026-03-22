package com.gobang.core.netty.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.model.entity.ChatMessage;
import com.gobang.model.entity.Friend;
import com.gobang.model.entity.User;
import com.gobang.service.AuthService;
import com.gobang.service.ChatService;
import com.gobang.service.FriendService;
import com.gobang.service.UserService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 好友API处理器
 * 处理好友列表、添加、接受、拒绝、删除、私聊消息等
 */
public class FriendsApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(FriendsApiHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FriendService friendService;
    private final UserService userService;
    private final AuthService authService;
    private final ChatService chatService;

    public FriendsApiHandler(FriendService friendService, UserService userService, AuthService authService,
                            ChatService chatService) {
        this.friendService = friendService;
        this.userService = userService;
        this.authService = authService;
        this.chatService = chatService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/friends";
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

            // 路由到具体的处理方法
            if (path.equals("/api/friends") || path.equals("/api/friends/list")) {
                if ("GET".equals(method)) {
                    handleGetFriendsList(ctx, userId);
                    return true;
                }
            } else if (path.equals("/api/friends/requests") || path.equals("/api/friends/requests/pending")) {
                if ("GET".equals(method)) {
                    handleGetPendingRequests(ctx, userId);
                    return true;
                }
            } else if (path.equals("/api/friends/request")) {
                if ("POST".equals(method)) {
                    handleSendFriendRequest(ctx, request, userId);
                    return true;
                }
            } else if (path.startsWith("/api/friends/accept")) {
                if ("POST".equals(method)) {
                    handleAcceptFriendRequest(ctx, request, userId);
                    return true;
                }
            } else if (path.startsWith("/api/friends/reject")) {
                if ("POST".equals(method)) {
                    handleRejectFriendRequest(ctx, request, userId);
                    return true;
                }
            } else if (path.startsWith("/api/friends/delete")) {
                if ("DELETE".equals(method) || "POST".equals(method)) {
                    handleRemoveFriend(ctx, userId, path);
                    return true;
                }
            } else if (path.matches("/api/friends/\\d+")) {
                // DELETE /api/friends/{friendId} - 删除好友
                logger.info("Matched /api/friends/\\d+ pattern, path: {}, method: {}", path, method);
                if ("DELETE".equals(method)) {
                    handleRemoveFriendById(ctx, userId, path);
                    return true;
                }
            } else if (path.matches("/api/friends/\\d+/messages")) {
                // 处理私聊消息: POST /api/friends/{friendId}/messages (发送) 或 GET (获取)
                if ("POST".equals(method)) {
                    handleSendMessage(ctx, request, userId, path);
                    return true;
                } else if ("GET".equals(method)) {
                    handleGetMessages(ctx, userId, path);
                    return true;
                }
            } else if (path.equals("/api/friends/messages/unread")) {
                // 获取未读消息数量和列表
                if ("GET".equals(method)) {
                    handleGetUnreadMessages(ctx, userId);
                    return true;
                }
            }

        } catch (Exception e) {
            logger.error("Friends API error", e);
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
     * 获取好友列表
     */
    private void handleGetFriendsList(ChannelHandlerContext ctx, Long userId) {
        try {
            List<User> friends = friendService.getFriendList(userId);

            // 添加在线状态
            List<Map<String, Object>> friendsWithStatus = friends.stream()
                .map(friend -> Map.<String, Object>of(
                    "id", friend.getId(),
                    "username", friend.getUsername(),
                    "nickname", friend.getNickname(),
                    "avatar", friend.getAvatar(),
                    "rating", friend.getRating(),
                    "level", friend.getLevel(),
                    "status", friend.getStatus(),
                    "isOnline", friend.getStatus() != null && friend.getStatus() > 0
                ))
                .toList();

            sendJsonResponse(ctx, HttpResponseStatus.OK, Map.of(
                "success", true,
                "friends", friendsWithStatus
            ));
        } catch (Exception e) {
            logger.error("Failed to get friends list for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取好友列表失败"));
        }
    }

    /**
     * 获取待处理的好友请求
     */
    private void handleGetPendingRequests(ChannelHandlerContext ctx, Long userId) {
        try {
            List<Friend> requests = friendService.getPendingRequests(userId);

            // 转换为响应格式
            List<Map<String, Object>> requestList = requests.stream()
                .map(req -> {
                    User sender = userService.getUserById(req.getUserId());
                    return Map.<String, Object>of(
                        "id", req.getId(),
                        "senderId", req.getUserId(),
                        "senderName", sender != null ? sender.getNickname() : "未知用户",
                        "message", req.getRequestMessage() != null ? req.getRequestMessage() : "",
                        "createdAt", req.getCreatedAt() != null ? req.getCreatedAt().toString() : ""
                    );
                })
                .toList();

            sendJsonResponse(ctx, HttpResponseStatus.OK, Map.of(
                "success", true,
                "requests", requestList
            ));
        } catch (Exception e) {
            logger.error("Failed to get pending requests for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取好友请求失败"));
        }
    }

    /**
     * 发送好友请求
     */
    private void handleSendFriendRequest(ChannelHandlerContext ctx, FullHttpRequest request, Long userId) {
        try {
            String content = request.content().toString(StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(content, Map.class);

            String message = (String) data.getOrDefault("message", "");
            User targetUser = null;

            // 支持 username 或 target_id（用户ID）
            String targetUsername = (String) data.get("username");
            Object targetIdObj = data.get("target_id");

            if (targetUsername != null && !targetUsername.isEmpty()) {
                // 通过用户名查找
                targetUser = userService.getUserByUsername(targetUsername);
                if (targetUser == null) {
                    sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                        Map.of("success", false, "message", "用户不存在"));
                    return;
                }
            } else if (targetIdObj != null) {
                // 通过用户ID查找
                Long targetId;
                if (targetIdObj instanceof Number) {
                    targetId = ((Number) targetIdObj).longValue();
                } else {
                    targetId = Long.parseLong(targetIdObj.toString());
                }
                targetUser = userService.getUserById(targetId);
                if (targetUser == null) {
                    sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                        Map.of("success", false, "message", "用户不存在"));
                    return;
                }
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "请提供用户名或用户ID"));
                return;
            }

            // 发送好友请求
            boolean success = friendService.sendRequest(userId, targetUser.getId(), message);

            if (success) {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "message", "好友请求已发送"));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "发送好友请求失败"));
            }
        } catch (Exception e) {
            logger.error("Failed to send friend request from user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "发送好友请求失败"));
        }
    }

    /**
     * 接受好友请求
     */
    private void handleAcceptFriendRequest(ChannelHandlerContext ctx, FullHttpRequest request, Long userId) {
        try {
            String content = request.content().toString(StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(content, Map.class);

            // 支持 requestId 和 request_id 两种命名
            Number requestIdNum = (Number) data.get("requestId");
            if (requestIdNum == null) {
                requestIdNum = (Number) data.get("request_id");
            }
            if (requestIdNum == null) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "缺少请求ID"));
                return;
            }

            Long requestId = requestIdNum.longValue();
            boolean success = friendService.acceptRequest(requestId);

            if (success) {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "message", "已添加好友"));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "接受好友请求失败"));
            }
        } catch (Exception e) {
            logger.error("Failed to accept friend request for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "接受好友请求失败"));
        }
    }

    /**
     * 拒绝好友请求
     */
    private void handleRejectFriendRequest(ChannelHandlerContext ctx, FullHttpRequest request, Long userId) {
        try {
            String content = request.content().toString(StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(content, Map.class);

            // 支持 requestId 和 request_id 两种命名
            Number requestIdNum = (Number) data.get("requestId");
            if (requestIdNum == null) {
                requestIdNum = (Number) data.get("request_id");
            }
            if (requestIdNum == null) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "缺少请求ID"));
                return;
            }

            Long requestId = requestIdNum.longValue();
            boolean success = friendService.rejectRequest(requestId);

            if (success) {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "message", "已拒绝好友请求"));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "拒绝好友请求失败"));
            }
        } catch (Exception e) {
            logger.error("Failed to reject friend request for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "拒绝好友请求失败"));
        }
    }

    /**
     * 删除好友
     */
    private void handleRemoveFriend(ChannelHandlerContext ctx, Long userId, String path) {
        try {
            // 从路径中提取好友ID: /api/friends/delete/{friendId}
            String[] parts = path.split("/");
            if (parts.length < 5) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "无效的请求"));
                return;
            }

            Long friendId = Long.parseLong(parts[4]);
            boolean success = friendService.removeFriend(userId, friendId);

            if (success) {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "message", "已删除好友"));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "删除好友失败"));
            }
        } catch (Exception e) {
            logger.error("Failed to remove friend for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "删除好友失败"));
        }
    }

    /**
     * 删除好友 (通过ID路径)
     * DELETE /api/friends/{friendId}
     */
    private void handleRemoveFriendById(ChannelHandlerContext ctx, Long userId, String path) {
        try {
            // 从路径中提取好友ID: /api/friends/{friendId}
            String[] parts = path.split("/");
            if (parts.length < 4) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "无效的请求"));
                return;
            }

            Long friendId = Long.parseLong(parts[3]);
            boolean success = friendService.removeFriend(userId, friendId);

            if (success) {
                sendJsonResponse(ctx, HttpResponseStatus.OK,
                    Map.of("success", true, "message", "已删除好友"));
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "删除好友失败"));
            }
        } catch (Exception e) {
            logger.error("Failed to remove friend for user {}", userId, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "删除好友失败"));
        }
    }

    /**
     * 发送私聊消息
     * POST /api/friends/{friendId}/messages
     */
    private void handleSendMessage(ChannelHandlerContext ctx, FullHttpRequest request, Long userId, String path) {
        try {
            logger.info("=== Sending private message ===");
            logger.info("User ID: {}", userId);
            logger.info("Path: {}", path);

            // 从路径中提取好友ID: /api/friends/{friendId}/messages
            String[] parts = path.split("/");
            if (parts.length < 5) {
                logger.error("Invalid path, parts length: {}", parts.length);
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "无效的请求"));
                return;
            }

            Long friendId = Long.parseLong(parts[3]);  // 修正：parts[3]是friendId
            logger.info("Friend ID: {}", friendId);

            // 解析请求体
            String content = request.content().toString(StandardCharsets.UTF_8);
            logger.info("Request body: {}", content);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(content, Map.class);
            String messageContent = (String) data.get("content");

            logger.info("Message content: {}", messageContent);

            if (messageContent == null || messageContent.trim().isEmpty()) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "消息内容不能为空"));
                return;
            }

            // 发送私聊消息
            logger.info("Calling chatService.sendPrivateMessage...");
            ChatMessage message = chatService.sendPrivateMessage(userId, friendId, messageContent.trim());
            logger.info("Message sent, ID: {}", message.getId());

            // 构建响应
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "发送成功");

            Map<String, Object> messageData = new HashMap<>();
            messageData.put("id", message.getId());
            messageData.put("senderId", userId);
            messageData.put("receiverId", friendId);
            messageData.put("content", message.getContent());
            messageData.put("createdAt", message.getCreatedAt() != null ? message.getCreatedAt().toString() : "");

            Map<String, Object> dataWrapper = new HashMap<>();
            dataWrapper.put("message", messageData);
            responseData.put("data", dataWrapper);

            sendJsonResponse(ctx, HttpResponseStatus.OK, responseData);

            logger.info("User {} sent message to friend {}: {}", userId, friendId, messageContent.trim());
        } catch (Exception e) {
            logger.error("Failed to send message from user {}", userId, e);
            logger.error("Error type: {}, message: {}", e.getClass().getName(), e.getMessage());
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "发送消息失败"));
        }
    }

    /**
     * 获取私聊消息
     * GET /api/friends/{friendId}/messages
     */
    private void handleGetMessages(ChannelHandlerContext ctx, Long userId, String path) {
        try {
            logger.info("=== Getting private messages ===");
            logger.info("User ID: {}, Path: {}", userId, path);

            // 从路径中提取好友ID: /api/friends/{friendId}/messages
            String[] parts = path.split("/");
            if (parts.length < 5) {
                logger.error("Invalid path, parts length: {}", parts.length);
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    Map.of("success", false, "message", "无效的请求"));
                return;
            }

            Long friendId = Long.parseLong(parts[3]);  // 修正：parts[3]是friendId
            logger.info("Friend ID: {}", friendId);

            // 使用ChatService获取消息
            List<ChatMessage> messages = chatService.getPrivateMessages(userId, friendId, 100);
            logger.info("Retrieved {} messages", messages.size());

            // 转换为响应格式（使用ArrayList以便反转）
            List<Map<String, Object>> messageList = new java.util.ArrayList<>();
            for (ChatMessage msg : messages) {
                Map<String, Object> msgData = new HashMap<>();
                msgData.put("id", msg.getId());
                msgData.put("senderId", msg.getSenderId());
                msgData.put("receiverId", msg.getReceiverId());
                msgData.put("content", msg.getContent());
                msgData.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : "");
                msgData.put("isRead", msg.getIsRead());
                // 添加发送者名称
                User sender = userService.getUserById(msg.getSenderId());
                msgData.put("senderName", sender != null ? sender.getNickname() : "未知用户");
                messageList.add(msgData);
            }

            // 反转顺序，让最新消息在后面
            java.util.Collections.reverse(messageList);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("data", Map.of("messages", messageList));

            sendJsonResponse(ctx, HttpResponseStatus.OK, responseData);
            logger.info("Successfully returned {} messages", messageList.size());
        } catch (Exception e) {
            logger.error("Failed to get messages for user {} with friend {}", userId, path, e);
            logger.error("Error type: {}, message: {}", e.getClass().getName(), e.getMessage());
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取消息失败"));
        }
    }

    /**
     * 获取未读私聊消息
     * GET /api/friends/messages/unread
     */
    private void handleGetUnreadMessages(ChannelHandlerContext ctx, Long userId) {
        try {
            logger.info("=== Getting unread messages for user {} ===", userId);

            // 使用ChatService获取未读消息
            List<ChatMessage> unreadMessages = chatService.getUnreadMessages(userId);
            logger.info("Retrieved {} unread messages", unreadMessages.size());

            // 转换为响应格式
            List<Map<String, Object>> messageList = new ArrayList<>();
            for (ChatMessage msg : unreadMessages) {
                Map<String, Object> msgData = new HashMap<>();
                msgData.put("id", msg.getId());
                msgData.put("senderId", msg.getSenderId());
                msgData.put("receiverId", msg.getReceiverId());
                msgData.put("content", msg.getContent());
                msgData.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : "");
                // 添加发送者名称
                User sender = userService.getUserById(msg.getSenderId());
                msgData.put("sender_name", sender != null ? sender.getNickname() : "未知用户");
                if (sender != null) {
                    msgData.put("senderUsername", sender.getUsername());
                }
                messageList.add(msgData);
            }

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("data", Map.of(
                "unread_count", unreadMessages.size(),
                "messages", messageList
            ));

            sendJsonResponse(ctx, HttpResponseStatus.OK, responseData);
            logger.info("Successfully returned {} unread messages", unreadMessages.size());
        } catch (Exception e) {
            logger.error("Failed to get unread messages for user {}", userId, e);
            logger.error("Error type: {}, message: {}", e.getClass().getName(), e.getMessage());
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "获取未读消息失败"));
        }
    }
}
