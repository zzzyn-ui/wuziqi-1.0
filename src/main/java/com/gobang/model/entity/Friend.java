package com.gobang.model.entity;

import java.time.LocalDateTime;

/**
 * 好友关系实体
 */
public class Friend {

    private Long id;
    private Long userId;
    private Long friendId;
    private Integer status;
    private String requestMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Friend() {
    }

    public Friend(Long userId, Long friendId) {
        this.userId = userId;
        this.friendId = friendId;
        this.status = 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFriendId() {
        return friendId;
    }

    public void setFriendId(Long friendId) {
        this.friendId = friendId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRequestMessage() {
        return requestMessage;
    }

    public void setRequestMessage(String requestMessage) {
        this.requestMessage = requestMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isPending() {
        return status == 0;
    }

    public boolean isAccepted() {
        return status == 1;
    }
}
