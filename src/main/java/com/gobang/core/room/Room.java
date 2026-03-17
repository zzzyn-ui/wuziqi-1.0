package com.gobang.core.room;

import com.gobang.core.game.Board;
import com.gobang.core.game.GameState;
import com.gobang.core.game.WinChecker;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 五子棋游戏房间 - 完整实现
 *
 * 功能：
 * 1. 创建房间（匹配成功时）
 * 2. 落子处理（合法性校验、回合校验）
 * 3. 胜负判定（四向扫描）
 * 4. 房间状态维护
 * 5. 断线重连支持
 *
 * @author Gobang Team
 */
public class Room {

    private static final Logger logger = LoggerFactory.getLogger(Room.class);

    // ==================== 常量 ====================
    private static final int BOARD_SIZE = 15;
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = 2;
    private static final long DEFAULT_MOVE_TIMEOUT = 300000; // 5分钟落子超时
    private static final long RECONNECT_WINDOW = 600000;     // 10分钟重连窗口

    // ==================== 房间基本信息 ====================
    private final String roomId;
    private final long createTime;
    private final AtomicLong lastActivityTime;

    // ==================== 玩家信息 ====================
    private RoomPlayer blackPlayer;
    private RoomPlayer whitePlayer;
    private final Map<Long, RoomPlayer> players = new ConcurrentHashMap<>();

    // ==================== 游戏状态 ====================
    private volatile GameState status = GameState.WAITING;
    private final Board board = new Board();
    private volatile int currentTurn = BLACK; // 1=黑 2=白
    private final AtomicInteger moveCount = new AtomicInteger(0);
    private volatile long lastMoveTime = 0;
    private final List<Move> moveHistory = new ArrayList<>();

    // ==================== 超时控制 ====================
    private final long moveTimeoutMillis;
    private volatile Long timeoutPlayerId = null;

    // ==================== 悔棋功能 ====================
    private volatile boolean undoRequested = false;
    private volatile Long undoRequesterId = null;

    // ==================== 观战者 ====================
    private final CopyOnWriteArraySet<Observer> observers = new CopyOnWriteArraySet<>();

    // ==================== 断线重连 ====================
    private final Map<Long, Long> disconnectTime = new ConcurrentHashMap<>(); // userId -> disconnect timestamp

    // ==================== 胜负信息 ====================
    private volatile Long winnerId = null;
    private volatile Integer endReason = null; // 0=胜利, 1=失败, 2=平局, 3=认输, 4=超时

    /**
     * 构造函数
     */
    public Room() {
        this.roomId = generateRoomId();
        this.createTime = System.currentTimeMillis();
        this.lastActivityTime = new AtomicLong(createTime);
        this.moveTimeoutMillis = DEFAULT_MOVE_TIMEOUT;
    }

    /**
     * 构造函数（带超时配置）
     */
    public Room(long moveTimeoutMillis) {
        this.roomId = generateRoomId();
        this.createTime = System.currentTimeMillis();
        this.lastActivityTime = new AtomicLong(createTime);
        this.moveTimeoutMillis = moveTimeoutMillis;
    }

    // ==================== 1. 创建房间（匹配成功时） ====================

    /**
     * 初始化房间 - 匹配成功后调用
     *
     * @param player1 玩家1
     * @param player2 玩家2
     * @return 初始化是否成功
     */
    public boolean initialize(RoomPlayer player1, RoomPlayer player2) {
        if (status != GameState.WAITING) {
            logger.warn("Room {} is not in WAITING status", roomId);
            return false;
        }

        // 随机决定先手
        boolean player1First = Math.random() < 0.5;

        if (player1First) {
            this.blackPlayer = player1;
            this.whitePlayer = player2;
        } else {
            this.blackPlayer = player2;
            this.whitePlayer = player1;
        }

        // 设置玩家颜色
        blackPlayer.setColor(BLACK);
        whitePlayer.setColor(WHITE);

        // 注册玩家
        players.put(blackPlayer.getUserId(), blackPlayer);
        players.put(whitePlayer.getUserId(), whitePlayer);

        // 开始游戏
        this.status = GameState.PLAYING;
        this.currentTurn = BLACK;
        this.lastMoveTime = System.currentTimeMillis();
        this.lastActivityTime.set(System.currentTimeMillis());

        logger.info("Room {} initialized: black={}, white={}",
            roomId, blackPlayer.getNickname(), whitePlayer.getNickname());

        return true;
    }

