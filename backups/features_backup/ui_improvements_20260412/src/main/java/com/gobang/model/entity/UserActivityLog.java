package com.gobang.model.entity;

import java.time.LocalDateTime;

/**
 * 用户活动日志实体
 */
public class UserActivityLog {

    private Long id;
    private Long userId;
    private String activityType;
    private String ipAddress;
    private String userAgent;
    private String activityData;
    private LocalDateTime createdAt;

    // 活动类型常量
    public static final String TYPE_LOGIN = "login";
    public static final String TYPE_LOGOUT = "logout";
    public static final String TYPE_MATCH_START = "match_start";
    public static final String TYPE_MATCH_SUCCESS = "match_success";
    public static final String TYPE_MATCH_CANCEL = "match_cancel";
    public static final String TYPE_GAME_START = "game_start";
    public static final String TYPE_GAME_END = "game_end";
    public static final String TYPE_RESIGN = "resign";
    public static final String TYPE_REGISTER = "register";
    public static final String TYPE_PROFILE_UPDATE = "profile_update";

    public UserActivityLog() {}

    public UserActivityLog(Long userId, String activityType) {
        this.userId = userId;
        this.activityType = activityType;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getActivityData() { return activityData; }
    public void setActivityData(String activityData) { this.activityData = activityData; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
