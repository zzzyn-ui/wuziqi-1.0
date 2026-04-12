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
import java.util.*;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GameServiceImpl.class);

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

        // 记录移动历史
        room.addMove(x, y);
        log.info("📍 落子并记录历史: roomId={}, userId={}, x={}, y={}, 历史步数={}",
                roomId, userId, x, y, room.getMoveCount());

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
     * 创建房间 - 使用房间名称作为房间ID
     */
    @Override
    public String createRoom(Long userId, String mode, String roomName, String password) {
        // 如果用户提供了房间名称，直接使用房间名称作为房间ID
        String roomId = (roomName != null && !roomName.trim().isEmpty())
            ? roomName.trim()
            : generateRoomId();

        GameRoom room = new GameRoom(roomId, userId, mode);
        // 保存密码（如果有）
        if (password != null && !password.trim().isEmpty()) {
            room.setPassword(password.trim());
        }
        rooms.put(roomId, room);

        // 在RoomManager中注册房间
        GameMode gameMode = GameMode.fromCode(mode);

        // 判断是否为私有房间：如果有密码，则为私有房间
        boolean isPrivate = (password != null && !password.trim().isEmpty());
        roomManager.createRoom(roomId, userId, roomId, mode, isPrivate);

        log.info("房间创建成功: roomId={}, userId={}, mode={}, isRanked={}, isPrivate={}",
                roomId, userId, mode, gameMode.isRanked(), isPrivate);

        // 不广播游戏状态，因为只有房主一人，等第二个玩家加入后再广播

        return roomId;
    }

    /**
     * 创建房间（指定黑棋玩家）
     * 注意：creatorId 是白棋玩家，blackPlayerId 是黑棋玩家
     */
    public String createRoom(Long creatorId, Long blackPlayerId, String mode) {
        String roomId = generateRoomId();

        log.info("🎮 开始创建匹配房间: roomId={}, creatorId(白)={}, blackPlayerId(黑)={}, mode={}",
                roomId, creatorId, blackPlayerId, mode);

        GameRoom room = new GameRoom(roomId, creatorId, blackPlayerId, mode);
        rooms.put(roomId, room);

        // 同时在RoomManager中注册房间
        GameMode gameMode = GameMode.fromCode(mode);
        roomManager.createRoom(roomId, creatorId, "房间" + roomId.substring(roomId.length() - 4), mode, false);

        // 由于匹配系统已经确定了两个玩家，直接将白棋玩家（创建者）加入房间
        // 这样两个玩家都已经准备好，不需要额外的 joinRoom 调用
        room.addPlayer(creatorId);

        log.info("✅ 房间创建完成: roomId={}, 黑棋={}, 白棋={}, mode={}",
                roomId, room.getBlackPlayerId(), room.getWhitePlayerId(), mode);

        // 广播游戏状态，两个玩家都能收到
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

        // 检查房间是否已满
        if (room.getWhitePlayerId() != null) {
            throw new RuntimeException("房间已满");
        }

        // 同时在RoomManager中加入
        roomManager.joinRoom(roomId, userId);

        room.addPlayer(userId);
        log.info("玩家加入房间: roomId={}, userId={}", roomId, userId);
        log.info("加入后房间状态: blackPlayerId={}, whitePlayerId={}, gameState={}",
                room.getBlackPlayerId(), room.getWhitePlayerId(), room.getGameState());

        // 广播游戏状态
        broadcastGameState(room);

        log.info("✅ 房间已满，游戏开始: roomId={}, 黑={}, 白={}",
                roomId, room.getBlackPlayerId(), room.getWhitePlayerId());
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

        log.info("🏳 玩家 {} 认输: roomId={}, mode={}", userId, roomId, room.getMode());

        int opponentColor = room.getPieceColor(userId) == 1 ? 2 : 1;
        Long winnerId = room.getPlayerByColor(opponentColor);

        log.info("🏳 认输处理: 认输者userId={}, 胜者winnerId={}, 胜者颜色winColor={}",
                userId, winnerId, opponentColor);

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

        log.info("🎮 handleGameOver 开始: roomId={}, mode={}", room.getRoomId(), room.getMode());

        GameMode gameMode = GameMode.fromCode(room.getMode());

        log.info("🎮 GameMode 解析结果: code={}, isRanked={}", gameMode.getCode(), gameMode.isRanked());

        // 获取玩家信息
        User blackPlayer = userService.getUserById(room.getBlackPlayerId());
        User whitePlayer = userService.getUserById(room.getWhitePlayerId());

        // 记录原始积分
        int blackRatingBefore = blackPlayer.getRating();
        int whiteRatingBefore = whitePlayer.getRating();
        int blackRatingChange = 0;
        int whiteRatingChange = 0;

        log.info("🎮 积分计算前: 黑棋rating={}, 白棋rating={}", blackRatingBefore, whiteRatingBefore);

        // 只有竞技模式才计算积分变化
        if (gameMode.isRanked()) {
            log.info("🎮 进入竞技模式积分计算分支");
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
            log.info("🎮 休闲模式: 不计算积分变化 (mode={}, isRanked={})", gameMode.getCode(), gameMode.isRanked());
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
        state.put("moveHistory", room.getMoveHistory());  // 发送移动历史

        // 使用GameRoom的getGameState方法获取游戏状态
        GameRoom.GameState gameState = room.getGameState();
        state.put("gameStatus", gameState.toString());
        state.put("remainingTime", 300);  // TODO: 实现真正的倒计时

        log.info("📢 [broadcastGameState] 准备广播游戏状态: roomId={}, gameStatus={}, blackPlayerId={}, whitePlayerId={}",
                room.getRoomId(), gameState, room.getBlackPlayerId(), room.getWhitePlayerId());

        // 获取玩家信息
        try {
            User blackPlayer = userService.getUserById(room.getBlackPlayerId());
            Map<String, Object> blackPlayerInfo = new HashMap<>();
            blackPlayerInfo.put("id", blackPlayer.getId());
            blackPlayerInfo.put("username", blackPlayer.getUsername());
            blackPlayerInfo.put("nickname", blackPlayer.getNickname());
            blackPlayerInfo.put("avatar", blackPlayer.getAvatar());
            blackPlayerInfo.put("rating", blackPlayer.getRating());
            state.put("blackPlayer", blackPlayerInfo);

            // 白方玩家可能为空（等待第二个玩家加入）
            if (room.getWhitePlayerId() != null) {
                User whitePlayer = userService.getUserById(room.getWhitePlayerId());
                if (whitePlayer != null) {
                    Map<String, Object> whitePlayerInfo = new HashMap<>();
                    whitePlayerInfo.put("id", whitePlayer.getId());
                    whitePlayerInfo.put("username", whitePlayer.getUsername());
                    whitePlayerInfo.put("nickname", whitePlayer.getNickname());
                    whitePlayerInfo.put("avatar", whitePlayer.getAvatar());
                    whitePlayerInfo.put("rating", whitePlayer.getRating());
                    state.put("whitePlayer", whitePlayerInfo);
                    log.info("✅ [broadcastGameState] 添加白方玩家信息: id={}, username={}", whitePlayer.getId(), whitePlayer.getUsername());
                } else {
                    log.warn("⚠️ [broadcastGameState] whitePlayerId 不为 null，但查询结果为 null: whitePlayerId={}", room.getWhitePlayerId());
                }
            } else {
                log.info("ℹ️ [broadcastGameState] 白方玩家尚未加入，不添加 whitePlayer 信息");
            }

            log.debug("广播游戏状态: roomId={}, currentTurn={}, gameStatus={}, 黑方={}, 白方={}",
                    room.getRoomId(), room.getPieceColor(room.getCurrentTurn()), gameState,
                    blackPlayer.getUsername(), room.getWhitePlayerId());
        } catch (Exception e) {
            log.error("获取玩家信息失败", e);
            // 失败时只发送ID
            state.put("blackPlayerId", room.getBlackPlayerId());
            if (room.getWhitePlayerId() != null) {
                state.put("whitePlayerId", room.getWhitePlayerId());
            }
        }

        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), state);
        log.info("📤 [broadcastGameState] 已发送游戏状态到 /topic/room/{}", room.getRoomId());
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
     * 生成房间ID（6位数字）
     */
    private String generateRoomId() {
        // 生成6位随机数字房间ID
        int min = 100000;
        int max = 999999;
        int roomId = min + (int)(Math.random() * (max - min + 1));
        return String.valueOf(roomId);
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

        // 记录悔棋请求者ID
        room.setUndoRequesterId(userId);

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

        // 获取悔棋请求者ID（从房间记录中获取）
        Long requesterId = room.getUndoRequesterId();

        if (requesterId == null) {
            log.warn("⚠️ 没有悔棋请求记录，可能已过期");
            // 发送错误消息
            Map<String, Object> errorMsg = new HashMap<>();
            errorMsg.put("type", "ERROR");
            errorMsg.put("message", "悔棋请求已过期");
            messagingTemplate.convertAndSend("/topic/room/" + roomId, errorMsg);
            return;
        }

        log.info("🔄 悔棋响应: roomId={}, 响应者userId={}, 请求者requesterId={}, 黑={}, 白={}, 当前回合={}",
                roomId, userId, requesterId, room.getBlackPlayerId(), room.getWhitePlayerId(), room.getCurrentTurn());

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

            // 移除最后一步移动历史
            room.removeLastMove();
            log.info("🔄 移除最后一步移动历史: roomId={}, 剩余步数={}", roomId, room.getMoveCount());

            // 重置游戏结束状态（因为悔棋后游戏继续）
            room.setGameOver(false);
            room.setWinnerId(null);
            room.setEndReason(0);
            log.info("🔄 悔棋后重置游戏结束状态: roomId={}, gameStatus=PLAYING", roomId);

            // 清除悔棋请求记录
            room.setUndoRequesterId(null);

            // 发送悔棋成功消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "UNDO_SUCCESS");
            message.put("roomId", roomId);
            message.put("board", board.getCells());
            message.put("currentTurn", room.getPieceColor(room.getCurrentTurn()));
            message.put("moveHistory", room.getMoveHistory()); // 发送更新后的移动历史
            message.put("undoneMove", undoneMove);
            message.put("requesterId", requesterId);
            message.put("gameStatus", "PLAYING"); // 悔棋后游戏继续，明确设置为 PLAYING
            // 添加玩家信息
            User blackUser = userService.getUserById(room.getBlackPlayerId());
            User whiteUser = userService.getUserById(room.getWhitePlayerId());
            if (blackUser != null) {
                message.put("blackPlayer", Map.of(
                    "id", blackUser.getId(),
                    "username", blackUser.getUsername(),
                    "nickname", blackUser.getNickname()
                ));
            }
            if (whiteUser != null) {
                message.put("whitePlayer", Map.of(
                    "id", whiteUser.getId(),
                    "username", whiteUser.getUsername(),
                    "nickname", whiteUser.getNickname()
                ));
            }

            messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
            log.info("悔棋成功: roomId={}, requesterId={}", roomId, requesterId);
        } else {
            // 拒绝悔棋
            // 清除悔棋请求记录
            room.setUndoRequesterId(null);

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
     * 发送玩家准备状态（房间对战准备或游戏结束后"再来一局"）
     */
    @Override
    public void sendPlayerReady(String roomId, Long userId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }

        // 检查玩家是否在房间中
        boolean isBlackPlayer = userId.equals(room.getBlackPlayerId());
        boolean isWhitePlayer = userId.equals(room.getWhitePlayerId());

        if (!isBlackPlayer && !isWhitePlayer) {
            throw new RuntimeException("玩家不在房间中");
        }

        // 更新准备状态
        if (isBlackPlayer) {
            room.setBlackPlayerReady(true);
        }
        if (isWhitePlayer) {
            room.setWhitePlayerReady(true);
        }

        // 广播准备状态
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PLAYER_READY");
        message.put("userId", userId);

        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
        log.info("发送玩家准备状态: roomId={}, userId={}, 黑棋准备={}, 白棋准备={}",
                roomId, userId, room.isBlackPlayerReady(), room.isWhitePlayerReady());

        // 检查是否双方都准备好了
        if (!room.isGameStarted() && room.getBlackPlayerId() != null && room.getWhitePlayerId() != null
                && room.isBlackPlayerReady() && room.isWhitePlayerReady()) {
            // 双方都准备好了，开始游戏
            startGame(room);
        }
    }

    /**
     * 开始游戏
     */
    private void startGame(GameRoom room) {
        // 清空棋盘和重置游戏状态
        room.getBoard().clear();
        room.setCurrentTurn(room.getBlackPlayerId()); // 黑棋先手
        room.setGameOver(false);
        room.setWinnerId(null);
        room.setEndReason(0);
        room.setUndoRequesterId(null); // 清除悔棋请求记录
        room.setGameStarted(true);
        room.clearMoveHistory(); // 清空移动历史

        log.info("🔄 startGame: 清空棋盘并重置游戏状态: roomId={}", room.getRoomId());

        // 广播游戏开始消息
        Map<String, Object> message = new HashMap<>();
        message.put("type", "GAME_START");
        message.put("roomId", room.getRoomId());
        message.put("blackPlayer", room.getBlackPlayerId());
        message.put("whitePlayer", room.getWhitePlayerId());

        messagingTemplate.convertAndSend("/topic/room/" + room.getRoomId(), message);
        log.info("游戏开始: roomId={}, 黑={}, 白={}",
                room.getRoomId(), room.getBlackPlayerId(), room.getWhitePlayerId());

        // 广播游戏状态（包含清空的棋盘）
        broadcastGameState(room);
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

        // 🎲 随机决定是否交换黑白棋
        java.util.Random random = new java.util.Random();
        boolean shouldSwap = random.nextBoolean();

        if (shouldSwap) {
            // 交换黑白棋
            Long originalBlack = room.getBlackPlayerId();
            Long originalWhite = room.getWhitePlayerId();

            room.setBlackPlayerId(originalWhite);
            room.setWhitePlayerId(originalBlack);

            log.info("🔄 交换黑白棋: roomId={}, 原黑{}→白, 原白{}→黑",
                    roomId, originalBlack, originalWhite);
        } else {
            log.info("✓ 保持原颜色: roomId={}, 黑={}, 白={}",
                    roomId, room.getBlackPlayerId(), room.getWhitePlayerId());
        }

        // 重置游戏状态
        room.getBoard().clear();
        room.setCurrentTurn(room.getBlackPlayerId()); // 黑棋先手
        room.setGameOver(false);
        room.setWinnerId(null);
        room.setEndReason(0);
        room.setUndoRequesterId(null); // 清除悔棋请求记录

        // 发送再来一局接受消息
        Map<String, Object> message = new HashMap<>();
        message.put("type", "PLAY_AGAIN_ACCEPT");
        message.put("acceptorId", userId);
        message.put("swapped", shouldSwap); // 告诉前端是否交换了颜色

        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);

        // 广播新的游戏状态
        broadcastGameState(room);

        log.info("🔄 同意再来一局，游戏已重置: roomId={}, 黑={}, 白={}, 交换={}",
                roomId, room.getBlackPlayerId(), room.getWhitePlayerId(), shouldSwap);
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
     * 发送玩家离开消息
     */
    @Override
    public void sendPlayerLeaveMessage(String roomId, Long userId) {
        log.info("🚪🚪🚪 [sendPlayerLeaveMessage] 开始执行: roomId={}, userId={}", roomId, userId);
        log.info("🚪 [sendPlayerLeaveMessage] 当前房间数: {}", rooms.size());

        Map<String, Object> message = new HashMap<>();
        message.put("type", "OPPONENT_LEFT");
        message.put("userId", userId);

        log.info("🚪 [sendPlayerLeaveMessage] 准备发送消息: /topic/room/{}", roomId);
        log.info("🚪 [sendPlayerLeaveMessage] 消息内容: {}", message);

        try {
            messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
            log.info("✅ [sendPlayerLeaveMessage] OPPONENT_LEFT消息已发送到 /topic/room/{}", roomId);
        } catch (Exception e) {
            log.error("❌ [sendPlayerLeaveMessage] 发送失败: ", e);
        }

        log.info("🚪🚪🚪 [sendPlayerLeaveMessage] 执行完成");
    }

    /**
     * 处理玩家离开（断开连接）
     * 通知房间内的对手该玩家已离开
     */
    @Override
    public void handlePlayerLeave(Long userId) {
        log.info("🚪 处理玩家离开: userId={}, 当前房间数={}", userId, rooms.size());

        boolean roomFound = false;

        // 遍历所有房间，查找玩家所在的房间
        for (Map.Entry<String, GameRoom> entry : rooms.entrySet()) {
            GameRoom room = entry.getValue();
            String roomId = entry.getKey();

            log.info("🔍 检查房间 {}: 黑={}, 白={}, 游戏结束={}, 房主={}",
                    roomId, room.getBlackPlayerId(), room.getWhitePlayerId(), room.isGameOver(), room.getBlackPlayerId());

            // 检查玩家是否在这个房间
            if (userId.equals(room.getBlackPlayerId()) || userId.equals(room.getWhitePlayerId())) {
                log.info("✅ 找到玩家所在的房间: roomId={}, userId={}, 黑={}, 白={}",
                        roomId, userId, room.getBlackPlayerId(), room.getWhitePlayerId());

                // 获取对手ID
                Long opponentId = userId.equals(room.getBlackPlayerId()) ? room.getWhitePlayerId() : room.getBlackPlayerId();

                log.info("📍 对手ID: {}", opponentId);

                // 检查是否为房主（黑棋）且游戏已结束
                boolean isCreator = userId.equals(room.getBlackPlayerId());
                boolean isGameFinished = room.isGameOver();

                if (opponentId != null) {
                    // 通知对手玩家已离开
                    Map<String, Object> message = new HashMap<>();
                    message.put("type", "OPPONENT_LEFT");
                    message.put("userId", userId);
                    message.put("opponentId", opponentId);

                    log.info("📤 发送OPPONENT_LEFT消息到房间 {}: 离开者={}, 对手={}, 目标主题=/topic/room/{}",
                            roomId, userId, opponentId, roomId);
                    messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
                    log.info("✅ OPPONENT_LEFT消息已发送到 /topic/room/{}", roomId);
                    roomFound = true;
                } else {
                    log.warn("⚠️ 对手ID为null，无法发送通知");
                }

                // 如果是房主且游戏已结束，删除房间
                if (isCreator && isGameFinished) {
                    log.info("🗑️ 房主离开且游戏已结束，删除房间: roomId={}, userId={}", roomId, userId);
                    rooms.remove(roomId);
                    roomManager.removeRoom(roomId);
                    log.info("✅ 房间已删除: roomId={}", roomId);
                }

                break; // 找到后就跳出循环
            }
        }

        if (!roomFound) {
            log.warn("⚠️ 未找到玩家 {} 所在的房间，无法发送离开通知", userId);
        }

        log.info("🚪 玩家离开处理完成: userId={}, roomFound={}", userId, roomFound);
    }

    /**
     * 游戏房间内部类
     */
    public static class GameRoom {
        private final String roomId;
        private final String mode;
        private Long blackPlayerId;
        private Long whitePlayerId;
        private final Board board;
        private Long currentTurn;
        private boolean gameOver;
        private Long winnerId;
        private int endReason;
        private Long undoRequesterId; // 悔棋请求者ID
        private final Set<Long> observers; // 观战者列表
        private boolean blackPlayerReady = false; // 黑棋玩家准备状态
        private boolean whitePlayerReady = false; // 白棋玩家准备状态
        private boolean gameStarted = false; // 游戏是否已开始
        private String password; // 房间密码（私有房间）
        private final List<int[]> moveHistory; // 落子历史记录

        public GameRoom(String roomId, Long creatorId, String mode) {
            this.roomId = roomId;
            this.mode = mode;
            // 不再固定创建者为黑棋，黑棋将由匹配系统随机分配
            // 暂时将创建者设为黑棋，但可能会在第二个玩家加入时被交换
            this.blackPlayerId = creatorId;
            this.currentTurn = creatorId;
            this.board = new Board();
            this.gameOver = false;
            this.observers = ConcurrentHashMap.newKeySet();
            this.moveHistory = new ArrayList<>();
        }

        public GameRoom(String roomId, Long creatorId, Long blackPlayerId, String mode) {
            this.roomId = roomId;
            this.mode = mode;
            this.blackPlayerId = blackPlayerId;
            this.currentTurn = blackPlayerId; // 黑棋先手
            this.board = new Board();
            this.gameOver = false;
            this.observers = ConcurrentHashMap.newKeySet();
            this.moveHistory = new ArrayList<>();
        }

        public void addPlayer(Long userId) {
            if (whitePlayerId == null && !userId.equals(blackPlayerId)) {
                whitePlayerId = userId;
            }
        }

        public void switchTurn() {
            if (whitePlayerId == null) {
                log.warn("⚠️ 尝试切换回合，但白棋玩家还未加入，忽略");
                return;
            }
            currentTurn = currentTurn.equals(blackPlayerId) ? whitePlayerId : blackPlayerId;
        }

        public int getPieceColor(Long userId) {
            if (userId == null) {
                throw new IllegalArgumentException("userId 不能为 null");
            }
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
        public Long getUndoRequesterId() { return undoRequesterId; }

        // 准备状态相关方法
        public boolean isBlackPlayerReady() { return blackPlayerReady; }
        public boolean isWhitePlayerReady() { return whitePlayerReady; }
        public boolean isGameStarted() { return gameStarted; }
        public void setBlackPlayerReady(boolean ready) { this.blackPlayerReady = ready; }
        public void setWhitePlayerReady(boolean ready) { this.whitePlayerReady = ready; }
        public void setGameStarted(boolean started) { this.gameStarted = started; }

        // 观战者相关方法
        public Set<Long> getObservers() { return observers; }
        public void addObserver(Long userId) { observers.add(userId); }
        public void removeObserver(Long userId) { observers.remove(userId); }
        public int getObserverCount() { return observers.size(); }
        public boolean isObserver(Long userId) { return observers.contains(userId); }

        // 获取游戏状态枚举
        public GameState getGameState() {
            if (gameOver) return GameState.FINISHED;
            if (whitePlayerId == null) return GameState.WAITING;
            return GameState.PLAYING;
        }

        // 获取当前回合（1=黑，2=白）
        public int getCurrentTurnValue() {
            if (currentTurn == null) return 1;
            return currentTurn.equals(blackPlayerId) ? 1 : 2;
        }

        // 获取棋盘数据
        public List<List<Integer>> getBoardData() {
            int[][] boardArray = board.getBoard();
            List<List<Integer>> result = new ArrayList<>();
            for (int[] row : boardArray) {
                List<Integer> rowList = new ArrayList<>();
                for (int cell : row) {
                    rowList.add(cell);
                }
                result.add(rowList);
            }
            return result;
        }

        // Setters
        public void setCurrentTurn(Long currentTurn) { this.currentTurn = currentTurn; }
        public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }
        public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }
        public void setEndReason(int endReason) { this.endReason = endReason; }
        public void setBlackPlayerId(Long blackPlayerId) { this.blackPlayerId = blackPlayerId; }
        public void setWhitePlayerId(Long whitePlayerId) { this.whitePlayerId = whitePlayerId; }
        public void setUndoRequesterId(Long undoRequesterId) { this.undoRequesterId = undoRequesterId; }

        // 密码相关方法
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public boolean hasPassword() { return password != null && !password.trim().isEmpty(); }

        // 移动历史相关方法
        public List<int[]> getMoveHistory() { return new ArrayList<>(moveHistory); }
        public void addMove(int x, int y) { moveHistory.add(new int[]{x, y}); }
        public void removeLastMove() {
            if (!moveHistory.isEmpty()) {
                moveHistory.remove(moveHistory.size() - 1);
            }
        }
        public void clearMoveHistory() { moveHistory.clear(); }
        public int getMoveCount() { return moveHistory.size(); }

        // 获取玩家信息
        public Map<String, Object> getPlayers() {
            Map<String, Object> players = new HashMap<>();
            players.put("black", blackPlayerId);
            players.put("white", whitePlayerId);
            return players;
        }

        // 游戏状态枚举
        public enum GameState {
            WAITING, PLAYING, FINISHED
        }
    }

    // ==================== 观战相关方法实现 ====================

    @Override
    public void addObserverToRoom(String roomId, Long userId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        room.addObserver(userId);
        log.info("观战者加入: roomId={}, userId={}", roomId, userId);
    }

    @Override
    public void removeObserverFromRoom(String roomId, Long userId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            return;
        }
        room.removeObserver(userId);
        log.info("观战者离开: roomId={}, userId={}", roomId, userId);
    }

    @Override
    public List<Long> getRoomObservers(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(room.getObservers());
    }

    @Override
    public int getRoomObserverCount(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            return 0;
        }
        return room.getObserverCount();
    }

    @Override
    public GameRoom getGameRoom(String roomId) {
        return rooms.get(roomId);
    }
}
