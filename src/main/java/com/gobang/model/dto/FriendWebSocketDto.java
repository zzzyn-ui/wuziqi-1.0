package com.gobang.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 好友系统 WebSocket 消息 DTO
 * 用于好友状态变化、游戏邀请等实时通知
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendWebSocketDto {

    /**
     * 消息类型
     */
    private String type;

    /**
     * 发送者用户ID
     */
    private Long fromUserId;

    /**
     * 发送者用户名
     */
    private String fromUsername;

    /**
     * 发送者昵称
     */
    private String fromNickname;

    /**
     * 接收者用户ID
     */
    private Long toUserId;

    /**
     * 好友ID（用于状态更新）
     */
    private Long friendId;

    /**
     * 在线状态 (0=离线, 1=在线, 2=游戏中, 3=匹配中)
     */
    private Integer status;

    /**
     * 房间ID（游戏邀请时使用）
     */
    private String roomId;

    /**
     * 游戏模式（casual/ranked）
     */
    private String gameMode;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    // 消息类型常量
    public static final String TYPE_FRIEND_ONLINE = "FRIEND_ONLINE";
    public static final String TYPE_FRIEND_OFFLINE = "FRIEND_OFFLINE";
    public static final String TYPE_FRIEND_STATUS_CHANGE = "FRIEND_STATUS_CHANGE";
    public static final String TYPE_GAME_INVITATION = "GAME_INVITATION";
    public static final String TYPE_INVITATION_ACCEPTED = "INVITATION_ACCEPTED";
    public static final String TYPE_INVITATION_REJECTED = "INVITATION_REJECTED";
    public static final String TYPE_INVITATION_CANCELLED = "INVITATION_CANCELLED";

    /**
     * 创建好友上线通知
     */
    public static FriendWebSocketDto online(Long friendId, String username, String nickname) {
        FriendWebSocketDto dto = new FriendWebSocketDto();
        dto.setType(TYPE_FRIEND_ONLINE);
        dto.setFriendId(friendId);
        dto.setFromUsername(username);
        dto.setFromNickname(nickname);
        dto.setStatus(1);
        dto.setTimestamp(LocalDateTime.now());
        return dto;
    }

    /**
     * 创建好友下线通知
     */
    public static FriendWebSocketDto offline(Long friendId, String username, String nickname) {
        FriendWebSocketDto dto = new FriendWebSocketDto();
        dto.setType(TYPE_FRIEND_OFFLINE);
        dto.setFriendId(friendId);
        dto.setFromUsername(username);
        dto.setFromNickname(nickname);
        dto.setStatus(0);
        dto.setTimestamp(LocalDateTime.now());
        return dto;
    }

    /**
     * 创建好友状态变化通知
     */
    public static FriendWebSocketDto statusChange(Long friendId, Integer status, String username, String nickname) {
        FriendWebSocketDto dto = new FriendWebSocketDto();
        dto.setType(TYPE_FRIEND_STATUS_CHANGE);
        dto.setFriendId(friendId);
        dto.setStatus(status);
        dto.setFromUsername(username);
        dto.setFromNickname(nickname);
        dto.setTimestamp(LocalDateTime.now());
        return dto;
    }

    /**
     * 创建游戏邀请
     */
    public static FriendWebSocketDto gameInvitation(Long fromUserId, String fromUsername, String fromNickname,
                                                    Long toUserId, String roomId, String gameMode) {
        FriendWebSocketDto dto = new FriendWebSocketDto();
        dto.setType(TYPE_GAME_INVITATION);
        dto.setFromUserId(fromUserId);
        dto.setFromUsername(fromUsername);
        dto.setFromNickname(fromNickname);
        dto.setToUserId(toUserId);
        dto.setRoomId(roomId);
        dto.setGameMode(gameMode);
        dto.setTimestamp(LocalDateTime.now());
        return dto;
    }

    /**
     * 创建邀请接受通知
     */
    public static FriendWebSocketDto invitationAccepted(Long toUserId, String roomId) {
        FriendWebSocketDto dto = new FriendWebSocketDto();
        dto.setType(TYPE_INVITATION_ACCEPTED);
        dto.setToUserId(toUserId);
        dto.setRoomId(roomId);
        dto.setTimestamp(LocalDateTime.now());
        return dto;
    }

    /**
     * 创建邀请拒绝通知
     */
    public static FriendWebSocketDto invitationRejected(Long fromUserId, String reason) {
        FriendWebSocketDto dto = new FriendWebSocketDto();
        dto.setType(TYPE_INVITATION_REJECTED);
        dto.setToUserId(fromUserId);
        dto.setMessage(reason);
        dto.setTimestamp(LocalDateTime.now());
        return dto;
    }

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getFromUserId() { return fromUserId; }
    public void setFromUserId(Long fromUserId) { this.fromUserId = fromUserId; }

    public String getFromUsername() { return fromUsername; }
    public void setFromUsername(String fromUsername) { this.fromUsername = fromUsername; }

    public String getFromNickname() { return fromNickname; }
    public void setFromNickname(String fromNickname) { this.fromNickname = fromNickname; }

    public Long getToUserId() { return toUserId; }
    public void setToUserId(Long toUserId) { this.toUserId = toUserId; }

    public Long getFriendId() { return friendId; }
    public void setFriendId(Long friendId) { this.friendId = friendId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
