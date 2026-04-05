package com.gobang.service;

import com.gobang.core.game.Board;
import com.gobang.core.game.WinChecker;
import com.gobang.core.rating.ELOCalculator;
import com.gobang.model.dto.GameMoveDto;
import com.gobang.model.entity.GameRecord;
import com.gobang.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameServiceImpl {

    private final UserServiceImpl userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ELOCalculator eloCalculator = new ELOCalculator();

    // 房间存储（生产环境应使用 Redis）
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    /**
     * 落子
     */
    public void makeMove(Long userId, String roomId, int x, int y) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        // 检查是否轮到该玩家
        if (room.getCurrentTurn() != userId) {
            throw new RuntimeException("不是你的回合");
        }

        // 检查游戏是否已结束
        if (room.isGameOver()) {
            throw new RuntimeException("游戏已结束");
        }

        // 落子
        Board board = room.getBoard();
        int color = room.getPieceColor(userId);
        if (!board.place(x, y, color)) {
            throw new RuntimeException("无效的落子");
        }

        // 记录移动历史
        room.addMove(x, y, color);

        // 检查是否获胜
        boolean hasWon = WinChecker.checkWin(board, x, y, color);
        if (hasWon) {
            // 游戏结束，当前落子方获胜
            handleGameOver(room, userId, color, 0);
        } else {
            // 切换回合
            room.switchTurn();
            // 广播游戏状态
            broadcastGameState(room);
        }
    }

    /**
     * 创建房间
     */
    public String createRoom(Long userId, String mode) {
        String roomId = generateRoomId();
        GameRoom room = new GameRoom(roomId, userId, mode);
        rooms.put(roomId, room);
        log.info("房间创建成功: roomId={}, userId={}, mode={}", roomId, userId, mode);
        return roomId;
    }

    /**
     * 加入房间
     */
    public void joinRoom(String roomId, Long userId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        room.addPlayer(userId);
        log.info("玩家加入房间: roomId={}, userId={}", roomId, userId);

        // 广播房间状态
        broadcastRoomState(room);
    }

    /**
     * 认输
     */
    public void resign(Long userId, String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        int opponentColor = room.getPieceColor(userId) == 1 ? 2 : 1;
        Long winnerId = room.getPlayerByColor(opponentColor);

        handleGameOver(room, winnerId, opponentColor, 3); // 3 = 认输
    }

    /**
     * 和棋
     */
    public void draw(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        // 和棋没有赢家，winnerId 为 null，winColor 为 0，endReason 为 2
        handleGameOver(room, null, 0, 2); // 2 = 和棋
    }

    /**
     * 超时
     */
    public void timeout(Long userId, String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            log.warn("超时处理失败: 房间不存在 roomId={}", roomId);
            return;
        }

        if (room.getCurrentTurn() == userId) {
            // 确认是当前回合玩家超时
            int color = room.getPieceColor(userId);
            Long winnerId = room.getPlayerByColor(color == 1 ? 2 : 1);
            handleGameOver(room, winnerId, color == 1 ? 2 : 1, 1); // 1 = 超时
            log.info("超时游戏结束: roomId={}, timeoutUserId={}", roomId, userId);
        } else {
            log.warn("超时处理失败: 不是当前回合玩家 userId={}", userId);
        }
    }

    /**
     * 悔棋
     */
    public void undoMove(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        if (room.isGameOver()) {
            throw new RuntimeException("游戏已结束，无法悔棋");
        }

        GameRoom.Move lastMove = room.undoMove();
        if (lastMove == null) {
            throw new RuntimeException("没有可以悔棋的步骤");
        }

        // 清除棋盘上的棋子
        Board board = room.getBoard();
        board.clearCell(lastMove.x, lastMove.y);

        // 广播悔棋后的游戏状态
        Map<String, Object> state = new HashMap<>();
        state.put("type", "UNDO_SUCCESS");
        state.put("roomId", room.getRoomId());
        state.put("board", room.getBoard().getCells());
        state.put("currentTurn", room.getPieceColor(room.getCurrentTurn()));
        state.put("blackTime", room.getBlackTimeRemaining());
        state.put("whiteTime", room.getWhiteTimeRemaining());
        state.put("undoneMove", Map.of("x", lastMove.x, "y", lastMove.y, "color", lastMove.color));
        state.put("gameStatus", "PLAYING");

        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), state);

        log.info("悔棋成功: roomId={}, x={}, y={}, color={}", roomId, lastMove.x, lastMove.y, lastMove.color);
    }

    /**
     * 设置玩家再来一局同意状态
     */
    public void setPlayAgainAgreement(String roomId, Long userId, boolean agreed) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        room.setPlayAgainAgreement(userId, agreed);
    }

    /**
     * 检查双方是否都同意再来一局
     */
    public boolean checkBothPlayersAgreePlayAgain(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        return room.bothPlayersAgreePlayAgain();
    }

    /**
     * 重置游戏（再来一局）
     */
    public void resetGame(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        room.resetForNewGame();
        log.info("游戏重置: roomId={}", roomId);
    }

    /**
     * 获取棋盘状态
     */
    public int[][] getBoardState(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        return room.getBoard().getCells();
    }

    /**
     * 处理游戏结束
     */
    private void handleGameOver(GameRoom room, Long winnerId, int winColor, int endReason) {
        room.setGameOver(true);
        room.setWinnerId(winnerId);
        room.setEndReason(endReason);

        // 获取玩家信息
        User blackPlayer = userService.getUserById(room.getBlackPlayerId());
        User whitePlayer = userService.getUserById(room.getWhitePlayerId());

        // 计算积分变化 - 只在竞技模式计算积分
        int[] ratingChanges = new int[] { 0, 0 };
        String mode = room.getMode();

        if ("ranked".equals(mode) && endReason != 2) { // 竞技模式且不是和棋
            int blackRatingBefore = blackPlayer.getRating();
            int whiteRatingBefore = whitePlayer.getRating();

            ratingChanges = eloCalculator.calculateRatingChange(
                blackRatingBefore,
                whiteRatingBefore
            );

            // 更新积分
            userService.updateUserRating(room.getBlackPlayerId(), blackRatingBefore + ratingChanges[0]);
            userService.updateUserRating(room.getWhitePlayerId(), whiteRatingBefore + ratingChanges[1]);

            log.info("积分更新: 黑棋 {} -> {}, 白棋 {} -> {}",
                blackRatingBefore, blackRatingBefore + ratingChanges[0],
                whiteRatingBefore, whiteRatingBefore + ratingChanges[1]);
        }

        // 记录对局
        GameRecord record = new GameRecord();
        record.setRoomId(room.getRoomId());
        record.setBlackPlayerId(room.getBlackPlayerId());
        record.setWhitePlayerId(room.getWhitePlayerId());
        record.setWinnerId(winnerId);
        record.setWinColor(winColor);
        record.setEndReason(endReason);
        record.setBlackRatingChange(ratingChanges[0]);
        record.setWhiteRatingChange(ratingChanges[1]);
        record.setGameMode(mode);
        record.setCreatedAt(LocalDateTime.now());

        // TODO: 保存对局记录
        // gameRecordMapper.insert(record);

        // 增加经验值（和棋时双方各得 50 经验）
        if (endReason == 2) { // 和棋
            userService.addUserExp(room.getBlackPlayerId(), 50);
            userService.addUserExp(room.getWhitePlayerId(), 50);
        } else {
            userService.addUserExp(winnerId, 100);
            userService.addUserExp(winnerId == room.getBlackPlayerId() ? room.getWhitePlayerId() : room.getBlackPlayerId(), 50);
        }

        // 广播游戏结束消息
        broadcastGameOver(room, record);

        log.info("游戏结束: roomId={}, mode={}, winnerId={}, winColor={}, endReason={}", room.getRoomId(), mode, winnerId, winColor, endReason);
    }

    /**
     * 广播游戏状态
     */
    private void broadcastGameState(GameRoom room) {
        room.updateCurrentPlayerTime();

        Map<String, Object> state = new HashMap<>();
        state.put("type", "GAME_STATE");
        state.put("roomId", room.getRoomId());
        state.put("board", room.getBoard().getCells());
        state.put("currentTurn", room.getPieceColor(room.getCurrentTurn()));
        state.put("gameStatus", room.isGameOver() ? "FINISHED" : "PLAYING");
        state.put("blackTime", room.getBlackTimeRemaining());
        state.put("whiteTime", room.getWhiteTimeRemaining());

        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), state);
    }

    /**
     * 广播房间状态
     */
    private void broadcastRoomState(GameRoom room) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "ROOM_STATE");
        state.put("roomId", room.getRoomId());
        state.put("players", room.getPlayers());
        state.put("gameStatus", room.isGameOver() ? "FINISHED" : "PLAYING");

        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), state);
    }

    /**
     * 广播游戏结束
     */
    private void broadcastGameOver(GameRoom room, GameRecord record) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "GAME_OVER");
        state.put("roomId", room.getRoomId());
        state.put("winnerId", record.getWinnerId());
        state.put("winColor", record.getWinColor());
        state.put("endReason", record.getEndReason());
        state.put("mode", record.getGameMode());
        state.put("blackRatingChange", record.getBlackRatingChange());
        state.put("whiteRatingChange", record.getWhiteRatingChange());

        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), state);
    }

    /**
     * 序列化棋盘
     */
    private String serializeBoard(Board board) {
        int[] array = board.toArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            sb.append(array[i]);
            if (i < array.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    /**
     * 生成房间ID
     */
    private String generateRoomId() {
        return String.valueOf(System.currentTimeMillis());
    }

    /**
     * 游戏房间内部类
     */
    private static class GameRoom {
        private final String roomId;
        private final String mode;
        private final Long blackPlayerId;
        private Long whitePlayerId;
        private final Board board;
        private Long currentTurn;
        private boolean gameOver;
        private Long winnerId;
        private int endReason;

        // 移动历史（用于悔棋）
        private final java.util.List<Move> moveHistory = new java.util.ArrayList<>();

        // 再来一局状态追踪
        private boolean blackPlayerWantsPlayAgain = false;
        private boolean whitePlayerWantsPlayAgain = false;

        // 倒计时（秒）- 每方各自5分钟
        private long blackTimeRemaining = 300;
        private long whiteTimeRemaining = 300;
        private long lastUpdateTime = System.currentTimeMillis();

        public GameRoom(String roomId, Long creatorId, String mode) {
            this.roomId = roomId;
            this.mode = mode;
            this.blackPlayerId = creatorId;
            this.currentTurn = creatorId;
            this.board = new Board();
            this.gameOver = false;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public void addPlayer(Long userId) {
            if (whitePlayerId == null && !userId.equals(blackPlayerId)) {
                whitePlayerId = userId;
            }
        }

        public void switchTurn() {
            currentTurn = currentTurn.equals(blackPlayerId) ? whitePlayerId : blackPlayerId;
            lastUpdateTime = System.currentTimeMillis();
        }

        public int getPieceColor(Long userId) {
            return userId.equals(blackPlayerId) ? 1 : 2;
        }

        public Long getPlayerByColor(int color) {
            return color == 1 ? blackPlayerId : whitePlayerId;
        }

        public Board getBoard() {
            return board;
        }

        /**
         * 添加移动记录
         */
        public void addMove(int x, int y, int color) {
            moveHistory.add(new Move(x, y, color));
        }

        /**
         * 悔棋 - 移除最后一步
         */
        public Move undoMove() {
            if (!moveHistory.isEmpty()) {
                Move lastMove = moveHistory.remove(moveHistory.size() - 1);
                // 切换回合回退
                currentTurn = currentTurn.equals(blackPlayerId) ? whitePlayerId : blackPlayerId;
                return lastMove;
            }
            return null;
        }

        /**
         * 获取最后一步移动
         */
        public Move getLastMove() {
            return moveHistory.isEmpty() ? null : moveHistory.get(moveHistory.size() - 1);
        }

        /**
         * 获取移动历史
         */
        public java.util.List<Move> getMoveHistory() {
            return new java.util.ArrayList<>(moveHistory);
        }

        /**
         * 更新当前玩家的倒计时
         */
        public void updateCurrentPlayerTime() {
            long now = System.currentTimeMillis();
            long elapsed = (now - lastUpdateTime) / 1000; // 转换为秒
            lastUpdateTime = now;

            if (currentTurn.equals(blackPlayerId)) {
                blackTimeRemaining = Math.max(0, blackTimeRemaining - elapsed);
            } else {
                whiteTimeRemaining = Math.max(0, whiteTimeRemaining - elapsed);
            }
        }

        /**
         * 获取黑棋剩余时间
         */
        public long getBlackTimeRemaining() {
            return blackTimeRemaining;
        }

        /**
         * 获取白棋剩余时间
         */
        public long getWhiteTimeRemaining() {
            return whiteTimeRemaining;
        }

        /**
         * 玩家同意再来一局
         */
        public void setPlayAgainAgreement(Long userId, boolean agreed) {
            if (userId.equals(blackPlayerId)) {
                blackPlayerWantsPlayAgain = agreed;
            } else if (userId.equals(whitePlayerId)) {
                whitePlayerWantsPlayAgain = agreed;
            }
        }

        /**
         * 检查双方是否都同意再来一局
         */
        public boolean bothPlayersAgreePlayAgain() {
            return blackPlayerWantsPlayAgain && whitePlayerWantsPlayAgain;
        }

        /**
         * 重置游戏（再来一局）
         */
        public void resetForNewGame() {
            // 清空棋盘
            board.clear();
            // 清空移动历史
            moveHistory.clear();
            // 重置回合（黑棋先手）
            currentTurn = blackPlayerId;
            // 重置游戏状态
            gameOver = false;
            winnerId = null;
            endReason = 0;
            // 重置再来一局状态
            blackPlayerWantsPlayAgain = false;
            whitePlayerWantsPlayAgain = false;
            // 重置倒计时
            blackTimeRemaining = 300;
            whiteTimeRemaining = 300;
            lastUpdateTime = System.currentTimeMillis();
        }

        // Getters
        public String getRoomId() { return roomId; }
        public String getMode() { return mode; }
        public Long getBlackPlayerId() { return blackPlayerId; }
        public Long getWhitePlayerId() { return whitePlayerId; }
        public Long getCurrentTurn() { return currentTurn; }
        public boolean isGameOver() { return gameOver; }
        public Long getWinnerId() { return winnerId; }
        public int getEndReason() { return endReason; }

        public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }
        public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }
        public void setEndReason(int endReason) { this.endReason = endReason; }

        /**
         * 移动记录类
         */
        public static class Move {
            public final int x;
            public final int y;
            public final int color;
            public final long timestamp;

            public Move(int x, int y, int color) {
                this.x = x;
                this.y = y;
                this.color = color;
                this.timestamp = System.currentTimeMillis();
            }
        }

        public Map<String, Object> getPlayers() {
            Map<String, Object> players = new HashMap<>();
            User blackUser = new User(); // TODO: 获取用户信息
            User whiteUser = new User();
            players.put("black", blackUser);
            players.put("white", whiteUser);
            return players;
        }
    }
}