    /**
     * 获取房间状态摘要
     */
    public RoomSnapshot getSnapshot() {
        return new RoomSnapshot(
            roomId,
            status,
            blackPlayer != null ? blackPlayer.getUserId() : null,
            whitePlayer != null ? whitePlayer.getNickname() : null,
            whitePlayer != null ? whitePlayer.getUserId() : null,
            whitePlayer != null ? whitePlayer.getNickname() : null,
            currentTurn,
            moveCount.get(),
            getRemainingMoveTime()
        );
    }

    // ==================== 2. 落子处理（合法性校验、回合校验） ====================

    /**
     * 落子
     *
     * @param userId 用户ID
     * @param x      行 (0-14)
     * @param y      列 (0-14)
     * @return 落子结果
     */
    public synchronized MoveResult makeMove(Long userId, int x, int y) {
        // 更新活动时间
        lastActivityTime.set(System.currentTimeMillis());

        // 检查游戏状态
        if (status != GameState.PLAYING) {
            return MoveResult.failed(MoveErrorCode.GAME_FINISHED);
        }

        // 检查玩家是否在房间中
        RoomPlayer player = players.get(userId);
        if (player == null) {
            return MoveResult.failed(MoveErrorCode.NOT_IN_ROOM);
        }

        // 回合校验
        if (player.getColor() != currentTurn) {
            return MoveResult.failed(MoveErrorCode.NOT_YOUR_TURN);
        }

        // 位置合法性校验
        if (x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE) {
            return MoveResult.failed(MoveErrorCode.INVALID_POSITION);
        }

        // 检查位置是否为空
        if (board.getCell(x, y) != EMPTY) {
            return MoveResult.failed(MoveErrorCode.POSITION_OCCUPIED);
        }

        // 执行落子
        int color = player.getColor();
        if (!board.place(x, y, color)) {
            return MoveResult.failed(MoveErrorCode.PLACE_FAILED);
        }

        // 记录落子
        Move move = new Move(moveCount.get(), userId, color, x, y, System.currentTimeMillis());
        moveHistory.add(move);
        moveCount.incrementAndGet();
        lastMoveTime = System.currentTimeMillis();

        // 检查胜负
        if (WinChecker.checkWin(board, x, y, color)) {
            endGame(userId, 0); // 0 = 正常胜利
            return MoveResult.success(move, true, GameEndReason.WIN);
        }

        // 检查平局
        if (WinChecker.checkDraw(board)) {
            endGame(null, 2); // 2 = 平局
            return MoveResult.success(move, true, GameEndReason.DRAW);
        }

        // 切换回合
        currentTurn = (currentTurn == BLACK) ? WHITE : BLACK;

        logger.debug("Room {} move: {} ({},{}) by user {}, turn: {}",
            roomId, moveCount, x, y, userId, (currentTurn == BLACK ? "WHITE" : "BLACK"));

        return MoveResult.success(move, false, null);
    }

    // ==================== 3. 胜负判定（四向扫描） ====================

    /**
     * 检查是否有玩家获胜
     */
    public boolean checkWin() {
        // 检查最后一步
        if (moveHistory.isEmpty()) {
            return false;
        }

        Move lastMove = moveHistory.get(moveHistory.size() - 1);
        return WinChecker.checkWin(board, lastMove.getX(), lastMove.getY(), lastMove.getColor());
    }

    /**
     * 获取获胜线（用于高亮显示）
     */
    public int[] getWinningLine() {
        if (moveHistory.isEmpty()) {
            return null;
        }

        Move lastMove = moveHistory.get(moveHistory.size() - 1);
        return WinChecker.getWinningLine(board, lastMove.getX(), lastMove.getY(), lastMove.getColor());
    }

    /**
     * 结束游戏
     *
     * @param winnerId  获胜者ID（null表示平局）
     * @param endReason 结束原因
     */
    public synchronized void endGame(Long winnerId, int endReason) {
        if (status == GameState.FINISHED) {
            return;
        }

        this.status = GameState.FINISHED;
        this.winnerId = winnerId;
        this.endReason = endReason;
        this.lastActivityTime.set(System.currentTimeMillis());

        logger.info("Room {} game ended: winner={}, reason={}",
            roomId, winnerId, endReason);
    }

