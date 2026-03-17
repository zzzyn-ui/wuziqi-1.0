package com.gobang.service;

import com.gobang.mapper.ChatMessageMapper;
import com.gobang.model.entity.ChatMessage;
import com.gobang.util.SensitiveWordFilter;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天服务
 */
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final SqlSessionFactory sqlSessionFactory;
    private final SensitiveWordFilter sensitiveWordFilter;

    public ChatService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.sensitiveWordFilter = new SensitiveWordFilter();
    }

    /**
     * 发送公屏消息
     */
    public ChatMessage sendPublicMessage(Long senderId, String roomId, String content) {
        // 过滤敏感词
        String filteredContent = sensitiveWordFilter.filter(content);
        boolean wasFiltered = !filteredContent.equals(content);

        if (wasFiltered) {
            logger.debug("Message filtered for user {} in room {}", senderId, roomId);
        }

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            ChatMessageMapper chatMessageMapper = session.getMapper(ChatMessageMapper.class);
            ChatMessage message = new ChatMessage(senderId, filteredContent);
            message.setRoomId(roomId);
            message.setCreatedAt(LocalDateTime.now());

            chatMessageMapper.insert(message);
            logger.debug("Public message saved in room {}", roomId);
            return message;
        }
    }

    /**
     * 发送私聊消息
     */
    public ChatMessage sendPrivateMessage(Long senderId, Long receiverId, String content) {
        // 过滤敏感词
        String filteredContent = sensitiveWordFilter.filter(content);
        boolean wasFiltered = !filteredContent.equals(content);

        if (wasFiltered) {
            logger.debug("Private message filtered from {} to {}", senderId, receiverId);
        }

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            ChatMessageMapper chatMessageMapper = session.getMapper(ChatMessageMapper.class);
            ChatMessage message = new ChatMessage(senderId, filteredContent);
            message.setReceiverId(receiverId);
            message.setCreatedAt(LocalDateTime.now());

            chatMessageMapper.insert(message);
            logger.debug("Private message from {} to {}", senderId, receiverId);
            return message;
        }
    }

    /**
     * 检查消息是否包含敏感词
     */
    public boolean containsSensitiveWord(String content) {
        return sensitiveWordFilter.contains(content);
    }

    /**
     * 获取消息中的敏感词列表
     */
    public java.util.Set<String> getSensitiveWords(String content) {
        return sensitiveWordFilter.getSensitiveWords(content);
    }

    /**
     * 获取房间消息历史
     */
    public List<ChatMessage> getRoomMessages(String roomId, int limit) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            ChatMessageMapper chatMessageMapper = session.getMapper(ChatMessageMapper.class);
            return chatMessageMapper.findRoomMessages(roomId, limit);
        }
    }

    /**
     * 获取私聊消息历史
     */
    public List<ChatMessage> getPrivateMessages(Long userId1, Long userId2, int limit) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            ChatMessageMapper chatMessageMapper = session.getMapper(ChatMessageMapper.class);
            return chatMessageMapper.findPrivateMessages(userId1, userId2, limit);
        }
    }

    /**
     * 获取未读消息
     */
    public List<ChatMessage> getUnreadMessages(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            ChatMessageMapper chatMessageMapper = session.getMapper(ChatMessageMapper.class);
            return chatMessageMapper.findUnreadMessages(userId);
        }
    }

    /**
     * 标记消息为已读
     */
    public void markAsRead(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            ChatMessageMapper chatMessageMapper = session.getMapper(ChatMessageMapper.class);
            chatMessageMapper.markAsRead(userId);
        }
    }

    /**
     * 清理旧消息
     */
    public void cleanupOldMessages(int daysToKeep) {
        LocalDateTime beforeDate = LocalDateTime.now().minusDays(daysToKeep);
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            ChatMessageMapper chatMessageMapper = session.getMapper(ChatMessageMapper.class);
            int deleted = chatMessageMapper.deleteOldMessages(beforeDate);
            logger.info("Cleaned up {} old chat messages", deleted);
        }
    }
}
