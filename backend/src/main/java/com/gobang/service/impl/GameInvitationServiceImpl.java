package com.gobang.service.impl;

import com.gobang.model.dto.FriendWebSocketDto;
import com.gobang.model.entity.GameInvitation;
import com.gobang.model.entity.User;
import com.gobang.service.FriendService;
import com.gobang.service.GameInvitationService;
import com.gobang.service.GameService;
import com.gobang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏邀请服务实现
 * 使用内存存储邀请（可改为数据库）
 */
@Service
@RequiredArgsConstructor
public class GameInvitationServiceImpl implements GameInvitationService {

    private static final Logger logger = LoggerFactory.getLogger(GameInvitationServiceImpl.class);

    // 内存存储待处理的邀请 (invitationId -> GameInvitation)
    private final Map<Long, GameInvitation> pendingInvitations = new ConcurrentHashMap<>();

    // 用户ID -> 待处理的邀请ID列表
    private final Map<Long, Set<Long>> userInvitations = new ConcurrentHashMap<>();

    // 邀请ID生成器
    private long invitationIdCounter = 1;

    private final UserService userService;
    private final FriendService friendService;
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public GameInvitation sendInvitation(Long inviterId, Long inviteeId, String gameMode) {
        // 检查是否是好友
        if (!friendService.isFriend(inviterId, inviteeId)) {
            throw new RuntimeException("只能邀请好友进行游戏");
        }

        // 检查被邀请者是否在线
        User invitee = userService.getUserById(inviteeId);
        if (invitee == null || !invitee.isOnline()) {
            throw new RuntimeException("对方不在线");
        }

        // 检查是否已有待处理的邀请
        if (hasPendingInvitations(inviteeId)) {
            throw new RuntimeException("对方已有待处理的邀请");
        }

        // 创建邀请
        GameInvitation invitation = new GameInvitation(inviterId, inviteeId, gameMode);
        invitation.setId(invitationIdCounter++);

        // 存储邀请
        pendingInvitations.put(invitation.getId(), invitation);
        userInvitations.computeIfAbsent(inviteeId, k -> new HashSet<>()).add(invitation.getId());

        logger.info("游戏邀请已创建: id={}, inviter={}, invitee={}, mode={}",
                invitation.getId(), inviterId, inviteeId, gameMode);

        // 获取邀请者信息
        User inviter = userService.getUserById(inviterId);

        // 发送WebSocket通知
        FriendWebSocketDto notification = FriendWebSocketDto.gameInvitation(
                inviterId, inviter.getUsername(), inviter.getNickname(),
                inviteeId, null, gameMode
        );
        notification.setMessage(invitation.getId().toString());

        messagingTemplate.convertAndSendToUser(
                String.valueOf(inviteeId),
                "/queue/friend/invitation",
                notification
        );

        return invitation;
    }

    @Override
    public String acceptInvitation(Long invitationId, Long inviteeId) {
        GameInvitation invitation = pendingInvitations.get(invitationId);
        if (invitation == null) {
            throw new RuntimeException("邀请不存在或已过期");
        }

        if (!invitation.getInviteeId().equals(inviteeId)) {
            throw new RuntimeException("无权接受此邀请");
        }

        if (invitation.isExpired()) {
            pendingInvitations.remove(invitationId);
            userInvitations.get(inviteeId).remove(invitationId);
            throw new RuntimeException("邀请已过期");
        }

        // 创建房间（好友邀请房间）
        String roomId = gameService.createRoom(
                invitation.getInviterId(),
                invitation.getInvitationType(),
                "好友对战房间",
                null  // 好友邀请房间不需要密码
        );

        // 接受邀请
        invitation.accept(roomId);

        // 从待处理列表中移除
        pendingInvitations.remove(invitationId);
        userInvitations.get(inviteeId).remove(invitationId);

        // 通知邀请者
        FriendWebSocketDto notification = FriendWebSocketDto.invitationAccepted(
                invitation.getInviterId(), roomId
        );

        messagingTemplate.convertAndSendToUser(
                String.valueOf(invitation.getInviterId()),
                "/queue/friend/invitation/response",
                notification
        );

        logger.info("游戏邀请已接受: id={}, roomId={}", invitationId, roomId);

        return roomId;
    }