    /**
     * 认输
     */
    public synchronized MoveResult resign(Long userId) {
        if (status != GameState.PLAYING) {
            return MoveResult.failed(MoveErrorCode.GAME_FINISHED);
        }

        RoomPlayer player = players.get(userId);
        if (player == null) {
            return MoveResult.failed(MoveErrorCode.NOT_IN_ROOM);
        }

        // 对手获胜
        Long winnerId = userId.equals(blackPlayer.getUserId())
            ? whitePlayer.getUserId()
            : blackPlayer.getUserId();

        endGame(winnerId, 3); // 3 = 认输

        return MoveResult.resign(winnerId);
    }

    // ==================== 4. 房间状态维护 ====================

    /**
     * 检查是否超时
     */
    public boolean isTimeout() {
        if (status != GameState.PLAYING || lastMoveTime == 0) {
            return false;
        }
        return System.currentTimeMillis() - lastMoveTime > moveTimeoutMillis;
    }

    /**
     * 处理超时
     *
     * @return 超时玩家ID，如果没有超时返回null
     */
    public Long handleTimeout() {
        if (!isTimeout()) {
            return null;
        }

        timeoutPlayerId = (currentTurn == BLACK)
            ? blackPlayer.getUserId()
            : whitePlayer.getUserId();

        Long winnerId = (currentTurn == BLACK)
            ? whitePlayer.getUserId()
            : blackPlayer.getUserId();

        endGame(winnerId, 4); // 4 = 超时

        logger.warn("Room {} timeout: player={}, winner={}",
            roomId, timeoutPlayerId, winnerId);

        return timeoutPlayerId;
    }

    /**
     * 获取剩余落子时间（毫秒）
     */
    public long getRemainingMoveTime() {
        if (status != GameState.PLAYING || lastMoveTime == 0) {
            return -1;
        }
        long elapsed = System.currentTimeMillis() - lastMoveTime;
        return Math.max(0, moveTimeoutMillis - elapsed);
    }

    /**
     * 更新活动时间
     */
    public void updateActivity() {
        lastActivityTime.set(System.currentTimeMillis());
    }

    /**
     * 检查房间是否过期
     */
    public boolean isExpired(long expireMillis) {
        if (status == GameState.PLAYING) {
            return false;
        }
        return System.currentTimeMillis() - lastActivityTime.get() > expireMillis;
    }

    // ==================== 5. 断线重连支持 ====================

    /**
     * 处理玩家断线
     *
     * @param userId 用户ID
     * @return 是否在房间中
     */
    public boolean handleDisconnect(Long userId) {
        RoomPlayer player = players.get(userId);
        if (player == null) {
            return false;
        }

        disconnectTime.put(userId, System.currentTimeMillis());
        logger.info("Room {} player {} disconnected, reconnect window: {}s",
            roomId, userId, RECONNECT_WINDOW / 1000);

        return true;
    }

