package com.gobang.core.social;

import com.gobang.protocol.protobuf.GobangProto;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天管理器
 * 处理游戏内聊天功能
 */
public class ChatManager {

    private static final Logger logger = LoggerFactory.getLogger(ChatManager.class);
    private static final io.netty.util.AttributeKey<Long> USER_ID_KEY = io.netty.util.AttributeKey.valueOf("userId");

    // 公屏聊天：每个房间的在线玩家
    private final Map<String, Channel> roomChannels = new ConcurrentHashMap<>();

    // 私聊：用户ID -> Channel
    private final Map<Long, Channel> userChannels = new ConcurrentHashMap<>();

    // 未读消息计数
    private final Map<Long, Integer> unreadCounts = new ConcurrentHashMap<>();

    public void addRoomChannel(String roomId, Channel channel) {
        roomChannels.put(roomId + ":" + getChannelUserId(channel), channel);
    }

    public void removeRoomChannel(String roomId, Channel channel) {
        roomChannels.remove(roomId + ":" + getChannelUserId(channel));
    }

    public void addUserChannel(Long userId, Channel channel) {
        userChannels.put(userId, channel);
    }

    public void removeUserChannel(Long userId) {
        userChannels.remove(userId);
    }

    /**
     * 发送公屏聊天消息
     */
    public void sendPublicChat(String roomId, Long senderId, String senderName, String content) {
        GobangProto.ChatReceive chatMsg = GobangProto.ChatReceive.newBuilder()
                .setSenderId(String.valueOf(senderId))
                .setSenderName(senderName)
                .setContent(content)
                .setTimestamp(System.currentTimeMillis())
                .setIsPrivate(false)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.CHAT_RECEIVE)
                .setTimestamp(System.currentTimeMillis())
                .setBody(chatMsg.toByteString())
                .build();

        // 广播给房间内所有人
        for (Map.Entry<String, Channel> entry : roomChannels.entrySet()) {
            if (entry.getKey().startsWith(roomId + ":")) {
                Channel ch = entry.getValue();
                if (ch.isActive()) {
                    ch.writeAndFlush(packet);
                }
            }
        }

        logger.debug("Public chat in room {}: {}", roomId, content);
    }

    /**
     * 发送私聊消息
     */
    public boolean sendPrivateChat(Long senderId, String senderName, Long receiverId, String content) {
        Channel receiverChannel = userChannels.get(receiverId);
        if (receiverChannel == null || !receiverChannel.isActive()) {
            // 接收者不在线，增加未读计数
            unreadCounts.merge(receiverId, 1, Integer::sum);
            return false;
        }

        GobangProto.ChatReceive chatMsg = GobangProto.ChatReceive.newBuilder()
                .setSenderId(String.valueOf(senderId))
                .setSenderName(senderName)
                .setContent(content)
                .setTimestamp(System.currentTimeMillis())
                .setIsPrivate(true)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.CHAT_RECEIVE)
                .setTimestamp(System.currentTimeMillis())
                .setBody(chatMsg.toByteString())
                .build();

        receiverChannel.writeAndFlush(packet);

        logger.debug("Private chat from {} to {}: {}", senderId, receiverId, content);
        return true;
    }

    /**
     * 发送系统消息
     */
    public void sendSystemMessage(String roomId, String message) {
        GobangProto.ChatReceive chatMsg = GobangProto.ChatReceive.newBuilder()
                .setSenderId("0")
                .setSenderName("系统")
                .setContent(message)
                .setTimestamp(System.currentTimeMillis())
                .setIsPrivate(false)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.CHAT_SYSTEM)
                .setTimestamp(System.currentTimeMillis())
                .setBody(chatMsg.toByteString())
                .build();

        if (roomId != null) {
            // 发送给房间
            for (Map.Entry<String, Channel> entry : roomChannels.entrySet()) {
                if (entry.getKey().startsWith(roomId + ":")) {
                    Channel ch = entry.getValue();
                    if (ch.isActive()) {
                        ch.writeAndFlush(packet);
                    }
                }
            }
        }

        logger.debug("System message in room {}: {}", roomId, message);
    }

    /**
     * 获取未读消息数
     */
    public int getUnreadCount(Long userId) {
        return unreadCounts.getOrDefault(userId, 0);
    }

    /**
     * 清除未读消息数
     */
    public void clearUnreadCount(Long userId) {
        unreadCounts.remove(userId);
    }

    private Long getChannelUserId(Channel channel) {
        // 从channel的attribute中获取userId
        return channel.attr(USER_ID_KEY).get();
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        roomChannels.clear();
        userChannels.clear();
        unreadCounts.clear();
    }
}
