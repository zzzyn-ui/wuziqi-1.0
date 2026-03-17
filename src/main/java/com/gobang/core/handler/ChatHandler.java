package com.gobang.core.handler;

import com.gobang.core.handler.AuthHandler;
import com.gobang.core.netty.ResponseUtil;
import com.gobang.core.protocol.MessageHandler;
import com.gobang.core.protocol.MessageType;
import com.gobang.core.room.RoomManager;
import com.gobang.core.security.RateLimitManager;
import com.gobang.model.entity.User;
import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.service.ChatService;
import com.gobang.service.UserService;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 聊天消息处理器
 * 处理公屏和私聊消息
 */
public class ChatHandler implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatHandler.class);

    private final ChatService chatService;
    private final UserService userService;
    private final RoomManager roomManager;
    private final RateLimitManager rateLimitManager;

    public ChatHandler(ChatService chatService, UserService userService, RoomManager roomManager, RateLimitManager rateLimitManager) {
        this.chatService = chatService;
        this.userService = userService;
        this.roomManager = roomManager;
        this.rateLimitManager = rateLimitManager;
    }

    public ChatHandler(ChatService chatService, UserService userService, RoomManager roomManager) {
        this(chatService, userService, roomManager, null);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, GobangProto.Packet packet) {
        // 验证认证
        if (!AuthHandler.isAuthenticated(ctx.channel())) {
            sendError(ctx, "请先登录");
            return;
        }

        Long userId = AuthHandler.getUserId(ctx.channel());
        if (userId == null) {
            sendError(ctx, "用户未认证");
            return;
        }

        MessageType messageType = MessageType.fromValue(packet.getType().getNumber());

        if (messageType == MessageType.CHAT_SEND) {
            handleChatSend(ctx, packet, userId);
        } else {
            logger.warn("Unsupported message type for ChatHandler: {}", messageType);
        }
    }

    /**
     * 处理发送聊天消息
     */
    private void handleChatSend(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            // 限流检查
            if (rateLimitManager != null) {
                if (!rateLimitManager.tryAcquire(RateLimitManager.LimitType.CHAT, userId)) {
                    sendError(ctx, rateLimitManager.getErrorMessage(RateLimitManager.LimitType.CHAT));
                    logger.warn("Chat rate limit exceeded for user: {}", userId);
                    return;
                }
            }

            GobangProto.ChatSend request = GobangProto.ChatSend.parseFrom(packet.getBody());

            // 获取发送者信息
            User sender = userService.getUserById(userId);
            if (sender == null) {
                sendError(ctx, "用户不存在");
                return;
            }

            // 内容长度检查
            if (request.getContent() == null || request.getContent().isEmpty()) {
                sendError(ctx, "消息内容不能为空");
                return;
            }

            if (request.getContent().length() > 500) {
                sendError(ctx, "消息内容过长");
                return;
            }

            // 敏感词过滤（简单实现）
            String content = filterContent(request.getContent());

            if (request.getTargetId().isEmpty()) {
                // 公屏消息
                handlePublicChat(ctx, userId, sender, content);
            } else {
                // 私聊消息
                handlePrivateChat(ctx, userId, sender, request.getTargetId(), content);
            }

        } catch (Exception e) {
            logger.error("Error handling chat send for user: {}", userId, e);
            sendError(ctx, "发送消息失败");
        }
    }

    /**
     * 处理公屏聊天（大厅聊天）
     */
    private void handlePublicChat(ChannelHandlerContext ctx, Long userId, User sender, String content) {
        // 保存消息
        chatService.sendPublicMessage(userId, "lobby", content);

        // 构建聊天消息
        GobangProto.ChatReceive chatReceive = GobangProto.ChatReceive.newBuilder()
                .setSenderId(String.valueOf(sender.getId()))
                .setSenderName(sender.getNickname())
                .setContent(content)
                .setTimestamp(System.currentTimeMillis())
                .setIsPrivate(false)
                .build();

        // 构建数据包
        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.CHAT_RECEIVE)
                .setSequenceId(0)
                .setTimestamp(System.currentTimeMillis())
                .setBody(chatReceive.toByteString())
                .build();

        // 发送给发送者（回显）- 使用JSON格式
        ResponseUtil.sendResponse(ctx.channel(), packet, new ResponseUtil.ChatReceiveJsonBuilder(chatReceive));

        logger.debug("Public chat from user {}: {}", userId, content);
    }

    /**
     * 处理私聊
     */
    private void handlePrivateChat(ChannelHandlerContext ctx, Long userId, User sender,
                                   String targetIdStr, String content) {
        try {
            Long targetId = Long.parseLong(targetIdStr);

            // 不能给自己发私聊
            if (targetId.equals(userId)) {
                sendError(ctx, "不能给自己发送私聊");
                return;
            }

            // 保存消息
            chatService.sendPrivateMessage(userId, targetId, content);

            // 构建消息
            GobangProto.ChatReceive chatReceive = GobangProto.ChatReceive.newBuilder()
                    .setSenderId(String.valueOf(sender.getId()))
                    .setSenderName(sender.getNickname())
                    .setContent(content)
                    .setTimestamp(System.currentTimeMillis())
                    .setIsPrivate(true)
                    .build();

            GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                    .setType(GobangProto.MessageType.CHAT_RECEIVE)
                    .setTimestamp(System.currentTimeMillis())
                    .setBody(chatReceive.toByteString())
                    .build();

            // 发送给接收者
            // 这里需要通过channelManager发送，暂时留空
            // 如果接收者在线，发送消息；否则存为未读

            logger.debug("Private chat from user {} to user {}: {}", userId, targetId, content);

        } catch (NumberFormatException e) {
            sendError(ctx, "目标用户ID格式错误");
        }
    }

    /**
     * 内容过滤（简单敏感词过滤）
     */
    private String filterContent(String content) {
        // 简单实现，实际应使用更完善的过滤系统
        String[] bannedWords = {"脏话", "辱骂"};
        String result = content;
        for (String word : bannedWords) {
            result = result.replace(word, "***");
        }
        return result;
    }

    /**
     * 发送系统消息
     */
    public void sendSystemMessage(ChannelHandlerContext ctx, String message) {
        GobangProto.ChatReceive chatReceive = GobangProto.ChatReceive.newBuilder()
                .setSenderId("0")
                .setSenderName("系统")
                .setContent(message)
                .setTimestamp(System.currentTimeMillis())
                .setIsPrivate(false)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.CHAT_SYSTEM)
                .setSequenceId(0)
                .setTimestamp(System.currentTimeMillis())
                .setBody(chatReceive.toByteString())
                .build();

        // 使用ResponseUtil发送JSON格式的响应
        ResponseUtil.sendResponse(ctx.channel(), packet, new ResponseUtil.ChatReceiveJsonBuilder(chatReceive));
    }

    /**
     * 发送错误消息
     */
    private void sendError(ChannelHandlerContext ctx, String message) {
        GobangProto.ChatReceive chatReceive = GobangProto.ChatReceive.newBuilder()
                .setSenderId("0")
                .setSenderName("系统")
                .setContent(message)
                .setTimestamp(System.currentTimeMillis())
                .setIsPrivate(false)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.CHAT_SYSTEM)
                .setSequenceId(0)
                .setTimestamp(System.currentTimeMillis())
                .setBody(chatReceive.toByteString())
                .build();

        // 使用ResponseUtil发送JSON格式的响应
        ResponseUtil.sendResponse(ctx.channel(), packet, new ResponseUtil.ChatReceiveJsonBuilder(chatReceive));
    }

    @Override
    public MessageType getSupportedType() {
        return MessageType.CHAT_SEND;
    }
}
