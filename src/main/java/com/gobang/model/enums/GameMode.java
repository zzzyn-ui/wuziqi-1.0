package com.gobang.model.enums;

/**
 * 游戏模式枚举
 */
public enum GameMode {
    /**
     * 休闲模式 - 不影响积分
     */
    CASUAL("casual", "休闲模式", false),

    /**
     * 竞技模式 - 使用ELO积分计算
     */
    RANKED("ranked", "竞技模式", true);

    private final String code;
    private final String description;
    private final boolean affectsRating;

    GameMode(String code, String description, boolean affectsRating) {
        this.code = code;
        this.description = description;
        this.affectsRating = affectsRating;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean affectsRating() {
        return affectsRating;
    }

    /**
     * 根据代码获取游戏模式
     */
    public static GameMode fromCode(String code) {
        if (code == null) {
            return CASUAL; // 默认休闲模式
        }
        for (GameMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return CASUAL; // 默认休闲模式
    }

    /**
     * 检查是否是竞技模式
     */
    public boolean isRanked() {
        return this == RANKED;
    }
}
