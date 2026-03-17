package com.gobang.mapper;

import com.gobang.model.entity.Friend;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 好友关系Mapper
 */
@Mapper
public interface FriendMapper {

    /**
     * 插入好友关系
     */
    @Insert("INSERT INTO friend (user_id, friend_id, status, request_message) " +
            "VALUES (#{userId}, #{friendId}, #{status}, #{requestMessage})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Friend friend);

    /**
     * 查询用户的好友列表
     */
    @Select("SELECT * FROM friend WHERE user_id = #{userId} AND status = 1")
    List<Friend> findFriendsByUserId(@Param("userId") Long userId);

    /**
     * 查询好友请求
     */
    @Select("SELECT * FROM friend WHERE friend_id = #{userId} AND status = 0")
    List<Friend> findPendingRequests(@Param("userId") Long userId);

    /**
     * 查询特定好友关系
     */
    @Select("SELECT * FROM friend WHERE user_id = #{userId} AND friend_id = #{friendId}")
    Friend findByUserAndFriend(@Param("userId") Long userId, @Param("friendId") Long friendId);

    /**
     * 根据ID查询好友关系
     */
    @Select("SELECT * FROM friend WHERE id = #{id}")
    Friend findById(@Param("id") Long id);

    /**
     * 更新好友状态
     */
    @Update("UPDATE friend SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") int status);

    /**
     * 删除好友关系
     */
    @Delete("DELETE FROM friend WHERE user_id = #{userId} AND friend_id = #{friendId}")
    int delete(@Param("userId") Long userId, @Param("friendId") Long friendId);

    /**
     * 检查是否已是好友
     */
    @Select("SELECT COUNT(*) FROM friend WHERE user_id = #{userId} AND friend_id = #{friendId} AND status = 1")
    int isFriend(@Param("userId") Long userId, @Param("friendId") Long friendId);
}
