package com.gobang.core.handler;

import com.gobang.core.handler.AuthHandler;
import com.gobang.core.protocol.MessageHandler;
import com.gobang.core.protocol.MessageType;
import com.gobang.core.room.RoomManager;
import com.gobang.core.security.RateLimitManager;
import com.gobang.model.entity.User;
import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.service.GameService;
import com.gobang.service.UserService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 匹配消息处理器
 * 处理开始匹配、取消匹配等消息
 */
public class MatchHandler implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(MatchHandler.class);

    private final GameService gameService;
    private final UserService userService;
    private final RoomManager roomManager;
    private final RateLimitManager rateLimitManager;

    public MatchHandler(GameService gameService, UserService userService, RoomManager roomManager, RateLimitManager rateLimitManager) {
        this.gameService = gameService;
        this.userService = userService;
        this.roomManager = roomManager;
        this.rateLimitManager = rateLimitManager;
    }

    public MatchHandler(GameService gameService, UserService userService, RoomManager roomManager) {
        this(gameService, userService, roomManager, null);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, GobangProto.Packet packet) {
        // 验证认证 - 获取用户ID
        Long userId = AuthHandler.getUserId(ctx.channel());
        logger.info("MatchHandler - userId from channel: {}", userId);

        if (userId == null) {
            logger.warn("Unauthenticated user");
            sendError(ctx, packet, "请先登录");
            return;
        }

        logger.info("Using authenticated user ID: {}", userId);

        MessageType messageType = MessageType.fromValue(packet.getType().getNumber());

        switch (messageType) {
            case MATCH_START:
                handleMatchStart(ctx, packet, userId);
                break;
            case MATCH_CANCEL:
                handleMatchCancel(ctx, packet, userId);
                break;
            default:
                logger.warn("Unsupported message type for MatchHandler: {}", messageType);
                break;
        }
    }

    /**
     * 处理开始匹配
     */
    private void handleMatchStart(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            // 限流检查
            if (rateLimitManager != null) {
                if (!rateLimitManager.tryAcquire(RateLimitManager.LimitType.MATCH, userId)) {
                    sendError(ctx, packet, rateLimitManager.getErrorMessage(RateLimitManager.LimitType.MATCH));
                    logger.warn("Match rate limit exceeded for user: {}", userId);
                    return;
                }
            }

            String username;
            String nickname;
            Integer rating;
            String mode = "casual";  // 默认休闲模式

            // 尝试从请求body中获取游戏模式
            if (packet.getBody() != null && !packet.getBody().isEmpty()) {
                try {
                    GobangProto.MatchRequest matchRequest = GobangProto.MatchRequest.parseFrom(packet.getBody());
                    if (matchRequest != null) {
                        String requestedMode = matchRequest.getMode();
                        logger.info("解析MatchRequest: userId={}, requestedMode='{}', rating={}",
                            userId, requestedMode, matchRequest.getRating());
                        if (requestedMode != null && !requestedMode.isEmpty()) {
                            mode = requestedMode;
                            logger.info("User {} requested match mode: {}", userId, mode);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse MatchRequest for user {}, using default mode: {}", userId, mode, e);
                }
            } else {
                logger.info("No request body, using default mode: {} for user: {}", mode, userId);
            }

            // 竞技模式只允许正式用户
            if ("competitive".equals(mode) && userId < 0) {
                sendError(ctx, packet, "竞技模式仅对正式注册用户开放，请先登录或选择休闲模式");
                return;
            }

            // 游客用户（userId为负数）
            if (userId < 0) {
                // 尝试从channel属性获取游客用户信息
                io.netty.util.AttributeKey<String> GUEST_USERNAME = io.netty.util.AttributeKey.valueOf("guestUsername");
                io.netty.util.AttributeKey<String> GUEST_NICKNAME = io.netty.util.AttributeKey.valueOf("guestNickname");

                String guestUsername = ctx.channel().attr(GUEST_USERNAME).get();
                String guestNickname = ctx.channel().attr(GUEST_NICKNAME).get();

                username = guestUsername != null ? guestUsername : "guest_" + Math.abs(userId);
                nickname = guestNickname != null ? guestNickname : "游客" + Math.abs(userId) % 1000;
                rating = 1200;
                logger.info("Guest user {} ({}) started matching in {} mode", userId, nickname, mode);
            } else {
                // 正式用户
                User user = userService.getUserById(userId);
                if (user == null) {
                    sendError(ctx, packet, "用户不存在");
                    return;
                }

                username = user.getUsername();
                nickname = user.getNickname();
                rating = user.getRating();

                // 检查是否已在房间中且游戏仍在进行中
                com.gobang.core.room.GameRoom existingRoom = roomManager.getRoomByUserId(userId);
                if (existingRoom != null) {
                    // 只有当游戏状态为 PLAYING 时才拒绝匹配
                    if (existingRoom.getGameState() == com.gobang.core.game.GameState.PLAYING) {
                        sendError(ctx, packet, "您已在游戏中，请先结束当前对局");
                        return;
                    }
                    // 游戏已结束，清理旧的房间映射
                    logger.info("User {} has finished game in room {}, removing old mapping", userId, existingRoom.getRoomId());
                    roomManager.removeRoom(existingRoom.getRoomId());
                }

                logger.info("User {} started matching in {} mode, rating: {}", userId, mode, rating);
            }

            // 开始匹配
            boolean success = gameService.startMatch(
                    userId,
                    username,
                    nickname,
                    rating,
                    ctx.channel(),
                    mode
            );

            if (!success) {
                sendError(ctx, packet, "匹配失败，您可能已在匹配队列中");
                return;
            }

            // 发送确认响应
            GobangProto.Packet response = GobangProto.Packet.newBuilder()
                    .setType(GobangProto.MessageType.MATCH_START)
                    .setSequenceId(packet.getSequenceId())
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            ctx.channel().writeAndFlush(response);

            logger.info("User {} added to match queue in {} mode", userId, mode);

        } catch (Exception e) {
            logger.error("Error handling match start for user: {}", userId, e);
            sendError(ctx, packet, "匹配请求处理失败");
        }
    }

    /**
     * 处理取消匹配
     */
    private void handleMatchCancel(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            logger.info("User {} requested to cancel matching", userId);

            // 取消匹配
            boolean success = gameService.cancelMatch(userId);

            if (success) {
                // 恢复用户状态
                userService.updateUserStatus(userId, 0);

                // 发送确认响应
                GobangProto.Packet response = GobangProto.Packet.newBuilder()
                        .setType(GobangProto.MessageType.MATCH_CANCEL)
                        .setSequenceId(packet.getSequenceId())
                        .setTimestamp(System.currentTimeMillis())
                        .build();

                ctx.channel().writeAndFlush(response);

                logger.info("User {} removed from match queue", userId);
            } else {
                sendError(ctx, packet, "取消匹配失败，您可能不在匹配队列中");
            }

        } catch (Exception e) {
            logger.error("Error handling match cancel for user: {}", userId, e);
            sendError(ctx, packet, "取消匹配请求处理失败");
        }
    }

    /**
     * 发送错误消息
     */
    private void sendError(ChannelHandlerContext ctx, GobangProto.Packet request, String message) {
        GobangProto.MatchFailed failed = GobangProto.MatchFailed.newBuilder()
                .setReason(message)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.MATCH_FAILED)
                .setSequenceId(request.getSequenceId())
                .setTimestamp(System.currentTimeMillis())
                .setBody(failed.toByteString())
                .build();

        ctx.channel().writeAndFlush(packet);
    }

    @Override
    public MessageType getSupportedType() {
        return MessageType.MATCH_START;
    }
}
