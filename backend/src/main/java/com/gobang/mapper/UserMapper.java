package com.gobang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gobang.model.entity.User;
import org.apache.ibatis.annotations.*;

/**
 * 用户Mapper
 * 使用 MyBatis-Plus BaseMapper 提供基础CRUD操作
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查询用户
     * 使用 MyBatis-Plus 的 LambdaQueryWrapper 也可以实现，但注解方式更直观
     */
    @Select("SELECT * FROM user WHERE username = #{username} AND deleted = 0")
    User findByUsername(@Param("username") String username);

    /**
     * 更新用户积分
     * 使用 MyBatis-Plus 的 UpdateWrapper 也可以实现
     */
    @Update("UPDATE user SET rating = #{rating}, updated_at = NOW() WHERE id = #{id}")
    int updateRating(@Param("id") Long id, @Param("rating") Integer rating);

    /**
     * 增加用户经验值
     * exp 参数可以是正数（增加）或负数（减少）
     */
    @Update("UPDATE user SET exp = exp + #{exp}, updated_at = NOW() WHERE id = #{id}")
    int addExp(@Param("id") Long id, @Param("exp") Integer exp);

    /**
     * 更新用户状态
     * status: 0=离线, 1=在线, 2=游戏中, 3=匹配中
     */
    @Update("UPDATE user SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 更新最后在线时间
     */
    @Update("UPDATE user SET last_online = #{lastOnline}, updated_at = NOW() WHERE id = #{id}")
    int updateLastOnline(@Param("id") Long id, @Param("lastOnline") java.time.LocalDateTime lastOnline);

    /**
     * 获取排行榜 - 按积分降序
     */
    @Select("SELECT * FROM user WHERE deleted = 0 ORDER BY rating DESC LIMIT #{limit}")
    java.util.List<User> getLeaderboard(@Param("limit") int limit);

    /**
     * 获取在线用户排行榜
     */
    @Select("SELECT * FROM user WHERE status > 0 AND deleted = 0 ORDER BY rating DESC LIMIT #{limit}")
    java.util.List<User> getOnlineLeaderboard(@Param("limit") int limit);

    /**
     * 统计在线用户数量
     */
    @Select("SELECT COUNT(*) FROM user WHERE status > 0 AND deleted = 0")
    Integer countOnlineUsers();
}
