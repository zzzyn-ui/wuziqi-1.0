package com.gobang.core.social;

import com.gobang.core.room.RoomManager;
import com.gobang.protocol.protobuf.GobangProto;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 观战管理器
 * 处理观战相关功能
 */
public class ObserverManager {

    private static final Logger logger = LoggerFactory.getLogger(ObserverManager.class);

    private final RoomManager roomManager;

    public ObserverManager(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    /**
     * 加入观战
     *
     * @return 是否成功
     */
    public boolean joinObservation(String roomId, Long userId, String username, String nickname, Channel channel) {
        var room = roomManager.getRoom(roomId);
        if (room == null) {
            logger.warn("Room {} not found for observation", roomId);
            return false;
        }

        if (room.getGameState() == com.gobang.core.game.GameState.WAITING) {
            logger.warn("Room {} not started, cannot observe", roomId);
            return false;
        }

        roomManager.addObserver(roomId, userId, username, nickname, channel);
        room.addObserver(userId, username, nickname, channel);

        logger.info("User {} joined observation of room {}", userId, roomId);
        return true;
    }

    /**
     * 离开观战
     */
    public void leaveObservation(String roomId, Long userId) {
        var room = roomManager.getRoom(roomId);
        if (room != null) {
            room.removeObserver(userId);
        }
        roomManager.removeObserver(roomId, userId);

        logger.info("User {} left observation of room {}", userId, roomId);
    }

    /**
     * 获取观战者列表
     */
    public List<GobangProto.ObserverInfo> getObserverList(String roomId) {
        List<GobangProto.ObserverInfo> observers = new ArrayList<>();
        var room = roomManager.getRoom(roomId);

        if (room != null) {
            for (com.gobang.core.room.GameRoom.Observer observer : room.getObservers()) {
                GobangProto.ObserverInfo info = GobangProto.ObserverInfo.newBuilder()
                        .setUserId(String.valueOf(observer.getUserId()))
                        .setUsername(observer.getUsername())
                        .setNickname(observer.getNickname())
                        .build();
                observers.add(info);
            }
        }

        return observers;
    }

    /**
     * 获取观战者数量
     */
    public int getObserverCount(String roomId) {
        return roomManager.getObserverCount(roomId);
    }

    /**
     * 广播观战者数量变化
     */
    public void broadcastObserverCount(String roomId) {
        var room = roomManager.getRoom(roomId);
        if (room == null) {
            return;
        }

        int count = room.getObserverCount();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.OBSERVER_COUNT)
                .setTimestamp(System.currentTimeMillis())
                .setBody(GobangProto.ObserverCount.newBuilder()
                        .setCount(count)
                        .build()
                        .toByteString())
                .build();

        room.broadcast(packet);
    }
}
