package com.gobang.core.room;

import com.gobang.util.IdGenerator;
import com.gobang.util.RedisUtil;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 房间管理器
 * 管理所有游戏房间的生命周期
 */
public class RoomManager {

    private static final Logger logger = LoggerFactory.getLogger(RoomManager.class);

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<Long, String> userRoomMap = new ConcurrentHashMap<>(); // userId -> roomId
    private final Map<Channel, Long> channelUserMap = new ConcurrentHashMap<>(); // Channel -> userId

    private final RedisUtil redisUtil;
    private final long roomExpireTime;
    private final long reconnectWindow;
    private final IdGenerator idGenerator;
    private final long moveTimeoutMillis;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // 超时回调接口
    public interface TimeoutCallback {
        void onTimeout(GameRoom room, Long timeoutPlayerId);
    }

    private TimeoutCallback timeoutCallback;

    public RoomManager(RedisUtil redisUtil, long roomExpireTime, long reconnectWindow, long moveTimeoutMillis) {
        this.redisUtil = redisUtil;
        this.roomExpireTime = roomExpireTime;
        this.reconnectWindow = reconnectWindow;
        this.moveTimeoutMillis = moveTimeoutMillis;
        this.idGenerator = new IdGenerator(1, 1);

        // 启动清理任务
        startCleanupTask();
        // 启动超时检查任务
        startTimeoutCheckTask();
    }

    /**
     * 创建新房间
     */
    public GameRoom createRoom() {
        String roomId = generateRoomId();
        GameRoom room = new GameRoom(roomId, moveTimeoutMillis);
        rooms.put(roomId, room);

        // 缓存到Redis
        redisUtil.setex("room:info:" + roomId, (int) roomExpireTime / 1000, "active");

        logger.info("Created new room: {}", roomId);
        return room;
    }

    /**
     * 设置超时回调
     */
    public void setTimeoutCallback(TimeoutCallback callback) {
        this.timeoutCallback = callback;
    }

    /**
     * 获取房间
     */
    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 根据用户ID获取所在房间
     */
    public GameRoom getRoomByUserId(Long userId) {
        String roomId = userRoomMap.get(userId);
        return roomId != null ? rooms.get(roomId) : null;
    }

    /**
     * 玩家加入房间
     */
    public void joinRoom(String roomId, Long userId, Channel channel) {
        userRoomMap.put(userId, roomId);
        channelUserMap.put(channel, userId);

        // 更新在线状态
        redisUtil.set("user:online:" + userId, "1");
        redisUtil.del("user:disconnect:" + userId);

        logger.info("User {} joined room {}", userId, roomId);
    }

    /**
     * 离开房间
     */
    public void leaveRoom(String roomId, Long userId) {
        userRoomMap.remove(userId);
        // 移除channel映射（需要通过userId找到channel）
        channelUserMap.entrySet().removeIf(entry -> entry.getValue().equals(userId));

        logger.info("User {} left room {}", userId, roomId);
    }

    /**
     * 处理玩家断线
     */
    public void handleDisconnect(Channel channel) {
        Long userId = channelUserMap.remove(channel);
        if (userId == null) {
            return;
        }

        // 记录断线时间
        redisUtil.setex("user:disconnect:" + userId, (int) reconnectWindow / 1000, String.valueOf(System.currentTimeMillis()));

        logger.info("User {} disconnected, room kept for {} seconds", userId, reconnectWindow / 1000);
    }

    /**
     * 处理玩家重连
     */
    public GameRoom handleReconnect(Long userId, Channel channel) {
        // 检查是否在重连窗口内
        String disconnectTime = redisUtil.get("user:disconnect:" + userId);
        if (disconnectTime == null) {
            return null;
        }

        long disconnectTimestamp = Long.parseLong(disconnectTime);
        long elapsed = System.currentTimeMillis() - disconnectTimestamp;

        if (elapsed > reconnectWindow) {
            redisUtil.del("user:disconnect:" + userId);
            return null;
        }

        // 获取房间
        GameRoom room = getRoomByUserId(userId);
        if (room != null) {
            // 更新映射
            channelUserMap.put(channel, userId);
            redisUtil.del("user:disconnect:" + userId);
            redisUtil.set("user:online:" + userId, "1");

            logger.info("User {} reconnected to room {}", userId, room.getRoomId());
            return room;
        }

        return null;
    }

