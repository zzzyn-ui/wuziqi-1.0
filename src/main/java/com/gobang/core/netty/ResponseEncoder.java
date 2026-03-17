package com.gobang.core.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.protocol.protobuf.GobangProto;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 响应编码器
 * 自动将Protobuf响应转换为JSON（如果channel是JSON模式）
 */
public class ResponseEncoder extends ChannelOutboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ResponseEncoder.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String JSON_MODE_KEY = "jsonMode";

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // 检查是否需要转换
        if (msg instanceof GobangProto.Packet && isJsonMode(ctx.channel())) {
            GobangProto.Packet packet = (GobangProto.Packet) msg;
            String json = convertPacketToJson(packet);
            if (json != null) {
                // 发送JSON格式的响应
                ctx.write(new TextWebSocketFrame(json), promise);
                return;
            }
        }

        // 其他情况直接发送
        super.write(ctx, msg, promise);
    }

    /**
     * 检查channel是否使用JSON模式
     */
    private boolean isJsonMode(io.netty.channel.Channel channel) {
        Boolean jsonMode = channel.attr(io.netty.util.AttributeKey.<Boolean>valueOf(JSON_MODE_KEY)).get();
        return jsonMode != null && jsonMode;
    }

    /**
     * 将Protobuf Packet转换为JSON字符串
     */
    private String convertPacketToJson(GobangProto.Packet packet) {
        try {
            int type = packet.getType().getNumber();
            Map<String, Object> response = new HashMap<>();
            response.put("type", type);
            response.put("sequence_id", packet.getSequenceId());  // 使用下划线命名
            response.put("timestamp", packet.getTimestamp());

            // 解析body并转换为JSON对象
            if (packet.getBody() != null && packet.getBody().size() > 0) {
                Object bodyObj = convertBodyToJson(type, packet.getBody());
                if (bodyObj != null) {
                    response.put("body", bodyObj);
                }
            }

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("Failed to convert packet to JSON", e);
            return null;
        }
    }

    /**
     * 将protobuf body转换为JSON对象
     */
    private Object convertBodyToJson(int messageType, com.google.protobuf.ByteString body) {
        try {
            byte[] bodyBytes = body.toByteArray();

            switch (messageType) {
                case 3: // AUTH_RESPONSE
                    GobangProto.AuthResponse authResponse = GobangProto.AuthResponse.parseFrom(bodyBytes);
                    Map<String, Object> authResult = new HashMap<>();
                    authResult.put("success", authResponse.getSuccess());
                    authResult.put("message", authResponse.getMessage());
                    String token = authResponse.getToken();
                    if (token != null && !token.isEmpty()) {
                        authResult.put("token", token);
                    }
                    if (authResponse.hasUserInfo()) {
                        Map<String, Object> userInfo = new HashMap<>();
                        userInfo.put("userId", authResponse.getUserInfo().getUserId());
                        userInfo.put("username", authResponse.getUserInfo().getUsername());
                        userInfo.put("nickname", authResponse.getUserInfo().getNickname());
                        userInfo.put("rating", authResponse.getUserInfo().getRating());
                        authResult.put("userInfo", userInfo);
                    }
                    return authResult;

                case 12: // MATCH_SUCCESS
                    GobangProto.MatchSuccess matchSuccess = GobangProto.MatchSuccess.parseFrom(bodyBytes);
                    Map<String, Object> matchResult = new HashMap<>();
                    matchResult.put("roomId", matchSuccess.getRoomId());
                    matchResult.put("isFirst", matchSuccess.getIsFirst());
                    if (matchSuccess.hasOpponent()) {
                        Map<String, Object> opponent = new HashMap<>();
                        opponent.put("userId", matchSuccess.getOpponent().getUserId());
                        opponent.put("username", matchSuccess.getOpponent().getUsername());
                        opponent.put("nickname", matchSuccess.getOpponent().getNickname());
                        opponent.put("rating", matchSuccess.getOpponent().getRating());
                        matchResult.put("opponent", opponent);
                    }
                    return matchResult;

                case 13: // MATCH_FAILED
                    GobangProto.MatchFailed matchFailed = GobangProto.MatchFailed.parseFrom(bodyBytes);
                    Map<String, Object> failedResult = new HashMap<>();
                    failedResult.put("reason", matchFailed.getReason());
                    return failedResult;

                case 22: // GAME_STATE
                    GobangProto.GameState gameState = GobangProto.GameState.parseFrom(bodyBytes);
                    Map<String, Object> stateResult = new HashMap<>();
                    stateResult.put("status", gameState.getStatusValue());
                    stateResult.put("currentPlayer", gameState.getCurrentPlayer());
                    stateResult.put("board", gameState.getBoardList());
                    stateResult.put("moveCount", gameState.getMoveCount());
                    return stateResult;

                case 21: // GAME_MOVE_RESULT
                    GobangProto.MoveResult moveResult = GobangProto.MoveResult.parseFrom(bodyBytes);
                    Map<String, Object> moveResultObj = new HashMap<>();
                    moveResultObj.put("success", moveResult.getSuccess());
                    moveResultObj.put("message", moveResult.getMessage());
                    if (moveResult.hasState()) {
                        moveResultObj.put("state", convertBodyToJson(22, moveResult.getState().toByteString()));
                    }
                    return moveResultObj;

                case 23: // GAME_OVER
                    GobangProto.GameOver gameOver = GobangProto.GameOver.parseFrom(bodyBytes);
                    Map<String, Object> gameOverResult = new HashMap<>();
                    gameOverResult.put("reason", gameOver.getReasonValue());
                    gameOverResult.put("winnerId", gameOver.getWinnerId());
                    gameOverResult.put("ratingChange", gameOver.getRatingChange());
                    return gameOverResult;

                case 41: // CHAT_RECEIVE
                case 42: // CHAT_SYSTEM
                    GobangProto.ChatReceive chatReceive = GobangProto.ChatReceive.parseFrom(bodyBytes);
                    Map<String, Object> chatResult = new HashMap<>();
                    chatResult.put("senderId", chatReceive.getSenderId());
                    chatResult.put("senderName", chatReceive.getSenderName());
                    chatResult.put("content", chatReceive.getContent());
                    chatResult.put("timestamp", chatReceive.getTimestamp());
                    chatResult.put("isPrivate", chatReceive.getIsPrivate());
                    return chatResult;

                default:
                    logger.debug("Unhandled message type for JSON conversion: {}", messageType);
                    return null;
            }
        } catch (Exception e) {
            logger.error("Failed to convert body to JSON, type: {}", messageType, e);
            return null;
        }
    }
}
