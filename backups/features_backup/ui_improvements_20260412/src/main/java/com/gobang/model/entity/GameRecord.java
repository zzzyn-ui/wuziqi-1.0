package com.gobang.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对局记录实体
 * 使用 MyBatis-Plus 注解
 */
@Data
@NoArgsConstructor
@TableName("game_record")
public class GameRecord {

    /**
     * 对局记录ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomId;
    private Long blackPlayerId;
    private Long whitePlayerId;
    private Long winnerId;
    private Integer winColor;
    private Integer endReason;
    private Integer moveCount;
    private Integer duration;
    private Integer blackRatingBefore;
    private Integer blackRatingAfter;
    private Integer blackRatingChange;
    private Integer whiteRatingBefore;
    private Integer whiteRatingAfter;
    private Integer whiteRatingChange;
    private String boardState;
    private String moves;
    private String gameMode; // pve(人机), pvp_online(在线对战), pvp_local(本地对战)
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public Long getBlackPlayerId() { return blackPlayerId; }
    public void setBlackPlayerId(Long blackPlayerId) { this.blackPlayerId = blackPlayerId; }

    public Long getWhitePlayerId() { return whitePlayerId; }
    public void setWhitePlayerId(Long whitePlayerId) { this.whitePlayerId = whitePlayerId; }

    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }

    public Integer getWinColor() { return winColor; }
    public void setWinColor(Integer winColor) { this.winColor = winColor; }

    public Integer getEndReason() { return endReason; }
    public void setEndReason(Integer endReason) { this.endReason = endReason; }

    public Integer getMoveCount() { return moveCount; }
    public void setMoveCount(Integer moveCount) { this.moveCount = moveCount; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public Integer getBlackRatingBefore() { return blackRatingBefore; }
    public void setBlackRatingBefore(Integer blackRatingBefore) { this.blackRatingBefore = blackRatingBefore; }

    public Integer getBlackRatingAfter() { return blackRatingAfter; }
    public void setBlackRatingAfter(Integer blackRatingAfter) { this.blackRatingAfter = blackRatingAfter; }

    public Integer getBlackRatingChange() { return blackRatingChange; }
    public void setBlackRatingChange(Integer blackRatingChange) { this.blackRatingChange = blackRatingChange; }

    public Integer getWhiteRatingBefore() { return whiteRatingBefore; }
    public void setWhiteRatingBefore(Integer whiteRatingBefore) { this.whiteRatingBefore = whiteRatingBefore; }

    public Integer getWhiteRatingAfter() { return whiteRatingAfter; }
    public void setWhiteRatingAfter(Integer whiteRatingAfter) { this.whiteRatingAfter = whiteRatingAfter; }

    public Integer getWhiteRatingChange() { return whiteRatingChange; }
    public void setWhiteRatingChange(Integer whiteRatingChange) { this.whiteRatingChange = whiteRatingChange; }

    public String getBoardState() { return boardState; }
    public void setBoardState(String boardState) { this.boardState = boardState; }

    public String getMoves() { return moves; }
    public void setMoves(String moves) { this.moves = moves; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