    @Override
    public void rejectInvitation(Long invitationId, Long inviteeId, String reason) {
        GameInvitation invitation = pendingInvitations.get(invitationId);
        if (invitation == null) {
            throw new RuntimeException("邀请不存在或已过期");
        }

        if (!invitation.getInviteeId().equals(inviteeId)) {
            throw new RuntimeException("无权拒绝此邀请");
        }

        // 拒绝邀请
        invitation.reject();

        // 从待处理列表中移除
        pendingInvitations.remove(invitationId);
        userInvitations.get(inviteeId).remove(invitationId);

        // 通知邀请者
        FriendWebSocketDto notification = FriendWebSocketDto.invitationRejected(
                invitation.getInviterId(), reason
        );

        messagingTemplate.convertAndSendToUser(
                String.valueOf(invitation.getInviterId()),
                "/queue/friend/invitation/response",
                notification
        );

        logger.info("游戏邀请已拒绝: id={}, reason={}", invitationId, reason);
    }

    @Override
    public void cancelInvitation(Long invitationId, Long inviterId) {
        GameInvitation invitation = pendingInvitations.get(invitationId);
        if (invitation == null) {
            throw new RuntimeException("邀请不存在或已过期");
        }

        if (!invitation.getInviterId().equals(inviterId)) {
            throw new RuntimeException("无权取消此邀请");
        }

        // 取消邀请
        invitation.cancel();

        // 从待处理列表中移除
        pendingInvitations.remove(invitationId);
        userInvitations.get(invitation.getInviteeId()).remove(invitationId);

        // 通知被邀请者
        FriendWebSocketDto notification = new FriendWebSocketDto();
        notification.setType(FriendWebSocketDto.TYPE_INVITATION_CANCELLED);
        notification.setToUserId(invitation.getInviteeId());
        notification.setTimestamp(LocalDateTime.now());

        messagingTemplate.convertAndSendToUser(
                String.valueOf(invitation.getInviteeId()),
                "/queue/friend/invitation/response",
                notification
        );

        logger.info("游戏邀请已取消: id={}", invitationId);
    }

    @Override
    public List<Map<String, Object>> getPendingInvitations(Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<Long> invitationIds = userInvitations.get(userId);

        if (invitationIds != null) {
            for (Long invitationId : invitationIds) {
                GameInvitation invitation = pendingInvitations.get(invitationId);
                if (invitation != null && !invitation.isExpired()) {
                    User inviter = userService.getUserById(invitation.getInviterId());
                    if (inviter != null) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("id", invitation.getId());
                        data.put("inviterId", invitation.getInviterId());
                        data.put("inviterName", inviter.getNickname() != null ? inviter.getNickname() : inviter.getUsername());
                        data.put("gameMode", invitation.getInvitationType());
                        data.put("expiresAt", invitation.getExpiresAt());
                        result.add(data);
                    }
                }
            }
        }

        return result;
    }

    @Override
    @Scheduled(fixedRate = 60000) // 每分钟清理一次
    public void cleanExpiredInvitations() {
        logger.debug("开始清理过期的游戏邀请...");

        List<Long> expiredIds = new ArrayList<>();
        for (Map.Entry<Long, GameInvitation> entry : pendingInvitations.entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredIds.add(entry.getKey());
            }
        }

        for (Long id : expiredIds) {
            GameInvitation invitation = pendingInvitations.remove(id);
            if (invitation != null) {
                userInvitations.get(invitation.getInviteeId()).remove(id);

                // 通知双方邀请已过期
                FriendWebSocketDto notification = new FriendWebSocketDto();
                notification.setType("INVITATION_TIMEOUT");
                notification.setToUserId(invitation.getInviteeId());
                notification.setTimestamp(LocalDateTime.now());

                messagingTemplate.convertAndSendToUser(
                        String.valueOf(invitation.getInviteeId()),
                        "/queue/friend/invitation/response",
                        notification
                );

                messagingTemplate.convertAndSendToUser(
                        String.valueOf(invitation.getInviterId()),
                        "/queue/friend/invitation/response",
                        notification
                );
            }
        }

        if (!expiredIds.isEmpty()) {
            logger.info("已清理 {} 个过期的游戏邀请", expiredIds.size());
        }
    }

    @Override
    public boolean hasPendingInvitations(Long userId) {
        Set<Long> invitationIds = userInvitations.get(userId);
        if (invitationIds == null || invitationIds.isEmpty()) {
            return false;
        }

        for (Long invitationId : invitationIds) {
            GameInvitation invitation = pendingInvitations.get(invitationId);
            if (invitation != null && !invitation.isExpired()) {
                return true;
            }
        }
        return false;
    }
}
