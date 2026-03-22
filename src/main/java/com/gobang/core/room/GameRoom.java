package com.gobang.core.room;

import com.gobang.core.game.Board;
import com.gobang.core.game.GameState;
import com.gobang.core.game.WinChecker;
import com.gobang.model.entity.GameRecord;
import com.gobang.protocol.protobuf.GobangProto;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 游戏房间
 */
public class GameRoom {

    private static final Logger logger = LoggerFactory.getLogger(GameRoom.class);
    private static final Gson gson = new Gson();

    private final String roomId;
    private final long createTime;

    // 玩家信息
    private Long blackPlayerId;
    private Long whitePlayerId;
    private Channel blackChannel;
    private Channel whiteChannel;

    // 房间创建者信息（用于等待玩家加入）
    private Long creatorId;
    private String creatorNickname;
    private String creatorUsername;
    private Channel creatorChannel;

    // 观战者
    private final CopyOnWriteArraySet<Observer> observers = new CopyOnWriteArraySet<>();

    // 游戏状态
    private GameState gameState = GameState.WAITING;
    private final Board board = new Board();
    private int currentPlayer = Board.BLACK; // 1=黑先, 2=白后
    private int moveCount = 0;
    private long lastMoveTime = 0;
    private final List<int[]> moves = new ArrayList<>(); // 落子记录

    // 游戏开始时间
    private long gameStartTime = 0;
    private long moveTimeoutMillis = 120000; // 单次思考超时2分钟
    private long totalThinkTimeMillis = 300000; // 每方总思考时间5分钟
    private long blackPlayerRemainingTime = 300000; // 黑方剩余时间
    private long whitePlayerRemainingTime = 300000; // 白方剩余时间
    private volatile boolean undoRequested = false; // 是否有悔棋请求
    private Long undoRequesterId = null; // 悔棋请求者
    private String gameMode = "competitive"; // 游戏模式: "casual"=休闲, "competitive"=竞技
    private Long lastMovePlayerId = null; // 最后落子的玩家ID（用于判定胜负）
    private int lastMoveColor = 0; // 最后落子的颜色（1=黑, 2=白）

    public GameRoom(String roomId) {
        this.roomId = roomId;
        this.createTime = System.currentTimeMillis();
    }

    public GameRoom(String roomId, long moveTimeoutMillis) {
        this.roomId = roomId;
        this.moveTimeoutMillis = moveTimeoutMillis;
        this.createTime = System.currentTimeMillis();
    }

    /**
     * 设置落子超时时间
     */
    public void setMoveTimeout(long timeoutMillis) {
        this.moveTimeoutMillis = timeoutMillis;
    }

    /**
     * 检查是否超时
     * @return 超时返回true
     */
    public boolean isTimeout() {
        if (gameState != GameState.PLAYING || lastMoveTime == 0) {
            return false;
        }
        return System.currentTimeMillis() - lastMoveTime > moveTimeoutMillis;
    }

    /**
     * 获取超时玩家ID（超时者判负）
     */
    public Long getTimeoutPlayerId() {
        if (!isTimeout()) {
            return null;
        }
        // 当前应该落子的玩家超时
        return currentPlayer == Board.BLACK ? blackPlayerId : whitePlayerId;
    }

    /**
     * 加入观战
     */
    public void addObserver(Long userId, String username, String nickname, Channel channel) {
        Observer observer = new Observer(userId, username, nickname, channel);
        observers.add(observer);
        logger.info("Observer {} joined room {}", userId, roomId);
    }

    /**
     * 离开观战
     */
    public void removeObserver(Long userId) {
        observers.removeIf(o -> o.userId.equals(userId));
        logger.info("Observer {} left room {}", userId, roomId);
    }

    /**
     * 获取观战者数量
     */
    public int getObserverCount() {
        return observers.size();
    }

    /**
     * 获取观战者列表
     */
    public List<Observer> getObservers() {
        return new ArrayList<>(observers);
    }

    /**
     * 开始游戏
     */
    public void startGame(Long blackId, Channel blackCh, Long whiteId, Channel whiteCh) {
        this.blackPlayerId = blackId;
        this.blackChannel = blackCh;
        this.whitePlayerId = whiteId;
        this.whiteChannel = whiteCh;
        this.gameState = GameState.PLAYING;
        this.currentPlayer = Board.BLACK;
        this.gameStartTime = System.currentTimeMillis();
        this.lastMoveTime = gameStartTime;
        this.lastMovePlayerId = null;
        this.lastMoveColor = 0;

        // 初始化双方剩余时间
        this.blackPlayerRemainingTime = totalThinkTimeMillis;
        this.whitePlayerRemainingTime = totalThinkTimeMillis;

        logger.info("Game started in room {}: black={}, white={}", roomId, blackId, whiteId);
    }

