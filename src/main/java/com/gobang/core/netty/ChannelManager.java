package com.gobang.core.netty;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接管理器
 * 管理用户ID与Channel的映射关系
 */
public class ChannelManager {

    private static final Logger logger = LoggerFactory.getLogger(ChannelManager.class);

    // userId -> Channel
    private final Map<Long, Channel> userChannels = new ConcurrentHashMap<>();

    // Channel -> userId
    private final Map<Channel, Long> channelUsers = new ConcurrentHashMap<>();

    /**
     * 添加用户连接
     */
    public void addChannel(Long userId, Channel channel) {
        // 移除旧连接
        Channel oldChannel = userChannels.get(userId);
        if (oldChannel != null && oldChannel.isActive()) {
            oldChannel.close();
        }

        userChannels.put(userId, channel);
        channelUsers.put(channel, userId);

        logger.debug("Channel added for user: {}", userId);
    }

    /**
     * 移除用户连接
     */
    public void removeChannel(Channel channel) {
        Long userId = channelUsers.remove(channel);
        if (userId != null) {
            userChannels.remove(userId);
            logger.debug("Channel removed for user: {}", userId);
        }
    }

    /**
     * 获取用户的Channel
     */
    public Channel getChannel(Long userId) {
        return userChannels.get(userId);
    }

    /**
     * 获取Channel对应的用户ID
     */
    public Long getUserId(Channel channel) {
        return channelUsers.get(channel);
    }

    /**
     * 检查用户是否在线
     */
    public boolean isOnline(Long userId) {
        Channel channel = userChannels.get(userId);
        return channel != null && channel.isActive();
    }

    /**
     * 向用户发送消息
     */
    public boolean sendToUser(Long userId, Object msg) {
        Channel channel = userChannels.get(userId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(msg);
            return true;
        }
        return false;
    }

    /**
     * 获取在线用户数量
     */
    public int getOnlineCount() {
        int count = 0;
        for (Channel channel : userChannels.values()) {
            if (channel.isActive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取所有活跃的Channel
     */
    public Iterable<Channel> getAllChannels() {
        return userChannels.values();
    }

    /**
     * 清理所有连接
     */
    public void clear() {
        for (Channel channel : userChannels.values()) {
            if (channel.isActive()) {
                channel.close();
            }
        }
        userChannels.clear();
        channelUsers.clear();
    }
}
