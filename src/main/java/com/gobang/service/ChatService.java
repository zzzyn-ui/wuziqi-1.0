package com.gobang.service;

import com.gobang.model.entity.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * 聊天服务接口
 */
public interface ChatService {

    /**
     * 发送私聊消息
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @param content 消息内容
     * @return 消息实体
     */
    ChatMessage sendPrivateMessage(Long senderId, Long receiverId, String content);

    /**
     * 获取与某个好友的聊天历史
     * @param userId 当前用户ID
     * @param friendId 好友ID
     * @param limit 获取消息数量
     * @return 消息列表
     */
    List<ChatMessage> getPrivateChatHistory(Long userId, Long friendId, int limit);

    /**
     * 获取未读消息数量
     * @param userId 用户ID
     * @return 未读消息数量
     */
    int getUnreadMessageCount(Long userId);

    /**
     * 获取未读消息列表
     * @param userId 用户ID
     * @return 未读消息列表
     */
    List<ChatMessage> getUnreadMessages(Long userId);

    /**
     * 标记与某个好友的消息为已读
     * @param userId 当前用户ID
     * @param friendId 好友ID
     * @return 是否成功
     */
    boolean markMessagesAsRead(Long userId, Long friendId);

    /**
     * 获取所有好友的最新消息（用于显示聊天列表）
     * @param userId 用户ID
     * @return 每个好友的最新消息列表
     */
    List<Map<String, Object>> getLatestMessagesFromFriends(Long userId);

    /**
     * 删除旧消息
     * @param beforeDate 删除此日期之前的消息
     * @return 删除的记录数
     */
    int deleteOldMessages(java.time.LocalDateTime beforeDate);
}
