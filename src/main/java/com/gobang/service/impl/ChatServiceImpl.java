package com.gobang.service.impl;

import com.gobang.mapper.ChatMessageMapper;
import com.gobang.model.entity.ChatMessage;
import com.gobang.model.entity.User;
import com.gobang.service.ChatService;
import com.gobang.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天服务实现
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final ChatMessageMapper chatMessageMapper;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;

    public ChatServiceImpl(ChatMessageMapper chatMessageMapper,
                           UserService userService,
                           SimpMessagingTemplate messagingTemplate,
                           SimpUserRegistry userRegistry) {
        this.chatMessageMapper = chatMessageMapper;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
        this.userRegistry = userRegistry;
    }

    @Override
    public ChatMessage sendPrivateMessage(Long senderId, Long receiverId, String content) {
        logger.info("💬 发送私聊消息: senderId={}, receiverId={}, content={}", senderId, receiverId, content);

        // 调试：打印当前连接的用户
        logger.info("🔍 当前连接的用户数: {}", userRegistry.getUserCount());
        for (SimpUser user : userRegistry.getUsers()) {
            logger.info("👤 连接的用户: name={}", user.getName());
            if (user.getName() != null) {
                logger.info("   - 用户 {} 的会话数: {}", user.getName(), user.getSessions().size());
                for (var session : user.getSessions()) {
                    logger.info("     - sessionId: {}", session.getId());
                }
            }
        }

        // 创建消息
        ChatMessage message = new ChatMessage();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setMessageType(0); // 0-文字
        message.setIsRead(false);
        message.setCreatedAt(LocalDateTime.now());

        // 保存到数据库
        chatMessageMapper.insert(message);
        logger.info("💬 消息已保存到数据库: id={}", message.getId());

        // 获取发送者信息
        User sender = userService.getUserById(senderId);

        // 构建响应消息
        Map<String, Object> response = new HashMap<>();
        response.put("type", "PRIVATE_MESSAGE");
        response.put("id", message.getId());
        response.put("senderId", senderId);
        response.put("senderNickname", sender != null ? sender.getNickname() : String.valueOf(senderId));
        response.put("senderUsername", sender != null ? sender.getUsername() : "User" + senderId);
        response.put("receiverId", receiverId);
        response.put("content", content);
        response.put("messageType", 0);
        response.put("isRead", false);
        response.put("createdAt", message.getCreatedAt().toString());

        logger.info("💬 准备发送私聊消息: senderId={}, receiverId={}, content={}", senderId, receiverId, content);

        // 使用 topic 广播消息（类似房间聊天的方式）
        // 订阅路径：/topic/chat/private/{userId}
        // 这样每个用户订阅自己的 topic 就能收到消息

        // 发送给接收者
        try {
            String receiverTopic = "/topic/chat/private/" + receiverId;
            messagingTemplate.convertAndSend(receiverTopic, response);
            logger.info("✅ 消息已发送给接收者: receiverId={}, topic={}", receiverId, receiverTopic);
        } catch (Exception e) {
            logger.error("❌ 发送消息给接收者失败: receiverId={}", receiverId, e);
        }

        // 同时也发送给发送者（用于确认消息已发送）
        try {
            String senderTopic = "/topic/chat/private/" + senderId;
            messagingTemplate.convertAndSend(senderTopic, response);
            logger.info("✅ 消息已发送回发送者: senderId={}, topic={}", senderId, senderTopic);
        } catch (Exception e) {
            logger.error("❌ 发送消息回发送者失败: senderId={}", senderId, e);
        }

        return message;
    }

    @Override
    public List<ChatMessage> getPrivateChatHistory(Long userId, Long friendId, int limit) {
        logger.info("📜 获取私聊历史: userId={}, friendId={}, limit={}", userId, friendId, limit);

        List<ChatMessage> messages = chatMessageMapper.findPrivateMessages(userId, friendId, limit);

        // 补充发送者和接收者的用户信息
        for (ChatMessage message : messages) {
            User sender = userService.getUserById(message.getSenderId());
            if (sender != null) {
                // 可以在消息对象中添加额外字段，但这里我们保持简单
                // 前端可以通过senderId去获取用户信息
            }
        }

        logger.info("📜 获取到 {} 条消息", messages.size());
        return messages;
    }

    @Override
    public int getUnreadMessageCount(Long userId) {
        List<ChatMessage> unreadMessages = chatMessageMapper.findUnreadMessages(userId);
        return unreadMessages.size();
    }

    @Override
    public List<ChatMessage> getUnreadMessages(Long userId) {
        return chatMessageMapper.findUnreadMessages(userId);
    }

    @Override
    public boolean markMessagesAsRead(Long userId, Long friendId) {
        logger.info("✅ 标记消息为已读: userId={}, friendId={}", userId, friendId);

        try {
            // 标记来自特定好友的消息为已读
            chatMessageMapper.markMessagesFromUserAsRead(userId, friendId);

            logger.info("✅ 消息已标记为已读");
            return true;
        } catch (Exception e) {
            logger.error("❌ 标记消息为已读失败", e);
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> getLatestMessagesFromFriends(Long userId) {
        logger.info("📋 获取好友的最新消息: userId={}", userId);

        // 获取所有未读消息
        List<ChatMessage> unreadMessages = chatMessageMapper.findUnreadMessages(userId);

        // 按发送者分组，取最新的一条
        Map<Long, Map<String, Object>> latestMessages = new HashMap<>();

        for (ChatMessage message : unreadMessages) {
            Long senderId = message.getSenderId();

            if (!latestMessages.containsKey(senderId)) {
                User sender = userService.getUserById(senderId);

                Map<String, Object> messageData = new HashMap<>();
                messageData.put("messageId", message.getId());
                messageData.put("friendId", senderId);
                messageData.put("friendNickname", sender != null ? sender.getNickname() : "User" + senderId);
                messageData.put("friendUsername", sender != null ? sender.getUsername() : "user" + senderId);
                messageData.put("content", message.getContent());
                messageData.put("messageType", message.getMessageType());
                messageData.put("createdAt", message.getCreatedAt().toString());
                messageData.put("unreadCount", 1); // 初始化为1

                latestMessages.put(senderId, messageData);
            } else {
                // 增加未读计数
                Map<String, Object> existing = latestMessages.get(senderId);
                existing.put("unreadCount", (Integer) existing.get("unreadCount") + 1);
            }
        }

        logger.info("📋 获取到 {} 个好友的最新消息", latestMessages.size());
        return new ArrayList<>(latestMessages.values());
    }

    @Override
    public int deleteOldMessages(LocalDateTime beforeDate) {
        logger.info("🗑️ 删除旧消息: beforeDate={}", beforeDate);
        int deleted = chatMessageMapper.deleteOldMessages(beforeDate);
        logger.info("🗑️ 删除了 {} 条消息", deleted);
        return deleted;
    }
}
