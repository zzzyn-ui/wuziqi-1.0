package com.gobang.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.core.game.Board;
import com.gobang.core.game.GameState;
import com.gobang.core.game.WinChecker;
import com.gobang.core.match.Player;
import com.gobang.core.room.GameRoom;
import com.gobang.core.room.RoomManager;
import com.gobang.model.entity.User;
import com.gobang.model.entity.GameRecord;
import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.util.SecureRandomUtil;
import com.gobang.core.netty.ResponseUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 房间管理服务
 *
 * 功能：
 * 1. 创建和管理游戏房间
 * 2. 处理落子请求
 * 3. 胜负判定
 * 4. 超时处理
 * 5. 观战管理
 */
public class RoomService {

    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);

    // ==================== 配置 ====================
    private static final int MOVE_TIMEOUT = 300;           // 落子超时（秒）
    private static final int ROOM_EXPIRE_TIME = 600;       // 房间过期时间（秒）
    private static final int RECONNECT_WINDOW = 600;       // 重连窗口（秒）

    // ==================== 依赖 ====================
    private final RoomManager roomManager;
    private final UserService userService;
    private final RecordService recordService;
    private final ObjectMapper objectMapper;

    // ==================== 定时任务 ====================
    private final ScheduledExecutorService scheduler;

    // ==================== 观战管理 ====================
    private final Map<String, Set<Long>> roomObservers = new ConcurrentHashMap<>();

    public RoomService(RoomManager roomManager, UserService userService, RecordService recordService) {
        this.roomManager = roomManager;
        this.userService = userService;
        this.recordService = recordService;
        this.objectMapper = new ObjectMapper();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "room-scheduler");
            t.setDaemon(true);
            return t;
        });

        // 设置房间超时回调
        roomManager.setTimeoutCallback(this::handleMoveTimeout);

        // 启动清理任务
        startCleanupTask();
    }

    // ==================== 房间管理 ====================

    /**
     * 创建房间
     */
    public GameRoom createRoom() {
        return roomManager.createRoom();
    }

    /**
     * 获取房间
     */
    public GameRoom getRoom(String roomId) {
        return roomManager.getRoom(roomId);
    }

    /**
     * 获取用户所在房间
     */
    public GameRoom getUserRoom(Long userId) {
        return roomManager.getRoomByUserId(userId);
    }

    /**
     * 移除房间
     */
    public void removeRoom(String roomId) {
        roomManager.removeRoom(roomId);
        roomObservers.remove(roomId);
    }

    // ==================== 游戏操作 ====================

    /**
     * 落子
     * @return 0=成功, 1=位置无效, 2=不是你的回合, 3=游戏已结束, 4=位置已有棋子
     */
    public int makeMove(Long userId, String roomId, int x, int y) {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return -1;
        }

        int result = room.makeMove(userId, x, y);

        if (result == 0) {
            // 落子成功，广播游戏状态
            broadcastGameState(room);

            // 检查游戏是否结束
            if (room.getGameState() == GameState.FINISHED) {
                handleGameOver(room);
            }
        } else {
            // 落子失败，发送错误消息
            sendMoveError(userId, result, x, y);
        }

        return result;
    }

    /**
     * 认输
     */
    public void resign(Long userId, String roomId) {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return;
        }

        Long winnerId = userId.equals(room.getBlackPlayerId())
            ? room.getWhitePlayerId()
            : room.getBlackPlayerId();

        room.resign(userId);
        handleGameOver(room, winnerId, 3); // 3 = 认输
    }

    /**
     * 处理超时判负
     */
    /**
     * 处理超时判负
     * 规则：当前回合玩家超时未落子判负，对手获胜
     */
    public void handleTimeout(Long timeoutPlayerId, String roomId) {
        logger.info("=== handleTimeout === 超时玩家: {}, 房间: {}", timeoutPlayerId, roomId);

        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            logger.warn("handleTimeout - 房间不存在: {}", roomId);
            return;
        }

        logger.info("handleTimeout - 房间: {}, 黑方: {}, 白方: {}, 当前玩家: {}, 超时玩家: {}",
            room.getRoomId(), room.getBlackPlayerId(), room.getWhitePlayerId(),
            room.getCurrentPlayer(), timeoutPlayerId);

        // 确定获胜者：超时玩家的对手
        Long winnerId = timeoutPlayerId.equals(room.getBlackPlayerId())
            ? room.getWhitePlayerId()
            : room.getBlackPlayerId();

        logger.info("handleTimeout - 超时玩家 {} 判负，对手 {} 获胜", timeoutPlayerId, winnerId);

        room.resign(timeoutPlayerId);
        handleGameOver(room, winnerId, 4); // 4 = 超时
    }

    /**
     * 请求悔棋
     */
    public int requestUndo(Long userId, String roomId) {
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return -1;
        }
        return room.requestUndo(userId);
    }

    /**
     * 响应悔棋
     */
    public int[] respondUndo(Long userId, String roomId, boolean accept) {
        logger.info("=== respondUndo 被调用 === userId: {}, roomId: {}, accept: {}", userId, roomId, accept);

        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            logger.warn("respondUndo - 房间不存在: {}", roomId);
            return null;
        }

        logger.info("respondUndo - 房间存在, 黑方: {}, 白方: {}, 当前玩家: {}, 棋子数: {}",
            room.getBlackPlayerId(), room.getWhitePlayerId(),
            room.getCurrentPlayer(), room.getMoves().size());

        int[] undoneMove = room.respondUndo(userId, accept);
        if (undoneMove != null) {
            logger.info("respondUndo - 悔棋成功, 撤销的落子: ({}, {}), 颜色: {}, 剩余棋子: {}",
                undoneMove[0], undoneMove[1], undoneMove[2], room.getMoves().size());
            // 悔棋成功，广播
            broadcastUndoNotify(room, undoneMove[0], undoneMove[1], undoneMove[2]);
            broadcastGameState(room);
        } else {
            logger.info("respondUndo - 悔棋失败或被拒绝, undoneMove: null");
        }
        return undoneMove;
    }

    // ==================== 广播消息 ====================

    /**
     * 广播游戏状态
     */
    private void broadcastGameState(GameRoom room) {
        try {
            // 使用标准JSON格式（数据在body中）
            Map<String, Object> gameState = new HashMap<>();
            gameState.put("room_id", room.getRoomId());
            gameState.put("board", room.getBoard().toArray());
            gameState.put("current_player", room.getCurrentPlayer());
            gameState.put("move_count", room.getMoves().size());
            gameState.put("game_state", room.getGameState().name());
            // 添加双方剩余时间（毫秒）
            gameState.put("black_remaining_time", room.getBlackPlayerRemainingTime());
            gameState.put("white_remaining_time", room.getWhitePlayerRemainingTime());

            logger.info("broadcastGameState - 房间: {}, 棋子数: {}, 当前玩家: {}, 黑方时间: {}ms, 白方时间: {}ms",
                room.getRoomId(), room.getMoves().size(), room.getCurrentPlayer(),
                room.getBlackPlayerRemainingTime(), room.getWhitePlayerRemainingTime());

            // 发送给黑方（添加 my_color）
            if (room.getBlackChannel() != null && room.getBlackChannel().isActive()) {
                Map<String, Object> blackState = new HashMap<>(gameState);
                blackState.put("my_color", 1); // 黑方
                com.gobang.core.netty.ResponseUtil.sendJsonResponse(
                    room.getBlackChannel(),
                    32, // GAME_STATE
                    0,
                    blackState
                );
                logger.info("已发送 GAME_STATE 到黑方 - 房间: {}", room.getRoomId());
            } else {
                logger.warn("黑方 channel 不可用 - 房间: {}", room.getRoomId());
            }

            // 发送给白方（添加 my_color）
            if (room.getWhiteChannel() != null && room.getWhiteChannel().isActive()) {
                Map<String, Object> whiteState = new HashMap<>(gameState);
                whiteState.put("my_color", 2); // 白方
                com.gobang.core.netty.ResponseUtil.sendJsonResponse(
                    room.getWhiteChannel(),
                    32, // GAME_STATE
                    0,
                    whiteState
                );
                logger.info("已发送 GAME_STATE 到白方 - 房间: {}", room.getRoomId());
            } else {
                logger.warn("白方 channel 不可用 - 房间: {}", room.getRoomId());
            }

        } catch (Exception e) {
            logger.error("广播游戏状态失败", e);
        }
    }

    /**
     * 发送落子错误
     */
    private void sendMoveError(Long userId, int errorCode, int x, int y) {
        try {
            String[] errorMessages = {
                "", // 0 = 成功
                "位置无效",
                "不是你的回合",
                "游戏已结束",
                "该位置已有棋子"
            };

            Map<String, Object> message = new HashMap<>();
            message.put("type", "MOVE_ERROR");
            message.put("code", errorCode);
            message.put("message", errorMessages[errorCode]);
            message.put("x", x);
            message.put("y", y);

            String json = objectMapper.writeValueAsString(message);

            // 发送给特定用户
            GameRoom room = roomManager.getRoomByUserId(userId);
            if (room != null) {
                Channel channel = userId.equals(room.getBlackPlayerId())
                    ? room.getBlackChannel()
                    : room.getWhiteChannel();
                if (channel != null && channel.isActive()) {
                    channel.writeAndFlush(createTextFrame(json));
                }
            }

        } catch (Exception e) {
            logger.error("发送落子错误失败", e);
        }
    }

    /**
     * 广播悔棋通知
     */
    private void broadcastUndoNotify(GameRoom room, int x, int y, int color) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "UNDO_NOTIFY");
            message.put("x", x);
            message.put("y", y);
            message.put("color", color);

            String json = objectMapper.writeValueAsString(message);
            TextWebSocketFrame frame = new TextWebSocketFrame(json);

            // 直接发送给两个玩家
            if (room.getBlackChannel() != null && room.getBlackChannel().isActive()) {
                room.getBlackChannel().writeAndFlush(frame);
            }
            if (room.getWhiteChannel() != null && room.getWhiteChannel().isActive()) {
                room.getWhiteChannel().writeAndFlush(frame);
            }

        } catch (Exception e) {
            logger.error("广播悔棋通知失败", e);
        }
    }

    /**
     * 广播游戏结束
     */
    private void broadcastGameOver(GameRoom room, Long winnerId, int endReason) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "GAME_OVER");
            message.put("roomId", room.getRoomId());
            message.put("winnerId", winnerId);
            message.put("endReason", endReason); // 0=胜利, 1=失败, 2=平局, 3=认输, 4=超时
            message.put("board", room.getBoard().toArray());
            message.put("moves", room.getMoves());

            String json = objectMapper.writeValueAsString(message);
            TextWebSocketFrame frame = new TextWebSocketFrame(json);

            // 直接发送给两个玩家
            if (room.getBlackChannel() != null && room.getBlackChannel().isActive()) {
                room.getBlackChannel().writeAndFlush(frame);
            }
            if (room.getWhiteChannel() != null && room.getWhiteChannel().isActive()) {
                room.getWhiteChannel().writeAndFlush(frame);
            }

        } catch (Exception e) {
            logger.error("广播游戏结束失败", e);
        }
    }

    // ==================== 游戏结束处理 ====================

    /**
     * 处理游戏结束（胜负判定）
     */
    private void handleGameOver(GameRoom room) {
        int lastColor = room.getCurrentPlayer() == Board.BLACK ? Board.WHITE : Board.BLACK;
        Long winnerId = lastColor == Board.BLACK
            ? room.getBlackPlayerId()
            : room.getWhitePlayerId();

        handleGameOver(room, winnerId, 0); // 0 = 正常胜负
    }

    /**
     * 处理游戏结束
     */
    private void handleGameOver(GameRoom room, Long winnerId, int endReason) {
        logger.info("游戏结束 - 房间: {}, 胜者: {}, 原因: {}",
            room.getRoomId(), winnerId, endReason);

        // 广播游戏结束
        broadcastGameOver(room, winnerId, endReason);

        // 保存游戏记录到数据库（包括超时判负）
        saveGameRecordToDb(room, winnerId, endReason);

        // 更新用户状态
        userService.updateUserStatus(room.getBlackPlayerId(), 0);
        userService.updateUserStatus(room.getWhitePlayerId(), 0);
    }

    /**
     * 保存游戏记录到数据库
     */
    private void saveGameRecordToDb(GameRoom room, Long winnerId, int endReason) {
        try {
            // 获取用户信息
            User blackUser = userService.getUserById(room.getBlackPlayerId());
            User whiteUser = userService.getUserById(room.getWhitePlayerId());

            int blackRatingBefore = blackUser != null ? blackUser.getRating() : 1200;
            int whiteRatingBefore = whiteUser != null ? whiteUser.getRating() : 1200;

            // 创建游戏记录
            GameRecord record = room.createRecord(
                winnerId,
                endReason,
                blackRatingBefore,
                whiteRatingBefore,
                0,  // 黑方积分变化（超时判负不计算积分变化）
                0   // 白方积分变化
            );

            logger.info("保存游戏记录 - 房间: {}, 黑: {}, 白: {}, 胜者: {}, 模式: {}, 原因: {}",
                room.getRoomId(), room.getBlackPlayerId(), room.getWhitePlayerId(),
                winnerId, room.getGameMode(), endReason);

            // 保存到数据库
            recordService.saveRecord(record);

            logger.info("✅ 游戏记录已保存 - 房间: {}, 原因: {}", room.getRoomId(), endReason);
        } catch (Exception e) {
            logger.error("❌ 保存游戏记录失败 - 房间: {}, 原因: {}", room.getRoomId(), endReason, e);
        }
    }

    /**
     * 处理落子超时
     */
    private void handleMoveTimeout(GameRoom room, Long timeoutPlayerId) {
        Long winnerId = timeoutPlayerId.equals(room.getBlackPlayerId())
            ? room.getWhitePlayerId()
            : room.getBlackPlayerId();

        logger.warn("落子超时 - 房间: {}, 超时玩家: {}, 判负: {}",
            room.getRoomId(), timeoutPlayerId, winnerId);

        room.resign(timeoutPlayerId);
        handleGameOver(room, winnerId, 4); // 4 = 超时
    }

    // ==================== 观战管理 ====================

    /**
     * 添加观战者
     */
    public void addObserver(String roomId, Long userId, String nickname, Channel channel) {
        // 获取用户信息以获取username
        User user = userService.getUserById(userId);
        String username = user != null ? user.getUsername() : String.valueOf(userId);

        roomManager.addObserver(roomId, userId, username, nickname, channel);
        roomObservers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId);

        // 发送当前游戏状态给观战者
        GameRoom room = roomManager.getRoom(roomId);
        if (room != null) {
            sendObserverGameState(room, channel);
        }

        logger.info("用户 {} 开始观战房间 {}", userId, roomId);
    }

    /**
     * 移除观战者
     */
    public void removeObserver(String roomId, Long userId) {
        roomManager.removeObserver(roomId, userId);
        Set<Long> observers = roomObservers.get(roomId);
        if (observers != null) {
            observers.remove(userId);
        }
    }

    /**
     * 发送游戏状态给观战者
     */
    private void sendObserverGameState(GameRoom room, Channel channel) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "GAME_STATE");
            message.put("roomId", room.getRoomId());
            message.put("board", room.getBoard().toArray());
            message.put("currentPlayer", room.getCurrentPlayer());
            message.put("moveCount", room.getMoves().size());
            message.put("gameState", room.getGameState().name());

            // 玩家信息
            message.put("blackPlayer", room.getBlackPlayerId());
            message.put("whitePlayer", room.getWhitePlayerId());

            String json = objectMapper.writeValueAsString(message);
            channel.writeAndFlush(createTextFrame(json));

        } catch (Exception e) {
            logger.error("发送观战游戏状态失败", e);
        }
    }

    // ==================== 清理任务 ====================

    /**
     * 启动清理任务
     */
    private void startCleanupTask() {
        // 每分钟清理过期房间
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredRooms();
            } catch (Exception e) {
                logger.error("清理过期房间失败", e);
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 清理过期房间
     */
    private void cleanupExpiredRooms() {
        int cleaned = 0;
        long now = System.currentTimeMillis();

        for (GameRoom room : roomManager.getAllRooms()) {
            if (room.getGameState() == GameState.FINISHED) {
                long expireTime = room.getCreateTime() + ROOM_EXPIRE_TIME * 1000L;
                if (now > expireTime) {
                    removeRoom(room.getRoomId());
                    cleaned++;
                }
            }
        }

        if (cleaned > 0) {
            logger.info("清理了 {} 个过期房间", cleaned);
        }
    }

    // ==================== 辅助方法 ====================

    private TextWebSocketFrame createTextFrame(String json) {
        return new TextWebSocketFrame(json);
    }
}
