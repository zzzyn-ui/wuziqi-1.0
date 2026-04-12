package com.gobang.service.impl;

import com.gobang.core.room.RoomManager;
import com.gobang.model.dto.ObserverRoomDto;
import com.gobang.model.entity.User;
import com.gobang.service.GameService;
import com.gobang.service.ObserverService;
import com.gobang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 观战服务实现
 * 管理观战者加入/离开，维护观战者列表
 */
@Service
@RequiredArgsConstructor
public class ObserverServiceImpl implements ObserverService {

    private static final Logger logger = LoggerFactory.getLogger(ObserverServiceImpl.class);

    private final RoomManager roomManager;
    private final GameService gameService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    // 用户ID -> 正在观战的房间ID
    private final Map<Long, String> userObservingRoom = new ConcurrentHashMap<>();

    @Override
    public List<Map<String, Object>> getObservableRooms() {
        List<Map<String, Object>> observableRooms = new ArrayList<>();

        // 获取所有进行中的房间
        for (RoomManager.RoomInfo roomInfo : roomManager.getPublicRooms()) {
            Map<String, Object> roomData = new HashMap<>();
            roomData.put("roomId", roomInfo.getRoomId());
            roomData.put("roomName", roomInfo.getRoomName());
            roomData.put("gameMode", roomInfo.getGameMode());
            roomData.put("playerCount", roomInfo.getPlayerCount());
            roomData.put("observerCount", getObserverCount(roomInfo.getRoomId()));

            // 获取玩家信息
            List<Long> playerIds = new ArrayList<>(roomInfo.getPlayers());
            if (playerIds.size() >= 2) {
                User player1 = userService.getUserById(playerIds.get(0));
                User player2 = userService.getUserById(playerIds.get(1));

                if (player1 != null && player2 != null) {
                    Map<String, Object> player1Info = new HashMap<>();
                    player1Info.put("id", player1.getId());
                    player1Info.put("username", player1.getUsername());
                    player1Info.put("nickname", player1.getNickname());
                    player1Info.put("rating", player1.getRating());

                    Map<String, Object> player2Info = new HashMap<>();
                    player2Info.put("id", player2.getId());
                    player2Info.put("username", player2.getUsername());
                    player2Info.put("nickname", player2.getNickname());
                    player2Info.put("rating", player2.getRating());

                    roomData.put("player1", player1Info);
                    roomData.put("player2", player2Info);
                }
            }

            observableRooms.add(roomData);
        }

        return observableRooms;
    }

    @Override
    public ObserverRoomDto joinObserver(String roomId, Long userId) {
        // 检查房间是否存在
        RoomManager.RoomInfo roomInfo = roomManager.getRoom(roomId);
        if (roomInfo == null) {
            throw new RuntimeException("房间不存在");
        }

        // 检查用户是否已经在观战
        String currentObserving = userObservingRoom.get(userId);
        if (currentObserving != null) {
            if (currentObserving.equals(roomId)) {
                throw new RuntimeException("你已经在观战此房间");
            } else {
                // 先离开之前的观战房间
                leaveObserver(currentObserving, userId);
            }
        }

        // 加入观战（这里需要在GameRoom中添加observers支持）
        // 暂时使用内存存储观战者
        gameService.addObserverToRoom(roomId, userId);

        // 记录用户观战状态
        userObservingRoom.put(userId, roomId);

        // 构建观战房间信息
        ObserverRoomDto dto = buildObserverRoomDto(roomId, roomInfo);

        // 广播观战者加入
        broadcastObserverCount(roomId);

        logger.info("用户 {} 加入观战: roomId={}, 当前观战者数: {}",
                userId, roomId, dto.getObserverCount());

        return dto;
    }

    @Override
    public void leaveObserver(String roomId, Long userId) {
        // 检查用户是否在观战此房间
        String currentObserving = userObservingRoom.get(userId);
        if (currentObserving == null || !currentObserving.equals(roomId)) {
            return;
        }

        // 移除观战者
        gameService.removeObserverFromRoom(roomId, userId);

        // 清除用户观战状态
        userObservingRoom.remove(userId);

        // 广播观战者离开
        broadcastObserverCount(roomId);

        logger.info("用户 {} 离开观战: roomId={}", userId, roomId);
    }

    @Override
    public List<Long> getObservers(String roomId) {
        return gameService.getRoomObservers(roomId);
    }

    @Override
    public int getObserverCount(String roomId) {
        return gameService.getRoomObserverCount(roomId);
    }

    @Override
    public String getObservingRoom(Long userId) {
        return userObservingRoom.get(userId);
    }

    /**
     * 构建观战房间DTO
     */
    private ObserverRoomDto buildObserverRoomDto(String roomId, RoomManager.RoomInfo roomInfo) {
        // 获取玩家信息
        List<Long> playerIds = new ArrayList<>(roomInfo.getPlayers());
        ObserverRoomDto.PlayerInfo blackPlayer = null;
        ObserverRoomDto.PlayerInfo whitePlayer = null;

        if (playerIds.size() >= 1) {
            User player1 = userService.getUserById(playerIds.get(0));
            if (player1 != null) {
                blackPlayer = new ObserverRoomDto.PlayerInfo(
                        player1.getId(),
                        player1.getUsername(),
                        player1.getNickname(),
                        player1.getRating(),
                        player1.getLevel()
                );
            }
        }

        if (playerIds.size() >= 2) {
            User player2 = userService.getUserById(playerIds.get(1));
            if (player2 != null) {
                whitePlayer = new ObserverRoomDto.PlayerInfo(
                        player2.getId(),
                        player2.getUsername(),
                        player2.getNickname(),
                        player2.getRating(),
                        player2.getLevel()
                );
            }
        }

        // 获取游戏状态
        Object gameRoom = gameService.getGameRoom(roomId);
        List<List<Integer>> board = null;
        Integer currentTurn = null;
        String gameStatus = "WAITING";

        if (gameRoom != null) {
            try {
                java.lang.reflect.Method getBoardMethod = gameRoom.getClass().getMethod("getBoardData");
                board = (List<List<Integer>>) getBoardMethod.invoke(gameRoom);

                java.lang.reflect.Method getCurrentTurnMethod = gameRoom.getClass().getMethod("getCurrentTurnValue");
                currentTurn = (Integer) getCurrentTurnMethod.invoke(gameRoom);

                java.lang.reflect.Method getGameStateMethod = gameRoom.getClass().getMethod("getGameState");
                Object gameState = getGameStateMethod.invoke(gameRoom);
                gameStatus = gameState.toString();
            } catch (Exception e) {
                // 反射失败，使用默认值
            }
        }

        // 获取观战者列表
        List<Long> observers = getObservers(roomId);

        // 转换Date为LocalDateTime
        LocalDateTime createTime = null;
        try {
            java.util.Date date = new java.util.Date(roomInfo.getCreateTime());
            createTime = date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            createTime = LocalDateTime.now();
        }

        return ObserverRoomDto.create(
                roomId,
                roomInfo.getRoomName(),
                roomInfo.getGameMode(),
                blackPlayer,
                whitePlayer,
                currentTurn,
                gameStatus,
                board,
                observers,
                createTime
        );
    }

    /**
     * 广播观战者数量变化
     */
    private void broadcastObserverCount(String roomId) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "OBSERVER_COUNT_CHANGE");
        message.put("roomId", roomId);
        message.put("observerCount", getObserverCount(roomId));

        // 广播给所有订阅房间消息的用户（包括玩家和观战者）
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/observer", message);
    }
}
