package com.gobang.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户统计实体
 */
@Data

@NoArgsConstructor
@TableName("user_stats")
public class UserStats {

    /**
     * 自增主键ID（MyBatis-Plus要求）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（业务主键）
     */
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

    /**
     * 构造函数
     */
    public UserStats(Long userId) {
        this.userId = userId;
        this.totalGames = 0;
        this.wins = 0;
        this.losses = 0;
        this.draws = 0;
        this.maxRating = 1200;
        this.currentStreak = 0;
        this.maxStreak = 0;
        this.totalMoves = 0;
        this.avgMovesPerGame = BigDecimal.ZERO;
    }

    /**
     * 计算胜率
     */
    public double getWinRate() {
        if (totalGames == null || totalGames == 0) {
            return 0.0;
        }
        return (double) wins / totalGames * 100;
    }

    /**
     * 增加游戏场次
     */
    public void addGame(boolean isWin, boolean isDraw, int moves) {
        this.totalGames = (this.totalGames == null ? 0 : this.totalGames) + 1;
        if (isWin) {
            this.wins = (this.wins == null ? 0 : this.wins) + 1;
            this.currentStreak = (this.currentStreak == null ? 0 : this.currentStreak) > 0
                ? this.currentStreak + 1
                : 1;
            if (this.maxStreak == null || this.currentStreak > this.maxStreak) {
                this.maxStreak = this.currentStreak;
            }
        } else if (isDraw) {
            this.draws = (this.draws == null ? 0 : this.draws) + 1;
            this.currentStreak = 0;
        } else {
            this.losses = (this.losses == null ? 0 : this.losses) + 1;
            this.currentStreak = (this.currentStreak == null ? 0 : this.currentStreak) < 0
                ? this.currentStreak - 1
                : -1;
        }

        this.totalMoves = (this.totalMoves == null ? 0 : this.totalMoves) + moves;

        // 更新平均每局落子数
        if (this.totalGames > 0) {
            this.avgMovesPerGame = BigDecimal.valueOf((double) this.totalMoves / this.totalGames)
                .setScale(2, BigDecimal.ROUND_HALF_UP);
        }

        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新最高积分
     */
    public void updateMaxRating(int newRating) {
        if (this.maxRating == null || newRating > this.maxRating) {
            this.maxRating = newRating;
        }
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getTotalGames() { return totalGames; }
    public void setTotalGames(Integer totalGames) { this.totalGames = totalGames; }

    public Integer getWins() { return wins; }
    public void setWins(Integer wins) { this.wins = wins; }

    public Integer getLosses() { return losses; }
    public void setLosses(Integer losses) { this.losses = losses; }

    public Integer getDraws() { return draws; }
    public void setDraws(Integer draws) { this.draws = draws; }

    public Integer getMaxRating() { return maxRating; }
    public void setMaxRating(Integer maxRating) { this.maxRating = maxRating; }

    public Integer getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(Integer currentStreak) { this.currentStreak = currentStreak; }

    public Integer getMaxStreak() { return maxStreak; }
    public void setMaxStreak(Integer maxStreak) { this.maxStreak = maxStreak; }

    public Integer getTotalMoves() { return totalMoves; }
    public void setTotalMoves(Integer totalMoves) { this.totalMoves = totalMoves; }

    public BigDecimal getAvgMovesPerGame() { return avgMovesPerGame; }
    public void setAvgMovesPerGame(BigDecimal avgMovesPerGame) { this.avgMovesPerGame = avgMovesPerGame; }

    public Integer getFastestWin() { return fastestWin; }
    public void setFastestWin(Integer fastestWin) { this.fastestWin = fastestWin; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
