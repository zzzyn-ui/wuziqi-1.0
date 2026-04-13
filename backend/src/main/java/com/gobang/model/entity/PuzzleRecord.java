package com.gobang.model.entity;

import java.sql.Timestamp;

/**
 * 残局通关记录实体类
 */
public class PuzzleRecord {
    private Long id;
    private Long userId;
    private Long puzzleId;
    private Integer attempts;
    private Boolean completed;
    private Integer bestMoves;
    private Integer bestTime;
    private Integer stars;
    private String solutionPath;
    private Timestamp completedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // 关联信息
    private String puzzleTitle;
    private String puzzleDifficulty;

    public PuzzleRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getPuzzleId() { return puzzleId; }
    public void setPuzzleId(Long puzzleId) { this.puzzleId = puzzleId; }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }

    public Integer getBestMoves() { return bestMoves; }
    public void setBestMoves(Integer bestMoves) { this.bestMoves = bestMoves; }

    public Integer getBestTime() { return bestTime; }
    public void setBestTime(Integer bestTime) { this.bestTime = bestTime; }

    public Integer getStars() { return stars; }
    public void setStars(Integer stars) { this.stars = stars; }

    public String getSolutionPath() { return solutionPath; }
    public void setSolutionPath(String solutionPath) { this.solutionPath = solutionPath; }

    public Timestamp getCompletedAt() { return completedAt; }
    public void setCompletedAt(Timestamp completedAt) { this.completedAt = completedAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String getPuzzleTitle() { return puzzleTitle; }
    public void setPuzzleTitle(String puzzleTitle) { this.puzzleTitle = puzzleTitle; }

    public String getPuzzleDifficulty() { return puzzleDifficulty; }
    public void setPuzzleDifficulty(String puzzleDifficulty) { this.puzzleDifficulty = puzzleDifficulty; }
}