    /**
     * 设置游戏模式
     */
    public void setGameMode(String mode) {
        this.gameMode = mode;
        logger.info("Game mode set to {} for room {}", mode, roomId);
    }

    /**
     * 落子
     *
     * @return 0=成功, 1=位置无效, 2=不是你的回合, 3=游戏已结束, 4=位置已有棋子, 5=思考时间用完
     */
    public synchronized int makeMove(Long userId, int x, int y) {
        if (gameState != GameState.PLAYING) {
            return 3;
        }

        int playerColor = (userId.equals(blackPlayerId)) ? Board.BLACK : Board.WHITE;
        if (playerColor != currentPlayer) {
            return 2;
        }

        // 检查思考时间是否用完
        if (playerColor == Board.BLACK && blackPlayerRemainingTime <= 0) {
            gameState = GameState.FINISHED;
            logger.info("Black player time out in room {}", roomId);
            return 5;
        }
        if (playerColor == Board.WHITE && whitePlayerRemainingTime <= 0) {
            gameState = GameState.FINISHED;
            logger.info("White player time out in room {}", roomId);
            return 5;
        }

        if (!board.place(x, y, playerColor)) {
            return board.isEmpty(x, y) ? 4 : 1;
        }

        // 计算思考时间并从总时间中扣除
        long thinkTime = lastMoveTime > 0 ? System.currentTimeMillis() - lastMoveTime : 0;
        // 第一手棋不计时间
        if (moves.isEmpty()) {
            thinkTime = 0;
        }

        // 扣除当前玩家的思考时间
        if (playerColor == Board.BLACK) {
            blackPlayerRemainingTime -= thinkTime;
            if (blackPlayerRemainingTime < 0) blackPlayerRemainingTime = 0;
            logger.debug("Black player remaining time: {}ms", blackPlayerRemainingTime);
        } else {
            whitePlayerRemainingTime -= thinkTime;
            if (whitePlayerRemainingTime < 0) whitePlayerRemainingTime = 0;
            logger.debug("White player remaining time: {}ms", whitePlayerRemainingTime);
        }

        moves.add(new int[]{x, y, playerColor});
        moveCount++;
        lastMoveTime = System.currentTimeMillis();

        // 记录最后落子的玩家信息（用于胜负判定）
        lastMovePlayerId = userId;
        lastMoveColor = playerColor;

        // 检查胜负
        if (WinChecker.checkWin(board, x, y, playerColor)) {
            gameState = GameState.FINISHED;
            logger.info("Player {} (color:{}) wins in room {}", userId, playerColor, roomId);
            return 0;
        }

        // 检查平局
        if (WinChecker.checkDraw(board)) {
            gameState = GameState.FINISHED;
            lastMovePlayerId = null; // 平局没有获胜者
            logger.info("Draw in room {}", roomId);
            return 0;
        }

        // 切换玩家
        currentPlayer = (currentPlayer == Board.BLACK) ? Board.WHITE : Board.BLACK;
        return 0;
    }

    /**
     * 认输
     */
    public void resign(Long userId) {
        if (gameState != GameState.PLAYING) {
            return;
        }
        gameState = GameState.FINISHED;
        logger.info("Player {} resigned in room {}", userId, roomId);
    }

    /**
     * 请求悔棋
     * @return 0=成功, 1=游戏未进行, 2=无棋可悔, 3=已有悔棋请求
     */
    public synchronized int requestUndo(Long userId) {
        if (gameState != GameState.PLAYING) {
            return 1;
        }
        if (moves.isEmpty()) {
            return 2;
        }
        if (undoRequested) {
            return 3;
        }

        undoRequested = true;
        undoRequesterId = userId;
        logger.info("Undo requested by user {} in room {}", userId, roomId);
        return 0;
    }

