package com.gobang.core.netty.api;

import com.gobang.core.room.GameRoom;
import com.gobang.core.room.RoomManager;
import com.gobang.service.AuthService;
import com.gobang.service.RoomService;
import com.gobang.service.UserService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 房间API处理器
 * 处理房间相关操作
 */
public class RoomsApiHandler implements IApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(RoomsApiHandler.class);
    private final RoomManager roomManager;
    private final RoomService roomService;
    private final UserService userService;
    private final AuthService authService;

    public RoomsApiHandler(RoomManager roomManager, RoomService roomService,
                          UserService userService, AuthService authService) {
        this.roomManager = roomManager;
        this.roomService = roomService;
        this.userService = userService;
        this.authService = authService;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String getPathPrefix() {
        return "/api/rooms";
    }

    @Override
    public boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request, String path, String method) {
        try {
            if ("GET".equals(method)) {
                if (path.equals("/api/rooms/playing")) {
                    handleGetPlayingRooms(ctx);
                    return true;
                }
                if (path.matches("/api/rooms/[^/]+/observers")) {
                    String roomId = path.substring(path.lastIndexOf('/') - 1, path.lastIndexOf('/'));
                    handleGetObservers(ctx, roomId);
                    return true;
                }
                handleGetRooms(ctx);
                return true;
            }
        } catch (Exception e) {
            logger.error("Rooms API error", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Map.of("success", false, "message", "服务器错误"));
        }
        return false;
    }

    private void handleGetRooms(ChannelHandlerContext ctx) {
        List<Map<String, Object>> rooms = roomManager.getAllRooms().stream()
            .map(room -> {
                Map<String, Object> roomMap = new java.util.HashMap<>();
                roomMap.put("room_id", room.getRoomId());
                roomMap.put("game_state", room.getGameState() != null ? room.getGameState().name() : "WAITING");
                roomMap.put("move_count", room.getMoves().size());
                roomMap.put("observer_count", room.getObserverCount());

                // 获取黑方玩家信息
                Long blackPlayerId = room.getBlackPlayerId();
                if (blackPlayerId != null) {
                    var blackUser = userService.getUserById(blackPlayerId);
                    if (blackUser != null) {
                        roomMap.put("black_player", Map.of(
                            "id", blackUser.getId(),
                            "nickname", blackUser.getNickname(),
                            "rating", blackUser.getRating()
                        ));
                    }
                }

                // 获取白方玩家信息
                Long whitePlayerId = room.getWhitePlayerId();
                if (whitePlayerId != null) {
                    var whiteUser = userService.getUserById(whitePlayerId);
                    if (whiteUser != null) {
                        roomMap.put("white_player", Map.of(
                            "id", whiteUser.getId(),
                            "nickname", whiteUser.getNickname(),
                            "rating", whiteUser.getRating()
                        ));
                    }
                }

                return roomMap;
            })
            .collect(Collectors.toList());

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("rooms", rooms, "count", rooms.size()));

        sendJsonResponse(ctx, HttpResponseStatus.OK, response);
    }

    private void handleGetPlayingRooms(ChannelHandlerContext ctx) {
        List<Map<String, Object>> playingRooms = roomManager.getPlayingRooms().stream()
            .map(room -> {
                Map<String, Object> roomMap = new java.util.HashMap<>();
                roomMap.put("room_id", room.getRoomId());
                roomMap.put("game_state", room.getGameState().name());
                roomMap.put("move_count", room.getMoves().size());
                roomMap.put("observer_count", room.getObserverCount());

                // 获取黑方玩家信息
                Long blackPlayerId = room.getBlackPlayerId();
                if (blackPlayerId != null) {
                    var blackUser = userService.getUserById(blackPlayerId);
                    if (blackUser != null) {
                        roomMap.put("black_player", Map.of(
                            "id", blackUser.getId(),
                            "nickname", blackUser.getNickname(),
                            "rating", blackUser.getRating()
                        ));
                    }
                }

                // 获取白方玩家信息
                Long whitePlayerId = room.getWhitePlayerId();
                if (whitePlayerId != null) {
                    var whiteUser = userService.getUserById(whitePlayerId);
                    if (whiteUser != null) {
                        roomMap.put("white_player", Map.of(
                            "id", whiteUser.getId(),
                            "nickname", whiteUser.getNickname(),
                            "rating", whiteUser.getRating()
                        ));
                    }
                }

                return roomMap;
            })
            .collect(Collectors.toList());

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("rooms", playingRooms, "count", playingRooms.size()));

        sendJsonResponse(ctx, HttpResponseStatus.OK, response);
    }

    private void handleGetObservers(ChannelHandlerContext ctx, String roomId) {
        var room = roomManager.getRoom(roomId);
        if (room == null) {
            sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                Map.of("success", false, "message", "房间不存在"));
            return;
        }

        List<Map<String, Object>> observers = room.getObservers().stream()
            .map(observer -> {
                Map<String, Object> observerMap = new java.util.HashMap<>();
                observerMap.put("user_id", observer.getUserId());
                observerMap.put("username", observer.getUsername());
                observerMap.put("nickname", observer.getNickname());
                return observerMap;
            })
            .collect(Collectors.toList());

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("observers", observers, "count", observers.size()));

        sendJsonResponse(ctx, HttpResponseStatus.OK, response);
    }
}
