package com.gobang.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户统计实体
 */
public class UserStats {

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

    public UserStats() {
    }

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

    // Getters and Setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getTotalGames() {
        return totalGames;
    }

    public void setTotalGames(Integer totalGames) {
        this.totalGames = totalGames;
    }

    public Integer getWins() {
        return wins;
    }

    public void setWins(Integer wins) {
        this.wins = wins;
    }

    public Integer getLosses() {
        return losses;
    }

    public void setLosses(Integer losses) {
        this.losses = losses;
    }

    public Integer getDraws() {
        return draws;
    }

    public void setDraws(Integer draws) {
        this.draws = draws;
    }

    public Integer getMaxRating() {
        return maxRating;
    }

    public void setMaxRating(Integer maxRating) {
        this.maxRating = maxRating;
    }

    public Integer getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(Integer currentStreak) {
        this.currentStreak = currentStreak;
    }

    public Integer getMaxStreak() {
        return maxStreak;
    }

    public void setMaxStreak(Integer maxStreak) {
        this.maxStreak = maxStreak;
    }

    public Integer getTotalMoves() {
        return totalMoves;
    }

    public void setTotalMoves(Integer totalMoves) {
        this.totalMoves = totalMoves;
    }

    public BigDecimal getAvgMovesPerGame() {
        return avgMovesPerGame;
    }

    public void setAvgMovesPerGame(BigDecimal avgMovesPerGame) {
        this.avgMovesPerGame = avgMovesPerGame;
    }

    public Integer getFastestWin() {
        return fastestWin;
    }

    public void setFastestWin(Integer fastestWin) {
        this.fastestWin = fastestWin;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 计算胜率
     */
    public double getWinRate() {
        if (totalGames == 0) {
            return 0.0;
        }
        return (double) wins / totalGames * 100;
    }
}