    /**
     * 移除房间
     */
    public void removeRoom(String roomId) {
        GameRoom room = rooms.remove(roomId);

        // 移除用户映射
        if (room != null) {
            if (room.getBlackPlayerId() != null) {
                userRoomMap.remove(room.getBlackPlayerId());
            }
            if (room.getWhitePlayerId() != null) {
                userRoomMap.remove(room.getWhitePlayerId());
            }
        }

        // 清除Redis缓存
        redisUtil.del("room:info:" + roomId);
        redisUtil.del("room:observers:" + roomId);

        logger.info("Removed room: {}", roomId);
    }

    /**
     * 添加观战者
     */
    public void addObserver(String roomId, Long userId, String username, String nickname, Channel channel) {
        GameRoom room = rooms.get(roomId);
        if (room != null) {
            room.addObserver(userId, username, nickname, channel);
            redisUtil.sadd("room:observers:" + roomId, String.valueOf(userId));
        }
    }

    /**
     * 移除观战者
     */
    public void removeObserver(String roomId, Long userId) {
        GameRoom room = rooms.get(roomId);
        if (room != null) {
            room.removeObserver(userId);
            redisUtil.srem("room:observers:" + roomId, String.valueOf(userId));
        }
    }

    /**
     * 获取房间观战者数量
     */
    public int getObserverCount(String roomId) {
        return (int) redisUtil.scard("room:observers:" + roomId);
    }

    /**
     * 获取活跃房间数量
     */
    public int getActiveRoomCount() {
        return rooms.size();
    }

    /**
     * 获取用户所在房间ID
     */
    public String getUserRoomId(Long userId) {
        return userRoomMap.get(userId);
    }

    /**
     * 获取所有房间
     */
    public Collection<GameRoom> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    /**
     * 获取正在进行的对局数量
     */
    public int getPlayingRoomsCount() {
        int count = 0;
        for (GameRoom room : rooms.values()) {
            if (room.getGameState() == com.gobang.core.game.GameState.PLAYING) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取正在进行的对局列表
     */
    public List<GameRoom> getPlayingRooms() {
        List<GameRoom> playingRooms = new ArrayList<>();
        for (GameRoom room : rooms.values()) {
            if (room.getGameState() == com.gobang.core.game.GameState.PLAYING) {
                playingRooms.add(room);
            }
        }
        return playingRooms;
    }

    /**
     * 清理过期房间
     */
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                List<String> toRemove = new ArrayList<>();

                for (Map.Entry<String, GameRoom> entry : rooms.entrySet()) {
                    GameRoom room = entry.getValue();

                    // 清理已结束且过期的房间
                    if (room.getGameState() == com.gobang.core.game.GameState.FINISHED) {
                        if (now - room.getCreateTime() > roomExpireTime) {
                            toRemove.add(entry.getKey());
                        }
                    }
                }

                for (String roomId : toRemove) {
                    removeRoom(roomId);
                }

                if (!toRemove.isEmpty()) {
                    logger.info("Cleaned up {} expired rooms", toRemove.size());
                }
            } catch (Exception e) {
                logger.error("Error in cleanup task", e);
            }
        }, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 超时检查任务 - 每秒检查一次
     */
    private void startTimeoutCheckTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<GameRoom> timeoutRooms = new ArrayList<>();

                for (GameRoom room : rooms.values()) {
                    if (room.isTimeout()) {
                        timeoutRooms.add(room);
                    }
                }

                for (GameRoom room : timeoutRooms) {
                    Long timeoutPlayerId = room.getTimeoutPlayerId();
                    if (timeoutPlayerId != null) {
                        logger.warn("Game timeout in room {}, player: {}", room.getRoomId(), timeoutPlayerId);
                        if (timeoutCallback != null) {
                            timeoutCallback.onTimeout(room, timeoutPlayerId);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error in timeout check task", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        scheduler.shutdown();
        rooms.clear();
        userRoomMap.clear();
        channelUserMap.clear();
    }

    /**
     * 生成房间ID
     */
    private String generateRoomId() {
        return String.format("%06d", Math.abs(idGenerator.nextId()) % 1000000);
    }
}
