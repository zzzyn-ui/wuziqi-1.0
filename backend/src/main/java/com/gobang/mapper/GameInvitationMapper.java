package com.gobang.mapper;

import com.gobang.model.entity.GameInvitation;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 游戏邀请Mapper
 */
@Mapper
public interface GameInvitationMapper {

    /**
     * 插入邀请
     */
    @Insert("INSERT INTO game_invitation (inviter_id, invitee_id, invitation_type, status, expires_at) " +
            "VALUES (#{inviterId}, #{inviteeId}, #{invitationType}, #{status}, #{expiresAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GameInvitation invitation);

    /**
     * 根据ID查询
     */
    @Select("SELECT * FROM game_invitation WHERE id = #{id}")
    GameInvitation findById(@Param("id") Long id);

    /**
     * 查询用户的待处理邀请
     */
    @Select("SELECT * FROM game_invitation WHERE invitee_id = #{userId} AND status = 'pending' " +
            "AND expires_at > NOW() ORDER BY created_at DESC")
    List<GameInvitation> findPendingInvitations(@Param("userId") Long userId);

    /**
     * 查询用户发送的邀请
     */
    @Select("SELECT * FROM game_invitation WHERE inviter_id = #{userId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<GameInvitation> findByInviterId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 更新邀请状态
     */
    @Update("UPDATE game_invitation SET status = #{status}, room_id = #{roomId}, " +
            "responded_at = #{respondedAt} WHERE id = #{id}")
    int updateStatus(
        @Param("id") Long id,
        @Param("status") String status,
        @Param("roomId") String roomId,
        @Param("respondedAt") LocalDateTime respondedAt
    );

    /**
     * 取消邀请
     */
    @Update("UPDATE game_invitation SET status = 'cancelled', responded_at = NOW() WHERE id = #{id}")
    int cancelInvitation(@Param("id") Long id);

    /**
     * 清理过期的邀请
     */
    @Delete("DELETE FROM game_invitation WHERE expires_at < #{beforeDate} OR " +
            "(status = 'pending' AND expires_at < NOW())")
    int cleanExpiredInvitations(@Param("beforeDate") LocalDateTime beforeDate);

    /**
     * 检查是否已有待处理的邀请
     */
    @Select("SELECT COUNT(*) FROM game_invitation WHERE inviter_id = #{inviterId} AND invitee_id = #{inviteeId} " +
            "AND status = 'pending' AND expires_at > NOW()")
    int countPendingInvitationBetween(@Param("inviterId") Long inviterId, @Param("inviteeId") Long inviteeId);
}
