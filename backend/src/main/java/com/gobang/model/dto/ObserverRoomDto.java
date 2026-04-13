package com.gobang.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 观战房间 DTO
 * 包含观战者信息和游戏状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObserverRoomDto {

    /**
     * 房间ID
     */
    private String roomId;

    /**
     * 房间名称
     */
    private String roomName;

    /**
     * 游戏模式
     */
    private String gameMode;

    /**
     * 黑方玩家信息
     */
    private PlayerInfo blackPlayer;

    /**
     * 白方玩家信息
     */
    private PlayerInfo whitePlayer;

    /**
     * 当前回合
     */
    private Integer currentTurn;

    /**
     * 游戏状态 (WAITING, PLAYING, FINISHED)
     */
    private String gameStatus;

    /**
     * 棋盘数据
     */
    private List<List<Integer>> board;

    /**
     * 观战者列表
     */
    private List<Long> observers;

    /**
     * 观战者数量
     */
    private Integer observerCount;

    /**
     * 房间创建时间
     */
    private LocalDateTime createTime;

    /**
     * 玩家信息
     */
    public static class PlayerInfo {
        private Long id;
        private String username;
        private String nickname;
        private Integer rating;
        private Integer level;

        public PlayerInfo() {}

        public PlayerInfo(Long id, String username, String nickname, Integer rating, Integer level) {
            this.id = id;
            this.username = username;
            this.nickname = nickname;
            this.rating = rating;
            this.level = level;
        }

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }

        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating; }

        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
    }

    /**
     * 创建观战房间信息
     */
    public static ObserverRoomDto create(
            String roomId,
            String roomName,
            String gameMode,
            PlayerInfo blackPlayer,
            PlayerInfo whitePlayer,
            Integer currentTurn,
            String gameStatus,
            List<List<Integer>> board,
            List<Long> observers,
            LocalDateTime createTime
    ) {
        ObserverRoomDto dto = new ObserverRoomDto();
        dto.setRoomId(roomId);
        dto.setRoomName(roomName);
        dto.setGameMode(gameMode);
        dto.setBlackPlayer(blackPlayer);
        dto.setWhitePlayer(whitePlayer);
        dto.setCurrentTurn(currentTurn);
        dto.setGameStatus(gameStatus);
        dto.setBoard(board);
        dto.setObservers(observers);
        dto.setObserverCount(observers != null ? observers.size() : 0);
        dto.setCreateTime(createTime);
        return dto;
    }

    // Getters and Setters
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public PlayerInfo getBlackPlayer() { return blackPlayer; }
    public void setBlackPlayer(PlayerInfo blackPlayer) { this.blackPlayer = blackPlayer; }

    public PlayerInfo getWhitePlayer() { return whitePlayer; }
    public void setWhitePlayer(PlayerInfo whitePlayer) { this.whitePlayer = whitePlayer; }

    public Integer getCurrentTurn() { return currentTurn; }
    public void setCurrentTurn(Integer currentTurn) { this.currentTurn = currentTurn; }

    public String getGameStatus() { return gameStatus; }
    public void setGameStatus(String gameStatus) { this.gameStatus = gameStatus; }

    public List<List<Integer>> getBoard() { return board; }
    public void setBoard(List<List<Integer>> board) { this.board = board; }

    public List<Long> getObservers() { return observers; }
    public void setObservers(List<Long> observers) { this.observers = observers; }

    public Integer getObserverCount() { return observerCount; }
    public void setObserverCount(Integer observerCount) { this.observerCount = observerCount; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
