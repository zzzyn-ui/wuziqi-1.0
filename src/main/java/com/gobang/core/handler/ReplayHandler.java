package com.gobang.core.handler;

import com.gobang.core.handler.AuthHandler;
import com.gobang.core.protocol.MessageHandler;
import com.gobang.core.protocol.MessageType;
import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.service.RecordService;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 回放消息处理器
 * 处理对局回放请求
 */
public class ReplayHandler implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(ReplayHandler.class);

    private final RecordService recordService;

    public ReplayHandler(RecordService recordService) {
        this.recordService = recordService;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, GobangProto.Packet packet) {
        // 验证认证
        if (!AuthHandler.isAuthenticated(ctx.channel())) {
            sendError(ctx, packet, "请先登录");
            return;
        }

        MessageType messageType = MessageType.fromValue(packet.getType().getNumber());

        switch (messageType) {
            case GAME_REPLAY_REQUEST:
                handleReplayRequest(ctx, packet);
                break;
            case GAME_HISTORY_REQUEST:
                handleHistoryRequest(ctx, packet);
                break;
            default:
                logger.warn("Unsupported message type for ReplayHandler: {}", messageType);
                sendError(ctx, packet, "不支持的消息类型");
        }
    }

    /**
     * 处理历史记录请求
     */
    private void handleHistoryRequest(ChannelHandlerContext ctx, GobangProto.Packet packet) {
        try {
            Long userId = AuthHandler.getUserId(ctx.channel());
            if (userId == null) {
                sendError(ctx, packet, "未认证的用户");
                return;
            }

            logger.info("History requested for user: {}", userId);

            // TODO: 实现获取历史记录的逻辑
            // 这里需要RecordService提供获取用户历史记录的方法

            sendError(ctx, packet, "历史记录功能正在开发中");

        } catch (Exception e) {
            logger.error("Error handling history request", e);
            sendError(ctx, packet, "获取历史记录失败");
        }
    }

    /**
     * 处理回放请求
     */
    private void handleReplayRequest(ChannelHandlerContext ctx, GobangProto.Packet packet) {
        try {
            GobangProto.ReplayRequest request = GobangProto.ReplayRequest.parseFrom(packet.getBody());
            String roomId = request.getRoomId();

            logger.info("Replay requested for room: {}", roomId);

            // 获取回放数据
            GobangProto.Packet replayPacket = recordService.getReplayPacket(roomId);

            if (replayPacket != null) {
                ctx.channel().writeAndFlush(replayPacket);
            } else {
                sendError(ctx, packet, "对局记录不存在");
            }

        } catch (Exception e) {
            logger.error("Error handling replay request", e);
            sendError(ctx, packet, "获取回放数据失败");
        }
    }

    /**
     * 发送错误消息
     */
    private void sendError(ChannelHandlerContext ctx, GobangProto.Packet request, String message) {
        GobangProto.ChatReceive error = GobangProto.ChatReceive.newBuilder()
                .setSenderId("0")
                .setSenderName("系统")
                .setContent(message)
                .setTimestamp(System.currentTimeMillis())
                .setIsPrivate(false)
                .build();

        GobangProto.Packet packet = GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.CHAT_RECEIVE)
                .setSequenceId(request.getSequenceId())
                .setTimestamp(System.currentTimeMillis())
                .setBody(error.toByteString())
                .build();

        ctx.channel().writeAndFlush(packet);
    }

    @Override
    public MessageType getSupportedType() {
        return MessageType.GAME_REPLAY_REQUEST;
    }
}