    /**
     * 处理玩家重连
     *
     * @param userId    用户ID
     * @param channel   新的连接
     * @return 重连是否成功
     */
    public boolean handleReconnect(Long userId, Channel channel) {
        Long disconnectTimestamp = disconnectTime.get(userId);
        if (disconnectTimestamp == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - disconnectTimestamp;
        if (elapsed > RECONNECT_WINDOW) {
            disconnectTime.remove(userId);
            logger.info("Room {} player {} reconnect window expired", roomId, userId);
            return false;
        }

        // 更新连接
        RoomPlayer player = players.get(userId);
        if (player != null) {
            player.setChannel(channel);
            disconnectTime.remove(userId);

            logger.info("Room {} player {} reconnected successfully", roomId, userId);
            return true;
        }

        return false;
    }

    /**
     * 获取断线玩家列表（用于重连）
     */
    public List<Long> getDisconnectedPlayers() {
        return new ArrayList<>(disconnectTime.keySet());
    }

    /**
     * 检查玩家是否在重连窗口内
     */
    public boolean isInReconnectWindow(Long userId) {
        Long disconnectTimestamp = disconnectTime.get(userId);
        if (disconnectTimestamp == null) {
            return false;
        }
        return System.currentTimeMillis() - disconnectTimestamp <= RECONNECT_WINDOW;
    }

    // ==================== 悔棋功能 ====================

    /**
     * 请求悔棋
     */
    public synchronized boolean requestUndo(Long userId) {
        if (status != GameState.PLAYING) {
            return false;
        }

        if (moveHistory.isEmpty()) {
            return false;
        }

        if (undoRequested) {
            return false;
        }

        undoRequested = true;
        undoRequesterId = userId;

        logger.info("Room {} undo requested by user {}", roomId, userId);
        return true;
    }

    /**
     * 响应悔棋
     */
    public synchronized boolean respondUndo(Long userId, boolean accept) {
        if (!undoRequested) {
            return false;
        }

        // 只有对手可以响应
        RoomPlayer requester = players.get(undoRequesterId);
        Long opponentId = (requester != null && requester.getUserId().equals(blackPlayer.getUserId()))
            ? whitePlayer.getUserId()
            : blackPlayer.getUserId();

        if (!userId.equals(opponentId)) {
            return false;
        }

        if (accept && !moveHistory.isEmpty()) {
            // 执行悔棋
            Move lastMove = moveHistory.remove(moveHistory.size() - 1);
            board.clearCell(lastMove.getX(), lastMove.getY());
            moveCount.decrementAndGet();
            currentTurn = lastMove.getColor();
            lastMoveTime = System.currentTimeMillis();

            logger.info("Room {} undo accepted: move ({},{}) removed",
                roomId, lastMove.getX(), lastMove.getY());
        }

        undoRequested = false;
        undoRequesterId = null;

        return true;
    }

    // ==================== 观战功能 ====================

    /**
     * 添加观战者
     */
    public void addObserver(Long userId, String nickname, Channel channel) {
        Observer observer = new Observer(userId, nickname, channel);
        observers.add(observer);
        logger.info("Room {} observer {} added", roomId, nickname);
    }

    /**
     * 移除观战者
     */
    public void removeObserver(Long userId) {
        observers.removeIf(obs -> obs.userId.equals(userId));
        logger.info("Room {} observer {} removed", roomId, userId);
    }

    /**
     * 获取观战者数量
     */
    public int getObserverCount() {
        return observers.size();
    }

    /**
     * 广播消息给所有玩家和观战者
     */
    public void broadcast(Object message) {
        // 发送给玩家
        if (blackPlayer != null && blackPlayer.getChannel() != null && blackPlayer.getChannel().isActive()) {
            blackPlayer.getChannel().writeAndFlush(message);
        }
        if (whitePlayer != null && whitePlayer.getChannel() != null && whitePlayer.getChannel().isActive()) {
            whitePlayer.getChannel().writeAndFlush(message);
        }

        // 发送给观战者
        for (Observer observer : observers) {
            if (observer.channel.isActive()) {
                observer.channel.writeAndFlush(message);
            }
        }
    }

    // ==================== Getter/Setter ====================

    public String getRoomId() {
        return roomId;
    }

    public long getCreateTime() {
        return createTime;
    }

    public GameState getStatus() {
        return status;
    }

    public Board getBoard() {
        return board;
    }

    public int getCurrentTurn() {
        return currentTurn;
    }

    public int getMoveCount() {
        return moveCount.get();
    }

    public List<Move> getMoveHistory() {
        return new ArrayList<>(moveHistory);
    }

    public RoomPlayer getBlackPlayer() {
        return blackPlayer;
    }

    public RoomPlayer getWhitePlayer() {
        return whitePlayer;
    }

    public Long getWinnerId() {
        return winnerId;
    }

    public Integer getEndReason() {
        return endReason;
    }

    public boolean isUndoRequested() {
        return undoRequested;
    }

    // ==================== 内部方法 ====================

    /**
     * 生成房间ID
     */
    private static String generateRoomId() {
        return String.format("%06d",
            Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1000000);
    }

    // ==================== 内部类 ====================

    /**
     * 房间玩家
     */
    public static class RoomPlayer {
        private final Long userId;
        private final String nickname;
        private final String username;
        private final Integer rating;
        private Channel channel;
        private int color; // 1=黑 2=白

        public RoomPlayer(Long userId, String username, String nickname, Integer rating, Channel channel) {
            this.userId = userId;
            this.username = username;
            this.nickname = nickname;
            this.rating = rating;
            this.channel = channel;
        }

        public Long getUserId() { return userId; }
        public String getNickname() { return nickname; }
        public String getUsername() { return username; }
        public Integer getRating() { return rating; }
        public Channel getChannel() { return channel; }
        public int getColor() { return color; }
        public void setColor(int color) { this.color = color; }
        public void setChannel(Channel channel) { this.channel = channel; }
    }

    /**
     * 落子记录
     */
    public static class Move {
        private final int sequence;
        private final Long userId;
        private final int color;
        private final int x;
        private final int y;
        private final long timestamp;

        public Move(int sequence, Long userId, int color, int x, int y, long timestamp) {
            this.sequence = sequence;
            this.userId = userId;
            this.color = color;
            this.x = x;
            this.y = y;
            this.timestamp = timestamp;
        }

        public int getSequence() { return sequence; }
        public Long getUserId() { return userId; }
        public int getColor() { return color; }
        public int getX() { return x; }
        public int getY() { return y; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 落子结果
     */
    public static class MoveResult {
        private final boolean success;
        private final MoveErrorCode errorCode;
        private final Move move;
        private final boolean gameEnded;
        private final GameEndReason endReason;
        private final Long winnerId;

        private MoveResult(boolean success, MoveErrorCode errorCode, Move move,
                          boolean gameEnded, GameEndReason endReason, Long winnerId) {
            this.success = success;
            this.errorCode = errorCode;
            this.move = move;
            this.gameEnded = gameEnded;
            this.endReason = endReason;
            this.winnerId = winnerId;
        }

        public static MoveResult success(Move move, boolean gameEnded, GameEndReason endReason) {
            return new MoveResult(true, null, move, gameEnded, endReason, null);
        }

        public static MoveResult failed(MoveErrorCode errorCode) {
            return new MoveResult(false, errorCode, null, false, null, null);
        }

        public static MoveResult resign(Long winnerId) {
            return new MoveResult(true, null, null, true, GameEndReason.RESIGN, winnerId);
        }

        public boolean isSuccess() { return success; }
        public MoveErrorCode getErrorCode() { return errorCode; }
        public Move getMove() { return move; }
        public boolean isGameEnded() { return gameEnded; }
        public GameEndReason getEndReason() { return endReason; }
        public Long getWinnerId() { return winnerId; }
    }

    /**
     * 落子错误码
     */
    public enum MoveErrorCode {
        INVALID_POSITION,   // 位置无效
        NOT_YOUR_TURN,      // 不是你的回合
        GAME_FINISHED,      // 游戏已结束
        POSITION_OCCUPIED,  // 位置已有棋子
        PLACE_FAILED,       // 落子失败
        NOT_IN_ROOM         // 不在房间中
    }

    /**
     * 游戏结束原因
     */
    public enum GameEndReason {
        WIN,    // 胜利
        LOSE,   // 失败
        DRAW,   // 平局
        RESIGN, // 认输
        TIMEOUT // 超时
    }

    /**
     * 房间状态快照
     */
    public static class RoomSnapshot {
        private final String roomId;
        private final GameState status;
        private final Long blackPlayerId;
        private final String blackPlayerName;
        private final Long whitePlayerId;
        private final String whitePlayerName;
        private final int currentTurn;
        private final int moveCount;
        private final long remainingMoveTime;

        public RoomSnapshot(String roomId, GameState status, Long blackPlayerId, String blackPlayerName,
                           Long whitePlayerId, String whitePlayerName, int currentTurn, int moveCount,
                           long remainingMoveTime) {
            this.roomId = roomId;
            this.status = status;
            this.blackPlayerId = blackPlayerId;
            this.blackPlayerName = blackPlayerName;
            this.whitePlayerId = whitePlayerId;
            this.whitePlayerName = whitePlayerName;
            this.currentTurn = currentTurn;
            this.moveCount = moveCount;
            this.remainingMoveTime = remainingMoveTime;
        }

        // Getters...
        public String getRoomId() { return roomId; }
        public GameState getStatus() { return status; }
        public Long getBlackPlayerId() { return blackPlayerId; }
        public String getBlackPlayerName() { return blackPlayerName; }
        public Long getWhitePlayerId() { return whitePlayerId; }
        public String getWhitePlayerName() { return whitePlayerName; }
        public int getCurrentTurn() { return currentTurn; }
        public int getMoveCount() { return moveCount; }
        public long getRemainingMoveTime() { return remainingMoveTime; }
    }

    /**
     * 观战者
     */
    private static class Observer {
        final Long userId;
        final String nickname;
        final Channel channel;

        Observer(Long userId, String nickname, Channel channel) {
            this.userId = userId;
            this.nickname = nickname;
            this.channel = channel;
        }
    }
}
