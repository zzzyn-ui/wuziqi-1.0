package com.gobang.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.core.game.Board;
import com.gobang.core.game.GameState;
import com.gobang.core.game.WinChecker;
import com.gobang.core.match.Player;
import com.gobang.core.room.GameRoom;
import com.gobang.core.room.RoomManager;
import com.gobang.model.entity.User;
import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.util.SecureRandomUtil;
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
    private final ObjectMapper objectMapper;

    // ==================== 定时任务 ====================
    private final ScheduledExecutorService scheduler;

    // ==================== 观战管理 ====================
    private final Map<String, Set<Long>> roomObservers = new ConcurrentHashMap<>();

    public RoomService(RoomManager roomManager, UserService userService) {
        this.roomManager = roomManager;
        this.userService = userService;
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
        GameRoom room = roomManager.getRoom(roomId);
        if (room == null) {
            return null;
        }

        int[] undoneMove = room.respondUndo(userId, accept);
        if (undoneMove != null) {
            // 悔棋成功，广播
            broadcastUndoNotify(room, undoneMove[0], undoneMove[1], undoneMove[2]);
            broadcastGameState(room);
        }
        return undoneMove;
    }

    // ==================== 广播消息 ====================

    /**
     * 广播游戏状态
     */
    private void broadcastGameState(GameRoom room) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "GAME_STATE");
            message.put("roomId", room.getRoomId());
            message.put("board", room.getBoard().toArray());
            message.put("currentPlayer", room.getCurrentPlayer());
            message.put("moveCount", room.getMoves().size());
            message.put("gameState", room.getGameState().name());

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

        // 更新用户状态
        userService.updateUserStatus(room.getBlackPlayerId(), 0);
        userService.updateUserStatus(room.getWhitePlayerId(), 0);
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
