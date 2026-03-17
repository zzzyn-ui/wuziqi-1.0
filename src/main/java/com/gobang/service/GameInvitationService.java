package com.gobang.service;

import com.gobang.mapper.GameInvitationMapper;
import com.gobang.model.entity.GameInvitation;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 游戏邀请服务
 */
public class GameInvitationService {

    private static final Logger logger = LoggerFactory.getLogger(GameInvitationService.class);

    private final SqlSessionFactory sqlSessionFactory;

    public GameInvitationService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 发送游戏邀请
     */
    public Long sendInvitation(Long inviterId, Long inviteeId, String invitationType) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameInvitationMapper mapper = session.getMapper(GameInvitationMapper.class);

            // 检查是否已有待处理的邀请
            int pendingCount = mapper.countPendingInvitationBetween(inviterId, inviteeId);
            if (pendingCount > 0) {
                logger.warn("已有待处理的邀请，不能重复发送: inviter={}, invitee={}", inviterId, inviteeId);
                return null;
            }

            // 不能邀请自己
            if (inviterId.equals(inviteeId)) {
                logger.warn("不能邀请自己: userId={}", inviterId);
                return null;
            }

            GameInvitation invitation = new GameInvitation(inviterId, inviteeId, invitationType);
            mapper.insert(invitation);
            session.commit();

            logger.info("发送游戏邀请: from={}, to={}, type={}", inviterId, inviteeId, invitationType);
            return invitation.getId();
        }
    }

    /**
     * 接受邀请
     */
    public boolean acceptInvitation(Long invitationId, String roomId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameInvitationMapper mapper = session.getMapper(GameInvitationMapper.class);
            GameInvitation invitation = mapper.findById(invitationId);

            if (invitation == null) {
                logger.warn("邀请不存在: invitationId={}", invitationId);
                return false;
            }

            if (invitation.isExpired()) {
                logger.warn("邀请已过期: invitationId={}", invitationId);
                mapper.updateStatus(invitationId, GameInvitation.STATUS_TIMEOUT, null, LocalDateTime.now());
                session.commit();
                return false;
            }

            if (!GameInvitation.STATUS_PENDING.equals(invitation.getStatus())) {
                logger.warn("邀请状态不正确: invitationId={}, status={}", invitationId, invitation.getStatus());
                return false;
            }

            invitation.accept(roomId);
            mapper.updateStatus(invitationId, invitation.getStatus(), invitation.getRoomId(), invitation.getRespondedAt());
            session.commit();

            logger.info("接受邀请: invitationId={}, roomId={}", invitationId, roomId);
            return true;
        }
    }

    /**
     * 拒绝邀请
     */
    public boolean rejectInvitation(Long invitationId) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            GameInvitationMapper mapper = session.getMapper(GameInvitationMapper.class);
            GameInvitation invitation = mapper.findById(invitationId);

            if (invitation == null) {
                return false;
            }

            invitation.reject();
            mapper.updateStatus(invitationId, invitation.getStatus(), null, invitation.getRespondedAt());

            logger.info("拒绝邀请: invitationId={}", invitationId);
            return true;
        }
    }

    /**
     * 取消邀请
     */
    public boolean cancelInvitation(Long invitationId) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            GameInvitationMapper mapper = session.getMapper(GameInvitationMapper.class);
            GameInvitation invitation = mapper.findById(invitationId);

            if (invitation == null) {
                return false;
            }

            invitation.cancel();
            mapper.cancelInvitation(invitationId);

            logger.info("取消邀请: invitationId={}", invitationId);
            return true;
        }
    }

    /**
     * 获取用户的待处理邀请
     */
    public List<GameInvitation> getPendingInvitations(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameInvitationMapper mapper = session.getMapper(GameInvitationMapper.class);
            return mapper.findPendingInvitations(userId);
        }
    }

    /**
     * 获取用户发送的邀请
     */
    public List<GameInvitation> getSentInvitations(Long userId, int limit) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameInvitationMapper mapper = session.getMapper(GameInvitationMapper.class);
            return mapper.findByInviterId(userId, limit);
        }
    }

    /**
     * 清理过期邀请
     */
    public int cleanExpiredInvitations() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            GameInvitationMapper mapper = session.getMapper(GameInvitationMapper.class);
            int deleted = mapper.cleanExpiredInvitations(LocalDateTime.now());
            logger.info("清理过期邀请: 删除{}条", deleted);
            return deleted;
        }
    }

    /**
     * 根据ID获取邀请
     */
    public GameInvitation getInvitation(Long invitationId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameInvitationMapper mapper = session.getMapper(GameInvitationMapper.class);
            return mapper.findById(invitationId);
        }
    }
}
