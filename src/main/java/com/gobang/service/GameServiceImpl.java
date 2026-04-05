package com.gobang.service;

import com.gobang.core.game.Board;
import com.gobang.core.game.WinChecker;
import com.gobang.core.rating.ELOCalculator;
import com.gobang.core.room.RoomManager;
import com.gobang.mapper.GameRecordMapper;
import com.gobang.model.entity.GameRecord;
import com.gobang.model.entity.User;
import com.gobang.model.enums.GameMode;
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
 * 使用 ConcurrentHashMap<String, GameRoom> 管理房间
 * 使用 SimpMessagingTemplate 发送 WebSocket 消息
 * 复用现有的 Board.java, WinChecker.java 和 ELOCalculator.java
 *
 * 支持两种游戏模式：
 * - casual (休闲): 不影响积分
 * - ranked (竞技): 使用ELO计算积分变化
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameServiceImpl implements GameService {

    private final UserServiceImpl userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ELOCalculator eloCalculator = new ELOCalculator();
    private final RoomManager roomManager;
    private final GameRecordMapper gameRecordMapper;

    // 房间存储（生产环境应使用 Redis）
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    /**
     * 落子
     */
    @Override
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
        if (!board.place(x, y, room.getPieceColor(userId))) {
            throw new RuntimeException("无效的落子");
        }

        // 检查是否获胜
        boolean hasWon = WinChecker.checkWin(board, x, y, room.getPieceColor(userId));
        if (hasWon) {
            // 游戏结束，当前落子方获胜
            handleGameOver(room, userId, room.getPieceColor(userId), 0);
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
    @Override
    public String createRoom(Long userId, String mode) {
        String roomId = generateRoomId();
        GameRoom room = new GameRoom(roomId, userId, mode);
        rooms.put(roomId, room);

        // 同时在RoomManager中注册房间
        GameMode gameMode = GameMode.fromCode(mode);
        roomManager.createRoom(roomId, userId, "房间" + roomId.substring(roomId.length() - 4), mode, false);

        log.info("房间创建成功: roomId={}, userId={}, mode={}, isRanked={}",
                roomId, userId, mode, gameMode.isRanked());

        // 广播初始游戏状态给房主
        broadcastGameState(room);

        return roomId;
    }

    /**
     * 加入房间
     */
    @Override
    public void joinRoom(String roomId, Long userId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        // 同时在RoomManager中加入
        roomManager.joinRoom(roomId, userId);

        room.addPlayer(userId);
        log.info("玩家加入房间: roomId={}, userId={}", roomId, userId);

        // 广播游戏状态（而不是房间状态）让前端可以初始化游戏
        // 当第二个玩家加入后，游戏正式开始，双方都能收到状态
        broadcastGameState(room);
    }

    /**
     * 认输
     */
    @Override
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
     * 处理游戏结束
     *
     * 积分规则：
     * - 休闲模式 (casual): 不影响积分
     * - 竞技模式 (ranked): 使用ELO算法计算积分变化
     */
    private void handleGameOver(GameRoom room, Long winnerId, int winColor, int endReason) {
        room.setGameOver(true);
        room.setWinnerId(winnerId);
        room.setEndReason(endReason);

        GameMode gameMode = GameMode.fromCode(room.getMode());

        // 获取玩家信息
        User blackPlayer = userService.getUserById(room.getBlackPlayerId());
        User whitePlayer = userService.getUserById(room.getWhitePlayerId());

        // 记录原始积分
        int blackRatingBefore = blackPlayer.getRating();
        int whiteRatingBefore = whitePlayer.getRating();
        int blackRatingChange = 0;
        int whiteRatingChange = 0;

        // 只有竞技模式才计算积分变化
        if (gameMode.isRanked()) {
            int[] ratingChanges = eloCalculator.calculateRatingChange(
                    blackRatingBefore,
                    whiteRatingBefore,
                    winColor == 1  // 黑棋是否获胜
            );

            // ratingChanges = [黑方新积分, 白方新积分, 黑方变化, 白方变化]
            blackRatingChange = ratingChanges[2];  // 黑方变化
            whiteRatingChange = ratingChanges[3];  // 白方变化

            // 更新积分
            userService.updateUserRating(room.getBlackPlayerId(), blackRatingBefore + blackRatingChange);
            userService.updateUserRating(room.getWhitePlayerId(), whiteRatingBefore + whiteRatingChange);

            log.info("📈 竞技模式积分变化: 黑棋 {} ({} → {}, {}{}), 白棋 {} ({} → {}, {}{})",
                    room.getBlackPlayerId(), blackRatingBefore, blackRatingBefore + blackRatingChange,
                    blackRatingChange >= 0 ? "+" : "", blackRatingChange,
                    room.getWhitePlayerId(), whiteRatingBefore, whiteRatingBefore + whiteRatingChange,
                    whiteRatingChange >= 0 ? "+" : "", whiteRatingChange);
        } else {
            log.info("🎮 休闲模式: 不计算积分变化");
        }

        // 记录对局
        GameRecord record = new GameRecord();
        record.setRoomId(room.getRoomId());
        record.setBlackPlayerId(room.getBlackPlayerId());
        record.setWhitePlayerId(room.getWhitePlayerId());
        record.setWinnerId(winnerId);
        record.setWinColor(winColor);
        record.setEndReason(endReason);
        record.setBlackRatingBefore(blackRatingBefore);
        record.setBlackRatingAfter(blackRatingBefore + blackRatingChange);
        record.setBlackRatingChange(blackRatingChange);
        record.setWhiteRatingBefore(whiteRatingBefore);
        record.setWhiteRatingAfter(whiteRatingBefore + whiteRatingChange);
        record.setWhiteRatingChange(whiteRatingChange);
        record.setBoardState(serializeBoard(room.getBoard()));
        record.setGameMode(room.getMode());
        record.setCreatedAt(LocalDateTime.now());

        // 保存对局记录到数据库
        try {
            gameRecordMapper.insert(record);
            log.info("对局记录已保存: roomId={}", room.getRoomId());
        } catch (Exception e) {
            log.error("保存对局记录失败: roomId={}", room.getRoomId(), e);
        }

        // 更新用户统计（无论哪种模式都更新统计）
        userService.addUserExp(winnerId, 100);
        userService.addUserExp(winnerId == room.getBlackPlayerId() ? room.getWhitePlayerId() : room.getBlackPlayerId(), 50);

        // 广播游戏结束消息
        broadcastGameOver(room, record, gameMode);

        log.info("游戏结束: roomId={}, winnerId={}, winColor={}, mode={}",
                room.getRoomId(), winnerId, winColor, gameMode.getDescription());
    }

    /**
     * 广播游戏状态（公共方法，用于外部调用）
     */
    public void broadcastGameState(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room != null) {
            broadcastGameState(room);
        } else {
            log.warn("尝试广播状态但房间不存在: roomId={}", roomId);
        }
    }

    /**
     * 广播游戏状态
     */
    private void broadcastGameState(GameRoom room) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "GAME_STATE");
        state.put("roomId", room.getRoomId());
        state.put("board", room.getBoard().getCells());  // 使用 2D 数组
        state.put("currentTurn", room.getPieceColor(room.getCurrentTurn()));  // 发送颜色 (1或2) 而不是 userId
        state.put("gameStatus", room.isGameOver() ? "FINISHED" : "PLAYING");
        state.put("remainingTime", 300);  // TODO: 实现真正的倒计时

        // 获取玩家信息
        try {
            User blackPlayer = userService.getUserById(room.getBlackPlayerId());
            User whitePlayer = userService.getUserById(room.getWhitePlayerId());

            // 添加玩家信息
            Map<String, Object> blackPlayerInfo = new HashMap<>();
            blackPlayerInfo.put("id", blackPlayer.getId());
            blackPlayerInfo.put("username", blackPlayer.getUsername());
            blackPlayerInfo.put("nickname", blackPlayer.getNickname());
            blackPlayerInfo.put("avatar", blackPlayer.getAvatar());
            blackPlayerInfo.put("rating", blackPlayer.getRating());

            Map<String, Object> whitePlayerInfo = new HashMap<>();
            whitePlayerInfo.put("id", whitePlayer.getId());
            whitePlayerInfo.put("username", whitePlayer.getUsername());
            whitePlayerInfo.put("nickname", whitePlayer.getNickname());
            whitePlayerInfo.put("avatar", whitePlayer.getAvatar());
            whitePlayerInfo.put("rating", whitePlayer.getRating());

            state.put("blackPlayer", blackPlayerInfo);
            state.put("whitePlayer", whitePlayerInfo);

            log.debug("广播游戏状态: roomId={}, currentTurn={}, 黑方={}, 白方={}",
                    room.getRoomId(), room.getPieceColor(room.getCurrentTurn()),
                    blackPlayer.getUsername(), whitePlayer.getUsername());
        } catch (Exception e) {
            log.error("获取玩家信息失败", e);
            // 失败时只发送ID
            state.put("blackPlayerId", room.getBlackPlayerId());
            state.put("whitePlayerId", room.getWhitePlayerId());
        }

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
    private void broadcastGameOver(GameRoom room, GameRecord record, GameMode gameMode) {
        Map<String, Object> state = new HashMap<>();
        state.put("type", "GAME_OVER");
        state.put("roomId", room.getRoomId());
        state.put("winnerId", record.getWinnerId());
        state.put("winColor", record.getWinColor());
        state.put("endReason", record.getEndReason());
        state.put("gameMode", gameMode.getCode());
        state.put("affectsRating", gameMode.affectsRating());
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
     * 发送和棋请求
     */
    @Override
    public void sendDrawRequest(String roomId, Long userId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        // 获取对手ID
        Long opponentId = userId.equals(room.getBlackPlayerId()) ? room.getWhitePlayerId() : room.getBlackPlayerId();

        Map<String, Object> message = new HashMap<>();
        message.put("type", "DRAW_REQUEST");
        message.put("requesterId", userId);

        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
        log.info("发送和棋请求: roomId={}, requesterId={}, opponentId={}", roomId, userId, opponentId);
    }

    /**
     * 响应和棋请求
     */
    @Override
    public void respondDrawRequest(String roomId, Long userId, boolean accept) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        Map<String, Object> message = new HashMap<>();
        message.put("type", "DRAW_RESPONSE");
        message.put("accepted", accept);
        message.put("responderId", userId);

        if (accept) {
            // 同意和棋，游戏结束
            handleGameOver(room, null, 0, 2); // 2 = 和棋
        } else {
            // 拒绝和棋，游戏继续
            messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
        }

        log.info("响应和棋请求: roomId={}, userId={}, accept={}", roomId, userId, accept);
    }

    /**
     * 发送悔棋请求
     */
    @Override
    public void sendUndoRequest(String roomId, Long userId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        // 验证：只有刚下完棋的人（不是当前回合的人）才能请求悔棋
        // 如果 currentTurn == userId，说明轮到userId下棋，他还没下，不能悔棋
        if (userId.equals(room.getCurrentTurn())) {
            throw new RuntimeException("只能在对手下棋前悔棋");
        }

        // 获取对手ID（当前回合的人）
        Long opponentId = room.getCurrentTurn();

        Map<String, Object> message = new HashMap<>();
        message.put("type", "UNDO_REQUEST");
        message.put("requesterId", userId);

        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
        log.info("发送悔棋请求: roomId={}, requesterId={}, opponentId={}", roomId, userId, opponentId);
    }

    /**
     * 响应悔棋请求
     */
    @Override
    public void respondUndoRequest(String roomId, Long userId, boolean accept) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        // userId是响应者，当前轮到的人
        // 请求悔棋的人是对手（刚下完棋的那个人）
        Long requesterId = userId.equals(room.getBlackPlayerId()) ? room.getWhitePlayerId() : room.getBlackPlayerId();

        if (accept) {
            // 同意悔棋，执行悔棋操作
            Board board = room.getBoard();

            // 撤销请求悔棋者的最后一步棋
            int requesterColor = room.getPieceColor(requesterId);
            int[] undoneMove = null;

            // 从后往前查找请求者的最后一步棋
            for (int y = 14; y >= 0; y--) {
                for (int x = 14; x >= 0; x--) {
                    if (board.getCells()[x][y] == requesterColor) {
                        undoneMove = new int[]{x, y};
                        board.getCells()[x][y] = 0;
                        break;
                    }
                }
                if (undoneMove != null) break;
            }

            // 切换到悔棋请求者的回合（让他们重新下棋）
            room.setCurrentTurn(requesterId);

            // 发送悔棋成功消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "UNDO_SUCCESS");
            message.put("board", board.getCells());
            message.put("currentTurn", room.getPieceColor(room.getCurrentTurn()));
            message.put("undoneMove", undoneMove);
            message.put("requesterId", requesterId);

            messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
            log.info("悔棋成功: roomId={}, requesterId={}", roomId, requesterId);
        } else {
            // 拒绝悔棋
            Map<String, Object> message = new HashMap<>();
            message.put("type", "UNDO_RESPONSE");
            message.put("accepted", false);

            messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
            log.info("拒绝悔棋: roomId={}, userId={}", roomId, userId);
        }
    }

    /**
     * 发送聊天消息
     */
    @Override
    public void sendChatMessage(String roomId, Long userId, String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        User user = userService.getUserById(userId);
        if (user == null) {
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("type", "CHAT_MESSAGE");
        message.put("sender", user.getNickname() != null ? user.getNickname() : user.getUsername());
        message.put("username", user.getUsername());
        message.put("content", content.trim());

        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
        log.info("发送聊天消息: roomId={}, userId={}, content={}", roomId, userId, content);
    }

    /**
     * 发送再来一局请求
     */
    @Override
    public void sendPlayAgainRequest(String roomId, Long userId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PLAY_AGAIN_REQUEST");
        message.put("requesterId", userId);

        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
        log.info("发送再来一局请求: roomId={}, userId={}", roomId, userId);
    }

    /**
     * 同意再来一局
     */
    @Override
    public void acceptPlayAgain(String roomId, Long userId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        // 重置游戏状态
        room.getBoard().clear();
        room.setCurrentTurn(room.getBlackPlayerId()); // 黑棋先手
        room.setGameOver(false);
        room.setWinnerId(null);
        room.setEndReason(0);

        // 发送再来一局接受消息
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PLAY_AGAIN_ACCEPT");
        message.put("acceptorId", userId);

        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);

        // 广播新的游戏状态
        broadcastGameState(room);

        log.info("同意再来一局，游戏已重置: roomId={}", roomId);
    }

    /**
     * 取消再来一局请求
     */
    @Override
    public void cancelPlayAgainRequest(String roomId, Long userId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PLAY_AGAIN_CANCEL");
        message.put("cancellerId", userId);

        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
        log.info("取消再来一局请求: roomId={}, userId={}", roomId, userId);
    }

    /**
     * 发送换桌消息
     */
    @Override
    public void sendChangeTableMessage(String roomId, Long userId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "CHANGE_TABLE");
        message.put("userId", userId);

        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
        log.info("发送换桌消息: roomId={}, userId={}", roomId, userId);
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

        public GameRoom(String roomId, Long creatorId, String mode) {
            this.roomId = roomId;
            this.mode = mode;
            this.blackPlayerId = creatorId;
            this.currentTurn = creatorId;
            this.board = new Board();
            this.gameOver = false;
        }

        public void addPlayer(Long userId) {
            if (whitePlayerId == null && !userId.equals(blackPlayerId)) {
                whitePlayerId = userId;
            }
        }

        public void switchTurn() {
            currentTurn = currentTurn.equals(blackPlayerId) ? whitePlayerId : blackPlayerId;
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

        // Getters
        public String getRoomId() { return roomId; }
        public String getMode() { return mode; }
        public Long getBlackPlayerId() { return blackPlayerId; }
        public Long getWhitePlayerId() { return whitePlayerId; }
        public Long getCurrentTurn() { return currentTurn; }
        public boolean isGameOver() { return gameOver; }
        public Long getWinnerId() { return winnerId; }
        public int getEndReason() { return endReason; }

        // Setters
        public void setCurrentTurn(Long currentTurn) { this.currentTurn = currentTurn; }
        public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }
        public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }
        public void setEndReason(int endReason) { this.endReason = endReason; }

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
