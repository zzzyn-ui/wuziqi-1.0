package com.gobang.core.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.protocol.protobuf.GobangProto;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 响应发送工具类
 * 支持JSON和Protobuf双格式
 */
public class ResponseUtil {

    private static final Logger logger = LoggerFactory.getLogger(ResponseUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String JSON_MODE_KEY = "jsonMode";

    /**
     * 发送响应（自动检测channel使用JSON还是Protobuf）
     */
    public static void sendResponse(Channel channel, GobangProto.Packet protobufPacket, JsonResponseBuilder jsonBuilder) {
        if (channel == null || !channel.isActive()) {
            return;
        }

        boolean isJsonMode = isJsonMode(channel);

        if (isJsonMode) {
            // 发送JSON格式（包含sequenceId）
            sendJsonResponse(channel, protobufPacket.getType().getNumber(),
                protobufPacket.getSequenceId(), jsonBuilder.build());
        } else {
            // 发送Protobuf格式
            channel.writeAndFlush(protobufPacket);
        }
    }

    /**
     * 检查channel是否使用JSON模式
     */
    public static boolean isJsonMode(Channel channel) {
        Boolean jsonMode = channel.attr(io.netty.util.AttributeKey.<Boolean>valueOf(JSON_MODE_KEY)).get();
        return jsonMode != null && jsonMode;
    }

    /**
     * 设置channel为JSON模式
     */
    public static void setJsonMode(Channel channel, boolean jsonMode) {
        channel.attr(io.netty.util.AttributeKey.<Boolean>valueOf(JSON_MODE_KEY)).set(jsonMode);
    }

    /**
     * 发送JSON响应（数据在body中）
     */
    public static void sendJsonResponse(Channel channel, int type, long sequenceId, Object body) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", type);
            response.put("sequence_id", sequenceId);  // 使用下划线命名
            response.put("timestamp", System.currentTimeMillis());
            if (body != null) {
                response.put("body", body);
            }

            String json = objectMapper.writeValueAsString(response);
            channel.writeAndFlush(new TextWebSocketFrame(json));
            logger.debug("Sent JSON response: type={}", type);
        } catch (Exception e) {
            logger.error("Failed to send JSON response", e);
        }
    }

    /**
     * 发送JSON响应（不带sequenceId，用于错误响应等）
     */
    public static void sendJsonResponse(Channel channel, int type, Object body) {
        sendJsonResponse(channel, type, 0, body);
    }

    /**
     * 发送扁平JSON响应（数据直接在消息中，不嵌套在body里）
     * 用于MATCH_SUCCESS, GAME_STATE等消息
     */
    public static void sendFlatJsonResponse(Channel channel, int type, long sequenceId, Map<String, Object> data) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", type);
            response.put("sequence_id", sequenceId);  // 使用下划线命名
            response.put("timestamp", System.currentTimeMillis());
            if (data != null) {
                response.putAll(data);
            }

            String json = objectMapper.writeValueAsString(response);
            channel.writeAndFlush(new TextWebSocketFrame(json));
            logger.debug("Sent flat JSON response: type={}", type);
        } catch (Exception e) {
            logger.error("Failed to send flat JSON response", e);
        }
    }

    /**
     * 发送响应（自动检测channel使用JSON还是Protobuf）
     * 支持扁平JSON格式（数据直接在消息中）
     */
    public static void sendFlatResponse(Channel channel, int type, long sequenceId, Map<String, Object> jsonData) {
        if (channel == null || !channel.isActive()) {
            return;
        }

        boolean isJsonMode = isJsonMode(channel);

        if (isJsonMode) {
            // 发送扁平JSON格式（数据不嵌套在body里）
            sendFlatJsonResponse(channel, type, sequenceId, jsonData);
        } else {
            // Protobuf模式暂不支持扁平格式
            logger.warn("Flat response not supported for protobuf mode");
        }
    }

    /**
     * JSON响应构建器接口
     */
    public interface JsonResponseBuilder {
        Object build();
    }

    /**
     * AuthResponse JSON构建器
     */
    public static class AuthResponseJsonBuilder implements JsonResponseBuilder {
        private final boolean success;
        private final String message;
        private final GobangProto.UserInfo userInfo;
        private final String token;

        public AuthResponseJsonBuilder(boolean success, String message, GobangProto.UserInfo userInfo) {
            this(success, message, userInfo, null);
        }

        public AuthResponseJsonBuilder(boolean success, String message, GobangProto.UserInfo userInfo, String token) {
            this.success = success;
            this.message = message;
            this.userInfo = userInfo;
            this.token = token;
        }

        @Override
        public Object build() {
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", message);
            if (token != null) {
                result.put("token", token);
            }
            if (userInfo != null) {
                Map<String, Object> info = new HashMap<>();
                info.put("user_id", userInfo.getUserId());  // 使用下划线命名
                info.put("username", userInfo.getUsername());
                info.put("nickname", userInfo.getNickname());
                info.put("rating", userInfo.getRating());
                result.put("user_info", info);  // 使用下划线命名
            }
            return result;
        }
    }

    /**
     * MatchSuccess JSON构建器
     * 格式匹配客户端期望：room_id, is_first, my_color, opponent
     */
    public static class MatchSuccessJsonBuilder implements JsonResponseBuilder {
        private final String roomId;
        private final GobangProto.UserInfo opponent;
        private final boolean isFirst;
        private final int myColor;

        public MatchSuccessJsonBuilder(String roomId, GobangProto.UserInfo opponent, boolean isFirst) {
            this(roomId, opponent, isFirst, isFirst ? 1 : 2);
        }

        public MatchSuccessJsonBuilder(String roomId, GobangProto.UserInfo opponent, boolean isFirst, int myColor) {
            this.roomId = roomId;
            this.opponent = opponent;
            this.isFirst = isFirst;
            this.myColor = myColor;
        }

        @Override
        public Object build() {
            Map<String, Object> result = new HashMap<>();
            result.put("room_id", roomId);
            result.put("is_first", isFirst);
            result.put("my_color", myColor);
            if (opponent != null) {
                Map<String, Object> info = new HashMap<>();
                info.put("user_id", String.valueOf(opponent.getUserId()));
                info.put("username", opponent.getUsername());
                info.put("nickname", opponent.getNickname());
                info.put("rating", opponent.getRating());
                result.put("opponent", info);
            }
            return result;
        }
    }

    /**
     * MatchFailed JSON构建器
     */
    public static class MatchFailedJsonBuilder implements JsonResponseBuilder {
        private final String reason;

        public MatchFailedJsonBuilder(String reason) {
            this.reason = reason;
        }

        @Override
        public Object build() {
            Map<String, Object> result = new HashMap<>();
            result.put("reason", reason);
            return result;
        }
    }

    /**
     * GameState JSON构建器
     * 格式匹配客户端期望：board, current_player 直接在消息中
     */
    public static class GameStateJsonBuilder implements JsonResponseBuilder {
        private final GobangProto.GameState state;
        private final String roomId;

        public GameStateJsonBuilder(GobangProto.GameState state) {
            this(state, null);
        }

        public GameStateJsonBuilder(GobangProto.GameState state, String roomId) {
            this.state = state;
            this.roomId = roomId;
        }

        @Override
        public Object build() {
            Map<String, Object> result = new HashMap<>();
            if (roomId != null) {
                result.put("room_id", roomId);
            }
            result.put("board", state.getBoardList());
            result.put("current_player", state.getCurrentPlayer());
            result.put("move_count", state.getMoveCount());
            result.put("game_state", state.getStatus().name());
            return result;
        }
    }

    /**
     * MoveResult JSON构建器
     */
    public static class MoveResultJsonBuilder implements JsonResponseBuilder {
        private final boolean success;
        private final String message;
        private final GobangProto.GameState state;

        public MoveResultJsonBuilder(boolean success, String message, GobangProto.GameState state) {
            this.success = success;
            this.message = message;
            this.state = state;
        }

        @Override
        public Object build() {
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", message);
            if (state != null) {
                result.put("state", new GameStateJsonBuilder(state).build());
            }
            return result;
        }
    }

    /**
     * GameOver JSON构建器
     */
    public static class GameOverJsonBuilder implements JsonResponseBuilder {
        private final GobangProto.GameOver gameOver;

        public GameOverJsonBuilder(GobangProto.GameOver gameOver) {
            this.gameOver = gameOver;
        }

        @Override
        public Object build() {
            Map<String, Object> result = new HashMap<>();
            result.put("reason", gameOver.getReasonValue());
            result.put("winnerId", gameOver.getWinnerId());
            result.put("ratingChange", gameOver.getRatingChange());
            return result;
        }
    }

    /**
     * ChatReceive JSON构建器
     */
    public static class ChatReceiveJsonBuilder implements JsonResponseBuilder {
        private final GobangProto.ChatReceive chat;

        public ChatReceiveJsonBuilder(GobangProto.ChatReceive chat) {
            this.chat = chat;
        }

        @Override
        public Object build() {
            Map<String, Object> result = new HashMap<>();
            result.put("senderId", chat.getSenderId());
            result.put("senderName", chat.getSenderName());
            result.put("content", chat.getContent());
            result.put("timestamp", chat.getTimestamp());
            result.put("isPrivate", chat.getIsPrivate());
            return result;
        }
    }
}
