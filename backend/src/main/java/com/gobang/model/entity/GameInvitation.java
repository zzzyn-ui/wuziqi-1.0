package com.gobang.model.entity;

import java.time.LocalDateTime;

/**
 * 游戏邀请实体
 */
public class GameInvitation {

    private Long id;
    private Long inviterId;
    private Long inviteeId;
    private String invitationType;
    private String roomId;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime respondedAt;
    private LocalDateTime createdAt;

    // 邀请类型
    public static final String TYPE_CASUAL = "casual";
    public static final String TYPE_RANKED = "ranked";

    // 状态
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_TIMEOUT = "timeout";
    public static final String STATUS_CANCELLED = "cancelled";

    // 邀请有效期（分钟）
    public static final int EXPIRATION_MINUTES = 5;

    public GameInvitation() {}

    public GameInvitation(Long inviterId, Long inviteeId, String invitationType) {
        this.inviterId = inviterId;
        this.inviteeId = inviteeId;
        this.invitationType = invitationType;
        this.status = STATUS_PENDING;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = createdAt.plusMinutes(EXPIRATION_MINUTES);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInviterId() { return inviterId; }
    public void setInviterId(Long inviterId) { this.inviterId = inviterId; }

    public Long getInviteeId() { return inviteeId; }
    public void setInviteeId(Long inviteeId) { this.inviteeId = inviteeId; }

    public String getInvitationType() { return invitationType; }
    public void setInvitationType(String invitationType) { this.invitationType = invitationType; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * 检查是否过期
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 接受邀请
     */
    public void accept(String roomId) {
        this.status = STATUS_ACCEPTED;
        this.roomId = roomId;
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * 拒绝邀请
     */
    public void reject() {
        this.status = STATUS_REJECTED;
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * 取消邀请
     */
    public void cancel() {
        this.status = STATUS_CANCELLED;
        this.respondedAt = LocalDateTime.now();
    }
}
