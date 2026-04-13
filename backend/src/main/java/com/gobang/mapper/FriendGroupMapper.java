package com.gobang.mapper;

import com.gobang.model.entity.FriendGroup;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 好友分组 Mapper
 */
@Mapper
public interface FriendGroupMapper {

    /**
     * 根据用户ID获取分组列表
     */
    @Select("SELECT * FROM friend_group WHERE user_id = #{userId} ORDER BY sort_order ASC, id ASC")
    List<FriendGroup> getByUserId(@Param("userId") Long userId);

    /**
     * 根据ID获取分组
     */
    @Select("SELECT * FROM friend_group WHERE id = #{id}")
    FriendGroup getById(@Param("id") Integer id);

    /**
     * 根据用户ID和分组名获取分组
     */
    @Select("SELECT * FROM friend_group WHERE user_id = #{userId} AND group_name = #{groupName}")
    FriendGroup getByUserIdAndName(@Param("userId") Long userId, @Param("groupName") String groupName);

    /**
     * 创建分组
     */
    @Insert("INSERT INTO friend_group (user_id, group_name, sort_order) VALUES (#{userId}, #{groupName}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FriendGroup friendGroup);

    /**
     * 更新分组名
     */
    @Update("UPDATE friend_group SET group_name = #{groupName}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateName(FriendGroup friendGroup);

    /**
     * 更新排序
     */
    @Update("UPDATE friend_group SET sort_order = #{sortOrder}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateSort(FriendGroup friendGroup);

    /**
     * 删除分组
     */
    @Delete("DELETE FROM friend_group WHERE id = #{id}")
    int delete(@Param("id") Integer id);

    /**
     * 获取用户的最大排序值
     */
    @Select("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM friend_group WHERE user_id = #{userId}")
    Integer getNextSortOrder(@Param("userId") Long userId);
}
