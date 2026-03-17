package com.gobang.core.handler;

import com.gobang.core.handler.AuthHandler;
import com.gobang.core.protocol.MessageHandler;
import com.gobang.core.protocol.MessageType;
import com.gobang.core.room.GameRoom;
import com.gobang.core.room.RoomManager;
import com.gobang.core.security.RateLimitManager;
import com.gobang.model.entity.User;
import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.service.GameService;
import com.gobang.service.RoomService;
import com.gobang.service.UserService;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 游戏消息处理器
 * 处理落子、认输、重连等游戏相关消息
 */
public class GameHandler implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(GameHandler.class);

    private final GameService gameService;
    private final RoomService roomService;
    private final UserService userService;
    private final RoomManager roomManager;
    private final RateLimitManager rateLimitManager;

    public GameHandler(GameService gameService, RoomService roomService, UserService userService, RoomManager roomManager, RateLimitManager rateLimitManager) {
        this.gameService = gameService;
        this.roomService = roomService;
        this.userService = userService;
        this.roomManager = roomManager;
        this.rateLimitManager = rateLimitManager;
    }

    public GameHandler(GameService gameService, RoomService roomService, UserService userService, RoomManager roomManager) {
        this(gameService, roomService, userService, roomManager, null);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, GobangProto.Packet packet) {
        // 获取用户ID（支持游客模式）
        Long userId = AuthHandler.getUserId(ctx.channel());

        // 如果未认证，使用临时游客ID（仅用于开发测试）
        if (userId == null) {
            // 从channel属性中获取临时用户ID
            io.netty.util.AttributeKey<Long> TEMP_USER_ID = io.netty.util.AttributeKey.valueOf("tempUserId");
            userId = ctx.channel().attr(TEMP_USER_ID).get();

            if (userId == null) {
                sendError(ctx, packet, "请先登录或开始匹配");
                return;
            }
        }

        MessageType messageType = MessageType.fromValue(packet.getType().getNumber());

        switch (messageType) {
            case GAME_MOVE:
                handleMove(ctx, packet, userId);
                break;
            case GAME_RESIGN:
                handleResign(ctx, packet, userId);
                break;
            case GAME_RECONNECT:
                handleReconnect(ctx, packet, userId);
                break;
            case GAME_UNDO_REQUEST:
                handleUndoRequest(ctx, packet, userId);
                break;
            case GAME_UNDO_RESPONSE:
                handleUndoResponse(ctx, packet, userId);
                break;
            default:
                logger.warn("Unsupported message type for GameHandler: {}", messageType);
                break;
        }
    }

    /**
     * 处理落子请求
     */
    private void handleMove(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            // 限流检查（防止过快落子）
            if (rateLimitManager != null) {
                if (!rateLimitManager.tryAcquire(RateLimitManager.LimitType.GAME_MOVE, userId)) {
                    sendMoveError(ctx, packet, rateLimitManager.getErrorMessage(RateLimitManager.LimitType.GAME_MOVE), null);
                    logger.debug("Game move rate limit exceeded for user: {}", userId);
                    return;
                }
            }

            GobangProto.MoveRequest request = GobangProto.MoveRequest.parseFrom(packet.getBody());

            // 获取用户所在房间
            GameRoom room = roomManager.getRoomByUserId(userId);
            if (room == null) {
                sendMoveError(ctx, packet, "您不在任何游戏中", request);
                return;
            }

            // 落子 - MoveResult 返回 success 和 message
            var moveResult = gameService.makeMove(userId, room.getRoomId(), request.getX(), request.getY());

            GobangProto.MoveResult.Builder resultBuilder = GobangProto.MoveResult.newBuilder();

            if (moveResult.isSuccess()) {
                // 落子成功
                resultBuilder.setSuccess(true)
                        .setMessage("落子成功")
                        .setState(room.buildGameStateProto());
                logger.debug("User {} made move at ({}, {}) in room {}",
                        userId, request.getX(), request.getY(), room.getRoomId());
            } else {
                // 落子失败
                resultBuilder.setSuccess(false).setMessage(moveResult.getMessage());
            }

            // 发送结果给当前玩家
            if (!moveResult.isSuccess()) {
                GobangProto.Packet response = GobangProto.Packet.newBuilder()
                        .setType(GobangProto.MessageType.GAME_MOVE_RESULT)
                        .setSequenceId(packet.getSequenceId())
                        .setTimestamp(System.currentTimeMillis())
                        .setBody(resultBuilder.build().toByteString())
                        .build();

                ctx.channel().writeAndFlush(response);
            }

        } catch (Exception e) {
            logger.error("Error handling move for user: {}", userId, e);
            sendMoveError(ctx, packet, "落子处理失败", null);
        }
    }

    /**
     * 处理认输
     */
    private void handleResign(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            // 获取用户所在房间
            GameRoom room = roomManager.getRoomByUserId(userId);
            if (room == null) {
                sendError(ctx, packet, "您不在任何游戏中");
                return;
            }

            logger.info("User {} resigned in room {}", userId, room.getRoomId());

            // 认输
            gameService.resign(userId, room.getRoomId());

            // 发送确认
            GobangProto.Packet response = GobangProto.Packet.newBuilder()
                    .setType(GobangProto.MessageType.GAME_RESIGN)
                    .setSequenceId(packet.getSequenceId())
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            ctx.channel().writeAndFlush(response);

        } catch (Exception e) {
            logger.error("Error handling resign for user: {}", userId, e);
            sendError(ctx, packet, "认输处理失败");
        }
    }

    /**
     * 处理重连请求
     */
    private void handleReconnect(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            logger.info("User {} requested reconnection", userId);

            // 尝试重连 - 通过 RoomManager 获取房间
            GameRoom room = roomManager.getRoomByUserId(userId);

            if (room != null && room.getGameState() == com.gobang.core.game.GameState.PLAYING) {
                // 更新玩家通道到新的连接
                room.updatePlayerChannel(userId, ctx.channel());

                // 确定玩家的颜色（1=黑方，2=白方）
                int myColor = 0;
                if (userId.equals(room.getBlackPlayerId())) {
                    myColor = 1;
                } else if (userId.equals(room.getWhitePlayerId())) {
                    myColor = 2;
                }

                // 重连成功，发送当前游戏状态（使用JSON格式）
                if (com.gobang.core.netty.ResponseUtil.isJsonMode(ctx.channel())) {
                    // JSON模式：发送扁平JSON格式
                    java.util.Map<String, Object> gameData = new java.util.HashMap<>();
                    gameData.put("room_id", room.getRoomId());
                    gameData.put("board", room.getBoard().toArray());
                    gameData.put("current_player", room.getCurrentPlayer());
                    gameData.put("move_count", room.getMoves().size());
                    gameData.put("game_state", room.getGameState().name());
                    gameData.put("my_color", myColor);

                    com.gobang.core.netty.ResponseUtil.sendFlatResponse(
                        ctx.channel(),
                        com.gobang.core.protocol.MessageType.GAME_STATE.getValue(),
                        packet.getSequenceId(),
                        gameData
                    );
                } else {
                    // Protobuf模式
                    GobangProto.Packet response = GobangProto.Packet.newBuilder()
                            .setType(GobangProto.MessageType.GAME_STATE)
                            .setSequenceId(packet.getSequenceId())
                            .setTimestamp(System.currentTimeMillis())
                            .setBody(room.buildGameStateProto().toByteString())
                            .build();

                    ctx.channel().writeAndFlush(response);
                }

                // 通知对手玩家已重连
                notifyOpponentReconnected(room, userId);

                logger.info("User {} reconnected to room {} as player {}", userId, room.getRoomId(), myColor);
            } else {
                // 无可重连的房间
                sendError(ctx, packet, "没有可重连的游戏");
            }

        } catch (Exception e) {
            logger.error("Error handling reconnect for user: {}", userId, e);
            sendError(ctx, packet, "重连处理失败");
        }
    }

    /**
     * 通知对手玩家已重连
     */
    private void notifyOpponentReconnected(GameRoom room, Long reconnectedUserId) {
        try {
            Long opponentId = reconnectedUserId.equals(room.getBlackPlayerId())
                    ? room.getWhitePlayerId() : room.getBlackPlayerId();

            io.netty.channel.Channel opponentChannel = opponentId.equals(room.getBlackPlayerId())
                    ? room.getBlackChannel() : room.getWhiteChannel();

            if (opponentChannel != null && opponentChannel.isActive()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.node.ObjectNode message = mapper.createObjectNode();
                message.put("type", MessageType.CHAT_SYSTEM.getValue());
                message.put("content", "对手已重新连接，游戏继续");

                String json = mapper.writeValueAsString(message);
                opponentChannel.writeAndFlush(
                    new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(json));

                logger.info("Notified player {} that opponent reconnected", opponentId);
            }
        } catch (Exception e) {
            logger.error("Error notifying opponent about reconnect", e);
        }
    }

    /**
     * 处理悔棋请求
     */
    private void handleUndoRequest(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            GameRoom room = roomManager.getRoomByUserId(userId);
            if (room == null) {
                sendError(ctx, packet, "您不在任何游戏中");
                return;
            }

            int result = roomService.requestUndo(userId, room.getRoomId());

            String message;
            switch (result) {
                case 0:
                    message = "悔棋请求已发送";
                    // 通知对方玩家
                    notifyUndoRequest(room, userId);
                    break;
                case 1:
                    message = "游戏未进行中";
                    break;
                case 2:
                    message = "无棋可悔";
                    break;
                case 3:
                    message = "已有悔棋请求";
                    break;
                default:
                    message = "请求失败";
                    break;
            }

            // 发送结果
            GobangProto.UndoResponse response = GobangProto.UndoResponse.newBuilder()
                    .setAccepted(result == 0)
                    .build();

            GobangProto.Packet responsePacket = GobangProto.Packet.newBuilder()
                    .setType(GobangProto.MessageType.GAME_UNDO_RESPONSE)
                    .setSequenceId(packet.getSequenceId())
                    .setTimestamp(System.currentTimeMillis())
                    .setBody(response.toByteString())
                    .build();

            ctx.channel().writeAndFlush(responsePacket);

        } catch (Exception e) {
            logger.error("Error handling undo request for user: {}", userId, e);
            sendError(ctx, packet, "悔棋请求处理失败");
        }
    }

    /**
     * 处理悔棋响应
     */
    private void handleUndoResponse(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            GobangProto.UndoResponse response = GobangProto.UndoResponse.parseFrom(packet.getBody());
            GameRoom room = roomManager.getRoomByUserId(userId);
            if (room == null) {
                sendError(ctx, packet, "您不在任何游戏中");
                return;
            }

            roomService.respondUndo(userId, room.getRoomId(), response.getAccepted());

        } catch (Exception e) {
            logger.error("Error handling undo response for user: {}", userId, e);
            sendError(ctx, packet, "悔棋响应处理失败");
        }
    }

    /**
     * 通知对方玩家有悔棋请求
     */
    private void notifyUndoRequest(GameRoom room, Long requesterId) {
        // 找到对手的 channel
        Long opponentId = requesterId.equals(room.getBlackPlayerId())
                ? room.getWhitePlayerId() : room.getBlackPlayerId();

        GobangProto.Packet notifyPacket = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.GAME_UNDO_REQUEST)
                .setTimestamp(System.currentTimeMillis())
                .build();

        // 发送给对手
        io.netty.channel.Channel opponentChannel = opponentId.equals(room.getBlackPlayerId())
                ? room.getBlackChannel() : room.getWhiteChannel();
        if (opponentChannel != null && opponentChannel.isActive()) {
            opponentChannel.writeAndFlush(notifyPacket);
        }
    }

    /**
     * 发送落子错误
     */
    private void sendMoveError(ChannelHandlerContext ctx, GobangProto.Packet request,
                               String message, GobangProto.MoveRequest moveRequest) {
        GobangProto.MoveResult result = GobangProto.MoveResult.newBuilder()
                .setSuccess(false)
                .setMessage(message)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.GAME_MOVE_RESULT)
                .setSequenceId(request.getSequenceId())
                .setTimestamp(System.currentTimeMillis())
                .setBody(result.toByteString())
                .build();

        ctx.channel().writeAndFlush(packet);
    }

    /**
     * 发送错误消息
     */
    private void sendError(ChannelHandlerContext ctx, GobangProto.Packet request, String message) {
        GobangProto.MoveResult result = GobangProto.MoveResult.newBuilder()
                .setSuccess(false)
                .setMessage(message)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.GAME_MOVE_RESULT)
                .setSequenceId(request.getSequenceId())
                .setTimestamp(System.currentTimeMillis())
                .setBody(result.toByteString())
                .build();

        ctx.channel().writeAndFlush(packet);
    }

    @Override
    public MessageType getSupportedType() {
        return MessageType.GAME_MOVE;
    }
}