    /**
     * 响应悔棋
     * @return null=失败, otherwise 返回被撤销的落子信息
     */
    public synchronized int[] respondUndo(Long userId, boolean accept) {
        logger.info("=== GameRoom.respondUndo === roomId: {}, userId: {}, accept: {}, undoRequested: {}, undoRequesterId: {}",
            roomId, userId, accept, undoRequested, undoRequesterId);

        if (!undoRequested) {
            logger.warn("respondUndo - 没有悔棋请求");
            return null;
        }

        // 只有对手可以响应悔棋请求（请求者自己不能响应）
        if (userId.equals(undoRequesterId)) {
            logger.warn("respondUndo - 悔棋请求者不能响应自己的请求: userId={}, undoRequesterId={}", userId, undoRequesterId);
            return null;
        }

        logger.info("respondUndo - 黑方: {}, 白方: {}, 当前用户: {}, 请求者: {}",
            blackPlayerId, whitePlayerId, userId, undoRequesterId);

        if (accept && !moves.isEmpty()) {
            // 执行悔棋
            int[] lastMove = moves.remove(moves.size() - 1);
            board.clearCell(lastMove[0], lastMove[1]);
            moveCount--;
            currentPlayer = lastMove[2]; // 切换回悔棋的玩家
            lastMoveTime = System.currentTimeMillis();
            undoRequested = false;
            undoRequesterId = null;

            logger.info("Undo accepted in room {}, removed move at ({}, {}), 剩余棋子: {}",
                roomId, lastMove[0], lastMove[1], moves.size());
            return lastMove;
        } else {
            // 拒绝悔棋
            undoRequested = false;
            undoRequesterId = null;
            logger.info("Undo rejected in room {}", roomId);
            return null;
        }
    }

    /**
     * 取消悔棋请求
     */
    public synchronized void cancelUndoRequest(Long userId) {
        if (undoRequested && userId.equals(undoRequesterId)) {
            undoRequested = false;
            undoRequesterId = null;
            logger.info("Undo request cancelled by user {} in room {}", userId, roomId);
        }
    }

    /**
     * 是否有悔棋请求
     */
    public boolean hasUndoRequest() {
        return undoRequested;
    }

    /**
     * 获取悔棋请求者ID
     */
    public Long getUndoRequesterId() {
        return undoRequesterId;
    }

    /**
     * 获取对局时长（秒）
     */
    public int getDuration() {
        if (gameStartTime == 0) {
            return 0;
        }
        long endTime = (gameState == GameState.FINISHED) ? lastMoveTime : System.currentTimeMillis();
        return (int) ((endTime - gameStartTime) / 1000);
    }

