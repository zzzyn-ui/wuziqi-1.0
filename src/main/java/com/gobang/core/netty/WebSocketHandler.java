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
import com.gobang.service.GameService;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket消息处理器
 * 处理所有WebSocket消息并路由到对应的业务处理器
 */
@ChannelHandler.Sharable
public class WebSocketHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String JSON_MODE_KEY = "jsonMode";

    // 超时检查定时任务
    private final ScheduledExecutorService timeoutChecker = Executors.newSingleThreadScheduledExecutor();

    private final Map<MessageType, MessageHandler> handlers = new HashMap<>();
    private final ChannelManager channelManager;
    private final UserService userService;
    private final AuthService authService;
    private final RoomManager roomManager;
    private final FriendService friendService;
    private final JwtUtil jwtUtil;
    private final FriendManager friendManager;
    private final GameService gameService;
    private HttpRequestHandler httpRequestHandler;
    private RoomHandler roomHandler;
    private JsonMessageHandler jsonMessageHandler;
    private HttpApiHandler httpApiHandler;  // 新增：HTTP API 处理器

    public WebSocketHandler(ChannelManager channelManager, UserService userService, AuthService authService, RoomManager roomManager, GameService gameService) {
        this.channelManager = channelManager;
        this.userService = userService;
        this.authService = authService;
        this.roomManager = roomManager;
        this.gameService = gameService;
        this.friendService = null;
        this.jwtUtil = null;
        this.friendManager = null;
        this.httpRequestHandler = new HttpRequestHandler("static", "/ws", roomManager, null, null, null);
        this.roomHandler = new RoomHandler(roomManager, userService);
    }

    public WebSocketHandler(ChannelManager channelManager, UserService userService, AuthService authService, RoomManager roomManager, FriendService friendService, JwtUtil jwtUtil, GameService gameService) {
        this(channelManager, userService, authService, roomManager, friendService, jwtUtil, null, gameService);
    }

    public WebSocketHandler(ChannelManager channelManager, UserService userService, AuthService authService, RoomManager roomManager, FriendService friendService, JwtUtil jwtUtil, FriendManager friendManager, GameService gameService) {
        this.channelManager = channelManager;
        this.userService = userService;
        this.authService = authService;
        this.roomManager = roomManager;
        this.friendService = friendService;
        this.jwtUtil = jwtUtil;
        this.friendManager = friendManager;
        this.gameService = gameService;
        this.httpRequestHandler = new HttpRequestHandler("static", "/ws", roomManager, friendService, userService, jwtUtil);
        this.roomHandler = new RoomHandler(roomManager, userService);

        // 启动超时检查任务，每5秒检查一次
        startTimeoutChecker();
    }

    /**
     * 更新 HttpApiHandler 依赖
     * 在 NettyServer 初始化后调用
     */
    public void setHttpApiHandler(HttpApiHandler httpApiHandler) {
        this.httpApiHandler = httpApiHandler;
    }

    /**
     * 启动超时检查任务
     */
    private void startTimeoutChecker() {
        timeoutChecker.scheduleAtFixedRate(() -> {
            try {
                checkAllRoomsTimeout();
            } catch (Exception e) {
                logger.error("Error in timeout checker", e);
            }
        }, 5, 5, TimeUnit.SECONDS);
        logger.info("Timeout checker started");
    }

    /**
     * 检查所有房间的超时情况
     */
    private void checkAllRoomsTimeout() {
        for (GameRoom room : roomManager.getAllRooms()) {
            if (room.getGameState() == com.gobang.core.game.GameState.PLAYING) {
                // 检查所有模式的超时（包括休闲和竞技模式）
                Long timeoutPlayerId = room.checkTimeout();
                if (timeoutPlayerId != null) {
                    // 有人超时，判负
                    handleTimeoutLoss(room, timeoutPlayerId);
                }
            }
        }
    }

    /**
     * 处理超时判负
     */
    private void handleTimeoutLoss(GameRoom room, Long timeoutPlayerId) {
        try {
            logger.info("Handling timeout loss for player {} in room {}", timeoutPlayerId, room.getRoomId());

            // 确定对手（获胜者）
            Long winnerId = timeoutPlayerId.equals(room.getBlackPlayerId())
                    ? room.getWhitePlayerId() : room.getBlackPlayerId();

            // 计算积分变化
            int winnerRating = getRating(winnerId);
            int loserRating = getRating(timeoutPlayerId);
            int[] newRatings = com.gobang.core.rating.RatingCalculator.calculateNewRatings(
                winnerRating, loserRating, false);

            // 广播游戏结束消息
            broadcastGameOverWithRating(room, winnerId, newRatings[0], newRatings[1]);

        } catch (Exception e) {
            logger.error("Error handling timeout loss", e);
        }
    }

    /**
     * 获取玩家积分（从数据库或使用默认值）
     */
    private int getRating(Long userId) {
        try {
            if (userService != null) {
                return userService.getRating(userId);
            }
        } catch (Exception e) {
            logger.warn("Failed to get rating for user {}, using default 1200", userId);
        }
        return 1200; // 默认积分
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
            // 优先使用 HttpApiHandler 处理（包含所有 9090 端口的 API）
            if (httpApiHandler != null) {
                boolean handled = httpApiHandler.handleApiRequest(ctx, request);
                if (handled) {
                    return true;
                }
            }

            // 如果 HttpApiHandler 没有处理，使用原有的处理方式
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

            // 包装成前端期望的格式 { success: true, data: {...} }
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", health);

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
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

            ctx.writeAndFlush(response);
        } catch (Exception e) {
            logger.error("Failed to send error response", e);
        }
    }

    /**
     * 处理CORS预检请求
     */
    private void handleCorsPreflight(ChannelHandlerContext ctx, FullHttpRequest request) {
        String origin = request.headers().get("Origin");
        if (origin == null) {
            origin = "*";
        }

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK
        );

        // 设置CORS头
        response.headers().set("Access-Control-Allow-Origin", origin);
        response.headers().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.headers().set("Access-Control-Max-Age", "86400");
        response.headers().set("Content-Length", 0);

        ctx.writeAndFlush(response);
        logger.info("CORS preflight request handled for origin: {}", origin);
    }

    /**
     * 为HTTP响应添加CORS头
     */
    private void addCorsHeaders(DefaultFullHttpResponse response) {
        String origin = "*"; // 允许所有来源，生产环境应该限制
        response.headers().set("Access-Control-Allow-Origin", origin);
        response.headers().set("Access-Control-Allow-Credentials", "true");
        response.headers().set("Access-Control-Expose-Headers", "Content-Type, Authorization");
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

            // 处理 CORS 预检请求
            if ("OPTIONS".equals(request.method().name())) {
                handleCorsPreflight(ctx, request);
                request.release();
                return;
            }

            // 对于 WebSocket 握手请求，先提取 token，然后传递给下一个处理器
            if (uri.startsWith("/ws")) {
                logger.info("WebSocket upgrade request detected, extracting token: {}", uri);
                // 提取 URL 参数中的 token 并保存到 channel 属性
                if (uri.contains("?")) {
                    try {
                        QueryStringDecoder decoder = new QueryStringDecoder(uri);
                        var tokens = decoder.parameters().get("token");
                        if (tokens != null && !tokens.isEmpty()) {
                            String token = tokens.get(0);
                            logger.info("Token found in WebSocket URL for {}: length={}", uri, token.length());
                            // 保存token到channel属性
                            ctx.channel().attr(io.netty.util.AttributeKey.<String>valueOf("authToken")).set(token);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to extract token from URI", e);
                    }
                }
                // 不处理 WebSocket 握手请求，让它传递给下一个处理器（WebSocketServerProtocolHandler）
                logger.info("Letting WebSocketServerProtocolHandler handle the upgrade request: {}", uri);
                // 不调用 return，让请求自然地传递给下一个处理器
                // 但是需要 retain 请求，因为当前的 handler 可能会释放它
                ReferenceCountUtil.retain(request);
                ctx.fireChannelRead(request);
                return;
            }

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
            logger.info("httpRequestHandler.handle returned: {} for URI: {}", handled, uri);

            if (!handled) {
                // 如果不是静态文件请求，继续处理（WebSocket升级）
                // 传递给下一个处理器
                logger.info("Passing HTTP request to next handler: {}", uri);
                ReferenceCountUtil.retain(request);
                ctx.fireChannelRead(request);
            } else {
                logger.info("HTTP request was handled by HttpRequestHandler: {}", uri);
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

        // 延迟执行认证检查，给 HTTP Request 处理留出时间
        io.netty.channel.Channel ch = ctx.channel();
        io.netty.util.concurrent.ScheduledFuture<?> timeoutFuture = ch.eventLoop().schedule(() -> {
            if (!AuthHandler.isAuthenticated(ctx.channel())) {
                logger.info("=== Delayed authentication check for channel {} ===", ctx.channel().id().asShortText());
                authenticateUserFromToken(ctx);
            } else {
                logger.info("Channel {} already authenticated", ctx.channel().id().asShortText());
            }
        }, 500, java.util.concurrent.TimeUnit.MILLISECONDS);

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
        logger.info("=== userEventTriggered: {} on channel {} ===", evt.getClass().getSimpleName(), ctx.channel().id().asShortText());

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

                    // 确保用户也被注册到 FriendManager
                    if (friendManager != null && friendService != null) {
                        // 检查是否已经在 FriendManager 中注册
                        if (!friendManager.isUserOnline(userId)) {
                            logger.info("Registering user {} to FriendManager after handshake", userId);
                            User user = userService.getUserById(userId);
                            if (user != null) {
                                io.netty.util.AttributeKey<User> USER_ATTR = io.netty.util.AttributeKey.valueOf("user");
                                ctx.channel().attr(USER_ATTR).set(user);

                                // 更新用户状态为在线
                                userService.updateUserStatus(userId, 1);

                                // 通知好友管理器用户上线
                                friendManager.userOnline(userId, ctx.channel());

                                // 加载用户的好友列表
                                try {
                                    var friendList = friendService.getFriendList(userId);
                                    var friendIds = friendList.stream()
                                            .map(com.gobang.model.entity.User::getId)
                                            .toList();
                                    friendManager.loadUserFriends(userId, friendIds);
                                    logger.info("Handshake auth: Loaded {} friends for user {}", friendIds.size(), userId);
                                } catch (Exception e) {
                                    logger.warn("Failed to load friends for user {}", userId, e);
                                }
                            }
                        } else {
                            logger.info("User {} already registered in FriendManager", userId);
                        }
                    }

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
        }
        // 总是调用 super.userEventTriggered，确保事件被传递
        super.userEventTriggered(ctx, evt);
    }

    /**
     * 从channel属性中的token认证用户（备用认证机制）
     */
    private void authenticateUserFromToken(ChannelHandlerContext ctx) {
        io.netty.util.AttributeKey<String> TOKEN_ATTR = io.netty.util.AttributeKey.valueOf("authToken");
        String token = ctx.channel().attr(TOKEN_ATTR).get();

        logger.info("=== Fallback authentication checking for channel {} ===", ctx.channel().id().asShortText());
        logger.info("Token in channel attributes: {}", token != null ? "present (length=" + token.length() + ")" : "null");

        if (token == null) {
            logger.warn("No token found in channel attributes during message processing");
            return;
        }

        Long userId = authService.validateToken(token);
        logger.info("Token validation result: userId={}", userId);

        if (userId == null) {
            logger.warn("Invalid token during message processing");
            return;
        }

        // 设置用户ID到channel属性
        io.netty.util.AttributeKey<Long> USER_ID_ATTR = io.netty.util.AttributeKey.valueOf("userId");
        ctx.channel().attr(USER_ID_ATTR).set(userId);
        io.netty.util.AttributeKey<Boolean> AUTHENTICATED_ATTR = io.netty.util.AttributeKey.valueOf("authenticated");
        ctx.channel().attr(AUTHENTICATED_ATTR).set(true);

        logger.info("User authenticated via fallback mechanism: {}", userId);

        // 获取用户信息并注册到FriendManager
        User user = userService.getUserById(userId);
        if (user != null && friendManager != null && friendService != null) {
            io.netty.util.AttributeKey<User> USER_ATTR = io.netty.util.AttributeKey.valueOf("user");
            ctx.channel().attr(USER_ATTR).set(user);

            // 更新用户状态为在线
            userService.updateUserStatus(userId, 1);

            // 通知好友管理器用户上线
            friendManager.userOnline(userId, ctx.channel());

            // 加载用户的好友列表
            try {
                var friendList = friendService.getFriendList(userId);
                var friendIds = friendList.stream()
                        .map(com.gobang.model.entity.User::getId)
                        .toList();
                friendManager.loadUserFriends(userId, friendIds);
                logger.info("Fallback auth: Loaded {} friends for user {}", friendIds.size(), userId);
            } catch (Exception e) {
                logger.warn("Failed to load friends for user {}", userId, e);
            }
        }
    }

    /**
     * 处理JSON格式的文本帧（用于测试）
     */
    private void handleJsonFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        try {
            // 标记此channel为JSON模式
            ResponseUtil.setJsonMode(ctx.channel(), true);

            // 认证检查：如果用户未认证但channel有token，尝试认证
            if (!AuthHandler.isAuthenticated(ctx.channel())) {
                authenticateUserFromToken(ctx);
            }

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

            // 特殊处理：GAME_MOVE消息 - 直接处理，避免handlers Map为空的问题
            if (messageType == MessageType.GAME_MOVE) {
                handleJsonGameMove(ctx, root, sequenceId);
                return;
            }

            // 特殊处理：GAME_RESIGN消息 - 直接处理认输
            if (messageType == MessageType.GAME_RESIGN) {
                handleJsonGameResign(ctx, root, sequenceId);
                return;
            }

            // 特殊处理：GAME_UNDO_REQUEST消息 - 请求悔棋
            if (messageType == MessageType.GAME_UNDO_REQUEST) {
                handleJsonGameUndoRequest(ctx, root, sequenceId);
                return;
            }

            // 特殊处理：GAME_UNDO_RESPONSE消息 - 响应悔棋
            if (messageType == MessageType.GAME_UNDO_RESPONSE) {
                handleJsonGameUndoResponse(ctx, root, sequenceId);
                return;
            }

            // 特殊处理：GAME_REMATCH_REQUEST消息 - 请求再来一局
            if (messageType == MessageType.GAME_REMATCH_REQUEST) {
                handleJsonGameRematchRequest(ctx, root, sequenceId);
                return;
            }

            // 特殊处理：GAME_REMATCH_RESPONSE消息 - 响应再来一局
            if (messageType == MessageType.GAME_REMATCH_RESPONSE) {
                handleJsonGameRematchResponse(ctx, root, sequenceId);
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

            logger.info("User {} requested reconnect", userId);

            // 使用GameService处理重连（支持从Redis恢复）
            Map<String, Object> result = gameService.handleReconnect(userId, ctx.channel());

            if (result != null && Boolean.TRUE.equals(result.get("found"))) {
                // 发送游戏状态
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_STATE.getValue(), sequenceId, result);
                logger.info("User {} reconnected successfully to room {}", userId, result.get("room_id"));
            } else {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_RECONNECT.getValue(), sequenceId,
                    Map.of("success", false, "message", result.getOrDefault("message", "未找到进行中的游戏")));
            }

        } catch (Exception e) {
            logger.error("Error processing GAME_RECONNECT", e);
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_RECONNECT.getValue(), sequenceId,
                Map.of("success", false, "message", "重连处理失败"));
        }
    }

    /**
     * 处理落子请求（JSON格式）
     */
    private void handleJsonGameMove(ChannelHandlerContext ctx, JsonNode root, long sequenceId) {
        try {
            // 获取用户ID
            Long userId = AuthHandler.getUserId(ctx.channel());
            if (userId == null) {
                // 尝试从channel属性中获取临时用户ID
                io.netty.util.AttributeKey<Long> TEMP_USER_ID = io.netty.util.AttributeKey.valueOf("tempUserId");
                userId = ctx.channel().attr(TEMP_USER_ID).get();
                if (userId == null) {
                    ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_MOVE_RESULT.getValue(), sequenceId,
                        Map.of("success", false, "message", "请先登录"));
                    return;
                }
            }

            // 解析坐标
            JsonNode body = root.get("body");
            if (body == null || !body.has("x") || !body.has("y")) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_MOVE_RESULT.getValue(), sequenceId,
                    Map.of("success", false, "message", "缺少坐标参数"));
                return;
            }

            int x = body.get("x").asInt();
            int y = body.get("y").asInt();

            // 获取用户所在房间
            GameRoom room = roomManager.getRoomByUserId(userId);
            if (room == null) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_MOVE_RESULT.getValue(), sequenceId,
                    Map.of("success", false, "message", "您不在任何游戏中"));
                return;
            }

            // 落子
            int result = room.makeMove(userId, x, y);

            String message;
            boolean success = false;
            switch (result) {
                case 0:
                    message = "落子成功";
                    success = true;
                    logger.info("User {} made move at ({}, {}) in room {}", userId, x, y, room.getRoomId());
                    break;
                case 1:
                    message = "位置无效";
                    break;
                case 2:
                    message = "不是您的回合";
                    break;
                case 3:
                    message = "游戏已结束";
                    break;
                case 4:
                    message = "该位置已有棋子";
                    break;
                default:
                    message = "落子失败";
                    break;
            }

            // 构建响应数据
            Map<String, Object> responseData = new java.util.HashMap<>();
            responseData.put("success", success);
            responseData.put("message", message);

            // 如果落子成功，发送更新后的游戏状态
            if (success) {
                Map<String, Object> gameState = new java.util.HashMap<>();
                gameState.put("room_id", room.getRoomId());
                gameState.put("board", room.getBoard().toArray());
                gameState.put("current_player", room.getCurrentPlayer());
                gameState.put("move_count", room.getMoves().size());
                gameState.put("game_state", room.getGameState().name());
                responseData.put("state", gameState);
            }

            // 发送结果给当前玩家
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_MOVE_RESULT.getValue(), sequenceId, responseData);

            // 如果落子成功，广播游戏状态给房间内的所有玩家（包括观战者）
            if (success) {
                broadcastGameState(room);

                // 检查游戏是否结束
                if (room.getGameState() == com.gobang.core.game.GameState.FINISHED) {
                    broadcastGameOver(room, room.getLastMovePlayerId());
                }
            }

        } catch (Exception e) {
            logger.error("Error processing GAME_MOVE", e);
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_MOVE_RESULT.getValue(), sequenceId,
                Map.of("success", false, "message", "落子处理失败"));
        }
    }

    /**
     * 广播游戏状态给房间内的所有玩家和观战者
     */
    private void broadcastGameState(GameRoom room) {
        try {
            // 使用标准JSON格式（数据在body中）
            Map<String, Object> gameState = new java.util.HashMap<>();
            gameState.put("room_id", room.getRoomId());
            gameState.put("board", room.getBoard().toArray());
            gameState.put("current_player", room.getCurrentPlayer());
            gameState.put("move_count", room.getMoves().size());
            gameState.put("game_state", room.getGameState().name());
            // 添加双方剩余时间（毫秒）
            gameState.put("black_remaining_time", room.getBlackPlayerRemainingTime());
            gameState.put("white_remaining_time", room.getWhitePlayerRemainingTime());

            logger.info("=== broadcastGameState 开始 === 房间: {}, 棋子数: {}, 当前玩家: {}, 游戏状态: {}",
                room.getRoomId(), room.getMoves().size(), room.getCurrentPlayer(), room.getGameState().name());

            // 发送给黑方（添加 my_color）
            logger.info("黑方channel状态: {}, 是否活跃: {}",
                room.getBlackChannel(), room.getBlackChannel() != null && room.getBlackChannel().isActive());

            if (room.getBlackChannel() != null && room.getBlackChannel().isActive()) {
                Map<String, Object> blackState = new java.util.HashMap<>(gameState);
                blackState.put("my_color", 1); // 黑方
                ResponseUtil.sendJsonResponse(room.getBlackChannel(),
                    MessageType.GAME_STATE.getValue(), 0, blackState);
                logger.info("✅ 已发送 GAME_STATE 给黑方 - 房间: {}, 棋子数: {}", room.getRoomId(), room.getMoves().size());
            } else {
                logger.warn("⚠️ 黑方channel不可用 - 房间: {}", room.getRoomId());
            }

            // 发送给白方（添加 my_color）
            logger.info("白方channel状态: {}, 是否活跃: {}",
                room.getWhiteChannel(), room.getWhiteChannel() != null && room.getWhiteChannel().isActive());

            if (room.getWhiteChannel() != null && room.getWhiteChannel().isActive()) {
                Map<String, Object> whiteState = new java.util.HashMap<>(gameState);
                whiteState.put("my_color", 2); // 白方
                ResponseUtil.sendJsonResponse(room.getWhiteChannel(),
                    MessageType.GAME_STATE.getValue(), 0, whiteState);
                logger.info("✅ 已发送 GAME_STATE 给白方 - 房间: {}, 棋子数: {}", room.getRoomId(), room.getMoves().size());
            } else {
                logger.warn("⚠️ 白方channel不可用 - 房间: {}", room.getRoomId());
            }

            logger.info("=== broadcastGameState 完成 ===");

        } catch (Exception e) {
            logger.error("Error broadcasting game state", e);
        }
    }

    /**
     * 处理认输请求（JSON格式）
     */
    private void handleJsonGameResign(ChannelHandlerContext ctx, JsonNode root, long sequenceId) {
        try {
            // 获取用户ID
            Long userId = AuthHandler.getUserId(ctx.channel());
            if (userId == null) {
                // 尝试从channel属性中获取临时用户ID
                io.netty.util.AttributeKey<Long> TEMP_USER_ID = io.netty.util.AttributeKey.valueOf("tempUserId");
                userId = ctx.channel().attr(TEMP_USER_ID).get();
                if (userId == null) {
                    ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_RESIGN.getValue(), sequenceId,
                        Map.of("success", false, "message", "请先登录"));
                    return;
                }
            }

            // 获取用户所在房间
            GameRoom room = roomManager.getRoomByUserId(userId);
            if (room == null) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_RESIGN.getValue(), sequenceId,
                    Map.of("success", false, "message", "您不在任何游戏中"));
                return;
            }

            logger.info("User {} resigned in room {}", userId, room.getRoomId());

            // 认输
            room.resign(userId);

            // 确定对手
            Long opponentId = userId.equals(room.getBlackPlayerId())
                    ? room.getWhitePlayerId() : room.getBlackPlayerId();

            // 发送确认
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_RESIGN.getValue(), sequenceId,
                Map.of("success", true, "message", "您已认输"));

            // 广播游戏结束消息
            broadcastGameOver(room, opponentId);

        } catch (Exception e) {
            logger.error("Error processing GAME_RESIGN", e);
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_RESIGN.getValue(), sequenceId,
                Map.of("success", false, "message", "认输处理失败"));
        }
    }

    /**
     * 广播游戏结束消息（带积分计算）
     */
    private void broadcastGameOver(GameRoom room, Long winnerId) {
        try {
            // 首先调用 GameService 处理游戏结束（保存记录、更新积分等）
            if (gameService != null) {
                gameService.endGame(room, winnerId, 0); // 0=正常胜负
            }

            boolean isCasual = "casual".equals(room.getGameMode());
            int winnerRatingChange = 0;
            int loserRatingChange = 0;

            // 竞技模式计算积分变化
            if (!isCasual && winnerId != null) {
                Long loserId = winnerId.equals(room.getBlackPlayerId())
                        ? room.getWhitePlayerId() : room.getBlackPlayerId();

                int winnerRating = getRating(winnerId);
                int loserRating = getRating(loserId);

                int[] newRatings = com.gobang.core.rating.RatingCalculator.calculateNewRatings(
                    winnerRating, loserRating, false);

                winnerRatingChange = newRatings[0] - winnerRating;
                loserRatingChange = newRatings[1] - loserRating;

                // 更新数据库中的积分
                updateRatingInDatabase(winnerId, newRatings[0]);
                updateRatingInDatabase(loserId, newRatings[1]);
            }

            Map<String, Object> gameOverData = new java.util.HashMap<>();
            gameOverData.put("room_id", room.getRoomId());
            gameOverData.put("winner_id", winnerId);
            gameOverData.put("end_reason", 1); // 1=正常结束
            gameOverData.put("is_casual", isCasual);
            gameOverData.put("rating_change", isCasual ? 0 : winnerRatingChange);

            // 发送给黑方
            if (room.getBlackChannel() != null && room.getBlackChannel().isActive()) {
                Map<String, Object> data = new java.util.HashMap<>(gameOverData);
                boolean isWinner = winnerId != null && winnerId.equals(room.getBlackPlayerId());
                data.put("is_winner", isWinner);
                data.put("rating_change", isCasual ? 0 :
                    (isWinner ? winnerRatingChange : loserRatingChange));
                ResponseUtil.sendJsonResponse(room.getBlackChannel(),
                    MessageType.GAME_OVER.getValue(), 0, data);
            }

            // 发送给白方
            if (room.getWhiteChannel() != null && room.getWhiteChannel().isActive()) {
                Map<String, Object> data = new java.util.HashMap<>(gameOverData);
                boolean isWinner = winnerId != null && winnerId.equals(room.getWhitePlayerId());
                data.put("is_winner", isWinner);
                data.put("rating_change", isCasual ? 0 :
                    (isWinner ? winnerRatingChange : loserRatingChange));
                ResponseUtil.sendJsonResponse(room.getWhiteChannel(),
                    MessageType.GAME_OVER.getValue(), 0, data);
            }

        } catch (Exception e) {
            logger.error("Error broadcasting game over", e);
        }
    }

    /**
     * 广播游戏结束消息（带积分计算，用于超时等情况）
     */
    private void broadcastGameOverWithRating(GameRoom room, Long winnerId, int winnerNewRating, int loserNewRating) {
        try {
            boolean isCasual = "casual".equals(room.getGameMode());

            Long loserId = winnerId.equals(room.getBlackPlayerId())
                    ? room.getWhitePlayerId() : room.getBlackPlayerId();

            int winnerOldRating = getRating(winnerId);
            int loserOldRating = getRating(loserId);

            int winnerRatingChange = winnerNewRating - winnerOldRating;
            int loserRatingChange = loserNewRating - loserOldRating;

            Map<String, Object> gameOverData = new java.util.HashMap<>();
            gameOverData.put("room_id", room.getRoomId());
            gameOverData.put("winner_id", winnerId);
            gameOverData.put("end_reason", 3); // 3=超时
            gameOverData.put("is_casual", isCasual);

            // 发送给黑方
            if (room.getBlackChannel() != null && room.getBlackChannel().isActive()) {
                Map<String, Object> data = new java.util.HashMap<>(gameOverData);
                boolean isWinner = winnerId.equals(room.getBlackPlayerId());
                data.put("is_winner", isWinner);
                data.put("rating_change", isCasual ? 0 :
                    (isWinner ? winnerRatingChange : loserRatingChange));
                ResponseUtil.sendJsonResponse(room.getBlackChannel(),
                    MessageType.GAME_OVER.getValue(), 0, data);
            }

            // 发送给白方
            if (room.getWhiteChannel() != null && room.getWhiteChannel().isActive()) {
                Map<String, Object> data = new java.util.HashMap<>(gameOverData);
                boolean isWinner = winnerId.equals(room.getWhitePlayerId());
                data.put("is_winner", isWinner);
                data.put("rating_change", isCasual ? 0 :
                    (isWinner ? winnerRatingChange : loserRatingChange));
                ResponseUtil.sendJsonResponse(room.getWhiteChannel(),
                    MessageType.GAME_OVER.getValue(), 0, data);
            }

        } catch (Exception e) {
            logger.error("Error broadcasting game over with rating", e);
        }
    }

    /**
     * 更新数据库中的积分
     */
    private void updateRatingInDatabase(Long userId, int newRating) {
        try {
            if (userService != null) {
                userService.updateRating(userId, newRating);
                logger.info("Updated rating for user {} to {}", userId, newRating);
            }
        } catch (Exception e) {
            logger.error("Failed to update rating for user {}", userId, e);
        }
    }

    /**
     * 处理悔棋请求（JSON格式）
     */
    private void handleJsonGameUndoRequest(ChannelHandlerContext ctx, JsonNode root, long sequenceId) {
        try {
            // 获取用户ID
            Long userId = AuthHandler.getUserId(ctx.channel());
            if (userId == null) {
                io.netty.util.AttributeKey<Long> TEMP_USER_ID = io.netty.util.AttributeKey.valueOf("tempUserId");
                userId = ctx.channel().attr(TEMP_USER_ID).get();
                if (userId == null) {
                    ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_UNDO_RESPONSE.getValue(), sequenceId,
                        Map.of("success", false, "message", "请先登录"));
                    return;
                }
            }

            // 获取用户所在房间
            GameRoom room = roomManager.getRoomByUserId(userId);
            if (room == null) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_UNDO_RESPONSE.getValue(), sequenceId,
                    Map.of("success", false, "message", "您不在任何游戏中"));
                return;
            }

            // **重要：更新当前用户的 channel**（防止重连后 channel 失效）
            if (userId.equals(room.getBlackPlayerId())) {
                room.setBlackChannel(ctx.channel());
                logger.info("更新黑方 channel: userId={}", userId);
            } else if (userId.equals(room.getWhitePlayerId())) {
                room.setWhiteChannel(ctx.channel());
                logger.info("更新白方 channel: userId={}", userId);
            }

            // **重要：从 ChannelManager 获取双方最新的活跃 channel**
            io.netty.channel.Channel activeBlackChannel = channelManager.getChannel(room.getBlackPlayerId());
            io.netty.channel.Channel activeWhiteChannel = channelManager.getChannel(room.getWhitePlayerId());

            if (activeBlackChannel != null && activeBlackChannel.isActive()) {
                room.setBlackChannel(activeBlackChannel);
                logger.info("从 ChannelManager 更新黑方活跃 channel: userId={}, 活跃={}",
                    room.getBlackPlayerId(), activeBlackChannel.isActive());
            } else {
                logger.warn("黑方 channel 不可用: userId={}", room.getBlackPlayerId());
            }

            if (activeWhiteChannel != null && activeWhiteChannel.isActive()) {
                room.setWhiteChannel(activeWhiteChannel);
                logger.info("从 ChannelManager 更新白方活跃 channel: userId={}, 活跃={}",
                    room.getWhitePlayerId(), activeWhiteChannel.isActive());
            } else {
                logger.warn("白方 channel 不可用: userId={}", room.getWhitePlayerId());
            }

            // 请求悔棋
            int result = room.requestUndo(userId);

            String message;
            boolean success = false;
            switch (result) {
                case 0:
                    message = "悔棋请求已发送";
                    success = true;
                    logger.info("User {} requested undo in room {}", userId, room.getRoomId());
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

            // 只在失败时向请求者发送错误消息
            // 成功时不发送消息，前端已显示等待模态框
            if (!success) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_UNDO_RESPONSE.getValue(), sequenceId,
                    Map.of("success", false, "message", message));
            }

            // 如果成功，通知对方玩家
            if (success) {
                notifyUndoRequest(room, userId);
            }

        } catch (Exception e) {
            logger.error("Error processing GAME_UNDO_REQUEST", e);
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_UNDO_RESPONSE.getValue(), sequenceId,
                Map.of("success", false, "message", "悔棋请求处理失败"));
        }
    }

    /**
     * 处理悔棋响应（JSON格式）
     */
    private void handleJsonGameUndoResponse(ChannelHandlerContext ctx, JsonNode root, long sequenceId) {
        try {
            // 获取用户ID
            Long userId = AuthHandler.getUserId(ctx.channel());
            if (userId == null) {
                io.netty.util.AttributeKey<Long> TEMP_USER_ID = io.netty.util.AttributeKey.valueOf("tempUserId");
                userId = ctx.channel().attr(TEMP_USER_ID).get();
                if (userId == null) {
                    ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_UNDO_RESPONSE.getValue(), sequenceId,
                        Map.of("success", false, "message", "请先登录"));
                    return;
                }
            }

            // 解析响应
            JsonNode body = root.get("body");
            if (body == null || !body.has("accepted")) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_UNDO_RESPONSE.getValue(), sequenceId,
                    Map.of("success", false, "message", "缺少accepted参数"));
                return;
            }

            boolean accepted = body.get("accepted").asBoolean();
            logger.info("=== handleJsonGameUndoResponse === userId: {}, accepted: {}", userId, accepted);

            // 获取用户所在房间
            GameRoom room = roomManager.getRoomByUserId(userId);
            if (room == null) {
                logger.warn("handleJsonGameUndoResponse - 用户不在任何游戏中: {}", userId);
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_UNDO_RESPONSE.getValue(), sequenceId,
                    Map.of("success", false, "message", "您不在任何游戏中"));
                return;
            }

            // **重要：更新当前用户的 channel**（防止重连后 channel 失效）
            if (userId.equals(room.getBlackPlayerId())) {
                room.setBlackChannel(ctx.channel());
                logger.info("更新黑方 channel: userId={}", userId);
            } else if (userId.equals(room.getWhitePlayerId())) {
                room.setWhiteChannel(ctx.channel());
                logger.info("更新白方 channel: userId={}", userId);
            }

            // **重要：从 ChannelManager 获取双方最新的活跃 channel**
            io.netty.channel.Channel activeBlackChannel = channelManager.getChannel(room.getBlackPlayerId());
            io.netty.channel.Channel activeWhiteChannel = channelManager.getChannel(room.getWhitePlayerId());

            if (activeBlackChannel != null && activeBlackChannel.isActive()) {
                room.setBlackChannel(activeBlackChannel);
                logger.info("从 ChannelManager 更新黑方活跃 channel: userId={}, 活跃={}",
                    room.getBlackPlayerId(), activeBlackChannel.isActive());
            } else {
                logger.warn("黑方 channel 不可用: userId={}", room.getBlackPlayerId());
            }

            if (activeWhiteChannel != null && activeWhiteChannel.isActive()) {
                room.setWhiteChannel(activeWhiteChannel);
                logger.info("从 ChannelManager 更新白方活跃 channel: userId={}, 活跃={}",
                    room.getWhitePlayerId(), activeWhiteChannel.isActive());
            } else {
                logger.warn("白方 channel 不可用: userId={}", room.getWhitePlayerId());
            }

            logger.info("handleJsonGameUndoResponse - 房间: {}, 黑方: {}, 白方: {}, 棋子数: {}, undoRequested: {}, undoRequesterId: {}",
                room.getRoomId(), room.getBlackPlayerId(), room.getWhitePlayerId(),
                room.getMoves().size(), room.hasUndoRequest(), room.getUndoRequesterId());

            // **在执行悔棋之前先获取请求者ID和channel**
            Long requesterId = room.getUndoRequesterId();
            logger.info("悔棋请求者ID: {}", requesterId);

            io.netty.channel.Channel requesterChannel = null;
            if (requesterId != null) {
                requesterChannel = requesterId.equals(room.getBlackPlayerId())
                        ? room.getBlackChannel() : room.getWhiteChannel();
                logger.info("请求者channel: {}, 是否活跃: {}",
                    requesterChannel, requesterChannel != null && requesterChannel.isActive());
            } else {
                logger.warn("requesterId 为 null！");
            }

            // 检查双方的channel状态
            logger.info("黑方channel: {}, 活跃: {}; 白方channel: {}, 活跃: {}",
                room.getBlackChannel(),
                room.getBlackChannel() != null && room.getBlackChannel().isActive(),
                room.getWhiteChannel(),
                room.getWhiteChannel() != null && room.getWhiteChannel().isActive());

            // 响应悔棋
            int[] undoneMove = room.respondUndo(userId, accepted);

            logger.info("handleJsonGameUndoResponse - respondUndo返回: {}, accepted: {}", undoneMove, accepted);

            if (undoneMove != null && accepted) {
                // 悔棋成功，广播游戏状态更新
                logger.info("✅ Undo accepted in room {}, broadcasting game state", room.getRoomId());

                // **调试：先检查能否发送消息给请求者**
                logger.info("=== 调试信息 ===");
                logger.info("请求者ID: {}", requesterId);
                logger.info("黑方ID: {}, 白方ID: {}", room.getBlackPlayerId(), room.getWhitePlayerId());
                logger.info("requesterChannel: {}, isActive: {}", requesterChannel, requesterChannel != null && requesterChannel.isActive());
                logger.info("room.getBlackChannel(): {}, isActive: {}", room.getBlackChannel(), room.getBlackChannel() != null && room.getBlackChannel().isActive());
                logger.info("room.getWhiteChannel(): {}, isActive: {}", room.getWhiteChannel(), room.getWhiteChannel() != null && room.getWhiteChannel().isActive());

                // **关键修复：直接从 ChannelManager 获取请求者的活跃 channel**
                Channel directRequesterChannel = channelManager.getChannel(requesterId);
                logger.info("从 ChannelManager 直接获取的 requester channel: {}, isActive: {}",
                    directRequesterChannel, directRequesterChannel != null && directRequesterChannel.isActive());

                // 给请求悔棋的人发送确认消息
                Channel channelToSend = (directRequesterChannel != null && directRequesterChannel.isActive())
                    ? directRequesterChannel : requesterChannel;

                if (channelToSend != null && channelToSend.isActive()) {
                    Map<String, Object> confirmMsg = Map.of("success", true, "message", "对方同意了悔棋请求");
                    logger.info("📤 准备发送确认消息给请求者: {}, 使用channel: {}", requesterId, channelToSend);
                    ResponseUtil.sendJsonResponse(channelToSend,
                        MessageType.GAME_UNDO_RESPONSE.getValue(), 0, confirmMsg);
                    logger.info("✅ 已发送悔棋成功消息给请求者: {}", requesterId);
                } else {
                    logger.error("❌ 无法发送消息给请求者 - 所有 channel 都不可用! requesterId={}", requesterId);
                }

                // 给响应悔棋的人（对方）也发送确认消息
                if (ctx.channel() != null && ctx.channel().isActive()) {
                    ResponseUtil.sendJsonResponse(ctx.channel(),
                        MessageType.GAME_UNDO_RESPONSE.getValue(), 0,
                        Map.of("success", true, "message", "已同意悔棋请求"));
                    logger.info("✅ 已发送悔棋成功消息给响应者: {}", userId);
                } else {
                    logger.error("❌ 无法发送消息给响应者 - ctx.channel() is null or not active!");
                }

                // 广播游戏状态更新给双方
                logger.info("📢 开始广播 GAME_STATE，悔棋后棋子数: {}", room.getMoves().size());
                broadcastGameState(room);
                logger.info("📢 GAME_STATE 广播完成");
            } else if (!accepted) {
                // 悔棋被拒绝
                logger.info("Undo rejected in room {}", room.getRoomId());

                // 给请求悔棋的人发送拒绝通知
                if (requesterChannel != null && requesterChannel.isActive()) {
                    ResponseUtil.sendJsonResponse(requesterChannel,
                        MessageType.GAME_UNDO_RESPONSE.getValue(), 0,
                        Map.of("success", false, "message", "对方拒绝了悔棋请求"));
                    logger.info("已发送悔棋拒绝消息给请求者: {}", requesterId);
                }

                // 给响应悔棋的人也发送确认
                if (ctx.channel() != null && ctx.channel().isActive()) {
                    ResponseUtil.sendJsonResponse(ctx.channel(),
                        MessageType.GAME_UNDO_RESPONSE.getValue(), 0,
                        Map.of("success", true, "message", "已拒绝悔棋请求"));
                    logger.info("已发送悔棋拒绝消息给响应者: {}", userId);
                }
            } else {
                logger.warn("Undo failed - undoneMove is null but accepted was true");
            }

        } catch (Exception e) {
            logger.error("Error processing GAME_UNDO_RESPONSE", e);
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_UNDO_RESPONSE.getValue(), sequenceId,
                Map.of("success", false, "message", "悔棋响应处理失败"));
        }
    }

    /**
     * 通知对方玩家有悔棋请求
     */
    private void notifyUndoRequest(GameRoom room, Long requesterId) {
        try {
            // 找到对手的 channel
            Long opponentId = requesterId.equals(room.getBlackPlayerId())
                    ? room.getWhitePlayerId() : room.getBlackPlayerId();

            io.netty.channel.Channel opponentChannel = opponentId.equals(room.getBlackPlayerId())
                    ? room.getBlackChannel() : room.getWhiteChannel();

            if (opponentChannel != null && opponentChannel.isActive()) {
                // 发送悔棋请求通知
                ResponseUtil.sendJsonResponse(opponentChannel,
                    MessageType.GAME_UNDO_REQUEST.getValue(), 0,
                    Map.of("success", true, "message", "对方请求悔棋", "from_user_id", requesterId));

                logger.info("Notified player {} about undo request from {}", opponentId, requesterId);
            }
        } catch (Exception e) {
            logger.error("Error notifying opponent about undo request", e);
        }
    }

    // 用于追踪房间内的再来一局状态
    private final Map<String, RematchState> rematchStates = new java.util.concurrent.ConcurrentHashMap<>();

    private static class RematchState {
        Long requesterId = null;
        Long responderId = null;
        boolean requesterAccepted = false;
        boolean responderAccepted = false;
    }

    /**
     * 处理再来一局请求
     */
    private void handleJsonGameRematchRequest(ChannelHandlerContext ctx, JsonNode root, long sequenceId) {
        try {
            logger.info("=== handleJsonGameRematchRequest === 收到再来一局请求");

            Long userId = AuthHandler.getUserId(ctx.channel());
            if (userId == null) {
                io.netty.util.AttributeKey<Long> TEMP_USER_ID = io.netty.util.AttributeKey.valueOf("tempUserId");
                userId = ctx.channel().attr(TEMP_USER_ID).get();
                if (userId == null) {
                    logger.warn("再来一局请求失败：用户未认证");
                    ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_REMATCH_RESPONSE.getValue(), sequenceId,
                        Map.of("success", false, "message", "请先登录"));
                    return;
                }
            }

            logger.info("再来一局请求 - 用户ID: {}", userId);

            GameRoom room = roomManager.getRoomByUserId(userId);
            if (room == null) {
                logger.warn("❌ 再来一局请求失败：用户 {} 不在任何房间中", userId);
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_REMATCH_RESPONSE.getValue(), sequenceId,
                    Map.of("success", false, "message", "您不在任何房间中"));
                return;
            }

            logger.info("✅ 再来一局请求 - 用户 {} 在房间 {} 中", userId, room.getRoomId());

            // 检查房间状态
            logger.info("房间状态 - gameState: {}, 黑方: {}, 白方: {}",
                room.getGameState(), room.getBlackPlayerId(), room.getWhitePlayerId());

            // 验证用户是否在这个房间中
            if (!userId.equals(room.getBlackPlayerId()) && !userId.equals(room.getWhitePlayerId())) {
                logger.warn("❌ 再来一局请求失败：用户 {} 不在房间 {} 的玩家列表中", userId, room.getRoomId());
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_REMATCH_RESPONSE.getValue(), sequenceId,
                    Map.of("success", false, "message", "您不在此房间中"));
                return;
            }

            RematchState state = rematchStates.computeIfAbsent(room.getRoomId(), k -> new RematchState());

            logger.info("RematchState - requesterId: {}, responderId: {}, requesterAccepted: {}, responderAccepted: {}",
                state.requesterId, state.responderId, state.requesterAccepted, state.responderAccepted);

            // 标记该玩家请求再来一局
            if (userId.equals(room.getBlackPlayerId())) {
                state.requesterId = userId;
                state.requesterAccepted = true;
                // 如果白方还没有设置，设为黑方的对手
                if (state.responderId == null) {
                    state.responderId = room.getWhitePlayerId();
                }
            } else {
                state.responderId = userId;
                state.responderAccepted = true;
                // 如果黑方还没有设置，设为白方的对手
                if (state.requesterId == null) {
                    state.requesterId = room.getBlackPlayerId();
                }
            }

            logger.info("Player {} requested rematch in room {}. State: requesterId={}, responderId={}, requesterAccepted={}, responderAccepted={}",
                userId, room.getRoomId(), state.requesterId, state.responderId, state.requesterAccepted, state.responderAccepted);

            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_REMATCH_RESPONSE.getValue(), sequenceId,
                Map.of("success", true, "message", "已发送再来一局请求"));

            // 通知对手
            notifyRematchRequest(room, userId);

            // 检查是否双方都同意
            checkAndStartRematch(room);

        } catch (Exception e) {
            logger.error("Error processing GAME_REMATCH_REQUEST", e);
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_REMATCH_RESPONSE.getValue(), sequenceId,
                Map.of("success", false, "message", "再来一局请求处理失败"));
        }
    }

    /**
     * 处理再来一局响应
     */
    private void handleJsonGameRematchResponse(ChannelHandlerContext ctx, JsonNode root, long sequenceId) {
        try {
            Long userId = AuthHandler.getUserId(ctx.channel());
            if (userId == null) {
                io.netty.util.AttributeKey<Long> TEMP_USER_ID = io.netty.util.AttributeKey.valueOf("tempUserId");
                userId = ctx.channel().attr(TEMP_USER_ID).get();
                if (userId == null) {
                    ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_REMATCH_RESPONSE.getValue(), sequenceId,
                        Map.of("success", false, "message", "请先登录"));
                    return;
                }
            }

            JsonNode body = root.get("body");
            if (body == null || !body.has("accepted")) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_REMATCH_RESPONSE.getValue(), sequenceId,
                    Map.of("success", false, "message", "缺少accepted参数"));
                return;
            }

            boolean accepted = body.get("accepted").asBoolean();

            GameRoom room = roomManager.getRoomByUserId(userId);
            if (room == null) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_REMATCH_RESPONSE.getValue(), sequenceId,
                    Map.of("success", false, "message", "您不在任何房间中"));
                return;
            }

            RematchState state = rematchStates.get(room.getRoomId());
            if (state == null) {
                ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_REMATCH_RESPONSE.getValue(), sequenceId,
                    Map.of("success", false, "message", "没有再来一局请求"));
                return;
            }

            logger.info("=== [REMATCH RESPONSE] BEFORE UPDATE: userId={}, accepted, requesterId={}, responderId={}, requesterAccepted={}, responderAccepted={} ===",
                userId, accepted, state.requesterId, state.responderId, state.requesterAccepted, state.responderAccepted);

            // 简化逻辑：直接根据rematch state中的requesterId和responderId来更新
            if (userId.equals(state.requesterId)) {
                state.requesterAccepted = accepted;
                logger.info("=== [REMATCH RESPONSE] Updated requesterAccepted to {} for user {} ===", accepted, userId);
            } else if (userId.equals(state.responderId)) {
                state.responderAccepted = accepted;
                logger.info("=== [REMATCH RESPONSE] Updated responderAccepted to {} for user {} ===", accepted, userId);
            } else {
                logger.warn("=== [REMATCH RESPONSE] User {} is neither requester ({}) nor responder ({}) ===",
                    userId, state.requesterId, state.responderId);
            }

            logger.info("Player {} responded to rematch: {} in room {}. State: requesterId={}, responderId={}, requesterAccepted={}, responderAccepted={}",
                userId, accepted, room.getRoomId(), state.requesterId, state.responderId, state.requesterAccepted, state.responderAccepted);

            // 发送响应
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_REMATCH_RESPONSE.getValue(), sequenceId,
                Map.of("success", true, "message", accepted ? "已同意再来一局" : "已拒绝再来一局"));

            // 通知对手
            notifyRematchResponse(room, userId, accepted);

            // 检查是否双方都同意
            checkAndStartRematch(room);

        } catch (Exception e) {
            logger.error("Error processing GAME_REMATCH_RESPONSE", e);
            ResponseUtil.sendJsonResponse(ctx.channel(), MessageType.GAME_REMATCH_RESPONSE.getValue(), sequenceId,
                Map.of("success", false, "message", "再来一局响应处理失败"));
        }
    }

    /**
     * 通知对手有再来一局请求
     */
    private void notifyRematchRequest(GameRoom room, Long requesterId) {
        try {
            Long opponentId = requesterId.equals(room.getBlackPlayerId())
                    ? room.getWhitePlayerId() : room.getBlackPlayerId();

            // 使用ChannelManager获取活跃的channel
            io.netty.channel.Channel opponentChannel = channelManager.getChannel(opponentId);

            logger.info("notifyRematchRequest - 对手ID: {}, 获取的channel: {}, isActive: {}",
                opponentId, opponentChannel != null ? "存在" : "null",
                opponentChannel != null && opponentChannel.isActive());

            if (opponentChannel != null && opponentChannel.isActive()) {
                ResponseUtil.sendJsonResponse(opponentChannel,
                    MessageType.GAME_REMATCH_REQUEST.getValue(), 0,
                    Map.of("success", true, "message", "对手申请再来一局"));
                logger.info("✅ Notified player {} about rematch request from {}", opponentId, requesterId);
            } else {
                logger.warn("⚠️ Cannot notify opponent {} - channel is null or inactive", opponentId);
            }
        } catch (Exception e) {
            logger.error("Error notifying opponent about rematch request", e);
        }
    }

    /**
     * 通知请求者对手的响应
     */
    private void notifyRematchResponse(GameRoom room, Long responderId, boolean accepted) {
        try {
            Long requesterId = responderId.equals(room.getBlackPlayerId())
                    ? room.getWhitePlayerId() : room.getBlackPlayerId();

            // 使用ChannelManager获取活跃的channel
            io.netty.channel.Channel requesterChannel = channelManager.getChannel(requesterId);

            logger.info("notifyRematchResponse - 请求者ID: {}, 获取的channel: {}, isActive: {}",
                requesterId, requesterChannel != null ? "存在" : "null",
                requesterChannel != null && requesterChannel.isActive());

            if (requesterChannel != null && requesterChannel.isActive()) {
                ResponseUtil.sendJsonResponse(requesterChannel,
                    MessageType.GAME_REMATCH_RESPONSE.getValue(), 0,
                    Map.of("accepted", accepted, "message", accepted ? "对方同意了再来一局" : "对方拒绝了再来一局"));
                logger.info("✅ Notified player {} about rematch response: {}", requesterId, accepted);
            } else {
                logger.warn("⚠️ Cannot notify requester {} - channel is null or inactive", requesterId);
            }
        } catch (Exception e) {
            logger.error("Error notifying requester about rematch response", e);
        }
    }

    /**
     * 检查是否双方都同意再来一局，如果是则开始新游戏
     */
    private void checkAndStartRematch(GameRoom room) {
        try {
            RematchState state = rematchStates.get(room.getRoomId());
            if (state == null) {
                logger.info("No rematch state for room {}", room.getRoomId());
                return;
            }

            logger.info("Checking rematch status for room {}: requesterId={}, responderId={}, requesterAccepted={}, responderAccepted={}",
                room.getRoomId(), state.requesterId, state.responderId, state.requesterAccepted, state.responderAccepted);

            // 检查是否双方都已响应
            if (state.requesterId != null && state.responderId != null &&
                state.requesterAccepted && state.responderAccepted) {

                logger.info("Both players agreed to rematch in room {}, starting new game", room.getRoomId());

                // 重置游戏状态
                room.restartGame();

                // 清除再来一局状态
                rematchStates.remove(room.getRoomId());

                // 广播新游戏开始
                broadcastRematchStart(room);
            } else {
                logger.info("Not all players agreed yet: requesterId={}, responderId={}, requesterAccepted={}, responderAccepted={}",
                    state.requesterId, state.responderId, state.requesterAccepted, state.responderAccepted);
            }
        } catch (Exception e) {
            logger.error("Error starting rematch", e);
        }
    }

    /**
     * 广播再来一局开始消息
     */
    private void broadcastRematchStart(GameRoom room) {
        try {
            // 先广播游戏状态（新游戏状态）
            broadcastGameState(room);

            // 使用ChannelManager获取活跃的channels
            io.netty.channel.Channel blackChannel = channelManager.getChannel(room.getBlackPlayerId());
            io.netty.channel.Channel whiteChannel = channelManager.getChannel(room.getWhitePlayerId());

            Map<String, Object> startData = new java.util.HashMap<>();
            startData.put("room_id", room.getRoomId());
            startData.put("message", "新游戏开始");
            startData.put("board", room.getBoard().toArray());
            startData.put("current_player", room.getCurrentPlayer());
            startData.put("game_state", room.getGameState().name());
            startData.put("black_remaining_time", room.getBlackPlayerRemainingTime());
            startData.put("white_remaining_time", room.getWhitePlayerRemainingTime());

            logger.info("Broadcasting rematch start for room {}, blackChannel={}, whiteChannel={}",
                room.getRoomId(),
                blackChannel != null ? blackChannel.isActive() : "null",
                whiteChannel != null ? whiteChannel.isActive() : "null");

            // 发送给黑方（添加 my_color）
            if (blackChannel != null && blackChannel.isActive()) {
                Map<String, Object> blackData = new java.util.HashMap<>(startData);
                blackData.put("my_color", 1); // 黑方
                ResponseUtil.sendJsonResponse(blackChannel,
                    MessageType.GAME_REMATCH_START.getValue(), 0, blackData);
                logger.info("✅ Sent GAME_REMATCH_START to black player in room {}", room.getRoomId());
            } else {
                logger.warn("⚠️ Black player channel is null or inactive in room {}", room.getRoomId());
            }

            // 发送给白方（添加 my_color）
            if (whiteChannel != null && whiteChannel.isActive()) {
                Map<String, Object> whiteData = new java.util.HashMap<>(startData);
                whiteData.put("my_color", 2); // 白方
                ResponseUtil.sendJsonResponse(whiteChannel,
                    MessageType.GAME_REMATCH_START.getValue(), 0, whiteData);
                logger.info("✅ Sent GAME_REMATCH_START to white player in room {}", room.getRoomId());
            } else {
                logger.warn("White player channel is null or inactive in room {}", room.getRoomId());
            }

            // 广播新的游戏状态，确保客户端获得正确的初始状态
            broadcastGameState(room);

        } catch (Exception e) {
            logger.error("Error broadcasting rematch start", e);
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

                case OBSERVER_JOIN:
                    GobangProto.ObserverJoin observerJoin = GobangProto.ObserverJoin.newBuilder()
                            .setRoomId(body.has("room_id") ? body.get("room_id").asText() : "")
                            .build();
                    return observerJoin.toByteArray();

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
