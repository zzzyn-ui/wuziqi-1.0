package com.gobang.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户统计数据传输对象
 * 不继承任何MyBatis-Plus类，避免实体映射问题
 */
@Data
public class UserStatsDTO {
    private Long userId;
    private Integer totalGames;
    private Integer wins;
    private Integer losses;
    private Integer draws;
    private Integer maxRating;
    private Integer currentStreak;
    private Integer maxStreak;
    private Integer totalMoves;
    private BigDecimal avgMovesPerGame;
    private Integer fastestWin;
    private LocalDateTime updatedAt;
}
