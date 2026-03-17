package com.gobang.core.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.core.handler.AuthHandler;
import com.gobang.core.netty.ResponseUtil;
import com.gobang.core.protocol.MessageHandler;
import com.gobang.core.protocol.MessageType;
import com.gobang.core.room.GameRoom;
import com.gobang.core.room.RoomManager;
import com.gobang.model.entity.User;
import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.service.UserService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 房间消息处理器
 * 处理创建房间、加入房间等房间相关消息
 */
public class RoomHandler implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(RoomHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final RoomManager roomManager;
    private final UserService userService;

    // 房间创建者信息：roomId -> 创建者信息
    private final Map<String, RoomCreator> roomCreators = new HashMap<>();

    public RoomHandler(RoomManager roomManager, UserService userService) {
        this.roomManager = roomManager;
        this.userService = userService;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, GobangProto.Packet packet) {
        // 房间相关消息只支持JSON格式
        logger.warn("Room messages must be sent in JSON format");
    }

    /**
     * 处理JSON格式的房间消息
     */
    public void handleJsonMessage(ChannelHandlerContext ctx, JsonNode root) {
        try {
            int typeValue = root.get("type").asInt();
            long sequenceId = root.has("sequenceId") ? root.get("sequenceId").asLong() :
                              (root.has("sequence_id") ? root.get("sequence_id").asLong() : 0);

            MessageType messageType = MessageType.fromValue(typeValue);

            // 获取用户ID
            Long userId = AuthHandler.getUserId(ctx.channel());
            if (userId == null) {
                ResponseUtil.sendJsonResponse(ctx.channel(), typeValue, sequenceId,
                    Map.of("success", false, "message", "请先登录"));
                return;
            }

            switch (messageType) {
                case CREATE_ROOM:
                    handleCreateRoom(ctx, root, userId, sequenceId);
                    break;
                case JOIN_ROOM:
                    handleJoinRoom(ctx, root, userId, sequenceId);
                    break;
                case LEAVE_ROOM:
                    handleLeaveRoom(ctx, root, userId, sequenceId);
                    break;
                case ROOM_INFO:
                    handleRoomInfo(ctx, root, userId, sequenceId);
                    break;
                case ROOM_JOINED:
                    // 这是服务器发给客户端的消息，客户端不应该发送
                    logger.warn("Client should not send ROOM_JOINED message");
                    break;
                default:
                    logger.warn("Unsupported room message type: {}", messageType);
                    break;
            }
        } catch (Exception e) {
            logger.error("Error handling room message", e);
        }
    }

    /**
     * 处理创建房间
     */
    private void handleCreateRoom(ChannelHandlerContext ctx, JsonNode root, Long userId, long sequenceId) {
        try {
            if (!root.has("body")) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.CREATE_ROOM.getValue(), sequenceId,
                    Map.of("success", false, "message", "缺少参数"));
                return;
            }

            JsonNode body = root.get("body");
            String roomId = body.has("room_id") ? body.get("room_id").asText() : null;
            JsonNode userInfo = body.has("user_info") ? body.get("user_info") : null;

            // 获取用户信息
            User user = userService.getUserById(userId);
            if (user == null) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.CREATE_ROOM.getValue(), sequenceId,
                    Map.of("success", false, "message", "用户不存在"));
                return;
            }

            // 如果客户端没有提供房间ID，生成一个
            if (roomId == null || roomId.isEmpty()) {
                GameRoom newRoom = roomManager.createRoom();
                roomId = newRoom.getRoomId();
            }

            // 保存房间创建者信息
            RoomCreator creator = new RoomCreator(userId, user.getNickname(), user.getUsername(), ctx.channel());
            roomCreators.put(roomId, creator);

            logger.info("User {} created room: {}", userId, roomId);

            // 发送成功响应
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("room_id", roomId);
            responseData.put("message", "房间创建成功");

            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.CREATE_ROOM.getValue(), sequenceId, responseData);

        } catch (Exception e) {
            logger.error("Error creating room for user: {}", userId, e);
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.CREATE_ROOM.getValue(), sequenceId,
                Map.of("success", false, "message", "创建房间失败"));
        }
    }

    /**
     * 处理加入房间
     */
    private void handleJoinRoom(ChannelHandlerContext ctx, JsonNode root, Long userId, long sequenceId) {
        String roomId = null;
        try {
            if (!root.has("body")) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.JOIN_ROOM.getValue(), sequenceId,
                    Map.of("success", false, "message", "缺少参数"));
                return;
            }

            JsonNode body = root.get("body");
            roomId = body.has("room_id") ? body.get("room_id").asText() : null;

            if (roomId == null || roomId.isEmpty()) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.JOIN_ROOM.getValue(), sequenceId,
                    Map.of("success", false, "message", "房间ID不能为空"));
                return;
            }

            // 获取房间创建者信息
            RoomCreator creator = roomCreators.get(roomId);
            if (creator == null) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.JOIN_ROOM.getValue(), sequenceId,
                    Map.of("success", false, "message", "房间不存在"));
                return;
            }

            // 获取加入者信息
            User joiner = userService.getUserById(userId);
            if (joiner == null) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.JOIN_ROOM.getValue(), sequenceId,
                    Map.of("success", false, "message", "用户不存在"));
                return;
            }

            // 获取或创建房间
            GameRoom room = roomManager.getRoom(roomId);
            if (room == null) {
                room = roomManager.createRoom();
                // 需要用正确的roomId
                // 这里简化处理，直接使用现有的房间
            }

            logger.info("User {} joining room: {}", userId, roomId);

            // 开始游戏：创建者为黑方，加入者为白方
            room.startGame(creator.userId, creator.channel, userId, ctx.channel());

            // 设置游戏模式为休闲（房间对战不计入排位）
            room.setGameMode("casual");

            // 将玩家加入房间管理器
            roomManager.joinRoom(roomId, creator.userId, creator.channel);
            roomManager.joinRoom(roomId, userId, ctx.channel());

            // 向创建者发送通知（有人加入了）
            Map<String, Object> creatorNotification = new HashMap<>();
            creatorNotification.put("type", MessageType.ROOM_JOINED.getValue());
            creatorNotification.put("player", Map.of(
                "user_id", userId,
                "nickname", joiner.getNickname(),
                "username", joiner.getUsername()
            ));

            String creatorJson = objectMapper.writeValueAsString(creatorNotification);
            creator.channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(creatorJson));

            // 向加入者发送游戏开始通知
            Map<String, Object> joinerResponse = new HashMap<>();
            joinerResponse.put("success", true);
            joinerResponse.put("room_id", roomId);
            joinerResponse.put("my_color", 2); // 加入者执白
            joinerResponse.put("opponent", Map.of(
                "user_id", creator.userId,
                "nickname", creator.nickname,
                "username", creator.username
            ));
            joinerResponse.put("game_started", true);

            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.JOIN_ROOM.getValue(), sequenceId, joinerResponse);

            // 向双方发送初始游戏状态
            sendGameStateToPlayer(room, creator.userId, creator.channel, 1);
            sendGameStateToPlayer(room, userId, ctx.channel(), 2);

            logger.info("Game started in room {}: black={}, white={}", roomId, creator.userId, userId);

        } catch (Exception e) {
            logger.error("Error joining room {} for user: {}", roomId, userId, e);
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.JOIN_ROOM.getValue(), sequenceId,
                Map.of("success", false, "message", "加入房间失败"));
        }
    }

    /**
     * 处理离开房间
     */
    private void handleLeaveRoom(ChannelHandlerContext ctx, JsonNode root, Long userId, long sequenceId) {
        String roomId = null;
        try {
            if (!root.has("body")) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.LEAVE_ROOM.getValue(), sequenceId,
                    Map.of("success", false, "message", "缺少参数"));
                return;
            }

            JsonNode body = root.get("body");
            roomId = body.has("room_id") ? body.get("room_id").asText() : null;

            if (roomId == null || roomId.isEmpty()) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.LEAVE_ROOM.getValue(), sequenceId,
                    Map.of("success", false, "message", "房间ID不能为空"));
                return;
            }

            // 从房间管理器移除玩家
            roomManager.leaveRoom(roomId, userId);

            // 清理房间创建者信息
            roomCreators.remove(roomId);

            logger.info("User {} left room: {}", userId, roomId);

            // 发送成功响应
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "已离开房间");

            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.LEAVE_ROOM.getValue(), sequenceId, responseData);

        } catch (Exception e) {
            logger.error("Error leaving room {} for user: {}", roomId, userId, e);
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.LEAVE_ROOM.getValue(), sequenceId,
                Map.of("success", false, "message", "离开房间失败"));
        }
    }

    /**
     * 处理获取房间信息
     */
    private void handleRoomInfo(ChannelHandlerContext ctx, JsonNode root, Long userId, long sequenceId) {
        try {
            if (!root.has("body")) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.ROOM_INFO.getValue(), sequenceId,
                    Map.of("success", false, "message", "缺少参数"));
                return;
            }

            JsonNode body = root.get("body");
            String roomId = body.has("room_id") ? body.get("room_id").asText() : null;

            if (roomId == null || roomId.isEmpty()) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.ROOM_INFO.getValue(), sequenceId,
                    Map.of("success", false, "message", "房间ID不能为空"));
                return;
            }

            // 获取房间信息
            GameRoom room = roomManager.getRoom(roomId);
            if (room == null) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.ROOM_INFO.getValue(), sequenceId,
                    Map.of("success", false, "message", "房间不存在"));
                return;
            }

            // 获取房间创建者信息
            RoomCreator creator = roomCreators.get(roomId);

            // 构建房间信息响应
            Map<String, Object> roomInfo = new HashMap<>();
            roomInfo.put("success", true);
            roomInfo.put("room_id", roomId);
            roomInfo.put("game_state", room.getGameState().name());
            roomInfo.put("current_player", room.getCurrentPlayer());
            roomInfo.put("move_count", room.getMoves().size());
            roomInfo.put("game_mode", room.getGameMode());

            if (creator != null) {
                roomInfo.put("creator", Map.of(
                    "user_id", creator.userId,
                    "nickname", creator.nickname,
                    "username", creator.username
                ));
            }

            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.ROOM_INFO.getValue(), sequenceId, roomInfo);

        } catch (Exception e) {
            logger.error("Error getting room info", e);
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.ROOM_INFO.getValue(), sequenceId,
                Map.of("success", false, "message", "获取房间信息失败"));
        }
    }

    /**
     * 向玩家发送游戏状态
     */
    private void sendGameStateToPlayer(GameRoom room, Long userId, Channel channel, int myColor) {
        try {
            Map<String, Object> gameState = new HashMap<>();
            gameState.put("type", MessageType.GAME_STATE.getValue());
            gameState.put("room_id", room.getRoomId());
            gameState.put("my_color", myColor);
            gameState.put("opponent_color", myColor == 1 ? 2 : 1);
            gameState.put("current_player", room.getCurrentPlayer());
            gameState.put("game_state", room.getGameState().name());
            gameState.put("board", room.getBoard().toArray());
            gameState.put("move_count", room.getMoves().size());

            String json = objectMapper.writeValueAsString(gameState);
            channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(json));

            logger.debug("Sent game state to user {}: myColor={}, currentPlayer={}", userId, myColor, room.getCurrentPlayer());
        } catch (Exception e) {
            logger.error("Error sending game state to user: {}", userId, e);
        }
    }

    @Override
    public MessageType getSupportedType() {
        return MessageType.CREATE_ROOM; // 返回房间相关的类型
    }

    /**
     * 房间创建者信息
     */
    private static class RoomCreator {
        final Long userId;
        final String nickname;
        final String username;
        final Channel channel;

        RoomCreator(Long userId, String nickname, String username, Channel channel) {
            this.userId = userId;
            this.nickname = nickname;
            this.username = username;
            this.channel = channel;
        }
    }
}
