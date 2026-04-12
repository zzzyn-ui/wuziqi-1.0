package com.gobang.mapper;

import com.gobang.model.entity.UserActivityLog;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户活动日志Mapper
 */
@Mapper
public interface UserActivityLogMapper {

    /**
     * 插入活动日志
     */
    @Insert("INSERT INTO user_activity_log (user_id, activity_type, ip_address, user_agent, activity_data) " +
            "VALUES (#{userId}, #{activityType}, #{ipAddress}, #{userAgent}, #{activityData})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserActivityLog log);

    /**
     * 根据用户ID查询活动日志
     */
    @Select("SELECT * FROM user_activity_log WHERE user_id = #{userId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<UserActivityLog> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 根据活动类型查询日志
     */
    @Select("SELECT * FROM user_activity_log WHERE activity_type = #{activityType} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<UserActivityLog> findByActivityType(@Param("activityType") String activityType, @Param("limit") int limit);

    /**
     * 查询用户在指定时间范围的日志
     */
    @Select("SELECT * FROM user_activity_log WHERE user_id = #{userId} " +
            "AND created_at BETWEEN #{startTime} AND #{endTime} " +
            "ORDER BY created_at DESC")
    List<UserActivityLog> findByUserIdAndTimeRange(
        @Param("userId") Long userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );

    /**
     * 统计用户活动次数
     */
    @Select("SELECT COUNT(*) FROM user_activity_log WHERE user_id = #{userId} AND activity_type = #{activityType}")
    int countByUserAndActivity(@Param("userId") Long userId, @Param("activityType") String activityType);

    /**
     * 删除指定时间之前的日志
     */
    @Delete("DELETE FROM user_activity_log WHERE created_at < #{beforeDate}")
    int deleteBeforeDate(@Param("beforeDate") LocalDateTime beforeDate);

    /**
     * 获取最近的登录记录
     */
    @Select("SELECT * FROM user_activity_log WHERE user_id = #{userId} AND activity_type = 'login' " +
            "ORDER BY created_at DESC LIMIT 1")
    UserActivityLog findLastLogin(@Param("userId") Long userId);
}
