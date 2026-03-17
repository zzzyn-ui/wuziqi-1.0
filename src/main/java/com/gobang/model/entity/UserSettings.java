package com.gobang.model.entity;

import java.time.LocalDateTime;

/**
 * 用户设置实体
 */
public class UserSettings {

    private Long userId;
    private Boolean soundEnabled;
    private Boolean musicEnabled;
    private Integer soundVolume;
    private Integer musicVolume;
    private String boardTheme;
    private String pieceStyle;
    private Boolean autoMatch;
    private Boolean showRating;
    private String language;
    private String timezone;
    private LocalDateTime updatedAt;

    // 默认构造函数
    public UserSettings() {
        // 设置默认值
        this.soundEnabled = true;
        this.musicEnabled = true;
        this.soundVolume = 80;
        this.musicVolume = 60;
        this.boardTheme = "classic";
        this.pieceStyle = "classic";
        this.autoMatch = true;
        this.showRating = true;
        this.language = "zh-CN";
        this.timezone = "Asia/Shanghai";
    }

    public UserSettings(Long userId) {
        this();
        this.userId = userId;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Boolean getSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(Boolean soundEnabled) { this.soundEnabled = soundEnabled; }

    public Boolean getMusicEnabled() { return musicEnabled; }
    public void setMusicEnabled(Boolean musicEnabled) { this.musicEnabled = musicEnabled; }

    public Integer getSoundVolume() { return soundVolume; }
    public void setSoundVolume(Integer soundVolume) { this.soundVolume = soundVolume; }

    public Integer getMusicVolume() { return musicVolume; }
    public void setMusicVolume(Integer musicVolume) { this.musicVolume = musicVolume; }

    public String getBoardTheme() { return boardTheme; }
    public void setBoardTheme(String boardTheme) { this.boardTheme = boardTheme; }

    public String getPieceStyle() { return pieceStyle; }
    public void setPieceStyle(String pieceStyle) { this.pieceStyle = pieceStyle; }

    public Boolean getAutoMatch() { return autoMatch; }
    public void setAutoMatch(Boolean autoMatch) { this.autoMatch = autoMatch; }

    public Boolean getShowRating() { return showRating; }
    public void setShowRating(Boolean showRating) { this.showRating = showRating; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
