package com.gobang.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户设置实体
 * 优化版：添加MyBatis-Plus注解，使用Lombok简化代码
 */
@Data
@NoArgsConstructor
@TableName("user_settings")
public class UserSettings {

    /**
     * 设置记录ID（独立主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（唯一）
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
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

    /**
     * 带用户ID的构造函数
     */
    public UserSettings(Long userId) {
        this.userId = userId;
    }

    /**
     * 创建默认设置的工厂方法
     */
    public static UserSettings createDefault(Long userId) {
        UserSettings settings = new UserSettings();
        settings.userId = userId;
        settings.soundEnabled = true;
        settings.musicEnabled = true;
        settings.soundVolume = 80;
        settings.musicVolume = 60;
        settings.boardTheme = "classic";
        settings.pieceStyle = "classic";
        settings.autoMatch = true;
        settings.showRating = true;
        settings.language = "zh-CN";
        settings.timezone = "Asia/Shanghai";
        return settings;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
