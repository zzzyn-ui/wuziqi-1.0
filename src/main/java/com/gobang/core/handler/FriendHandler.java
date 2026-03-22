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
import com.gobang.service.FriendGroupService;
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
    private final FriendGroupService friendGroupService;
    private final UserService userService;
    private final FriendManager friendManager;
    private final RateLimitManager rateLimitManager;

    public FriendHandler(FriendService friendService, FriendGroupService friendGroupService, UserService userService, FriendManager friendManager, RateLimitManager rateLimitManager) {
        this.friendService = friendService;
        this.friendGroupService = friendGroupService;
        this.userService = userService;
        this.friendManager = friendManager;
        this.rateLimitManager = rateLimitManager;
    }

    public FriendHandler(FriendService friendService, FriendGroupService friendGroupService, UserService userService, FriendManager friendManager) {
        this(friendService, friendGroupService, userService, friendManager, null);
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
            case FRIEND_REMARK:
                handleFriendRemark(ctx, packet, userId);
                break;
            case FRIEND_GROUP_CREATE:
                handleFriendGroupCreate(ctx, packet, userId);
                break;
            case FRIEND_GROUP_LIST:
                handleFriendGroupList(ctx, packet, userId);
                break;
            case FRIEND_MOVE_GROUP:
                handleFriendMoveGroup(ctx, packet, userId);
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
                logger.info("Trying to get channel for target user {}: {}", targetId, targetChannel != null ? "found" : "NOT FOUND");
                if (targetChannel != null) {
                    logger.info("Target channel active: {}", targetChannel.isActive());
                }
                if (targetChannel != null && targetChannel.isActive()) {
                    ResponseUtil.sendJsonResponse(targetChannel, MessageType.FRIEND_REQUEST.getValue(), 0, bodyData);
                    logger.info("Friend request notification sent to user {} from user {}", targetId, userId);
                } else {
                    logger.info("User {} is offline, friend request saved for later", targetId);
                    logger.info("FriendManager online users: {}", friendManager.isUserOnline(targetId) ? "YES" : "NO");
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

                // 通知被删除的好友 - 使用FRIEND_REMOVE消息类型
                Channel friendChannel = friendManager.getChannel(friendId);
                if (friendChannel != null && friendChannel.isActive()) {
                    Map<String, Object> removeNotify = new HashMap<>();
                    removeNotify.put("friend_id", String.valueOf(userId));
                    removeNotify.put("friend_name", userService.getUserById(userId).getNickname());
                    ResponseUtil.sendJsonResponse(friendChannel, MessageType.FRIEND_REMOVE.getValue(),
                        0, removeNotify);
                    logger.info("Notified user {} that friend {} removed them", friendId, userId);
                }

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

            List<Map<String, Object>> friendList = new ArrayList<>();
            for (User friend : friends) {
                boolean isOnline = friendManager.isUserOnline(friend.getId());
                Map<String, Object> friendInfo = new HashMap<>();
                friendInfo.put("user_id", String.valueOf(friend.getId()));
                friendInfo.put("username", friend.getUsername());
                friendInfo.put("nickname", friend.getNickname());
                friendInfo.put("avatar", friend.getAvatar());
                friendInfo.put("rating", friend.getRating());
                friendInfo.put("online", isOnline);
                friendInfo.put("last_online", friend.getLastOnline() != null ?
                        friend.getLastOnline().toEpochSecond(java.time.ZoneOffset.of("+8")) * 1000 : 0);
                friendList.add(friendInfo);
            }

            Map<String, Object> bodyData = new HashMap<>();
            bodyData.put("friends", friendList);

            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.FRIEND_LIST.getValue(),
                packet.getSequenceId(), bodyData);

            logger.info("Friend list sent to user {}, count: {}", userId, friends.size());

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
        Map<String, Object> bodyData = new HashMap<>();
        bodyData.put("success", true);
        bodyData.put("message", message);

        ResponseUtil.sendJsonResponse(ctx.channel(), type.getValue(),
            request.getSequenceId(), bodyData);
    }

    /**
     * 发送错误消息
     */
    private void sendError(ChannelHandlerContext ctx, GobangProto.Packet request, String message) {
        Map<String, Object> bodyData = new HashMap<>();
        bodyData.put("success", false);
        bodyData.put("message", message);

        ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.CHAT_SYSTEM.getValue(),
            request.getSequenceId(), bodyData);
    }

    /**
     * 处理设置好友备注
     */
    private void handleFriendRemark(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            // 使用JSON格式处理
            com.fasterxml.jackson.databind.JsonNode body = parseJsonBody(packet);
            if (body == null) {
                sendError(ctx, packet, "消息格式错误");
                return;
            }

            Long friendId = body.has("friend_id") ? body.get("friend_id").asLong() : null;
            String remark = body.has("remark") ? body.get("remark").asText() : null;

            if (friendId == null) {
                sendError(ctx, packet, "缺少好友ID");
                return;
            }

            boolean success = friendService.setFriendRemark(userId, friendId, remark);

            Map<String, Object> bodyData = new HashMap<>();
            bodyData.put("success", success);
            bodyData.put("message", success ? "备注设置成功" : "设置失败");

            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.FRIEND_REMARK.getValue(),
                packet.getSequenceId(), bodyData);

            logger.info("User {} set remark for friend {}: {}", userId, friendId, remark);

        } catch (Exception e) {
            logger.error("Error handling friend remark", e);
            sendError(ctx, packet, "设置备注失败");
        }
    }

    /**
     * 处理获取分组列表
     */
    private void handleFriendGroupList(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            List<com.gobang.model.entity.FriendGroup> groups = friendGroupService.getUserGroups(userId);

            // 转换为前端需要的格式
            List<Map<String, Object>> groupData = new ArrayList<>();
            for (com.gobang.model.entity.FriendGroup group : groups) {
                Map<String, Object> groupInfo = new HashMap<>();
                groupInfo.put("id", group.getId());
                groupInfo.put("group_name", group.getGroupName());
                groupInfo.put("sort_order", group.getSortOrder());
                groupData.add(groupInfo);
            }

            Map<String, Object> bodyData = new HashMap<>();
            bodyData.put("success", true);
            bodyData.put("groups", groupData);

            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.FRIEND_GROUP_LIST.getValue(),
                packet.getSequenceId(), bodyData);

            logger.info("User {} retrieved {} groups", userId, groups.size());

        } catch (Exception e) {
            logger.error("Error handling friend group list", e);
            sendError(ctx, packet, "获取分组列表失败");
        }
    }

    /**
     * 处理创建好友分组
     */
    private void handleFriendGroupCreate(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            com.fasterxml.jackson.databind.JsonNode body = parseJsonBody(packet);
            if (body == null) {
                sendError(ctx, packet, "消息格式错误");
                return;
            }

            String groupName = body.has("group_name") ? body.get("group_name").asText() : null;
            if (groupName == null || groupName.trim().isEmpty()) {
                sendError(ctx, packet, "分组名称不能为空");
                return;
            }

            groupName = groupName.trim();
            if (groupName.length() > 20) {
                sendError(ctx, packet, "分组名称不能超过20个字符");
                return;
            }

            try {
                com.gobang.model.entity.FriendGroup group = friendGroupService.createGroup(userId, groupName);

                Map<String, Object> bodyData = new HashMap<>();
                bodyData.put("success", true);
                bodyData.put("message", "分组创建成功");
                bodyData.put("group", Map.of(
                    "id", group.getId(),
                    "group_name", group.getGroupName(),
                    "sort_order", group.getSortOrder()
                ));

                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.FRIEND_GROUP_CREATE.getValue(),
                    packet.getSequenceId(), bodyData);

                logger.info("User {} created group: {}", userId, groupName);

            } catch (IllegalArgumentException e) {
                sendError(ctx, packet, e.getMessage());
            }

        } catch (Exception e) {
            logger.error("Error handling friend group create", e);
            sendError(ctx, packet, "创建分组失败");
        }
    }

    /**
     * 处理移动好友到分组
     */
    private void handleFriendMoveGroup(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            com.fasterxml.jackson.databind.JsonNode body = parseJsonBody(packet);
            if (body == null) {
                sendError(ctx, packet, "消息格式错误");
                return;
            }

            Long friendId = body.has("friend_id") ? body.get("friend_id").asLong() : null;
            Integer groupId = body.has("group_id") ? body.get("group_id").asInt() : null;

            if (friendId == null) {
                sendError(ctx, packet, "缺少好友ID");
                return;
            }

            boolean success = friendService.moveFriendToGroup(userId, friendId, groupId);

            Map<String, Object> bodyData = new HashMap<>();
            bodyData.put("success", success);
            bodyData.put("message", success ? "移动成功" : "移动失败");

            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.FRIEND_MOVE_GROUP.getValue(),
                packet.getSequenceId(), bodyData);

            logger.info("User {} moved friend {} to group {}", userId, friendId, groupId);

        } catch (Exception e) {
            logger.error("Error handling friend move group", e);
            sendError(ctx, packet, "移动分组失败");
        }
    }

    /**
     * 解析JSON格式的body
     */
    private com.fasterxml.jackson.databind.JsonNode parseJsonBody(GobangProto.Packet packet) {
        try {
            byte[] bodyBytes = packet.getBody().toByteArray();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readTree(bodyBytes);
        } catch (Exception e) {
            logger.error("Failed to parse JSON body", e);
            return null;
        }
    }

    @Override
    public MessageType getSupportedType() {
        return MessageType.FRIEND_REQUEST;
    }
}
