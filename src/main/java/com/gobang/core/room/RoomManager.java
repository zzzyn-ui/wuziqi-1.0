package com.gobang.core.room;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间管理器
 * 管理游戏房间的创建、加入、删除
 */
@Component
public class RoomManager {

    private static final Logger logger = LoggerFactory.getLogger(RoomManager.class);

    // 房间ID -> 房间信息
    private final Map<String, RoomInfo> rooms = new ConcurrentHashMap<>();

    /**
     * 创建房间
     */
    public RoomInfo createRoom(String roomId, Long userId, String roomName, String gameMode, boolean isPrivate) {
        RoomInfo room = new RoomInfo(roomId, userId, roomName, gameMode, isPrivate);
        rooms.put(roomId, room);
        logger.info("房间创建成功: roomId={}, userId={}, name={}", roomId, userId, roomName);
        return room;
    }

    /**
     * 加入房间
     */
    public boolean joinRoom(String roomId, Long userId) {
        RoomInfo room = rooms.get(roomId);
        if (room == null) {
            logger.warn("房间不存在: roomId={}", roomId);
            return false;
        }

        if (room.getPlayerCount() >= 2) {
            logger.warn("房间已满: roomId={}", roomId);
            return false;
        }

        room.addPlayer(userId);
        logger.info("玩家 {} 加入房间: roomId={}", userId, roomId);
        return true;
    }

    /**
     * 获取房间信息
     */
    public RoomInfo getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 获取所有公开房间
     */
    public List<RoomInfo> getPublicRooms() {
        List<RoomInfo> publicRooms = new ArrayList<>();
        for (RoomInfo room : rooms.values()) {
            if (!room.isPrivate() && room.getPlayerCount() < 2) {
                publicRooms.add(room);
            }
        }
        return publicRooms;
    }

    /**
     * 删除房间
     */
    public void removeRoom(String roomId) {
        rooms.remove(roomId);
        logger.info("房间已删除: roomId={}", roomId);
    }

    /**
     * 清理空房间
     */
    public void cleanEmptyRooms() {
        rooms.entrySet().removeIf(entry -> {
            RoomInfo room = entry.getValue();
            return room.getPlayerCount() == 0 && room.isExpired();
        });
    }

    /**
     * 房间信息
     */
    public static class RoomInfo {
        private final String roomId;
        private final Long creatorId;
        private final String roomName;
        private final String gameMode;
        private final boolean isPrivate;
        private final Set<Long> players = new HashSet<>();
        private final long createTime;

        public RoomInfo(String roomId, Long creatorId, String roomName, String gameMode, boolean isPrivate) {
            this.roomId = roomId;
            this.creatorId = creatorId;
            this.roomName = roomName;
            this.gameMode = gameMode;
            this.isPrivate = isPrivate;
            this.players.add(creatorId);
            this.createTime = System.currentTimeMillis();
        }

        public String getRoomId() { return roomId; }
        public Long getCreatorId() { return creatorId; }
        public String getRoomName() { return roomName; }
        public String getGameMode() { return gameMode; }
        public boolean isPrivate() { return isPrivate; }
        public Set<Long> getPlayers() { return players; }
        public long getCreateTime() { return createTime; }

        public int getPlayerCount() { return players.size(); }

        public void addPlayer(Long playerId) {
            players.add(playerId);
        }

        public boolean isExpired() {
            // 房间创建超过30分钟且没有玩家，视为过期
            return System.currentTimeMillis() - createTime > 30 * 60 * 1000;
        }
    }
}
