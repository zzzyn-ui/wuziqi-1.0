package com.gobang.core.handler;

import com.gobang.core.handler.AuthHandler;
import com.gobang.core.protocol.MessageHandler;
import com.gobang.core.protocol.MessageType;
import com.gobang.core.room.GameRoom;
import com.gobang.core.room.RoomManager;
import com.gobang.core.social.ObserverManager;
import com.gobang.model.entity.User;
import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.service.UserService;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 观战消息处理器
 * 处理加入观战、离开观战等消息
 */
public class ObserverHandler implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(ObserverHandler.class);

    private final ObserverManager observerManager;
    private final UserService userService;
    private final RoomManager roomManager;

    public ObserverHandler(ObserverManager observerManager, UserService userService, RoomManager roomManager) {
        this.observerManager = observerManager;
        this.userService = userService;
        this.roomManager = roomManager;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, GobangProto.Packet packet) {
        // 验证认证
        if (!AuthHandler.isAuthenticated(ctx.channel())) {
            sendError(ctx, packet, "请先登录");
            return;
        }

        Long userId = AuthHandler.getUserId(ctx.channel());
        if (userId == null) {
            sendError(ctx, packet, "用户未认证");
            return;
        }

        MessageType messageType = MessageType.fromValue(packet.getType().getNumber());

        switch (messageType) {
            case OBSERVER_JOIN:
                handleObserverJoin(ctx, packet, userId);
                break;
            case OBSERVER_LEAVE:
                handleObserverLeave(ctx, packet, userId);
                break;
            case OBSERVER_LIST:
                handleObserverList(ctx, packet, userId);
                break;
            default:
                logger.warn("Unsupported message type for ObserverHandler: {}", messageType);
                break;
        }
    }

    /**
     * 处理加入观战
     */
    private void handleObserverJoin(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            GobangProto.ObserverJoin request = GobangProto.ObserverJoin.parseFrom(packet.getBody());

            // 获取用户信息
            User user = userService.getUserById(userId);
            if (user == null) {
                sendError(ctx, packet, "用户不存在");
                return;
            }

            // 检查房间是否存在
            GameRoom room = roomManager.getRoom(request.getRoomId());
            if (room == null) {
                sendError(ctx, packet, "房间不存在");
                return;
            }

            // 不能观战自己所在的房间
            if (userId.equals(room.getBlackPlayerId()) || userId.equals(room.getWhitePlayerId())) {
                sendError(ctx, packet, "不能观战自己正在进行的游戏");
                return;
            }

            // 加入观战
            boolean success = observerManager.joinObservation(
                    request.getRoomId(),
                    userId,
                    user.getUsername(),
                    user.getNickname(),
                    ctx.channel()
            );

            if (success) {
                // 发送游戏状态
                GobangProto.Packet gameStatePacket = GobangProto.Packet.newBuilder()
                        .setType(GobangProto.MessageType.GAME_STATE)
                        .setTimestamp(System.currentTimeMillis())
                        .setBody(room.buildGameStateProto().toByteString())
                        .build();

                ctx.channel().writeAndFlush(gameStatePacket);

                // 发送确认
                GobangProto.Packet response = GobangProto.Packet.newBuilder()
                        .setType(GobangProto.MessageType.OBSERVER_JOIN)
                        .setSequenceId(packet.getSequenceId())
                        .setTimestamp(System.currentTimeMillis())
                        .build();

                ctx.channel().writeAndFlush(response);

                // 广播观战者数量变化
                observerManager.broadcastObserverCount(request.getRoomId());

                logger.info("User {} joined observation of room {}", userId, request.getRoomId());
            } else {
                sendError(ctx, packet, "加入观战失败");
            }

        } catch (Exception e) {
            logger.error("Error handling observer join for user: {}", userId, e);
            sendError(ctx, packet, "加入观战处理失败");
        }
    }

    /**
     * 处理离开观战
     */
    private void handleObserverLeave(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            GobangProto.ObserverLeave request = GobangProto.ObserverLeave.parseFrom(packet.getBody());

            // 离开观战
            observerManager.leaveObservation(request.getRoomId(), userId);

            // 发送确认
            GobangProto.Packet response = GobangProto.Packet.newBuilder()
                    .setType(GobangProto.MessageType.OBSERVER_LEAVE)
                    .setSequenceId(packet.getSequenceId())
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            ctx.channel().writeAndFlush(response);

            // 广播观战者数量变化
            observerManager.broadcastObserverCount(request.getRoomId());

            logger.info("User {} left observation of room {}", userId, request.getRoomId());

        } catch (Exception e) {
            logger.error("Error handling observer leave for user: {}", userId, e);
            sendError(ctx, packet, "离开观战处理失败");
        }
    }

    /**
     * 处理获取观战者列表
     */
    private void handleObserverList(ChannelHandlerContext ctx, GobangProto.Packet packet, Long userId) {
        try {
            // 从请求中获取房间ID（需要扩展协议）
            // 这里暂时实现为获取用户所在房间的观战者列表

            String roomId = roomManager.getUserRoomId(userId);
            if (roomId == null) {
                sendError(ctx, packet, "您不在任何房间中");
                return;
            }

            List<GobangProto.ObserverInfo> observers = observerManager.getObserverList(roomId);

            GobangProto.ObserverList observerList = GobangProto.ObserverList.newBuilder()
                    .addAllObservers(observers)
                    .build();

            GobangProto.Packet response = GobangProto.Packet.newBuilder()
                    .setType(GobangProto.MessageType.OBSERVER_LIST)
                    .setSequenceId(packet.getSequenceId())
                    .setTimestamp(System.currentTimeMillis())
                    .setBody(observerList.toByteString())
                    .build();

            ctx.channel().writeAndFlush(response);

        } catch (Exception e) {
            logger.error("Error handling observer list for user: {}", userId, e);
            sendError(ctx, packet, "获取观战者列表失败");
        }
    }

    /**
     * 发送错误消息
     */
    private void sendError(ChannelHandlerContext ctx, GobangProto.Packet request, String message) {
        GobangProto.ChatReceive chatReceive = GobangProto.ChatReceive.newBuilder()
                .setSenderId("0")
                .setSenderName("系统")
                .setContent(message)
                .setTimestamp(System.currentTimeMillis())
                .setIsPrivate(false)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.CHAT_SYSTEM)
                .setSequenceId(request.getSequenceId())
                .setTimestamp(System.currentTimeMillis())
                .setBody(chatReceive.toByteString())
                .build();

        ctx.channel().writeAndFlush(packet);
    }

    @Override
    public MessageType getSupportedType() {
        return MessageType.OBSERVER_JOIN;
    }
}
