package com.gobang.service;

import com.gobang.model.dto.FriendWebSocketDto;
import com.gobang.model.entity.Friend;
import com.gobang.model.entity.GameInvitation;
import com.gobang.model.entity.User;

import java.util.List;
import java.util.Map;

/**
 * 游戏邀请服务接口
 * 处理好友之间的游戏邀请逻辑
 */
public interface GameInvitationService {

    /**
     * 发送游戏邀请
     * @param inviterId 邀请者ID
     * @param inviteeId 被邀请者ID
     * @param gameMode 游戏模式
     * @return GameInvitation
     */
    GameInvitation sendInvitation(Long inviterId, Long inviteeId, String gameMode);

    /**
     * 接受游戏邀请
     * @param invitationId 邀请ID
     * @param inviteeId 被邀请者ID
     * @return 房间ID
     */
    String acceptInvitation(Long invitationId, Long inviteeId);

    /**
     * 拒绝游戏邀请
     * @param invitationId 邀请ID
     * @param inviteeId 被邀请者ID
     * @param reason 拒绝原因
     */
    void rejectInvitation(Long invitationId, Long inviteeId, String reason);

    /**
     * 取消游戏邀请
     * @param invitationId 邀请ID
     * @param inviterId 邀请者ID
     */
    void cancelInvitation(Long invitationId, Long inviterId);

    /**
     * 获取待处理的邀请
     * @param userId 用户ID
     * @return 待处理的邀请列表
     */
    List<Map<String, Object>> getPendingInvitations(Long userId);

    /**
     * 清理过期的邀请
     */
    void cleanExpiredInvitations();

    /**
     * 检查用户是否有待处理的邀请
     * @param userId 用户ID
     * @return 是否有待处理的邀请
     */
    boolean hasPendingInvitations(Long userId);
}