    /**
     * 创建游戏记录
     */
    public GameRecord createRecord(Long winnerId, int endReason,
                                   int blackRatingBefore, int whiteRatingBefore,
                                   int blackRatingChange, int whiteRatingChange) {
        GameRecord record = new GameRecord();
        record.setRoomId(roomId);
        record.setBlackPlayerId(blackPlayerId);
        record.setWhitePlayerId(whitePlayerId);
        record.setWinnerId(winnerId);
        record.setWinColor(winnerId != null ? (winnerId.equals(blackPlayerId) ? 1 : 2) : null);
        record.setEndReason(endReason);
        record.setMoveCount(moveCount);
        record.setDuration(getDuration());
        record.setBlackRatingBefore(blackRatingBefore);
        record.setBlackRatingAfter(blackRatingBefore + blackRatingChange);
        record.setBlackRatingChange(blackRatingChange);
        record.setWhiteRatingBefore(whiteRatingBefore);
        record.setWhiteRatingAfter(whiteRatingBefore + whiteRatingChange);
        record.setWhiteRatingChange(whiteRatingChange);
        record.setBoardState(compressBoard(board.toArray()));
        record.setMoves(gson.toJson(moves));
        record.setGameMode(gameMode); // 设置游戏模式
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    /**
     * 压缩棋盘数据
     */
    private String compressBoard(int[] board) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < board.length; i++) {
            sb.append(board[i]);
        }
        return sb.toString();
    }

    /**
     * 构建游戏状态消息
     */
    public GobangProto.GameState buildGameStateProto() {
        GobangProto.GameState.Builder builder = GobangProto.GameState.newBuilder()
                .setStatus(GobangProto.GameState.Status.valueOf(gameState.name()))
                .setCurrentPlayer(currentPlayer)
                .setMoveCount(moveCount)
                .setLastMoveTime(lastMoveTime);

        for (int cell : board.toArray()) {
            builder.addBoard(cell);
        }

        return builder.build();
    }

    /**
     * 广播消息给所有玩家和观战者
     */
    public void broadcast(GobangProto.Packet packet) {
        // 发送给黑方
        if (blackChannel != null && blackChannel.isActive()) {
            blackChannel.writeAndFlush(packet);
        }

        // 发送给白方
        if (whiteChannel != null && whiteChannel.isActive()) {
            whiteChannel.writeAndFlush(packet);
        }

        // 发送给观战者
        for (Observer observer : observers) {
            if (observer.channel.isActive()) {
                observer.channel.writeAndFlush(packet);
            }
        }
    }

    // Getters
    public String getRoomId() {
        return roomId;
    }

    public GameState getGameState() {
        return gameState;
    }

    public Long getBlackPlayerId() {
        return blackPlayerId;
    }

    public Long getWhitePlayerId() {
        return whitePlayerId;
    }

    public Channel getBlackChannel() {
        return blackChannel;
    }

    public void setBlackChannel(Channel channel) {
        this.blackChannel = channel;
    }

    public Channel getWhiteChannel() {
        return whiteChannel;
    }

    public void setWhiteChannel(Channel channel) {
        this.whiteChannel = channel;
    }

    public long getBlackPlayerRemainingTime() {
        return blackPlayerRemainingTime;
    }

    public long getWhitePlayerRemainingTime() {
        return whitePlayerRemainingTime;
    }

    public int getCurrentPlayer() {
        return currentPlayer;
    }

    public long getCreateTime() {
        return createTime;
    }

    public Board getBoard() {
        return board;
    }

    public List<int[]> getMoves() {
        return new ArrayList<>(moves);
    }

    public String getGameMode() {
        return gameMode;
    }

    public Long getLastMovePlayerId() {
        return lastMovePlayerId;
    }

    public int getLastMoveColor() {
        return lastMoveColor;
    }

    /**
     * 设置房间创建者信息
     */
    public void setCreator(Long userId, String nickname, String username, Channel channel) {
        this.creatorId = userId;
        this.creatorNickname = nickname;
        this.creatorUsername = username;
        this.creatorChannel = channel;
        logger.info("Room {} creator set: {} ({})", roomId, nickname, userId);
    }

    /**
     * 获取房间创建者信息
     */
    public Long getCreatorId() {
        return creatorId;
    }

    public String getCreatorNickname() {
        return creatorNickname;
    }

    public String getCreatorUsername() {
        return creatorUsername;
    }

    public Channel getCreatorChannel() {
        return creatorChannel;
    }

    /**
     * 检查是否有创建者
     */
    public boolean hasCreator() {
        return creatorId != null;
    }

    /**
     * 更新玩家通道（用于重连）
     */
    public void updatePlayerChannel(Long userId, Channel newChannel) {
        if (userId.equals(blackPlayerId)) {
            this.blackChannel = newChannel;
            logger.info("Updated black channel for user {} in room {}", userId, roomId);
        } else if (userId.equals(whitePlayerId)) {
            this.whiteChannel = newChannel;
            logger.info("Updated white channel for user {} in room {}", userId, roomId);
        }
    }

    /**
     * 重置游戏（用于再来一局）
     */
    public synchronized void resetGame() {
        this.board.reset();
        this.moves.clear();
        this.gameState = GameState.WAITING;
        this.currentPlayer = Board.BLACK;
        this.moveCount = 0;
        this.lastMoveTime = 0;
        this.lastMovePlayerId = null;
        this.lastMoveColor = 0;
        this.undoRequested = false;
        this.undoRequesterId = null;
        logger.info("Game reset in room {}", roomId);
    }

    /**
     * 开始游戏（重置后重新开始）
     */
    public synchronized void restartGame() {
        this.board.reset();
        this.moves.clear();
        this.gameState = GameState.PLAYING;
        this.currentPlayer = Board.BLACK;
        this.moveCount = 0;
        this.lastMoveTime = System.currentTimeMillis();
        this.lastMovePlayerId = null;
        this.lastMoveColor = 0;
        this.undoRequested = false;
        this.undoRequesterId = null;
        // 重置双方剩余时间
        this.blackPlayerRemainingTime = totalThinkTimeMillis;
        this.whitePlayerRemainingTime = totalThinkTimeMillis;
        logger.info("Game restarted in room {}", roomId);
    }

    /**
     * 检查是否超时
     * 规则：单次思考超过2分钟判负，或总时间用完判负
     * @return 超时的玩家ID，如果没有超时返回null
     */
    public synchronized Long checkTimeout() {
        if (gameState != GameState.PLAYING) {
            return null;
        }

        // 计算从最后一次落子到现在经过的时间
        long timeSinceLastMove = lastMoveTime > 0 ? System.currentTimeMillis() - lastMoveTime : 0;

        // 首先检查单次思考时间是否超过2分钟（120秒）
        if (timeSinceLastMove > moveTimeoutMillis) {
            gameState = GameState.FINISHED;
            Long timeoutPlayerId = currentPlayer == Board.BLACK ? blackPlayerId : whitePlayerId;
            String timeoutPlayerColor = currentPlayer == Board.BLACK ? "黑方" : "白方";
            Long winnerId = currentPlayer == Board.BLACK ? whitePlayerId : blackPlayerId;
            String winnerColor = currentPlayer == Board.BLACK ? "白方" : "黑方";

            logger.info("=== 单步思考超时 === 房间: {}, 当前玩家: {}, 超时玩家ID: {}, 超时玩家颜色: {}, " +
                    "经过时间: {}ms, 限制: {}ms, 获胜者ID: {}, 获胜者颜色: {}",
                roomId, currentPlayer, timeoutPlayerId, timeoutPlayerColor,
                timeSinceLastMove, moveTimeoutMillis, winnerId, winnerColor);

            // 返回当前应该落子的玩家ID（判负）
            return timeoutPlayerId;
        }

        // 检查当前玩家的时间是否用完（需要加上经过的时间）
        long currentBlackRemaining = blackPlayerRemainingTime - (currentPlayer == Board.BLACK ? timeSinceLastMove : 0);
        long currentWhiteRemaining = whitePlayerRemainingTime - (currentPlayer == Board.WHITE ? timeSinceLastMove : 0);

        if (currentPlayer == Board.BLACK && currentBlackRemaining <= 0) {
            gameState = GameState.FINISHED;
            blackPlayerRemainingTime = 0;
            logger.info("=== 总时间用完超时 === 房间: {}, 黑方总时间用完, 剩余: {}ms, 白方获胜",
                roomId, currentBlackRemaining);
            return blackPlayerId;
        }
        if (currentPlayer == Board.WHITE && currentWhiteRemaining <= 0) {
            gameState = GameState.FINISHED;
            whitePlayerRemainingTime = 0;
            logger.info("=== 总时间用完超时 === 房间: {}, 白方总时间用完, 剩余: {}ms, 黑方获胜",
                roomId, currentWhiteRemaining);
            return whitePlayerId;
        }

        return null;
    }

    /**
     * 获取剩余时间（毫秒）
     * 返回当前应该落子的玩家的剩余总思考时间
     */
    public synchronized long getRemainingTime() {
        if (gameState != GameState.PLAYING) {
            return 0;
        }

        // 返回当前应该落子的玩家的剩余时间
        long remainingTime = (currentPlayer == Board.BLACK) ? blackPlayerRemainingTime : whitePlayerRemainingTime;
        return Math.max(0, remainingTime);
    }

    /**
     * 观战者信息
     */
    public static class Observer {
        private final Long userId;
        private final String username;
        private final String nickname;
        private final Channel channel;

        public Observer(Long userId, String username, String nickname, Channel channel) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.channel = channel;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getNickname() {
            return nickname;
        }

        public Channel getChannel() {
            return channel;
        }
    }

    /**
     * 序列化游戏状态为JSON（用于保存到Redis）
     */
    public String serializeGameState() {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> state = new java.util.HashMap<>();

            state.put("roomId", roomId);
            state.put("gameState", gameState.name());
            state.put("gameMode", gameMode);
            state.put("currentPlayer", currentPlayer);
            state.put("blackPlayerId", blackPlayerId);
            state.put("whitePlayerId", whitePlayerId);
            state.put("blackPlayerRemainingTime", blackPlayerRemainingTime);
            state.put("whitePlayerRemainingTime", whitePlayerRemainingTime);
            state.put("moveCount", moveCount);
            state.put("gameStartTime", gameStartTime);
            state.put("lastMoveTime", lastMoveTime);
            state.put("lastMovePlayerId", lastMovePlayerId);
            state.put("lastMoveColor", lastMoveColor);
            state.put("totalThinkTimeMillis", totalThinkTimeMillis);

            // 序列化棋盘
            state.put("board", board.toArray());

            // 序列化落子记录
            java.util.List<int[]> movesList = new java.util.ArrayList<>(moves);
            state.put("moves", movesList);

            return mapper.writeValueAsString(state);
        } catch (Exception e) {
            logger.error("Failed to serialize game state for room {}", roomId, e);
            return null;
        }
    }
}
