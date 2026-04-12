package com.gobang.mapper;

import com.gobang.model.entity.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 聊天消息Mapper
 */
@Mapper
public interface ChatMessageMapper {

    /**
     * 插入消息
     */
    @Insert("INSERT INTO chat_message (sender_id, receiver_id, room_id, content, message_type, is_read) " +
            "VALUES (#{senderId}, #{receiverId}, #{roomId}, #{content}, #{messageType}, #{isRead})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessage message);

    /**
     * 查询私聊消息
     */
    @Select("SELECT * FROM chat_message WHERE " +
            "((sender_id = #{userId1} AND receiver_id = #{userId2}) OR " +
            "(sender_id = #{userId2} AND receiver_id = #{userId1})) " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<ChatMessage> findPrivateMessages(@Param("userId1") Long userId1,
                                           @Param("userId2") Long userId2,
                                           @Param("limit") int limit);

    /**
     * 查询房间公屏消息
     */
    @Select("SELECT * FROM chat_message WHERE room_id = #{roomId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<ChatMessage> findRoomMessages(@Param("roomId") String roomId, @Param("limit") int limit);

    /**
     * 查询未读消息
     */
    @Select("SELECT * FROM chat_message WHERE receiver_id = #{userId} AND is_read = 0 " +
            "ORDER BY created_at DESC")
    List<ChatMessage> findUnreadMessages(@Param("userId") Long userId);

    /**
     * 标记消息为已读
     */
    @Update("UPDATE chat_message SET is_read = 1 WHERE receiver_id = #{userId} AND is_read = 0")
    int markAsRead(@Param("userId") Long userId);

    /**
     * 标记来自特定用户的消息为已读
     */
    @Update("UPDATE chat_message SET is_read = 1 WHERE receiver_id = #{userId} AND sender_id = #{senderId} AND is_read = 0")
    int markMessagesFromUserAsRead(@Param("userId") Long userId, @Param("senderId") Long senderId);

    /**
     * 删除旧消息
     */
    @Delete("DELETE FROM chat_message WHERE created_at < #{beforeDate}")
    int deleteOldMessages(@Param("beforeDate") java.time.LocalDateTime beforeDate);
}
