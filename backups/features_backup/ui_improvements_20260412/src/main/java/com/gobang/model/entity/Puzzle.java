package com.gobang.model.entity;

import java.util.List;

/**
 * 残局实体类
 */
public class Puzzle {
    private Long id;
    private String title;
    private String description;
    private String difficulty;           // easy, medium, hard, expert
    private String puzzleType;           // four_three, double_four, vcf, vct, forbidden, classic, strategy

    // 棋盘数据
    private String boardState;           // 初始棋盘状态(15x15字符串)
    private String firstPlayer;          // black, white
    private String playerColor;          // black, white

    // 胜利条件
    private String winCondition;         // win, draw, opponent_lose
    private Integer maxMoves;            // 最大步数限制
    private Integer optimalMoves;        // 最佳解法步数

    // 最佳解法
    private String solution;             // 最佳解法(JSON格式)
    private String alternativeSolutions; // 备选解法(JSON格式)

    // 提示
    private String hint;                 // 提示信息
    private String hintMoves;            // 提示步数(JSON)

    // 排序和状态
    private Integer levelOrder;          // 关卡顺序
    private Boolean isActive;

    // 统计信息(关联查询)
    private Integer totalAttempts;
    private Integer totalCompletions;
    private Double completionRate;
    private Integer threeStarCount;

    public Puzzle() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getPuzzleType() { return puzzleType; }
    public void setPuzzleType(String puzzleType) { this.puzzleType = puzzleType; }

    public String getBoardState() { return boardState; }
    public void setBoardState(String boardState) { this.boardState = boardState; }

    public String getFirstPlayer() { return firstPlayer; }
    public void setFirstPlayer(String firstPlayer) { this.firstPlayer = firstPlayer; }

    public String getPlayerColor() { return playerColor; }
    public void setPlayerColor(String playerColor) { this.playerColor = playerColor; }

    public String getWinCondition() { return winCondition; }
    public void setWinCondition(String winCondition) { this.winCondition = winCondition; }

    public Integer getMaxMoves() { return maxMoves; }
    public void setMaxMoves(Integer maxMoves) { this.maxMoves = maxMoves; }

    public Integer getOptimalMoves() { return optimalMoves; }
    public void setOptimalMoves(Integer optimalMoves) { this.optimalMoves = optimalMoves; }

    public String getSolution() { return solution; }
    public void setSolution(String solution) { this.solution = solution; }

    public String getAlternativeSolutions() { return alternativeSolutions; }
    public void setAlternativeSolutions(String alternativeSolutions) { this.alternativeSolutions = alternativeSolutions; }

    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }

    public String getHintMoves() { return hintMoves; }
    public void setHintMoves(String hintMoves) { this.hintMoves = hintMoves; }

    public Integer getLevelOrder() { return levelOrder; }
    public void setLevelOrder(Integer levelOrder) { this.levelOrder = levelOrder; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getTotalAttempts() { return totalAttempts; }
    public void setTotalAttempts(Integer totalAttempts) { this.totalAttempts = totalAttempts; }

    public Integer getTotalCompletions() { return totalCompletions; }
    public void setTotalCompletions(Integer totalCompletions) { this.totalCompletions = totalCompletions; }

    public Double getCompletionRate() { return completionRate; }
    public void setCompletionRate(Double completionRate) { this.completionRate = completionRate; }

    public Integer getThreeStarCount() { return threeStarCount; }
    public void setThreeStarCount(Integer threeStarCount) { this.threeStarCount = threeStarCount; }
}
