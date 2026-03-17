package com.gobang.core.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.core.handler.AuthHandler;
import com.gobang.core.handler.RoomHandler;
import com.gobang.core.protocol.MessageHandler;
import com.gobang.core.protocol.MessageType;
import com.gobang.core.protocol.PacketCodec;
import com.gobang.core.room.GameRoom;
import com.gobang.core.room.RoomManager;
import com.gobang.core.social.FriendManager;
import com.gobang.model.entity.User;
import com.gobang.protocol.protobuf.GobangProto;
import com.gobang.service.AuthService;
import com.gobang.service.FriendService;
import com.gobang.service.UserService;
import com.gobang.util.JwtUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebSocket消息处理器
 * 处理所有WebSocket消息并路由到对应的业务处理器
 */
@ChannelHandler.Sharable
public class WebSocketHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String JSON_MODE_KEY = "jsonMode";

    private final Map<MessageType, MessageHandler> handlers = new HashMap<>();
    private final ChannelManager channelManager;
    private final UserService userService;
    private final AuthService authService;
    private final RoomManager roomManager;
    private final FriendService friendService;
    private final JwtUtil jwtUtil;
    private final FriendManager friendManager;
    private HttpRequestHandler httpRequestHandler;
    private RoomHandler roomHandler;
    private JsonMessageHandler jsonMessageHandler;

    public WebSocketHandler(ChannelManager channelManager, UserService userService, AuthService authService, RoomManager roomManager) {
        this.channelManager = channelManager;
        this.userService = userService;
        this.authService = authService;
        this.roomManager = roomManager;
        this.friendService = null;
        this.jwtUtil = null;
        this.friendManager = null;
        this.httpRequestHandler = new HttpRequestHandler("static", "/ws", roomManager, null, null, null);
        this.roomHandler = new RoomHandler(roomManager, userService);
    }

    public WebSocketHandler(ChannelManager channelManager, UserService userService, AuthService authService, RoomManager roomManager, FriendService friendService, JwtUtil jwtUtil) {
        this(channelManager, userService, authService, roomManager, friendService, jwtUtil, null);
    }

    public WebSocketHandler(ChannelManager channelManager, UserService userService, AuthService authService, RoomManager roomManager, FriendService friendService, JwtUtil jwtUtil, FriendManager friendManager) {
        this.channelManager = channelManager;
        this.userService = userService;
        this.authService = authService;
        this.roomManager = roomManager;
        this.friendService = friendService;
        this.jwtUtil = jwtUtil;
        this.friendManager = friendManager;
        this.httpRequestHandler = new HttpRequestHandler("static", "/ws", roomManager, friendService, userService, jwtUtil);
        this.roomHandler = new RoomHandler(roomManager, userService);
    }

    public void setRoomHandler(RoomHandler roomHandler) {
        this.roomHandler = roomHandler;
    }

    public void setJsonMessageHandler(JsonMessageHandler jsonMessageHandler) {
        this.jsonMessageHandler = jsonMessageHandler;
    }

    public void setHttpRequestHandler(HttpRequestHandler handler) {
        this.httpRequestHandler = handler;
    }

    /**
     * 更新HttpRequestHandler的依赖（用于注入friendService等）
     */
    public void updateHttpRequestHandlerDependencies(FriendService friendService, UserService userService, JwtUtil jwtUtil) {
        // 只更新httpRequestHandler，不修改final字段
        this.httpRequestHandler = new HttpRequestHandler("static", "/ws", roomManager, friendService, userService, jwtUtil);
    }

    /**
     * 发送JSON响应
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
     * 处理HTTP API请求
     */
    private boolean handleApiRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        logger.debug("Handling API request: {}", uri);

        try {
            if (uri.startsWith("/api/health") || uri.equals("/api/health")) {
                return handleHealthApi(ctx);
            }
            if (uri.startsWith("/api/leaderboard")) {
                return handleLeaderboardApi(ctx, request);
            }
            return false;
        } catch (Exception e) {
            logger.error("Error handling API request", e);
            sendJsonError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "服务器错误");
            return true;
        }
    }

    /**
     * 处理健康检查API请求
     */
    private boolean handleHealthApi(ChannelHandlerContext ctx) {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "ok");
            health.put("server", "gobang-server");
            health.put("timestamp", System.currentTimeMillis());
            health.put("websocket", "ws://" + ctx.channel().localAddress().toString() + "/ws");

            // 检查数据库连接
            try {
                int onlineCount = userService.getOnlineUserCount();
                health.put("database", "connected");
                health.put("online_users", onlineCount);
            } catch (Exception e) {
                health.put("database", "disconnected");
                health.put("database_error", e.getMessage());
            }

            String json = objectMapper.writeValueAsString(health);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

            DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(jsonBytes)
            );
            httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
            httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, jsonBytes.length);
            httpResponse.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

            ctx.writeAndFlush(httpResponse);
            logger.debug("Sent health check response");
            return true;

        } catch (Exception e) {
            logger.error("Error handling health check", e);
            return false;
        }
    }

    /**
     * 处理排行榜API请求
     */
    private boolean handleLeaderboardApi(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            String limitStr = decoder.parameters().getOrDefault("limit", List.of("50")).get(0);
            int limit = Integer.parseInt(limitStr);

            // 从数据库获取排行榜数据
            List<User> leaderboard = userService.getLeaderboard(limit);

            // 构建响应数据
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", buildLeaderboardData(leaderboard));

            String json = objectMapper.writeValueAsString(response);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

            DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(jsonBytes)
            );
            httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
            httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, jsonBytes.length);
            httpResponse.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

            ctx.writeAndFlush(httpResponse);
            logger.info("Sent leaderboard data: {} players", leaderboard.size());
            return true;

        } catch (Exception e) {
            logger.error("Error handling leaderboard API", e);
            sendJsonError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "获取排行榜失败");
            return true;
        }
    }

    /**
     * 构建排行榜数据
     */
    private List<Map<String, Object>> buildLeaderboardData(List<User> users) {
        return users.stream().map(user -> {
            Map<String, Object> data = new HashMap<>();
            data.put("userId", user.getId());
            data.put("nickname", user.getNickname());
            data.put("rating", user.getRating());
            data.put("level", user.getLevel());
            data.put("exp", user.getExp());
            data.put("status", user.getStatus());
            data.put("online", user.getStatus() > 0);
            return data;
        }).toList();
    }

    /**
     * 发送JSON错误响应
     */
    private void sendJsonError(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", message);

            String json = objectMapper.writeValueAsString(error);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(jsonBytes)
            );
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

            ctx.writeAndFlush(response);
        } catch (Exception e) {
            logger.error("Failed to send error response", e);
        }
    }

    /**
     * 注册消息处理器
     */
    public void registerHandler(MessageHandler handler) {
        handlers.put(handler.getSupportedType(), handler);
        logger.debug("Registered handler for: {}", handler.getSupportedType());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        logger.debug("WebSocketHandler channelRead: {} - {}", msg.getClass().getSimpleName(), msg);

        // 处理HTTP请求（静态文件和API）
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            String uri = request.uri();

            logger.info("HTTP Request received: {} {} on channel {}", request.method(), uri, ctx.channel().id().asShortText());

            // 检查是否是 API 请求
            if (uri.startsWith("/api/")) {
                boolean handled = handleApiRequest(ctx, request);
                if (handled) {
                    // request已释放
                } else {
                    ReferenceCountUtil.retain(request);
                    ctx.fireChannelRead(request);
                }
                return;
            }

            boolean handled = httpRequestHandler.handle(ctx, request);

            if (!handled) {
                // 如果不是静态文件请求，继续处理（WebSocket升级）
                // 传递给下一个处理器
                ReferenceCountUtil.retain(request);
                ctx.fireChannelRead(request);
            }
            // 如果已处理，httpRequestHandler内部已经释放了请求
            return;
        }

        // 处理WebSocket帧
        if (msg instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) msg;

            // 处理Ping帧
            if (frame instanceof PingWebSocketFrame) {
                ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
                return;
            }

            // 处理关闭帧
            if (frame instanceof CloseWebSocketFrame) {
                ctx.close();
                return;
            }

            // 处理文本帧（JSON格式，用于测试）
            if (frame instanceof TextWebSocketFrame) {
                handleJsonFrame(ctx, (TextWebSocketFrame) frame);
                return;
            }

            // 处理二进制帧
            try {
                byte[] data = new byte[frame.content().readableBytes()];
                frame.content().readBytes(data);

                GobangProto.Packet packet = GobangProto.Packet.parseFrom(data);
                MessageType messageType = MessageType.fromValue(packet.getType().getNumber());

                logger.debug("Received message: type={}, sequenceId={}", messageType, packet.getSequenceId());

                // 路由到对应的处理器
                MessageHandler handler = handlers.get(messageType);
                if (handler != null) {
                    handler.handle(ctx, packet);
                } else {
                    logger.warn("No handler found for message type: {}", messageType);
                }

            } catch (Exception e) {
                logger.error("Failed to process message", e);
            }
            return;
        }

        // 其他类型继续传递
        ReferenceCountUtil.retain(msg);
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("=== Channel connected: {} ===", ctx.channel().id().asShortText());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long userId = channelManager.getUserId(ctx.channel());
        logger.info("=== Channel disconnected: {}, userId: {} ===", ctx.channel().id().asShortText(), userId);

        // 清理连接
        channelManager.removeChannel(ctx.channel());

        // 更新用户状态为离线
        if (userId != null && userId > 0) {
            try {
                userService.updateUserStatus(userId, 0);
                logger.info("Updated user {} status to offline", userId);
            } catch (Exception e) {
                logger.error("Failed to update user status for userId: {}", userId, e);
            }

            // 通知好友管理器用户离线
            if (friendManager != null) {
                friendManager.userOffline(userId);
                logger.info("Notified FriendManager that user {} is offline", userId);
            }
        }

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Channel exception: {}", ctx.channel().id().asShortText(), cause);
        cause.printStackTrace(System.err);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        logger.info("=== userEventTriggered: {} ===", evt.getClass().getSimpleName());

        // 处理WebSocket握手完成事件
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete handshake = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            logger.info("=== WebSocket handshake completed: {} ===", ctx.channel().id().asShortText());

            // 从握手请求中获取URI并提取token
            String uri = handshake.requestUri();
            logger.info("Handshake request URI: {}", uri);

            // 打印所有请求头
            logger.debug("Handshake headers: {}", handshake.requestHeaders());

            // 首先尝试从channel属性中获取token（由HttpRequestHandler提取）
            io.netty.util.AttributeKey<String> TOKEN_ATTR = io.netty.util.AttributeKey.valueOf("authToken");
            String token = ctx.channel().attr(TOKEN_ATTR).get();

            logger.debug("Token from channel attributes: {}", token != null ? "exists (length=" + token.length() + ")" : "null");

            if (token != null) {
                logger.info("Token found in channel attributes, length: {}", token.length());
            } else if (uri != null && uri.contains("token=")) {
                // 如果channel属性中没有，从URI中提取
                try {
                    io.netty.handler.codec.http.QueryStringDecoder decoder = new io.netty.handler.codec.http.QueryStringDecoder(uri);
                    java.util.List<String> tokens = decoder.parameters().get("token");
                    token = (tokens != null && !tokens.isEmpty()) ? tokens.get(0) : null;
                    logger.info("Token extracted from handshake URI, length: {}", token != null ? token.length() : 0);
                } catch (Exception e) {
                    logger.error("Failed to parse token from URI", e);
                }
            } else {
                logger.warn("No token found in channel attributes or URI, will be treated as guest");
            }

            if (token != null) {
                Long userId = authService.validateToken(token);
                logger.info("Token validation result: userId={}", userId);

                if (userId != null) {
                    // 设置用户ID到channel属性
                    io.netty.util.AttributeKey<Long> USER_ID_ATTR = io.netty.util.AttributeKey.valueOf("userId");
                    ctx.channel().attr(USER_ID_ATTR).set(userId);
                    io.netty.util.AttributeKey<Boolean> AUTHENTICATED_ATTR = io.netty.util.AttributeKey.valueOf("authenticated");
                    ctx.channel().attr(AUTHENTICATED_ATTR).set(true);
                    logger.info("User authenticated via token: {}", userId);

                    // 获取用户信息并设置到channel
                    User user = userService.getUserById(userId);
                    if (user != null) {
                        io.netty.util.AttributeKey<User> USER_ATTR = io.netty.util.AttributeKey.valueOf("user");
                        ctx.channel().attr(USER_ATTR).set(user);
                        logger.info("User info loaded: {} ({})", user.getNickname(), user.getUsername());

                        // 更新用户状态为在线
                        userService.updateUserStatus(userId, 1);
                        logger.info("Updated user {} status to online", userId);

                        // 通知好友管理器用户上线（重要：这样好友请求才能被发送）
                        if (friendManager != null && friendService != null) {
                            friendManager.userOnline(userId, ctx.channel());
                            // 加载用户的好友列表
                            try {
                                var friendList = friendService.getFriendList(userId);
                                var friendIds = friendList.stream()
                                        .map(com.gobang.model.entity.User::getId)
                                        .toList();
                                friendManager.loadUserFriends(userId, friendIds);
                                logger.info("Loaded {} friends for user {}", friendIds.size(), userId);
                            } catch (Exception e) {
                                logger.warn("Failed to load friends for user {}", userId, e);
                            }
                        }
                    }
                } else {
                    logger.warn("Invalid token in WebSocket handshake");
                }
            } else {
                logger.warn("No token found - allowing guest connection");
                // 为游客用户分配临时ID（负数）
                io.netty.util.AttributeKey<Long> USER_ID_ATTR = io.netty.util.AttributeKey.valueOf("userId");
                Long tempUserId = -(long)(Math.random() * 1000000 + 1000000);
                ctx.channel().attr(USER_ID_ATTR).set(tempUserId);
                io.netty.util.AttributeKey<Boolean> AUTHENTICATED_ATTR = io.netty.util.AttributeKey.valueOf("authenticated");
                ctx.channel().attr(AUTHENTICATED_ATTR).set(false);
                logger.info("Guest user assigned temporary ID: {}", tempUserId);
            }
        }
        // 处理空闲事件
        else if (evt instanceof io.netty.handler.timeout.IdleStateEvent) {
            logger.info("Channel idle, closing: {}", ctx.channel().id().asShortText());
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 处理JSON格式的文本帧（用于测试）
     */
    private void handleJsonFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        try {
            // 标记此channel为JSON模式
            ResponseUtil.setJsonMode(ctx.channel(), true);

            // Debug: Print raw byte content BEFORE getting text
            io.netty.buffer.ByteBuf content = frame.content();
            int readableBytes = content.readableBytes();
            System.err.println("=== [DEBUG] Frame received, bytes: " + readableBytes);

            int bytesToRead = Math.min(readableBytes, 50);
            byte[] rawBytes = new byte[bytesToRead];
            content.getBytes(content.readerIndex(), rawBytes);

            StringBuilder hexString = new StringBuilder();
            for (byte b : rawBytes) {
                hexString.append(String.format("%02X ", b));
            }
            System.err.println("=== [DEBUG] Raw bytes (hex, first 50): " + hexString);

            String jsonText = frame.text();
            System.err.println("=== [DEBUG] JSON string: '" + jsonText + "'");
            System.err.println("=== [DEBUG] JSON string length: " + jsonText.length());
            logger.info("Received JSON message: {}", jsonText);

            JsonNode root = objectMapper.readTree(jsonText);
            int typeValue = root.get("type").asInt();

            // 同时支持 sequenceId (驼峰) 和 sequence_id (下划线)
            long sequenceId = 0;
            if (root.has("sequenceId")) {
                sequenceId = root.get("sequenceId").asLong();
            } else if (root.has("sequence_id")) {
                sequenceId = root.get("sequence_id").asLong();
            }

            long timestamp = root.has("timestamp") ? root.get("timestamp").asLong() : System.currentTimeMillis();

            MessageType messageType = MessageType.fromValue(typeValue);
            if (messageType == MessageType.UNKNOWN) {
                logger.warn("Unknown message type: {}", typeValue);
                ResponseUtil.sendJsonResponse(ctx.channel(), typeValue, 0, Map.of("success", false, "message", "未知的消息类型"));
                return;
            }

            logger.info("Processing message: type={}, sequenceId={}", messageType, sequenceId);

            // 特殊处理：TOKEN_AUTH消息（在连接后发送token进行认证）
            // 必须在构建protobuf Packet之前处理，因为TOKEN_AUTH不在protobuf枚举中
            if (messageType == MessageType.TOKEN_AUTH) {
                handleTokenAuth(ctx, root);
                return;
            }

            // 特殊处理：GAME_RECONNECT消息（需要JSON格式的body）
            // 必须在构建protobuf Packet之前处理
            if (messageType == MessageType.GAME_RECONNECT) {
                handleJsonGameReconnect(ctx, root, sequenceId);
                return;
            }

            // 特殊处理：房间相关消息（CREATE_ROOM, JOIN_ROOM, ROOM_JOINED）
            if (messageType == MessageType.CREATE_ROOM ||
                messageType == MessageType.JOIN_ROOM ||
                messageType == MessageType.ROOM_JOINED ||
                messageType == MessageType.ROOM_PLAYER_LEFT) {
                if (roomHandler != null) {
                    roomHandler.handleJsonMessage(ctx, root);
                } else {
                    logger.warn("RoomHandler is not initialized");
                    ResponseUtil.sendJsonResponse(ctx.channel(), typeValue, sequenceId,
                        Map.of("success", false, "message", "房间功能未启用"));
                }
                return;
            }

            // 特殊处理：人机对战（BOT_MATCH_START）- 使用JsonMessageHandler
            if (messageType == MessageType.BOT_MATCH_START) {
                if (jsonMessageHandler != null) {
                    jsonMessageHandler.handleJsonMessage(ctx, jsonText);
                } else {
                    logger.warn("JsonMessageHandler is not initialized");
                    ResponseUtil.sendJsonResponse(ctx.channel(), typeValue, sequenceId,
                        Map.of("success", false, "message", "人机对战功能未启用"));
                }
                return;
            }

            // 构造 protobuf Packet 对象
            GobangProto.Packet.Builder packetBuilder = GobangProto.Packet.newBuilder()
                    .setType(GobangProto.MessageType.forNumber(typeValue))
                    .setSequenceId(sequenceId)
                    .setTimestamp(timestamp);

            System.out.println("=== [DEBUG] Building packet: type=" + typeValue + ", sequenceId=" + sequenceId);

            // 将JSON body转换为protobuf格式
            if (root.has("body") && root.get("body").isObject()) {
                JsonNode body = root.get("body");
                System.out.println("=== [DEBUG] Has body: " + body.toString());
                byte[] bodyBytes = convertJsonBodyToProtobuf(messageType, body);
                if (bodyBytes != null) {
                    packetBuilder.setBody(com.google.protobuf.ByteString.copyFrom(bodyBytes));
                    System.out.println("=== [DEBUG] Body bytes size: " + bodyBytes.length);
                } else {
                    System.out.println("=== [DEBUG] convertJsonBodyToProtobuf returned NULL!");
                }
            }

            GobangProto.Packet packet = packetBuilder.build();
            System.out.println("=== [DEBUG] Packet built: type=" + packet.getType().getNumber() + ", hasBody=" + (packet.getBody() != null));

            // 路由到对应的处理器
            MessageHandler handler = handlers.get(messageType);
            System.out.println("=== [DEBUG] Looking for handler for type: " + messageType + ", found: " + (handler != null));
            if (handler != null) {
                logger.info("=== Routing to handler: {} ===", messageType);
                logger.info("Handler class: {}", handler.getClass().getSimpleName());
                System.out.println("=== [DEBUG] Routing to handler: " + messageType + ", class=" + handler.getClass().getSimpleName());
                logger.info("Packet type={}, sequenceId={}", packet.getType().getNumber(), packet.getSequenceId());
                logger.info("Packet body: {}", packet.getBody() != null && !packet.getBody().isEmpty() ? "present" : "empty/null");

                // 对于游客用户的MATCH_START请求，保存用户信息到channel属性
                if (messageType == MessageType.MATCH_START && root.has("body")) {
                    JsonNode body = root.get("body");
                    if (body.has("username") && body.has("nickname")) {
                        io.netty.util.AttributeKey<String> GUEST_USERNAME = io.netty.util.AttributeKey.valueOf("guestUsername");
                        io.netty.util.AttributeKey<String> GUEST_NICKNAME = io.netty.util.AttributeKey.valueOf("guestNickname");
                        ctx.channel().attr(GUEST_USERNAME).set(body.get("username").asText());
                        ctx.channel().attr(GUEST_NICKNAME).set(body.get("nickname").asText());
                        logger.info("Guest user info saved to channel: {} ({})", body.get("username").asText(), body.get("nickname").asText());
                    }
                }

                try {
                    logger.info("Calling handler.handle()...");
                    System.out.println("=== [DEBUG] Calling handler.handle()...");
                    handler.handle(ctx, packet);
                    System.out.println("=== [DEBUG] handler.handle() completed successfully");
                    logger.info("handler.handle() completed successfully");
                } catch (Exception e) {
                    logger.error("Handler threw exception", e);
                    System.out.println("=== [DEBUG] Handler exception: " + e.getClass().getName() + ": " + e.getMessage());
                    e.printStackTrace(System.err);
                    ResponseUtil.sendJsonResponse(ctx.channel(), messageType.getValue(), 0,
                        Map.of("success", false, "message", "处理失败: " + e.getMessage()));
                }
            } else {
                logger.warn("=== No handler found for message type: {} ===", messageType);
                logger.warn("Registered handlers: {}", handlers.keySet());
                System.out.println("=== [DEBUG] No handler found for type: " + messageType + ", handlers: " + handlers.keySet());
                ResponseUtil.sendJsonResponse(ctx.channel(), messageType.getValue(), sequenceId,
                    Map.of("success", false, "message", "未注册的消息处理器: " + messageType));
            }

        } catch (Exception e) {
            logger.error("Failed to process JSON frame", e);
            System.err.println("=== [DEBUG] Failed to process JSON frame: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.err);
            // 发送错误响应给客户端
            try {
                ResponseUtil.sendJsonResponse(ctx.channel(), 0, Map.of("success", false, "message", "消息处理失败: " + e.getMessage()));
            } catch (Exception ex) {
                logger.error("Failed to send error response", ex);
            }
        }
    }

    /**
     * 处理Token认证消息
     */
    private void handleTokenAuth(ChannelHandlerContext ctx, JsonNode root) {
        try {
            logger.info("=== Processing TOKEN_AUTH message ===");
            logger.info("Request JSON: {}", root.toString());

            if (!root.has("body")) {
                logger.warn("No body in TOKEN_AUTH request");
                ResponseUtil.sendJsonResponse(ctx.channel(), 100, Map.of("success", false, "message", "Missing token"));
                return;
            }

            JsonNode body = root.get("body");
            logger.info("Body JSON: {}", body.toString());

            if (!body.has("token")) {
                logger.warn("No token in body");
                ResponseUtil.sendJsonResponse(ctx.channel(), 100, Map.of("success", false, "message", "Missing token field"));
                return;
            }

            String token = body.get("token").asText();
            logger.info("Token received, length: {}", token != null ? token.length() : 0);

            if (token == null || token.isEmpty()) {
                logger.warn("Token is null or empty");
                ResponseUtil.sendJsonResponse(ctx.channel(), 100, Map.of("success", false, "message", "Token is empty"));
                return;
            }

            logger.info("Validating token...");
            Long userId = authService.validateToken(token);
            logger.info("Token validation result: userId={}", userId);

            if (userId != null) {
                // 设置用户ID到channel属性
                io.netty.util.AttributeKey<Long> USER_ID_ATTR = io.netty.util.AttributeKey.valueOf("userId");
                ctx.channel().attr(USER_ID_ATTR).set(userId);
                io.netty.util.AttributeKey<Boolean> AUTHENTICATED_ATTR = io.netty.util.AttributeKey.valueOf("authenticated");
                ctx.channel().attr(AUTHENTICATED_ATTR).set(true);
                logger.info("User authenticated via TOKEN_AUTH: {}", userId);

                // 获取用户信息并设置到channel
                User user = userService.getUserById(userId);
                if (user != null) {
                    io.netty.util.AttributeKey<User> USER_ATTR = io.netty.util.AttributeKey.valueOf("user");
                    ctx.channel().attr(USER_ATTR).set(user);
                    logger.info("User info loaded: {} ({})", user.getNickname(), user.getUsername());

                    // 更新用户状态为在线
                    userService.updateUserStatus(userId, 1);
                    logger.info("Updated user {} status to online", userId);

                    // 发送认证成功响应
                    long sequenceId = root.has("sequenceId") ? root.get("sequenceId").asLong(0) : 0;
                    logger.info("Sending auth success response, sequenceId={}", sequenceId);

                    ResponseUtil.sendJsonResponse(ctx.channel(), 100, sequenceId,
                        Map.of("success", true, "userId", userId, "nickname", user.getNickname()));
                    logger.info("Auth success response sent");
                } else {
                    logger.warn("User not found in database: {}", userId);
                    ResponseUtil.sendJsonResponse(ctx.channel(), 100, Map.of("success", false, "message", "User not found"));
                }
            } else {
                logger.warn("Invalid token in TOKEN_AUTH message");
                ResponseUtil.sendJsonResponse(ctx.channel(), 100, root.has("sequenceId") ? root.get("sequenceId").asLong(0) : 0,
                    Map.of("success", false, "message", "Invalid token"));
            }
        } catch (Exception e) {
            logger.error("Error processing TOKEN_AUTH", e);
            logger.error("Exception details:", e);
            try {
                ResponseUtil.sendJsonResponse(ctx.channel(), 100, Map.of("success", false, "message", "Authentication failed: " + e.getMessage()));
            } catch (Exception ex) {
                logger.error("Failed to send error response", ex);
            }
        }
    }

    /**
     * 处理JSON格式的GAME_RECONNECT消息
     */
    private void handleJsonGameReconnect(ChannelHandlerContext ctx, JsonNode root, long sequenceId) {
        try {
            // 获取用户ID
            Long userId = AuthHandler.getUserId(ctx.channel());
            if (userId == null) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_RECONNECT.getValue(), sequenceId,
                    Map.of("success", false, "message", "未认证用户"));
                return;
            }

            // 获取房间ID
            if (!root.has("body") || !root.get("body").has("room_id")) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_RECONNECT.getValue(), sequenceId,
                    Map.of("success", false, "message", "缺少房间ID"));
                return;
            }

            String roomId = root.get("body").get("room_id").asText();
            logger.info("User {} requested reconnect to room {}", userId, roomId);

            // 获取房间
            GameRoom room = roomManager.getRoom(roomId);
            if (room == null) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_RECONNECT.getValue(), sequenceId,
                    Map.of("success", false, "message", "房间不存在"));
                return;
            }

            // 验证用户是否在该房间中
            if (!room.getBlackPlayerId().equals(userId) && !room.getWhitePlayerId().equals(userId)) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_RECONNECT.getValue(), sequenceId,
                    Map.of("success", false, "message", "您不在此房间中"));
                return;
            }

            // 更新玩家通道
            if (room.getBlackPlayerId().equals(userId)) {
                room.updatePlayerChannel(userId, ctx.channel());
            } else {
                room.updatePlayerChannel(userId, ctx.channel());
            }

            // 发送游戏状态（使用body格式以匹配客户端期望）
            Map<String, Object> bodyData = new java.util.HashMap<>();
            bodyData.put("success", true);
            bodyData.put("room_id", room.getRoomId());
            bodyData.put("board", room.getBoard().toArray());
            bodyData.put("current_player", room.getCurrentPlayer());
            bodyData.put("move_count", room.getMoves().size());
            bodyData.put("game_state", room.getGameState().name());

            // 添加玩家自己的颜色（客户端需要知道自己是黑方还是白方）
            int myColor = room.getBlackPlayerId().equals(userId) ? 1 : 2;
            bodyData.put("my_color", myColor);

            logger.info("Sending game state to user {}: myColor={}, currentPlayer={}", userId, myColor, room.getCurrentPlayer());

            ResponseUtil.sendJsonResponse(
                ctx.channel(),
                MessageType.GAME_STATE.getValue(),
                sequenceId,
                bodyData
            );

            logger.info("User {} reconnected to room {} successfully", userId, roomId);

        } catch (Exception e) {
            logger.error("Error processing GAME_RECONNECT", e);
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_RECONNECT.getValue(), sequenceId,
                Map.of("success", false, "message", "重连处理失败"));
        }
    }

    /**
     * 将JSON body转换为protobuf字节数组
     */
    private byte[] convertJsonBodyToProtobuf(MessageType messageType, JsonNode body) {
        try {
            logger.info("=== convertJsonBodyToProtobuf: {} ===", messageType);
            logger.info("Input JSON body: {}", body.toString());

            switch (messageType) {
                case AUTH_LOGIN:
                    logger.info("Converting AUTH_LOGIN: username={}, password={}",
                        body.has("username") ? body.get("username").asText() : "MISSING",
                        body.has("password") ? "***" : "MISSING");

                    GobangProto.LoginRequest loginRequest = GobangProto.LoginRequest.newBuilder()
                            .setUsername(body.get("username").asText())
                            .setPassword(body.get("password").asText())
                            .build();

                    logger.info("AUTH_LOGIN protobuf created, size: {} bytes", loginRequest.toByteArray().length);
                    return loginRequest.toByteArray();

                case AUTH_REGISTER:
                    GobangProto.RegisterRequest.Builder registerBuilder = GobangProto.RegisterRequest.newBuilder()
                            .setUsername(body.get("username").asText())
                            .setPassword(body.get("password").asText());
                    // 可选字段
                    if (body.has("nickname")) {
                        registerBuilder.setNickname(body.get("nickname").asText());
                    }
                    if (body.has("email")) {
                        registerBuilder.setEmail(body.get("email").asText());
                    }
                    return registerBuilder.build().toByteArray();

                case MATCH_START:
                    GobangProto.MatchRequest.Builder matchBuilder = GobangProto.MatchRequest.newBuilder()
                            .setRating(body.has("rating") ? body.get("rating").asInt() : 1200)
                            .setTimeout(300);
                    // 设置游戏模式（休闲/竞技）
                    if (body.has("mode")) {
                        String mode = body.get("mode").asText();
                        matchBuilder.setMode(mode);
                        logger.info("MATCH_START: 设置模式为 '{}'", mode);
                    } else {
                        logger.warn("MATCH_START: 未找到mode字段，使用默认值 'casual'");
                        matchBuilder.setMode("casual");
                    }
                    return matchBuilder.build().toByteArray();

                case GAME_MOVE:
                    GobangProto.MoveRequest moveRequest = GobangProto.MoveRequest.newBuilder()
                            .setX(body.get("x").asInt())
                            .setY(body.get("y").asInt())
                            .build();
                    return moveRequest.toByteArray();

                case GAME_RESIGN:
                case MATCH_CANCEL:
                case GAME_UNDO_REQUEST:
                    return new byte[0]; // 这些消息没有body

                case GAME_UNDO_RESPONSE:
                    GobangProto.UndoResponse undoResponse = GobangProto.UndoResponse.newBuilder()
                            .setAccepted(body.get("accepted").asBoolean())
                            .build();
                    return undoResponse.toByteArray();

                case CHAT_SEND:
                    GobangProto.ChatSend chatSend = GobangProto.ChatSend.newBuilder()
                            .setContent(body.get("content").asText())
                            .setTargetId(body.has("target_id") ? body.get("target_id").asText() : "")
                            .build();
                    return chatSend.toByteArray();

                case FRIEND_REQUEST:
                    GobangProto.FriendRequestMsg friendRequest = GobangProto.FriendRequestMsg.newBuilder()
                            .setTargetId(body.has("target_id") ? body.get("target_id").asText() : "")
                            .setMessage(body.has("message") ? body.get("message").asText() : "")
                            .build();
                    return friendRequest.toByteArray();

                case FRIEND_ACCEPT:
                    GobangProto.FriendAcceptMsg friendAccept = GobangProto.FriendAcceptMsg.newBuilder()
                            .setRequestId(body.has("request_id") ? body.get("request_id").asText() : "")
                            .build();
                    return friendAccept.toByteArray();

                case FRIEND_REJECT:
                    GobangProto.FriendRejectMsg friendReject = GobangProto.FriendRejectMsg.newBuilder()
                            .setRequestId(body.has("request_id") ? body.get("request_id").asText() : "")
                            .build();
                    return friendReject.toByteArray();

                case FRIEND_REMOVE:
                    GobangProto.FriendRemoveMsg friendRemove = GobangProto.FriendRemoveMsg.newBuilder()
                            .setFriendId(body.has("friend_id") ? body.get("friend_id").asText() : "")
                            .build();
                    return friendRemove.toByteArray();

                case FRIEND_LIST:
                    // FRIEND_LIST doesn't need a body
                    return new byte[0];

                default:
                    logger.warn("Unhandled message type for JSON conversion: {}", messageType);
                    return null;
            }
        } catch (Exception e) {
            logger.error("Failed to convert JSON body to protobuf", e);
            return null;
        }
    }
}
