package com.gobang.core.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.core.handler.AuthHandler;
import com.gobang.core.handler.GameHandler;
import com.gobang.core.handler.MatchHandler;
import com.gobang.core.handler.ChatHandler;
import com.gobang.service.AuthService;
import com.gobang.service.ChatService;
import com.gobang.service.GameService;
import com.gobang.service.RoomService;
import com.gobang.service.UserService;
import com.gobang.core.room.RoomManager;
import com.gobang.core.social.ChatManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON格式的WebSocket消息处理器
 * 支持JSON格式的消息，方便测试和前端对接
 */
public class JsonMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(JsonMessageHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AuthHandler authHandler;
    private final MatchHandler matchHandler;
    private final GameHandler gameHandler;
    private final ChatHandler chatHandler;
    private final GameService gameService;
    private final AuthService authService;
    private final UserService userService;

    private long sequenceId = 0;

    public JsonMessageHandler(AuthService authService, UserService userService,
                             GameService gameService, RoomService roomService, ChatService chatService,
                             RoomManager roomManager, ChatManager chatManager) {
        this.gameService = gameService;
        this.authService = authService;
        this.userService = userService;
        this.authHandler = new AuthHandler(authService, userService);
        this.matchHandler = new MatchHandler(gameService, userService, roomManager);
        this.gameHandler = new GameHandler(gameService, roomService, userService, roomManager);
        this.chatHandler = new ChatHandler(chatService, userService, roomManager);
    }

    /**
     * 处理JSON格式的消息
     */
    public void handleJsonMessage(ChannelHandlerContext ctx, String jsonText) {
        try {
            JsonNode root = objectMapper.readTree(jsonText);
            int type = root.path("type").asInt();

            logger.debug("收到JSON消息: type={}", type);

            // 转换JSON消息为Protobuf格式并路由到对应Handler
            switch (type) {
                case 1: // AUTH_LOGIN
                    handleLogin(ctx, root);
                    break;
                case 2: // AUTH_REGISTER
                    handleRegister(ctx, root);
                    break;
                case 10: // MATCH_START
                    handleMatchStart(ctx, root);
                    break;
                case 11: // MATCH_CANCEL
                    handleMatchCancel(ctx, root);
                    break;
                case 15: // BOT_MATCH_START - 人机对战
                    handleBotMatchStart(ctx, root);
                    break;
                case 20: // GAME_MOVE
                    handleGameMove(ctx, root);
                    break;
                case 24: // GAME_RESIGN
                    handleResign(ctx, root);
                    break;
                case 26: // GAME_UNDO_REQUEST
                    handleUndoRequest(ctx, root);
                    break;
                case 27: // GAME_UNDO_RESPONSE
                    handleUndoResponse(ctx, root);
                    break;
                case 40: // CHAT_SEND
                    handleChatSend(ctx, root);
                    break;
                case 100: // TOKEN_AUTH - WebSocket Token认证
                    handleTokenAuth(ctx, root);
                    break;
                default:
                    logger.warn("未知的消息类型: {}", type);
                    sendJsonError(ctx, type, "未知的消息类型");
            }
        } catch (Exception e) {
            logger.error("处理JSON消息失败", e);
            sendJsonError(ctx, 0, "消息处理失败: " + e.getMessage());
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, JsonNode root) {
        try {
            String username = root.path("username").asText();
            String password = root.path("password").asText();

            // 构造 protobuf LoginRequest
            com.gobang.protocol.protobuf.GobangProto.LoginRequest loginRequest =
                com.gobang.protocol.protobuf.GobangProto.LoginRequest.newBuilder()
                    .setUsername(username)
                    .setPassword(password)
                    .build();

            com.gobang.protocol.protobuf.GobangProto.Packet packet =
                com.gobang.protocol.protobuf.GobangProto.Packet.newBuilder()
                    .setType(com.gobang.protocol.protobuf.GobangProto.MessageType.AUTH_LOGIN)
                    .setSequenceId(++sequenceId)
                    .setTimestamp(System.currentTimeMillis())
                    .setBody(loginRequest.toByteString())
                    .build();

            authHandler.handle(ctx, packet);
        } catch (Exception e) {
            logger.error("处理登录请求失败", e);
            sendJsonResponse(ctx, 3, Map.of("success", false, "message", "登录失败: " + e.getMessage()));
        }
    }

    private void handleRegister(ChannelHandlerContext ctx, JsonNode root) {
        try {
            String username = root.path("username").asText();
            String password = root.path("password").asText();
            String nickname = root.path("nickname").asText();

            com.gobang.protocol.protobuf.GobangProto.RegisterRequest registerRequest =
                com.gobang.protocol.protobuf.GobangProto.RegisterRequest.newBuilder()
                    .setUsername(username)
                    .setPassword(password)
                    .setNickname(nickname.isEmpty() ? username : nickname)
                    .build();

            com.gobang.protocol.protobuf.GobangProto.Packet packet =
                com.gobang.protocol.protobuf.GobangProto.Packet.newBuilder()
                    .setType(com.gobang.protocol.protobuf.GobangProto.MessageType.AUTH_REGISTER)
                    .setSequenceId(++sequenceId)
                    .setTimestamp(System.currentTimeMillis())
                    .setBody(registerRequest.toByteString())
                    .build();

            authHandler.handle(ctx, packet);
        } catch (Exception e) {
            logger.error("处理注册请求失败", e);
            sendJsonResponse(ctx, 3, Map.of("success", false, "message", "注册失败: " + e.getMessage()));
        }
    }

    private void handleMatchStart(ChannelHandlerContext ctx, JsonNode root) {
        try {
            logger.info("=== JSON处理匹配请求 - 原始消息 ===");
            logger.info("完整JSON: {}", root.toString());

            int rating = root.path("rating").asInt(1200);
            // 从body中获取mode，如果没有body则直接从root获取
            String mode = null;

            // 首先尝试从body对象中获取
            if (root.has("body") && root.path("body").isObject()) {
                JsonNode body = root.path("body");
                logger.info("Body内容: {}", body.toString());
                mode = body.path("mode").asText(null);
                logger.info("从body获取的mode: '{}'", mode);
            }

            // 如果body中没有，直接从root获取（兼容性）
            if (mode == null || mode.isEmpty()) {
                mode = root.path("mode").asText(null);
                logger.info("从root获取的mode: '{}'", mode);
            }

            // 如果还是没有，使用默认值
            if (mode == null || mode.isEmpty()) {
                mode = "casual";
                logger.warn("⚠️ 未找到mode参数，使用默认值: '{}'", mode);
                logger.warn("⚠️ 这可能导致所有玩家都被分配到休闲模式！");
            }

            logger.info("✓ 最终使用的参数: rating={}, mode='{}'", rating, mode);

            com.gobang.protocol.protobuf.GobangProto.MatchRequest.Builder matchRequestBuilder =
                com.gobang.protocol.protobuf.GobangProto.MatchRequest.newBuilder()
                    .setRating(rating)
                    .setTimeout(300);

            // 设置mode字段
            if (mode != null && !mode.isEmpty()) {
                matchRequestBuilder.setMode(mode);
                logger.info("已设置mode到protobuf: '{}'", mode);
            }

            com.gobang.protocol.protobuf.GobangProto.MatchRequest matchRequest = matchRequestBuilder.build();
            logger.info("构建的MatchRequest: rating={}, mode='{}'", matchRequest.getRating(), matchRequest.getMode());

            com.gobang.protocol.protobuf.GobangProto.Packet packet =
                com.gobang.protocol.protobuf.GobangProto.Packet.newBuilder()
                    .setType(com.gobang.protocol.protobuf.GobangProto.MessageType.MATCH_START)
                    .setSequenceId(++sequenceId)
                    .setTimestamp(System.currentTimeMillis())
                    .setBody(matchRequest.toByteString())
                    .build();

            matchHandler.handle(ctx, packet);
        } catch (Exception e) {
            logger.error("处理匹配请求失败", e);
            sendJsonResponse(ctx, 13, Map.of("reason", "匹配失败: " + e.getMessage()));
        }
    }

    private void handleMatchCancel(ChannelHandlerContext ctx, JsonNode root) {
        try {
            com.gobang.protocol.protobuf.GobangProto.Packet packet =
                com.gobang.protocol.protobuf.GobangProto.Packet.newBuilder()
                    .setType(com.gobang.protocol.protobuf.GobangProto.MessageType.MATCH_CANCEL)
                    .setSequenceId(++sequenceId)
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            matchHandler.handle(ctx, packet);
        } catch (Exception e) {
            logger.error("处理取消匹配失败", e);
        }
    }

    /**
     * 处理人机对战请求
     * JSON消息格式: {"type": 15, "rating": 1200}
     */
    private void handleBotMatchStart(ChannelHandlerContext ctx, JsonNode root) {
        try {
            Long userId = AuthHandler.getUserId(ctx.channel());

            // 游客用户处理
            if (userId == null) {
                io.netty.util.AttributeKey<Long> TEMP_USER_ID = io.netty.util.AttributeKey.valueOf("tempUserId");
                userId = ctx.channel().attr(TEMP_USER_ID).get();
                if (userId == null) {
                    userId = -(long) (Math.random() * 1000000 + 1000000);
                    ctx.channel().attr(TEMP_USER_ID).set(userId);
                }
            }

            int rating = root.path("rating").asInt(1200);
            String username = userId < 0 ? "guest_" + Math.abs(userId) : "user_" + userId;
            String nickname = userId < 0 ? "游客" + Math.abs(userId) % 1000 : "玩家" + userId;

            // 直接调用人机对战方法
            boolean success = gameService.startBotMatch(
                userId,
                username,
                nickname,
                rating,
                ctx.channel()
            );

            if (!success) {
                sendJsonResponse(ctx, 13, Map.of("reason", "启动人机对战失败"));
            }

        } catch (Exception e) {
            logger.error("处理人机对战请求失败", e);
            sendJsonResponse(ctx, 13, Map.of("reason", "人机对战失败: " + e.getMessage()));
        }
    }

    private void handleGameMove(ChannelHandlerContext ctx, JsonNode root) {
        try {
            // 从 body 中读取坐标
            JsonNode body = root.path("body");
            int x = body.path("x").asInt();
            int y = body.path("y").asInt();

            com.gobang.protocol.protobuf.GobangProto.MoveRequest moveRequest =
                com.gobang.protocol.protobuf.GobangProto.MoveRequest.newBuilder()
                    .setX(x)
                    .setY(y)
                    .build();

            com.gobang.protocol.protobuf.GobangProto.Packet packet =
                com.gobang.protocol.protobuf.GobangProto.Packet.newBuilder()
                    .setType(com.gobang.protocol.protobuf.GobangProto.MessageType.GAME_MOVE)
                    .setSequenceId(++sequenceId)
                    .setTimestamp(System.currentTimeMillis())
                    .setBody(moveRequest.toByteString())
                    .build();

            gameHandler.handle(ctx, packet);
        } catch (Exception e) {
            logger.error("处理落子请求失败", e);
        }
    }

    private void handleResign(ChannelHandlerContext ctx, JsonNode root) {
        try {
            com.gobang.protocol.protobuf.GobangProto.Packet packet =
                com.gobang.protocol.protobuf.GobangProto.Packet.newBuilder()
                    .setType(com.gobang.protocol.protobuf.GobangProto.MessageType.GAME_RESIGN)
                    .setSequenceId(++sequenceId)
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            gameHandler.handle(ctx, packet);
        } catch (Exception e) {
            logger.error("处理认输请求失败", e);
        }
    }

    private void handleUndoRequest(ChannelHandlerContext ctx, JsonNode root) {
        try {
            com.gobang.protocol.protobuf.GobangProto.Packet packet =
                com.gobang.protocol.protobuf.GobangProto.Packet.newBuilder()
                    .setType(com.gobang.protocol.protobuf.GobangProto.MessageType.GAME_UNDO_REQUEST)
                    .setSequenceId(++sequenceId)
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            gameHandler.handle(ctx, packet);
        } catch (Exception e) {
            logger.error("处理悔棋请求失败", e);
        }
    }

    private void handleUndoResponse(ChannelHandlerContext ctx, JsonNode root) {
        try {
            boolean accepted = root.path("accepted").asBoolean();

            com.gobang.protocol.protobuf.GobangProto.UndoResponse undoResponse =
                com.gobang.protocol.protobuf.GobangProto.UndoResponse.newBuilder()
                    .setAccepted(accepted)
                    .build();

            com.gobang.protocol.protobuf.GobangProto.Packet packet =
                com.gobang.protocol.protobuf.GobangProto.Packet.newBuilder()
                    .setType(com.gobang.protocol.protobuf.GobangProto.MessageType.GAME_UNDO_RESPONSE)
                    .setSequenceId(++sequenceId)
                    .setTimestamp(System.currentTimeMillis())
                    .setBody(undoResponse.toByteString())
                    .build();

            gameHandler.handle(ctx, packet);
        } catch (Exception e) {
            logger.error("处理悔棋响应失败", e);
        }
    }

    private void handleChatSend(ChannelHandlerContext ctx, JsonNode root) {
        try {
            String content = root.path("content").asText();

            com.gobang.protocol.protobuf.GobangProto.ChatSend chatSend =
                com.gobang.protocol.protobuf.GobangProto.ChatSend.newBuilder()
                    .setContent(content)
                    .build();

            com.gobang.protocol.protobuf.GobangProto.Packet packet =
                com.gobang.protocol.protobuf.GobangProto.Packet.newBuilder()
                    .setType(com.gobang.protocol.protobuf.GobangProto.MessageType.CHAT_SEND)
                    .setSequenceId(++sequenceId)
                    .setTimestamp(System.currentTimeMillis())
                    .setBody(chatSend.toByteString())
                    .build();

            chatHandler.handle(ctx, packet);
        } catch (Exception e) {
            logger.error("处理聊天消息失败", e);
        }
    }

    /**
     * 处理Token认证请求
     * JSON消息格式: {"type": 100, "body": {"token": "jwt_token"}}
     */
    private void handleTokenAuth(ChannelHandlerContext ctx, JsonNode root) {
        try {
            // 从body中获取token，如果没有body则直接从root获取
            String token = null;
            if (root.has("body") && root.path("body").isObject()) {
                token = root.path("body").path("token").asText(null);
            }
            if (token == null || token.isEmpty()) {
                token = root.path("token").asText(null);
            }

            if (token == null || token.isEmpty()) {
                logger.warn("Token认证失败: token为空");
                sendJsonResponse(ctx, 100, Map.of(
                    "success", false,
                    "message", "Token不能为空"
                ));
                return;
            }

            logger.info("收到Token认证请求");

            // 验证token
            Long userId = authService.validateToken(token);

            if (userId == null) {
                logger.warn("Token验证失败");
                sendJsonResponse(ctx, 100, Map.of(
                    "success", false,
                    "message", "Token无效或已过期"
                ));
                return;
            }

            // 获取用户信息
            com.gobang.model.entity.User user = userService.getUserById(userId);
            if (user == null) {
                logger.warn("用户不存在: {}", userId);
                sendJsonResponse(ctx, 100, Map.of(
                    "success", false,
                    "message", "用户不存在"
                ));
                return;
            }

            // 设置channel属性
            io.netty.util.AttributeKey<Long> USER_ID_ATTR = io.netty.util.AttributeKey.valueOf("userId");
            io.netty.util.AttributeKey<Boolean> AUTHENTICATED_ATTR = io.netty.util.AttributeKey.valueOf("authenticated");
            ctx.channel().attr(USER_ID_ATTR).set(userId);
            ctx.channel().attr(AUTHENTICATED_ATTR).set(true);

            logger.info("Token认证成功: userId={}, username={}", userId, user.getUsername());

            // 返回认证成功响应
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", String.valueOf(user.getId()));
            userInfo.put("username", user.getUsername());
            userInfo.put("nickname", user.getNickname());
            userInfo.put("rating", user.getRating());
            userInfo.put("level", user.getLevel());
            userInfo.put("exp", user.getExp());
            userInfo.put("avatar", user.getAvatar());

            sendJsonResponse(ctx, 100, Map.of(
                "success", true,
                "message", "认证成功",
                "userId", userId,
                "userInfo", userInfo
            ));

        } catch (Exception e) {
            logger.error("处理Token认证失败", e);
            sendJsonResponse(ctx, 100, Map.of(
                "success", false,
                "message", "认证处理失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 发送JSON响应
     */
    public static void sendJsonResponse(ChannelHandlerContext ctx, int type, Object body) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", type);
            response.put("timestamp", System.currentTimeMillis());
            if (body != null) {
                response.put("body", body);
            }

            String json = objectMapper.writeValueAsString(response);
            ctx.writeAndFlush(new TextWebSocketFrame(json));
            logger.debug("发送JSON响应: type={}", type);
        } catch (Exception e) {
            logger.error("发送JSON响应失败", e);
        }
    }

    /**
     * 发送JSON错误响应
     */
    public static void sendJsonError(ChannelHandlerContext ctx, int type, String message) {
        sendJsonResponse(ctx, type, Map.of("success", false, "message", message));
    }
}
