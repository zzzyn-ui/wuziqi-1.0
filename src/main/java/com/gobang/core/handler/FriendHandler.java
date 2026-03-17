package com.gobang.core.handler;

import com.gobang.core.handler.AuthHandler;
import com.gobang.core.protocol.MessageHandler;
import com.gobang.core.protocol.MessageType;
import com.gobang.core.security.RateLimitManager;
import com.gobang.core.social.FriendManager;
import com.gobang.core.netty.ResponseUtil;
import com.gobang.mapper.FriendMapper;
import com.gobang.model.entity.Friend;
import com.gobang.model.entity.User;
import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.service.FriendService;
import com.gobang.service.UserService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 好友消息处理器
 * 处理好友添加、删除、列表等消息
 */
public class FriendHandler implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(FriendHandler.class);

    private final FriendService friendService;
    private final UserService userService;
    private final FriendManager friendManager;
    private final RateLimitManager rateLimitManager;

    public FriendHandler(FriendService friendService, UserService userService, FriendManager friendManager, RateLimitManager rateLimitManager) {
        this.friendService = friendService;
        this.userService = userService;
        this.friendManager = friendManager;
        this.rateLimitManager = rateLimitManager;
    }

    public FriendHandler(FriendService friendService, UserService userService, FriendManager friendManager) {
        this(friendService, userService, friendManager, null);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, GobangProto.Packet packet) {
        // 验证认证
        if (!AuthHandler.isAuthenticated(ctx.channel())) {
            sendError(ctx, packet, "请先登录");
            return;
        }

        Long userId = AuthHandler.getUserId(ctx.channel());
        if (userId == null) {
            sendError(ctx, packet, "用户未认证");
            return;
        }

        MessageType messageType = MessageType.fromValue(packet.getType().getNumber());

        switch (messageType) {
            case FRIEND_REQUEST:
                handleFriendRequest(ctx, packet, userId);
                break;
            case FRIEND_ACCEPT:
                handleFriendAccept(ctx, packet, userId);
                break;
            case FRIEND_REJECT:
                handleFriendReject(ctx, packet, userId);
                break;
            case FRIEND_REMOVE:
                handleFriendRemove(ctx, packet, userId);
                break;
            case FRIEND_LIST:
                handleFriendList(ctx, packet, userId);
                break;
            default:
                logger.warn("Unsupported message type for FriendHandler: {}", messageType);
                break;
        }
    }

    /**
     * 处理好友请求
     */
    private void handleFriendRequest(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            logger.info("用户 {} 发送好友请求", userId);

            // 限流检查
            if (rateLimitManager != null) {
                if (!rateLimitManager.tryAcquire(RateLimitManager.LimitType.FRIEND_REQUEST, userId)) {
                    sendError(ctx, packet, rateLimitManager.getErrorMessage(RateLimitManager.LimitType.FRIEND_REQUEST));
                    logger.warn("Friend request rate limit exceeded for user: {}", userId);
                    return;
                }
            }

            GobangProto.FriendRequestMsg request = GobangProto.FriendRequestMsg.parseFrom(packet.getBody());
            logger.info("好友请求目标: {}", request.getTargetId());

            Long targetId;
            try {
                targetId = Long.parseLong(request.getTargetId());
            } catch (NumberFormatException e) {
                sendError(ctx, packet, "目标用户ID格式错误");
                return;
            }

            // 不能添加自己为好友
            if (targetId.equals(userId)) {
                sendError(ctx, packet, "不能添加自己为好友");
                return;
            }

            // 检查目标用户是否存在
            User targetUser = userService.getUserById(targetId);
            if (targetUser == null) {
                sendError(ctx, packet, "目标用户不存在");
                return;
            }

            logger.info("用户 {} 向用户 {} 发送好友请求，消息: {}", userId, targetId, request.getMessage());

            // 发送好友请求
            boolean success = friendService.sendRequest(userId, targetId, request.getMessage());
            logger.info("好友请求保存结果: {}", success);

            if (success) {
                // 获取发送者信息
                User sender = userService.getUserById(userId);
                String senderName = sender != null ? sender.getNickname() : "用户" + userId;

                // 构建JSON格式的好友请求通知
                String notificationMessage = senderName + "|" + (request.getMessage().isEmpty() ? "想加你好友" : request.getMessage());

                Map<String, Object> bodyData = new HashMap<>();
                bodyData.put("sender_id", String.valueOf(userId));
                bodyData.put("sender_name", senderName);
                bodyData.put("message", notificationMessage);

                // 使用FriendManager发送JSON格式的通知
                Channel targetChannel = friendManager.getChannel(targetId);
                if (targetChannel != null && targetChannel.isActive()) {
                    ResponseUtil.sendJsonResponse(targetChannel, MessageType.FRIEND_REQUEST.getValue(), 0, bodyData);
                    logger.info("Friend request notification sent to user {} from user {}", targetId, userId);
                } else {
                    logger.info("User {} is offline, friend request saved for later", targetId);
                }

                // 发送确认给发送者
                sendConfirmation(ctx, packet, MessageType.FRIEND_REQUEST, "好友请求已发送");
                logger.info("User {} sent friend request to user {}", userId, targetId);
            } else {
                sendError(ctx, packet, "发送好友请求失败（可能已是好友或已有待处理请求）");
            }

        } catch (Exception e) {
            logger.error("Error handling friend request for user: {}", userId, e);
            sendError(ctx, packet, "发送好友请求处理失败");
        }
    }

    /**
     * 处理接受好友请求
     */
    private void handleFriendAccept(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            GobangProto.FriendAcceptMsg request = GobangProto.FriendAcceptMsg.parseFrom(packet.getBody());

            Long requestId;
            try {
                requestId = Long.parseLong(request.getRequestId());
            } catch (NumberFormatException e) {
                sendError(ctx, packet, "请求ID格式错误");
                return;
            }

            // 获取待处理的请求
            List<Friend> pendingRequests = friendService.getPendingRequests(userId);
            Friend targetRequest = null;
            for (Friend req : pendingRequests) {
                if (req.getId().equals(requestId)) {
                    targetRequest = req;
                    break;
                }
            }

            if (targetRequest == null) {
                sendError(ctx, packet, "好友请求不存在");
                return;
            }

            // 接受好友请求
            boolean success = friendService.acceptRequest(requestId);

            if (success) {
                // 更新内存中的好友关系
                friendManager.addFriendship(userId, targetRequest.getUserId());

                // 通知接受者 - 使用JSON格式
                Map<String, Object> acceptData = new HashMap<>();
                acceptData.put("friend_id", String.valueOf(targetRequest.getUserId()));
                acceptData.put("friend_name", userService.getUserById(targetRequest.getUserId()).getNickname());

                Channel userChannel = friendManager.getChannel(userId);
                if (userChannel != null && userChannel.isActive()) {
                    ResponseUtil.sendJsonResponse(userChannel, MessageType.FRIEND_ONLINE.getValue(), 0, acceptData);
                }

                // 通知发送者（目标用户） - 使用JSON格式
                Map<String, Object> notifyData = new HashMap<>();
                notifyData.put("friend_id", String.valueOf(userId));
                notifyData.put("friend_name", userService.getUserById(userId).getNickname());

                Channel targetChannel = friendManager.getChannel(targetRequest.getUserId());
                if (targetChannel != null && targetChannel.isActive()) {
                    ResponseUtil.sendJsonResponse(targetChannel, MessageType.FRIEND_ONLINE.getValue(), 0, notifyData);
                }

                sendConfirmation(ctx, packet, MessageType.FRIEND_ACCEPT, "已接受好友请求");
                logger.info("User {} accepted friend request from user {}", userId, targetRequest.getUserId());
            } else {
                sendError(ctx, packet, "接受好友请求失败");
            }

        } catch (Exception e) {
            logger.error("Error handling friend accept for user: {}", userId, e);
            sendError(ctx, packet, "接受好友请求处理失败");
        }
    }

    /**
     * 处理拒绝好友请求
     */
    private void handleFriendReject(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            GobangProto.FriendRejectMsg request = GobangProto.FriendRejectMsg.parseFrom(packet.getBody());

            Long requestId;
            try {
                requestId = Long.parseLong(request.getRequestId());
            } catch (NumberFormatException e) {
                sendError(ctx, packet, "请求ID格式错误");
                return;
            }

            // 拒绝好友请求
            boolean success = friendService.rejectRequest(requestId);

            if (success) {
                sendConfirmation(ctx, packet, MessageType.FRIEND_REJECT, "已拒绝好友请求");
                logger.info("User {} rejected friend request {}", userId, requestId);
            } else {
                sendError(ctx, packet, "拒绝好友请求失败");
            }

        } catch (Exception e) {
            logger.error("Error handling friend reject for user: {}", userId, e);
            sendError(ctx, packet, "拒绝好友请求处理失败");
        }
    }

    /**
     * 处理删除好友
     */
    private void handleFriendRemove(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            GobangProto.FriendRemoveMsg request = GobangProto.FriendRemoveMsg.parseFrom(packet.getBody());

            Long friendId;
            try {
                friendId = Long.parseLong(request.getFriendId());
            } catch (NumberFormatException e) {
                sendError(ctx, packet, "好友ID格式错误");
                return;
            }

            // 删除好友
            boolean success = friendService.removeFriend(userId, friendId);

            if (success) {
                // 更新内存中的好友关系
                friendManager.removeFriendship(userId, friendId);

                sendConfirmation(ctx, packet, MessageType.FRIEND_REMOVE, "已删除好友");
                logger.info("User {} removed friend {}", userId, friendId);
            } else {
                sendError(ctx, packet, "删除好友失败");
            }

        } catch (Exception e) {
            logger.error("Error handling friend remove for user: {}", userId, e);
            sendError(ctx, packet, "删除好友处理失败");
        }
    }

    /**
     * 处理获取好友列表
     */
    private void handleFriendList(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            List<User> friends = friendService.getFriendList(userId);

            List<GobangProto.FriendInfo> friendInfos = new ArrayList<>();
            for (User friend : friends) {
                boolean isOnline = friendManager.isUserOnline(friend.getId());

                GobangProto.FriendInfo info = GobangProto.FriendInfo.newBuilder()
                        .setUserId(String.valueOf(friend.getId()))
                        .setUsername(friend.getUsername())
                        .setNickname(friend.getNickname())
                        .setAvatar(friend.getAvatar())
                        .setRating(friend.getRating())
                        .setOnline(isOnline)
                        .setLastOnline(friend.getLastOnline() != null ?
                                friend.getLastOnline().toEpochSecond(java.time.ZoneOffset.of("+8")) * 1000 : 0)
                        .build();

                friendInfos.add(info);
            }

            GobangProto.FriendListMsg friendList = GobangProto.FriendListMsg.newBuilder()
                    .addAllFriends(friendInfos)
                    .build();

            GobangProto.Packet response = GobangProto.Packet.newBuilder()
                    .setType(GobangProto.MessageType.FRIEND_LIST)
                    .setSequenceId(packet.getSequenceId())
                    .setTimestamp(System.currentTimeMillis())
                    .setBody(friendList.toByteString())
                    .build();

            ctx.channel().writeAndFlush(response);

        } catch (Exception e) {
            logger.error("Error handling friend list for user: {}", userId, e);
            sendError(ctx, packet, "获取好友列表失败");
        }
    }

    /**
     * 发送确认消息
     */
    private void sendConfirmation(ChannelHandlerContext ctx, GobangProto.Packet request,
                                   MessageType type, String message) {
        GobangProto.ChatReceive chatReceive = GobangProto.ChatReceive.newBuilder()
                .setSenderId("0")
                .setSenderName("系统")
                .setContent(message)
                .setTimestamp(System.currentTimeMillis())
                .setIsPrivate(false)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.CHAT_SYSTEM)
                .setSequenceId(request.getSequenceId())
                .setTimestamp(System.currentTimeMillis())
                .setBody(chatReceive.toByteString())
                .build();

        ctx.channel().writeAndFlush(packet);
    }

    /**
     * 发送错误消息
     */
    private void sendError(ChannelHandlerContext ctx, GobangProto.Packet request, String message) {
        GobangProto.ChatReceive chatReceive = GobangProto.ChatReceive.newBuilder()
                .setSenderId("0")
                .setSenderName("系统")
                .setContent(message)
                .setTimestamp(System.currentTimeMillis())
                .setIsPrivate(false)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.CHAT_SYSTEM)
                .setSequenceId(request.getSequenceId())
                .setTimestamp(System.currentTimeMillis())
                .setBody(chatReceive.toByteString())
                .build();

        ctx.channel().writeAndFlush(packet);
    }

    @Override
    public MessageType getSupportedType() {
        return MessageType.FRIEND_REQUEST;
    }
}
