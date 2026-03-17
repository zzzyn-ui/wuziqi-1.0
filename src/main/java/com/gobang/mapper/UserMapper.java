package com.gobang.mapper;

import com.gobang.model.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户Mapper
 */
@Mapper
public interface UserMapper {

    /**
     * 根据ID查询用户
     */
    @Select("SELECT * FROM user WHERE id = #{userId}")
    User findById(@Param("userId") Long userId);

    /**
     * 根据用户名查询用户
     */
    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    /**
     * 插入用户
     */
    @Insert("INSERT INTO user (username, password, nickname, email, avatar, rating, level, exp, created_at, status) " +
            "VALUES (#{username}, #{password}, #{nickname}, #{email}, #{avatar}, #{rating}, #{level}, #{exp}, #{createdAt}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(User user);

    /**
     * 更新用户
     */
    @Update("UPDATE user SET nickname = #{nickname}, email = #{email}, avatar = #{avatar}, " +
            "rating = #{rating}, level = #{level}, exp = #{exp}, last_online = #{lastOnline}, status = #{status} " +
            "WHERE id = #{id}")
    int update(User user);

    /**
     * 更新用户积分
     */
    @Update("UPDATE user SET rating = #{rating}, level = #{level}, exp = #{exp} WHERE id = #{id}")
    int updateRating(@Param("id") Long id, @Param("rating") Integer rating,
                     @Param("level") Integer level, @Param("exp") Integer exp);

    /**
     * 更新用户状态
     */
    @Update("UPDATE user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 更新最后在线时间
     */
    @Update("UPDATE user SET last_online = #{lastOnline} WHERE id = #{id}")
    int updateLastOnline(@Param("id") Long id, @Param("lastOnline") java.time.LocalDateTime lastOnline);

    /**
     * 获取排行榜 - 按积分降序
     */
    @Select("SELECT * FROM user ORDER BY rating DESC LIMIT #{limit}")
    List<User> getLeaderboard(@Param("limit") int limit);

    /**
     * 获取在线用户排行榜
     */
    @Select("SELECT * FROM user WHERE status > 0 ORDER BY rating DESC LIMIT #{limit}")
    List<User> getOnlineLeaderboard(@Param("limit") int limit);

    /**
     * 统计在线用户数量
     */
    @Select("SELECT COUNT(*) FROM user WHERE status > 0")
    Integer countOnlineUsers();

    /**
     * 统计用户总数
     */
    @Select("SELECT COUNT(*) FROM user")
    Long countUsers();
}
