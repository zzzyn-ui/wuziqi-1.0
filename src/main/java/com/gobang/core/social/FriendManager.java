package com.gobang.core.social;

import com.gobang.protocol.protobuf.GobangProto;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 好友管理器
 * 处理好友在线状态和通知
 */
public class FriendManager {

    private static final Logger logger = LoggerFactory.getLogger(FriendManager.class);

    // 在线用户：userId -> Channel
    private final Map<Long, Channel> onlineUsers = new ConcurrentHashMap<>();

    // 用户的好友列表：userId -> Set<friendId>
    private final Map<Long, Set<Long>> userFriends = new ConcurrentHashMap<>();

    public void userOnline(Long userId, Channel channel) {
        onlineUsers.put(userId, channel);

        // 通知好友该用户上线
        notifyFriendsStatusChange(userId, true);
    }

    public void userOffline(Long userId) {
        onlineUsers.remove(userId);

        // 通知好友该用户离线
        notifyFriendsStatusChange(userId, false);
    }

    /**
     * 加载用户的好友列表
     */
    public void loadUserFriends(Long userId, List<Long> friendIds) {
        Set<Long> friends = new HashSet<>(friendIds);
        userFriends.put(userId, friends);
    }

    /**
     * 添加好友关系
     */
    public void addFriendship(Long userId, Long friendId) {
        userFriends.computeIfAbsent(userId, k -> new HashSet<>()).add(friendId);
        userFriends.computeIfAbsent(friendId, k -> new HashSet<>()).add(userId);
    }

    /**
     * 移除好友关系
     */
    public void removeFriendship(Long userId, Long friendId) {
        Set<Long> friends1 = userFriends.get(userId);
        if (friends1 != null) {
            friends1.remove(friendId);
        }

        Set<Long> friends2 = userFriends.get(friendId);
        if (friends2 != null) {
            friends2.remove(userId);
        }
    }

    /**
     * 通知好友状态变化
     */
    private void notifyFriendsStatusChange(Long userId, boolean online) {
        Set<Long> friends = userFriends.get(userId);
        if (friends == null || friends.isEmpty()) {
            return;
        }

        GobangProto.Packet.Builder packetBuilder = GobangProto.Packet.newBuilder()
                .setTimestamp(System.currentTimeMillis());

        if (online) {
            packetBuilder.setType(GobangProto.MessageType.FRIEND_ONLINE);
            // 这里需要获取用户详细信息
        } else {
            packetBuilder.setType(GobangProto.MessageType.FRIEND_OFFLINE)
                    .setBody(GobangProto.FriendOffline.newBuilder()
                            .setFriendId(String.valueOf(userId))
                            .build()
                            .toByteString());
        }

        GobangProto.Packet packet = packetBuilder.build();

        // 通知所有在线的好友
        for (Long friendId : friends) {
            Channel channel = onlineUsers.get(friendId);
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(packet);
            }
        }

        logger.debug("Notified friends that user {} is {}", userId, online ? "online" : "offline");
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(Long userId) {
        return onlineUsers.containsKey(userId);
    }

    /**
     * 获取用户在线好友列表
     */
    public List<Long> getOnlineFriends(Long userId) {
        Set<Long> friends = userFriends.get(userId);
        if (friends == null) {
            return Collections.emptyList();
        }

        List<Long> onlineFriends = new ArrayList<>();
        for (Long friendId : friends) {
            if (onlineUsers.containsKey(friendId)) {
                onlineFriends.add(friendId);
            }
        }

        return onlineFriends;
    }

    /**
     * 向用户发送消息
     */
    public boolean sendToUser(Long userId, GobangProto.Packet packet) {
        Channel channel = onlineUsers.get(userId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(packet);
            return true;
        }
        return false;
    }

    /**
     * 获取用户的Channel
     */
    public Channel getChannel(Long userId) {
        return onlineUsers.get(userId);
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        onlineUsers.clear();
        userFriends.clear();
    }
}
