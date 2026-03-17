package com.gobang.model.entity;

import java.time.LocalDateTime;

/**
 * 对局收藏实体
 */
public class GameFavorite {

    private Long id;
    private Long userId;
    private Long gameRecordId;
    private String note;
    private String tags;
    private Boolean isPublic;
    private LocalDateTime createdAt;

    public GameFavorite() {
        this.isPublic = true;
    }

    public GameFavorite(Long userId, Long gameRecordId) {
        this();
        this.userId = userId;
        this.gameRecordId = gameRecordId;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getGameRecordId() { return gameRecordId; }
    public void setGameRecordId(Long gameRecordId) { this.gameRecordId = gameRecordId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
